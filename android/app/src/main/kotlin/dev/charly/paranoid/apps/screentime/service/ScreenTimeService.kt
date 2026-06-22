package dev.charly.paranoid.apps.screentime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.screentime.CheckpointScheduler
import dev.charly.paranoid.apps.screentime.DEFAULT_DEBOUNCE_MILLIS
import dev.charly.paranoid.apps.screentime.ForegroundAppSampler
import dev.charly.paranoid.apps.screentime.OverlayProgress
import dev.charly.paranoid.apps.screentime.SessionStateMachine
import dev.charly.paranoid.apps.screentime.StaleSessionResolver
import dev.charly.paranoid.apps.screentime.data.ScreenTimeDao
import dev.charly.paranoid.apps.screentime.data.SessionEntity
import dev.charly.paranoid.apps.screentime.data.toEntities
import dev.charly.paranoid.apps.screentime.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that keeps screen-time monitoring alive. While running it:
 * - shows a persistent notification (required for a foreground service),
 * - listens for screen on/off via a dynamically registered [BroadcastReceiver]
 *   (these system broadcasts cannot be declared in the manifest),
 * - drives the pure [SessionStateMachine], using a [Handler] to apply the 30 s screen-off
 *   debounce, and persists sessions to [ScreenTimeDao].
 *
 * Foreground app sampling (the 5 s poll loop) is added in ticket 4; this class provides the
 * lifecycle and screen-event wiring.
 */
class ScreenTimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debounceHandler = Handler(Looper.getMainLooper())
    private val checkpointHandler = Handler(Looper.getMainLooper())

    private lateinit var dao: ScreenTimeDao
    private lateinit var sampler: ForegroundAppSampler
    private lateinit var checkpointScheduler: CheckpointScheduler
    private lateinit var overlayManager: OverlayManager
    private val machine = SessionStateMachine(DEFAULT_DEBOUNCE_MILLIS)

    /** Row id of the currently open session, or null when no session is active. */
    private var currentSessionId: Long? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var samplingJob: Job? = null
    private var usageAccessWarning = false

    private val debounceRunnable = Runnable {
        val closed = machine.onScreenOffElapsed(System.currentTimeMillis())
        if (closed != null) finishPersistingSession(closed)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        dao = ParanoidDatabase.getInstance(this).screenTimeDao()
        sampler = ForegroundAppSampler(
            source = AndroidForegroundAppSource(this),
            onForegroundApp = { packageName, atMillis -> machine.onForegroundApp(packageName, atMillis) },
            onAccessUnavailable = { setUsageAccessWarning(true) },
            onAccessRestored = { setUsageAccessWarning(false) },
        )
        checkpointScheduler = CheckpointScheduler(
            timer = HandlerCheckpointTimer(checkpointHandler),
            onCheckpoint = { elapsedMillis -> postCheckpointNotification(elapsedMillis) },
        )
        overlayManager = OverlayManager(this)
        createNotificationChannel()
        registerScreenReceiver()
        startSamplingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        ScreenTimeMonitoringPrefs.setEnabled(this, true)
        startForeground(NOTIFICATION_ID, buildNotification())
        recoverStaleOpenSession(serviceStartMillis = System.currentTimeMillis())
        MorningReportScheduler.enqueueNext(this)
    }

    private fun stopMonitoring() {
        ScreenTimeMonitoringPrefs.setEnabled(this, false)
        MorningReportScheduler.cancel(this)
        debounceHandler.removeCallbacks(debounceRunnable)
        // Close the active session at the current time before tearing down.
        val now = System.currentTimeMillis()
        machine.onScreenOff(now)
        val closed = machine.onScreenOffElapsed(now + DEFAULT_DEBOUNCE_MILLIS)
        if (closed != null) finishPersistingSession(closed)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Screen events -----------------------------------------------------------------

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> handleScreenOn(System.currentTimeMillis())
                    Intent.ACTION_SCREEN_OFF -> handleScreenOff(System.currentTimeMillis())
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    private fun handleScreenOn(nowMillis: Long) {
        // A screen-on within the debounce window cancels the pending close.
        debounceHandler.removeCallbacks(debounceRunnable)
        val wasActive = machine.hasActiveSession
        machine.onScreenOn(nowMillis)
        if (!wasActive && machine.hasActiveSession) {
            startPersistingSession(nowMillis)
            // Fresh session: checkpoint clock starts now. A screen-on within the debounce window
            // keeps the existing schedule (we never cancel it on screen-off), so it is not reset.
            checkpointScheduler.start(startMillis = nowMillis, nowMillis = nowMillis)
            overlayManager.show()
            overlayManager.setFillFraction(0f)
        }
    }

    private fun handleScreenOff(nowMillis: Long) {
        if (!machine.hasActiveSession) return
        machine.onScreenOff(nowMillis)
        debounceHandler.removeCallbacks(debounceRunnable)
        debounceHandler.postDelayed(debounceRunnable, DEFAULT_DEBOUNCE_MILLIS)
    }

    // --- Foreground app sampling -------------------------------------------------------

    /** Polls the foreground app every [SAMPLE_INTERVAL_MS] while a session is active. */
    private fun startSamplingLoop() {
        samplingJob = scope.launch {
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                if (machine.hasActiveSession) {
                    val now = System.currentTimeMillis()
                    sampler.sample(now)
                    updateOverlayFill(now)
                }
            }
        }
    }

    /** Advances the overlay bar toward the next checkpoint based on session elapsed time. */
    private fun updateOverlayFill(nowMillis: Long) {
        val start = machine.currentSession()?.startMillis ?: return
        val elapsed = (nowMillis - start).coerceAtLeast(0L)
        overlayManager.setFillFraction(OverlayProgress.fillFraction(elapsed))
    }

    private fun setUsageAccessWarning(warning: Boolean) {
        if (usageAccessWarning == warning) return
        usageAccessWarning = warning
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    // --- Persistence -------------------------------------------------------------------

    private fun startPersistingSession(startMillis: Long) {
        ioScope.launch {
            val id = dao.insertSession(SessionEntity(startMillis = startMillis, endMillis = null))
            withContext(Dispatchers.Main) { currentSessionId = id }
        }
    }

    private fun finishPersistingSession(closed: Session) {
        // Session has ended: stop (and reset) the checkpoint clock and remove the overlay.
        checkpointScheduler.stop()
        overlayManager.hide()
        val id = currentSessionId ?: return
        currentSessionId = null
        ioScope.launch {
            dao.updateSession(SessionEntity(id = id, startMillis = closed.startMillis, endMillis = closed.endMillis))
            if (closed.appIntervals.isNotEmpty()) {
                dao.insertIntervals(closed.appIntervals.toEntities(sessionId = id))
            }
        }
    }

    /**
     * Closes any session left open in the store (e.g. after the OS killed the service). The end
     * time is resolved by [StaleSessionResolver]: last sample time if known, else the service
     * start time, clamped to the session's start day.
     */
    private fun recoverStaleOpenSession(serviceStartMillis: Long) {
        ioScope.launch {
            val open = dao.openSession() ?: return@launch
            val lastSample = dao.intervalsForSession(open.id).maxOfOrNull { it.endMillis }
            val endMillis = StaleSessionResolver.resolveEnd(
                sessionStartMillis = open.startMillis,
                lastSampleMillis = lastSample,
                serviceStartMillis = serviceStartMillis,
            )
            dao.updateSession(open.copy(endMillis = endMillis))
        }
    }

    // --- Notification ------------------------------------------------------------------

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ongoing = NotificationChannel(
            CHANNEL_ID,
            "Screen time monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Tracks continuous screen-on sessions"
            setShowBadge(false)
        }
        val checkpoints = NotificationChannel(
            CHECKPOINT_CHANNEL_ID,
            "Screen time nudges",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Periodic reminders of how long the screen has been on"
        }
        manager.createNotificationChannel(ongoing)
        manager.createNotificationChannel(checkpoints)
    }

    private fun postCheckpointNotification(checkpointElapsedMillis: Long) {
        val minutes = checkpointElapsedMillis / 60_000L
        val notification = NotificationCompat.Builder(this, CHECKPOINT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Screen on for $minutes min")
            .setContentText("Time for a break?")
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(CHECKPOINT_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenTimeService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val contentText = if (usageAccessWarning) {
            "Usage access required for per-app times"
        } else {
            "Monitoring screen-on sessions"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Screen time")
            .setContentText(contentText)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStop)
            .build()
    }

    override fun onDestroy() {
        debounceHandler.removeCallbacks(debounceRunnable)
        checkpointScheduler.stop()
        overlayManager.hide()
        samplingJob?.cancel()
        screenReceiver?.let { unregisterReceiver(it) }
        screenReceiver = null
        scope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "screentime"
        const val CHECKPOINT_CHANNEL_ID = "screentime_checkpoints"
        const val NOTIFICATION_ID = 1004
        const val CHECKPOINT_NOTIFICATION_ID = 1005
        const val ACTION_START = "dev.charly.paranoid.START_SCREENTIME"
        const val ACTION_STOP = "dev.charly.paranoid.STOP_SCREENTIME"
        const val SAMPLE_INTERVAL_MS = 5_000L

        fun startIntent(context: Context): Intent =
            Intent(context, ScreenTimeService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenTimeService::class.java).apply { action = ACTION_STOP }
    }
}
