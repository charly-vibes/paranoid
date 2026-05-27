package dev.charly.paranoid.apps.sensorlogger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.service.SensorRecordingService
import dev.charly.paranoid.apps.sensorlogger.service.SensorSample
import dev.charly.paranoid.apps.sensorlogger.ui.LiveGraphView
import dev.charly.paranoid.apps.sensorlogger.ui.filterVisibleSensors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Live graph screen — host for [LiveGraphView]. Binds to
 * [SensorRecordingService] to read both `liveStream` (samples) and
 * `sessionProfile` (the frozen visibility set). The graph renders the
 * intersection of the two; mid-session `visibleOnGraph` edits do not affect
 * the rendered set because we read the frozen snapshot, not the live store.
 *
 * On Activity recreate (e.g. rotation) we read both StateFlow `value`s once
 * before launching the collector so the current snapshot renders immediately
 * — no blank gap until the next emission.
 */
class SensorLiveGraphActivity : AppCompatActivity() {

    private lateinit var graphView: LiveGraphView
    private lateinit var emptyState: TextView

    private var recordingService: SensorRecordingService? = null
    private var serviceBound: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            val svc = (binder as SensorRecordingService.LocalBinder).service
            recordingService = svc

            // Rotation safety (ticket 5.9): seed the View with the current
            // snapshot synchronously, before the collector takes over, so
            // a configuration change does not blank the graph until the
            // next periodic emission arrives.
            renderFiltered(svc.liveStream.value, svc.sessionProfile.value)

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    svc.liveStream
                        .combine(svc.sessionProfile) { snapshot, profile ->
                            snapshot to profile
                        }
                        .collect { (snapshot, profile) ->
                            renderFiltered(snapshot, profile)
                        }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            renderFiltered(emptyMap(), null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_live_graph)
        graphView = findViewById(R.id.live_graph)
        emptyState = findViewById(R.id.empty_state)
        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SensorRecordingService::class.java)
        serviceBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            recordingService = null
        }
    }

    private fun renderFiltered(
        snapshot: Map<SensorType, List<SensorSample>>,
        sessionProfile: RecordingProfile?,
    ) {
        val filtered = filterVisibleSensors(snapshot, sessionProfile)
        graphView.setData(filtered)
        when {
            sessionProfile == null -> {
                emptyState.text = "Start a recording to see live data"
                emptyState.visibility = View.VISIBLE
                graphView.visibility = View.INVISIBLE
            }
            filtered.isEmpty() -> {
                emptyState.text = "No sensors selected for visualization — open Configure capture"
                emptyState.visibility = View.VISIBLE
                graphView.visibility = View.INVISIBLE
            }
            else -> {
                emptyState.visibility = View.GONE
                graphView.visibility = View.VISIBLE
            }
        }
    }
}
