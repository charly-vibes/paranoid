package dev.charly.paranoid.apps.sensorlogger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent as AndroidSensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.database.sqlite.SQLiteFullException
import androidx.core.app.NotificationCompat
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.config.from
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.sensorlogger.data.toEntity
import dev.charly.paranoid.apps.sensorlogger.model.SensorEvent
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class SensorRecordingService : Service(), SensorEventListener2 {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var sensorManager: SensorManager
    private lateinit var db: ParanoidDatabase
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var recordingProfileStore: RecordingProfileStore
    private lateinit var presenceProbe: SensorPresenceProbe

    private val buffer = SensorEventBuffer()

    private var sessionId: Long = -1L
    var sessionStartElapsedMs: Long = 0L
        private set
    private var totalEventCount = 0
    private val _registeredSensors = mutableListOf<SensorType>()
    val registeredSensors: List<SensorType> get() = _registeredSensors.toList()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount

    /**
     * Snapshot of the [RecordingProfile] taken at `startRecording()`. Frozen
     * for the duration of the session — subsequent `RecordingProfileStore`
     * updates do NOT mutate this flow until the next session begins. `null`
     * when no session is active.
     */
    private val _sessionProfile = MutableStateFlow<RecordingProfile?>(null)
    val sessionProfile: StateFlow<RecordingProfile?> = _sessionProfile

    private var notificationJob: Job? = null
    private var periodicFlushJob: Job? = null
    private var flushCompleteDeferred: CompletableDeferred<Unit>? = null
    private val pendingFlushCount = AtomicInteger(0)

    inner class LocalBinder : Binder() {
        val service: SensorRecordingService get() = this@SensorRecordingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        db = ParanoidDatabase.getInstance(this)
        recordingProfileStore = RecordingProfileStore.from(this)
        presenceProbe = SensorManagerPresenceProbe(sensorManager)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> scope.launch(Dispatchers.IO) { stopRecording() }
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (_isRecording.value) return
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        totalEventCount = 0
        _isRecording.value = true
        _eventCount.value = 0
        _registeredSensors.clear()

        // Freeze the recording profile for the duration of this session.
        // Subsequent RecordingProfileStore.update(...) calls do NOT affect
        // the in-flight registration set or the write-path gate.
        //
        // runBlocking on the main thread is intentional and bounded: this is
        // a single-key read from DataStore (in-memory once the cache is warm,
        // a small file read on first launch) and we need the snapshot before
        // calling startForeground / registerListener so the foreground
        // notification reflects which sensors will actually run. The
        // alternative — async snapshot + delayed registration — would risk
        // missing Android's 5s ANR budget for foreground services.
        val snapshot = runBlocking { recordingProfileStore.flow.first() }
        _sessionProfile.value = snapshot

        startForeground(NOTIFICATION_ID, buildNotification(0, 0))

        scope.launch(Dispatchers.IO) {
            sessionId = db.sensorSessionDao().insert(
                SensorSessionEntity(startedAt = System.currentTimeMillis())
            )
        }

        registerSensorsFromProfile(snapshot)

        notificationJob = scope.launch {
            while (true) {
                delay(NOTIFICATION_UPDATE_MS)
                val elapsed = (SystemClock.elapsedRealtime() - sessionStartElapsedMs) / 1000
                updateNotification(elapsed, totalEventCount)
            }
        }

        periodicFlushJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                if (flushBufferToDb() is FlushResult.DiskFull) {
                    handleDiskFull()
                }
            }
        }
    }

    private fun registerSensorsFromProfile(profile: RecordingProfile) {
        val plan = planRegistrations(profile, presenceProbe)
        for (planned in plan) {
            val androidType = ANDROID_SENSOR_TYPE_OF[planned.type] ?: continue
            val sensor = sensorManager.getDefaultSensor(androidType) ?: continue
            val registered = sensorManager.registerListener(
                this, sensor, planned.delay, BATCH_LATENCY_US,
            )
            if (registered) _registeredSensors.add(planned.type)
        }
    }

    override fun onSensorChanged(event: AndroidSensorEvent) {
        val type = SENSOR_TYPE_MAP[event.sensor.type] ?: return
        if (!_isRecording.value) return
        // Write-path filter: visualize-only sensors (enabled=false,
        // visibleOnGraph=true) are registered with SensorManager but their
        // samples must NOT reach the persisted buffer.
        if (!shouldWrite(_sessionProfile.value, type)) return
        val elapsedMs = SystemClock.elapsedRealtime() - sessionStartElapsedMs
        val domainEvent = SensorEvent(
            sessionId = sessionId,
            elapsedMs = elapsedMs,
            sensorType = type,
            x = event.values.getOrElse(0) { 0f },
            y = event.values.getOrElse(1) { 0f },
            z = event.values.getOrElse(2) { 0f },
            accuracy = event.accuracy,
        )
        buffer.append(domainEvent)
        totalEventCount++
        _eventCount.value = totalEventCount
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun onFlushCompleted(sensor: Sensor?) {
        if (pendingFlushCount.decrementAndGet() <= 0) {
            flushCompleteDeferred?.complete(Unit)
        }
    }

    private suspend fun stopRecording() {
        notificationJob?.cancel()
        periodicFlushJob?.cancel()
        _isRecording.value = false

        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        try {
            flushCompleteDeferred = CompletableDeferred()
            val sensorCount = _registeredSensors.size
            if (sensorCount > 0) {
                pendingFlushCount.set(sensorCount)
                sensorManager.flush(this)
                withTimeoutOrNull(FLUSH_AWAIT_TIMEOUT_MS) { flushCompleteDeferred?.await() }
            }
            sensorManager.unregisterListener(this)

            when (flushBufferToDb()) {
                is FlushResult.Success -> {
                    val current = db.sensorSessionDao().getById(sessionId) ?: return
                    db.sensorSessionDao().update(current.copy(endedAt = System.currentTimeMillis()))
                }
                is FlushResult.DiskFull -> {
                    postErrorNotification()
                }
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            _registeredSensors.clear()
            _sessionProfile.value = null
            sessionId = -1L
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun flushBufferToDb(): FlushResult {
        val batch = buffer.flush()
        if (batch.isEmpty()) return FlushResult.Success
        return try {
            db.sensorEventDao().insertBatch(batch.map { it.toEntity() })
            FlushResult.Success
        } catch (e: SQLiteFullException) {
            FlushResult.DiskFull(e)
        }
    }

    private fun handleDiskFull() {
        notificationJob?.cancel()
        periodicFlushJob?.cancel()
        _isRecording.value = false
        sensorManager.unregisterListener(this)
        _registeredSensors.clear()
        _sessionProfile.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        postErrorNotification()
        stopSelf()
    }

    private fun postErrorNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Recording stopped — storage full")
            .setContentText("Free up space to record again.")
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(ERROR_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        if (_isRecording.value && sessionId != -1L) {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                sensorManager.unregisterListener(this@SensorRecordingService)
                flushBufferToDb()
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active sensor data recording"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSec: Long, eventCount: Int): Notification {
        val stopIntent = Intent(this, SensorRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Sensor Recording — ${formatDuration(elapsedSec)}")
            .setContentText("$eventCount events")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStop)
            .build()
    }

    private fun updateNotification(elapsedSec: Long, eventCount: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(elapsedSec, eventCount))
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    companion object {
        const val CHANNEL_ID = "sensor_recording"
        const val NOTIFICATION_ID = 1002
        const val ERROR_NOTIFICATION_ID = 1003
        const val ACTION_START = "dev.charly.paranoid.SENSOR_START"
        const val ACTION_STOP = "dev.charly.paranoid.SENSOR_STOP"
        const val BATCH_LATENCY_US = 5_000_000
        const val FLUSH_INTERVAL_MS = 30_000L
        const val NOTIFICATION_UPDATE_MS = 5_000L
        const val WAKE_LOCK_TAG = "paranoid:sensor_recording"
        const val WAKE_LOCK_TIMEOUT_MS = 5_000L
        const val FLUSH_AWAIT_TIMEOUT_MS = 500L

        val SENSOR_TYPE_MAP = mapOf(
            Sensor.TYPE_ACCELEROMETER to SensorType.ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE to SensorType.GYROSCOPE,
            Sensor.TYPE_LINEAR_ACCELERATION to SensorType.LINEAR_ACCELERATION,
            Sensor.TYPE_GRAVITY to SensorType.GRAVITY,
            Sensor.TYPE_ROTATION_VECTOR to SensorType.ROTATION_VECTOR,
            Sensor.TYPE_MAGNETIC_FIELD to SensorType.MAGNETIC_FIELD,
            Sensor.TYPE_PRESSURE to SensorType.PRESSURE,
            Sensor.TYPE_LIGHT to SensorType.LIGHT,
            Sensor.TYPE_PROXIMITY to SensorType.PROXIMITY,
        )

        /** Inverse of [SENSOR_TYPE_MAP]: domain [SensorType] -> Android `Sensor.TYPE_*` int. */
        val ANDROID_SENSOR_TYPE_OF: Map<SensorType, Int> =
            SENSOR_TYPE_MAP.entries.associate { (androidType, domainType) -> domainType to androidType }

        fun startIntent(context: Context) =
            Intent(context, SensorRecordingService::class.java).apply {
                action = ACTION_START
            }

        fun stopIntent(context: Context) =
            Intent(context, SensorRecordingService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
