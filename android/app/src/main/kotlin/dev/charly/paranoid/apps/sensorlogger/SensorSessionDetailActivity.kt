package dev.charly.paranoid.apps.sensorlogger

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.data.export.ShareHelper
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.model.prettySensorName
import dev.charly.paranoid.apps.sensorlogger.ui.SensorSessionDetailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SensorSessionDetailActivity : AppCompatActivity() {

    private val viewModel: SensorSessionDetailViewModel by viewModels()

    private lateinit var incompleteWarning: TextView
    private lateinit var startView: TextView
    private lateinit var endView: TextView
    private lateinit var durationView: TextView
    private lateinit var totalEventsView: TextView
    private lateinit var breakdownView: TextView
    private lateinit var markClosedBtn: TextView
    private lateinit var exportBtn: TextView
    private lateinit var deleteBtn: TextView

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId <= 0) {
            finish()
            return
        }

        setContentView(R.layout.activity_sensor_session_detail)

        incompleteWarning = findViewById(R.id.tv_incomplete_warning)
        startView = findViewById(R.id.tv_start_time)
        endView = findViewById(R.id.tv_end_time)
        durationView = findViewById(R.id.tv_duration)
        totalEventsView = findViewById(R.id.tv_total_events)
        breakdownView = findViewById(R.id.tv_sensor_breakdown)
        markClosedBtn = findViewById(R.id.btn_mark_closed)
        exportBtn = findViewById(R.id.btn_export)
        deleteBtn = findViewById(R.id.btn_delete)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        markClosedBtn.setOnClickListener { viewModel.markAsClosed() }
        exportBtn.setOnClickListener { showExportDialog() }
        deleteBtn.setOnClickListener { confirmDelete() }

        lifecycleScope.launch {
            viewModel.state.collect { renderState(it) }
        }
        lifecycleScope.launch {
            viewModel.deleted.collect { finish() }
        }
        lifecycleScope.launch {
            viewModel.exports.collect { payload ->
                ShareHelper.share(this@SensorSessionDetailActivity, payload.content, payload.filename, payload.mimeType)
            }
        }

        viewModel.load(sessionId)
    }

    private fun renderState(state: SensorSessionDetailViewModel.State) {
        when (state) {
            SensorSessionDetailViewModel.State.Loading -> Unit
            SensorSessionDetailViewModel.State.NotFound -> finish()
            is SensorSessionDetailViewModel.State.Loaded ->
                renderLoaded(state.session, state.totalEvents, state.bySensor)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderLoaded(
        session: SensorSessionEntity,
        totalEvents: Int,
        bySensor: Map<SensorType, Int>,
    ) {
        startView.text = "Start: ${dateFmt.format(Date(session.startedAt))}"
        val ended = session.endedAt
        if (ended == null) {
            incompleteWarning.visibility = View.VISIBLE
            endView.text = "End: Incomplete"
            durationView.text = "Duration: —"
            markClosedBtn.visibility = View.VISIBLE
        } else {
            incompleteWarning.visibility = View.GONE
            endView.text = "End: ${dateFmt.format(Date(ended))}"
            durationView.text = "Duration: ${formatDuration((ended - session.startedAt) / 1000)}"
            markClosedBtn.visibility = View.GONE
        }
        totalEventsView.text = "Total events: $totalEvents"
        breakdownView.text = if (bySensor.isEmpty()) {
            "(no events recorded)"
        } else {
            bySensor.entries
                .sortedByDescending { it.value }
                .joinToString("\n") { (type, count) ->
                    "%-22s %d".format(prettySensorName(type), count)
                }
        }
    }

    private fun showExportDialog() {
        val formats = SensorSessionDetailViewModel.ExportFormat.values()
        val labels = formats.map { it.extension.uppercase() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Export as")
            .setItems(labels) { _, which -> viewModel.requestExport(formats[which]) }
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete session?")
            .setMessage("This will permanently delete the session and all its events.")
            .setPositiveButton("Delete") { _, _ -> viewModel.delete() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}
