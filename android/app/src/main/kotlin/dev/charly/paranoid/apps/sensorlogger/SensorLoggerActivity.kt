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
import androidx.appcompat.app.AlertDialog
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.service.RecordingService
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.model.prettySensorName
import dev.charly.paranoid.apps.sensorlogger.service.SensorRecordingService
import dev.charly.paranoid.apps.sensorlogger.config.from
import dev.charly.paranoid.apps.sensorlogger.ui.SensorLoggerViewModel
import dev.charly.paranoid.apps.sensorlogger.ui.START_GATE_HINT
import dev.charly.paranoid.apps.sensorlogger.ui.canStartRecording
import dev.charly.paranoid.apps.sensorlogger.ui.isLiveGraphButtonEnabled
import dev.charly.paranoid.apps.sensorlogger.ui.shouldShowDefaultsDialog
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
    private lateinit var startGateHint: TextView
    private lateinit var configureBtn: TextView
    private lateinit var liveGraphBtn: TextView

    private lateinit var profileStore: RecordingProfileStore
    /**
     * True once the first-launch defaults dialog has appeared for this
     * Activity instance. Prevents the dialog from re-appearing if
     * `hasSeenDefaultsDialog()` re-emits `false` before
     * `markDefaultsDialogSeen()`'s DataStore write commits.
     */
    private var defaultsDialogShownInSession: Boolean = false
    private var startEnabledByGate: Boolean = true

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
        startGateHint = findViewById(R.id.tv_start_gate_hint)
        configureBtn = findViewById(R.id.btn_configure)
        liveGraphBtn = findViewById(R.id.btn_live_graph)

        profileStore = RecordingProfileStore.from(this)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_sessions).setOnClickListener {
            startActivity(Intent(this, SensorSessionsActivity::class.java))
        }
        findViewById<TextView>(R.id.btn_share_log).setOnClickListener {
            DebugLog.dumpAndShare(this)
        }
        configureBtn.setOnClickListener {
            startActivity(Intent(this, SensorCaptureConfigActivity::class.java))
        }
        liveGraphBtn.setOnClickListener {
            startActivity(Intent(this, SensorLiveGraphActivity::class.java))
        }

        startStopBtn.setOnClickListener {
            val svc = sensorService
            if (svc != null && svc.isRecording.value) {
                startService(SensorRecordingService.stopIntent(this))
            } else if (startEnabledByGate) {
                startService(SensorRecordingService.startIntent(this))
                bindToSensorService()
            }
        }

        lifecycleScope.launch {
            viewModel.idleSummary.collect { renderIdleSummary(it) }
        }

        // Observe the recording profile so the Start gate flips reactively.
        lifecycleScope.launch {
            profileStore.flow.collect { profile ->
                startEnabledByGate = canStartRecording(profile)
                renderStartGate()
            }
        }

        // First-launch defaults dialog.
        lifecycleScope.launch {
            profileStore.hasSeenDefaultsDialog().collect { seen ->
                if (shouldShowDefaultsDialog(seen, defaultsDialogShownInSession)) {
                    defaultsDialogShownInSession = true
                    showDefaultsDialog()
                }
            }
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
                renderLiveGraphButton(recording)
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

    private fun renderLiveGraphButton(isRecording: Boolean) {
        val enabled = isLiveGraphButtonEnabled(isRecording)
        liveGraphBtn.isEnabled = enabled
        liveGraphBtn.alpha = if (enabled) 1f else 0.4f
    }

    private fun renderStartGate() {
        // Gate the Start button on the profile-derived predicate. When the
        // service is already recording the Stop label takes over, so the
        // gate only affects the "Start" affordance.
        val recording = sensorService?.isRecording?.value == true
        if (recording) {
            startStopBtn.isEnabled = true
            startStopBtn.alpha = 1f
            startGateHint.visibility = View.GONE
            return
        }
        startStopBtn.isEnabled = startEnabledByGate
        startStopBtn.alpha = if (startEnabledByGate) 1f else 0.4f
        startGateHint.text = START_GATE_HINT
        startGateHint.visibility = if (startEnabledByGate) View.GONE else View.VISIBLE
    }

    private fun showDefaultsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sensor recording defaults")
            .setMessage(
                "Recording now defaults to accelerometer, gyroscope, and linear " +
                    "acceleration only. Open Configure capture to enable other " +
                    "sensors or change rates."
            )
            .setPositiveButton("Open Configure capture") { _, _ ->
                startActivity(Intent(this, SensorCaptureConfigActivity::class.java))
            }
            .setNegativeButton("Got it", null)
            // One persistence path: fires on positive, negative, back press,
            // and outside tap — no duplicate writes from per-button handlers.
            .setOnDismissListener {
                lifecycleScope.launch { profileStore.markDefaultsDialogSeen() }
            }
            .setCancelable(true)
            .show()
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
        // Re-apply the Start gate so disabling/enabling tracks the latest
        // recording state (Stop is always enabled while recording; Start
        // is gated on the profile predicate when idle).
        renderStartGate()
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
