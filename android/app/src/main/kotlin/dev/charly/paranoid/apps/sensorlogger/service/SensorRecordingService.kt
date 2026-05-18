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
import androidx.core.app.NotificationCompat
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class SensorRecordingService : Service(), SensorEventListener2 {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var sensorManager: SensorManager
    private lateinit var db: ParanoidDatabase
    private lateinit var wakeLock: PowerManager.WakeLock

    private val buffer = SensorEventBuffer()

    private var sessionId: Long = -1L
    private var sessionStartElapsedMs: Long = 0L
    private var totalEventCount = 0
    private val _registeredSensors = mutableListOf<SensorType>()
    val registeredSensors: List<SensorType> get() = _registeredSensors.toList()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount

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

        startForeground(NOTIFICATION_ID, buildNotification(0, 0))

        scope.launch(Dispatchers.IO) {
            sessionId = db.sensorSessionDao().insert(
                SensorSessionEntity(startedAt = System.currentTimeMillis())
            )
        }

        registerAllSensors()

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
                flushBufferToDb()
            }
        }
    }

    private fun registerAllSensors() {
        SENSOR_TYPE_MAP.forEach { (androidType, domainType) ->
            val sensor = sensorManager.getDefaultSensor(androidType) ?: return@forEach
            val registered = sensorManager.registerListener(
                this, sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                BATCH_LATENCY_US
            )
            if (registered) _registeredSensors.add(domainType)
        }
    }

    override fun onSensorChanged(event: AndroidSensorEvent) {
        val type = SENSOR_TYPE_MAP[event.sensor.type] ?: return
        if (!_isRecording.value) return
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

            flushBufferToDb()

            val current = db.sensorSessionDao().getById(sessionId) ?: return
            db.sensorSessionDao().update(current.copy(endedAt = System.currentTimeMillis()))
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            _registeredSensors.clear()
            sessionId = -1L
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun flushBufferToDb() {
        val batch = buffer.flush()
        if (batch.isEmpty()) return
        db.sensorEventDao().insertBatch(batch.map { it.toEntity() })
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
