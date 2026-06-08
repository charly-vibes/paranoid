package dev.charly.paranoid.apps.sensorlogger

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.data.export.ShareHelper
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.sensorlogger.data.formatByteSize
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

    private var totalEvents: Int = 0
    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressLabel: TextView? = null

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
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportState.collect { renderExportState(it) }
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
        this.totalEvents = totalEvents
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
        val estimates = viewModel.estimates(totalEvents)
        val labels = estimates.map { est ->
            "${est.format.extension.uppercase()}  (~${formatByteSize(est.estimatedBytes)})"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Export $totalEvents events as")
            .setItems(labels) { _, which -> viewModel.requestExport(estimates[which].format) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderExportState(state: SensorSessionDetailViewModel.ExportState) {
        when (state) {
            SensorSessionDetailViewModel.ExportState.Idle -> dismissProgress()
            is SensorSessionDetailViewModel.ExportState.Running -> showProgress(state)
            is SensorSessionDetailViewModel.ExportState.Ready -> {
                dismissProgress()
                ShareHelper.shareFile(this, state.file, state.mimeType)
                viewModel.exportConsumed(state)
            }
            is SensorSessionDetailViewModel.ExportState.Failed -> {
                dismissProgress()
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                viewModel.exportConsumed(state)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showProgress(state: SensorSessionDetailViewModel.ExportState.Running) {
        if (progressDialog == null) {
            val pad = (24 * resources.displayMetrics.density).toInt()
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
            }
            val label = TextView(this).apply { setPadding(0, 0, 0, pad / 2) }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
            }
            container.addView(label)
            container.addView(bar)
            progressLabel = label
            progressBar = bar
            progressDialog = AlertDialog.Builder(this)
                .setTitle("Exporting ${state.format.extension.uppercase()}")
                .setView(container)
                .setCancelable(false)
                .setNegativeButton("Cancel") { _, _ -> viewModel.cancelExport() }
                .create()
            progressDialog?.show()
        }
        val pct = if (state.totalEvents > 0) {
            ((state.writtenEvents * 100) / state.totalEvents).toInt()
        } else 0
        progressBar?.progress = pct
        progressLabel?.text = "${state.writtenEvents} / ${state.totalEvents} events"
    }

    private fun dismissProgress() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressLabel = null
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        progressDialog = null
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
