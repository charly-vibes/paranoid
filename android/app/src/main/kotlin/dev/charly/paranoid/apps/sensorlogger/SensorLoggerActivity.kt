package dev.charly.paranoid.apps.sensorlogger

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.service.RecordingService
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.model.prettySensorName
import dev.charly.paranoid.apps.sensorlogger.service.SensorRecordingService
import dev.charly.paranoid.apps.sensorlogger.ui.SensorLoggerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SensorLoggerActivity : AppCompatActivity() {

    private val viewModel: SensorLoggerViewModel by viewModels()

    private var sensorService: SensorRecordingService? = null
    private var sensorBound = false
    private var netmapService: RecordingService? = null
    private var netmapBound = false

    private lateinit var elapsedView: TextView
    private lateinit var eventCountView: TextView
    private lateinit var sensorsView: TextView
    private lateinit var startStopBtn: TextView
    private lateinit var combinedNotice: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var observeJobs = mutableListOf<Job>()

    private val sensorConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            sensorService = (binder as SensorRecordingService.LocalBinder).service
            sensorBound = true
            observeSensorService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            sensorService = null
            sensorBound = false
        }
    }

    private val netmapConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            netmapService = (binder as RecordingService.LocalBinder).service
            netmapBound = true
            observeNetmapService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            netmapService = null
            netmapBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_logger)

        elapsedView = findViewById(R.id.tv_elapsed)
        eventCountView = findViewById(R.id.tv_event_count)
        sensorsView = findViewById(R.id.tv_sensors)
        startStopBtn = findViewById(R.id.btn_start_stop)
        combinedNotice = findViewById(R.id.tv_combined_notice)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_sessions).setOnClickListener {
            startActivity(Intent(this, SensorSessionsActivity::class.java))
        }

        startStopBtn.setOnClickListener {
            val svc = sensorService
            if (svc != null && svc.isRecording.value) {
                startService(SensorRecordingService.stopIntent(this))
            } else {
                startService(SensorRecordingService.startIntent(this))
                bindToSensorService()
            }
        }

        lifecycleScope.launch {
            viewModel.idleSummary.collect { renderIdleSummary(it) }
        }
    }

    override fun onStart() {
        super.onStart()
        bindToSensorService()
        bindToNetmapService()
    }

    override fun onStop() {
        super.onStop()
        stopTicking()
        observeJobs.forEach { it.cancel() }
        observeJobs.clear()
        if (sensorBound) { unbindService(sensorConnection); sensorBound = false; sensorService = null }
        if (netmapBound) { unbindService(netmapConnection); netmapBound = false; netmapService = null }
    }

    private fun bindToSensorService() {
        bindService(Intent(this, SensorRecordingService::class.java), sensorConnection, Context.BIND_AUTO_CREATE)
    }

    private fun bindToNetmapService() {
        // No BIND_AUTO_CREATE: we only want a connection if NetMap is already running.
        bindService(Intent(this, RecordingService::class.java), netmapConnection, 0)
    }

    private fun observeSensorService() {
        val svc = sensorService ?: return
        observeJobs += lifecycleScope.launch {
            svc.isRecording.collect { recording ->
                renderRecordingState(recording)
                if (recording) startTicking() else stopTicking()
                renderCombinedNotice()
            }
        }
        observeJobs += lifecycleScope.launch {
            svc.eventCount.collect { count ->
                renderEventCount(count)
            }
        }
    }

    private fun observeNetmapService() {
        val svc = netmapService ?: return
        observeJobs += lifecycleScope.launch {
            svc.isRecording.collect { renderCombinedNotice() }
        }
    }

    private fun startTicking() {
        stopTicking()
        val r = object : Runnable {
            override fun run() {
                renderElapsed()
                handler.postDelayed(this, 1000L)
            }
        }
        tickRunnable = r
        handler.post(r)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    @SuppressLint("SetTextI18n")
    private fun renderElapsed() {
        val svc = sensorService ?: return
        if (!svc.isRecording.value) return
        val elapsedSec = (SystemClock.elapsedRealtime() - svc.sessionStartElapsedMs) / 1000
        elapsedView.text = formatDuration(elapsedSec)
    }

    @SuppressLint("SetTextI18n")
    private fun renderEventCount(count: Int) {
        eventCountView.text = "$count events"
    }

    @SuppressLint("SetTextI18n")
    private fun renderRecordingState(recording: Boolean) {
        if (recording) {
            startStopBtn.text = "Stop Recording"
            sensorsView.visibility = View.VISIBLE
            val svc = sensorService
            if (svc != null) sensorsView.text = formatSensorList(svc.registeredSensors)
            renderElapsed()
        } else {
            startStopBtn.text = "Start Recording"
            elapsedView.text = "0:00"
            sensorsView.visibility = View.GONE
            // No explicit refresh needed — the ViewModel observes the DAO
            // flow and will re-emit once the service commits `endedAt`.
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderIdleSummary(state: SensorLoggerViewModel.IdleSummary) {
        // Only show the idle summary while not currently recording.
        if (sensorService?.isRecording?.value == true) return
        eventCountView.text = when (state) {
            SensorLoggerViewModel.IdleSummary.Loading -> "…"
            SensorLoggerViewModel.IdleSummary.Empty -> "No recordings yet"
            is SensorLoggerViewModel.IdleSummary.Latest ->
                "Last: ${formatDuration(state.durationSec)} · ${state.eventCount} events"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderCombinedNotice() {
        val sensorRecording = sensorService?.isRecording?.value == true
        val netmapRecording = netmapService?.isRecording?.value == true
        combinedNotice.visibility =
            if (sensorRecording && netmapRecording) View.VISIBLE else View.GONE
        combinedNotice.text = "NetMap is also recording — combined battery usage is higher"
    }

    private fun formatSensorList(sensors: List<SensorType>): String =
        if (sensors.isEmpty()) "" else sensors.joinToString(" · ") { prettySensorName(it) }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
