package dev.charly.paranoid.apps.netmap.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dev.charly.paranoid.apps.netmap.NetMapActivity
import dev.charly.paranoid.apps.netmap.data.CellsJsonConverter
import dev.charly.paranoid.apps.netmap.data.LocationSource
import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.netmap.data.RecordingEntity
import dev.charly.paranoid.apps.netmap.data.TelephonySource
import dev.charly.paranoid.apps.netmap.data.toDomain
import dev.charly.paranoid.apps.netmap.data.toEntity
import dev.charly.paranoid.apps.netmap.estimate.AntennaEstimator
import dev.charly.paranoid.apps.netmap.model.Measurement
import dev.charly.paranoid.apps.netmap.model.GeoPoint
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val buffer = mutableListOf<MeasurementEntity>()

    private lateinit var locationSource: LocationSource
    private lateinit var telephonySource: TelephonySource
    private lateinit var db: ParanoidDatabase

    private var recordingId: String? = null
    private var startTimeMs: Long = 0L
    private var measurementCount = 0
    private var collectionJob: Job? = null
    private var notificationJob: Job? = null

    private val _latestMeasurement = MutableSharedFlow<Measurement>(replay = 1)
    val latestMeasurement: SharedFlow<Measurement> = _latestMeasurement

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    inner class LocalBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        locationSource = LocationSource(this)
        telephonySource = TelephonySource(this)
        db = ParanoidDatabase.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_RECORDING_ID) ?: return START_NOT_STICKY
                startRecording(id)
            }
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(id: String) {
        if (_isRecording.value) return
        recordingId = id
        startTimeMs = SystemClock.elapsedRealtime()
        measurementCount = 0
        _isRecording.value = true
        _count.value = 0

        startForeground(NOTIFICATION_ID, buildNotification(0, 0, SignalLevel.NONE))

        scope.launch(Dispatchers.IO) {
            val carrier = telephonySource.snapshot().carrierName
            db.recordingDao().insert(RecordingEntity(
                id = id,
                name = "Trip ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                    .format(java.util.Date())}",
                startedAt = System.currentTimeMillis(),
                carrier = carrier
            ))
        }

        collectionJob = scope.launch {
            locationSource.locationUpdates().collect { location ->
                val snapshot = telephonySource.snapshot()
                val serving = snapshot.cells.firstOrNull { it.isServing }

                val measurement = Measurement(
                    recordingId = id,
                    timestamp = System.currentTimeMillis(),
                    location = GeoPoint(location.latitude, location.longitude),
                    gpsAccuracyM = location.accuracy,
                    gpsSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else null,
                    gpsBearing = if (location.hasBearing()) location.bearing else null,
                    gpsAltitude = if (location.hasAltitude()) location.altitude else null,
                    cells = snapshot.cells,
                    networkType = snapshot.networkType,
                    dataState = dev.charly.paranoid.apps.netmap.model.DataState.CONNECTED
                )
                _latestMeasurement.emit(measurement)

                val entity = MeasurementEntity(
                    recordingId = id,
                    timestamp = measurement.timestamp,
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracyM = location.accuracy,
                    speedKmh = measurement.gpsSpeedKmh,
                    bearing = measurement.gpsBearing,
                    altitude = measurement.gpsAltitude,
                    networkType = snapshot.networkType.name,
                    dataState = "CONNECTED",
                    cellsJson = CellsJsonConverter.toJson(snapshot.cells)
                )

                synchronized(buffer) { buffer.add(entity) }
                measurementCount++
                _count.value = measurementCount

                if (buffer.size >= BATCH_SIZE) {
                    flushBuffer()
                }
            }
        }

        notificationJob = scope.launch {
            while (true) {
                delay(NOTIFICATION_UPDATE_MS)
                val elapsed = (SystemClock.elapsedRealtime() - startTimeMs) / 1000
                val serving = _latestMeasurement.replayCache.lastOrNull()
                    ?.cells?.firstOrNull { it.isServing }
                val level = serving?.signalLevel ?: SignalLevel.NONE
                updateNotification(elapsed, measurementCount, level)
            }
        }

        scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                if (buffer.isNotEmpty()) flushBuffer()
            }
        }
    }

    private fun stopRecording() {
        collectionJob?.cancel()
        notificationJob?.cancel()
        _isRecording.value = false

        scope.launch(Dispatchers.IO) {
            flushBuffer()
            val id = recordingId ?: return@launch
            val recording = db.recordingDao().getById(id) ?: return@launch
            val lastTs = db.measurementDao().lastTimestamp(id)
            db.recordingDao().update(recording.copy(endedAt = lastTs ?: System.currentTimeMillis()))
            // Compute + persist antenna estimates BEFORE deleteIfEmpty:
            // deleteIfEmpty only removes the recording when it has 0
            // measurements, in which case the estimator yields an empty list
            // and the upsert is a no-op. CASCADE handles the cleanup if
            // the recording is then removed.
            persistAntennaEstimates(id)
            db.recordingDao().deleteIfEmpty(id)
            recordingId = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Run [AntennaEstimator] over all persisted measurements for [recordingId]
     * and upsert the results. Pure local work — no network I/O.
     * See PARANOID-f0x.2 + spec netmap-data §Antenna Estimate Persistence.
     */
    private suspend fun persistAntennaEstimates(recordingId: String) {
        val measurements = db.measurementDao()
            .getByRecording(recordingId)
            .map { it.toDomain() }
        val estimates = AntennaEstimator.estimate(recordingId, measurements)
        if (estimates.isNotEmpty()) {
            db.antennaEstimateDao().upsertAll(estimates.map { it.toEntity() })
        }
    }

    private suspend fun flushBuffer() {
        val batch: List<MeasurementEntity>
        synchronized(buffer) {
            if (buffer.isEmpty()) return
            batch = buffer.toList()
            buffer.clear()
        }
        db.measurementDao().insertBatch(batch)
    }

    override fun onDestroy() {
        val id = recordingId
        if (id != null && buffer.isNotEmpty()) {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                flushBuffer()
                val recording = db.recordingDao().getById(id)
                if (recording != null) {
                    val lastTs = db.measurementDao().lastTimestamp(id)
                    db.recordingDao().update(recording.copy(
                        endedAt = lastTs ?: System.currentTimeMillis()
                    ))
                    persistAntennaEstimates(id)
                }
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active signal recording"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSec: Long, count: Int, level: SignalLevel): Notification {
        val tapIntent = Intent(this, NetMapActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val duration = formatDuration(elapsedSec)
        val levelText = level.name.lowercase().replaceFirstChar { it.uppercase() }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Recording — $duration")
            .setContentText("$count measurements · $levelText")
            .setOngoing(true)
            .setContentIntent(pendingTap)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStop)
            .build()
    }

    private fun updateNotification(elapsedSec: Long, count: Int, level: SignalLevel) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(elapsedSec, count, level))
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    companion object {
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "dev.charly.paranoid.START_RECORDING"
        const val ACTION_STOP = "dev.charly.paranoid.STOP_RECORDING"
        const val EXTRA_RECORDING_ID = "recording_id"
        const val BATCH_SIZE = 20
        const val FLUSH_INTERVAL_MS = 30_000L
        const val NOTIFICATION_UPDATE_MS = 5_000L

        fun startIntent(context: Context): Intent {
            val id = UUID.randomUUID().toString()
            return Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RECORDING_ID, id)
            }
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
