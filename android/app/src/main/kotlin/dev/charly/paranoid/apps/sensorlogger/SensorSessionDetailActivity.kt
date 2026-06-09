package dev.charly.paranoid.apps.sensorlogger

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
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
import dev.charly.paranoid.apps.sensorlogger.data.ExportSampling
import dev.charly.paranoid.apps.sensorlogger.data.SensorExportFormat
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.sensorlogger.data.estimateExportBytes
import dev.charly.paranoid.apps.sensorlogger.data.estimateSampledCount
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
    private var bySensor: Map<SensorType, Int> = emptyMap()
    private var sessionDurationMs: Long? = null
    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressLabel: TextView? = null
    private var configDialog: AlertDialog? = null

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
        this.bySensor = bySensor
        this.sessionDurationMs = session.endedAt?.let { it - session.startedAt }
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

    @SuppressLint("SetTextI18n")
    private fun showExportDialog() {
        if (bySensor.isEmpty()) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_export_config, null)
        val sensorContainer = view.findViewById<LinearLayout>(R.id.container_sensors)
        val samplingSpinner = view.findViewById<Spinner>(R.id.spinner_sampling)
        val formatSpinner = view.findViewById<Spinner>(R.id.spinner_format)
        val estimateView = view.findViewById<TextView>(R.id.tv_estimate)

        // Sensor checkboxes (default all checked), most-frequent first.
        val sensors = bySensor.entries.sortedByDescending { it.value }.map { it.key }
        val checks = LinkedHashMap<SensorType, CheckBox>()
        for (type in sensors) {
            val cb = CheckBox(this).apply {
                text = "${prettySensorName(type)}  (${bySensor[type]})"
                setTextColor(0xFFDDDDDD.toInt())
                isChecked = true
            }
            checks[type] = cb
            sensorContainer.addView(cb)
        }

        val samplingOptions = SAMPLING_PRESETS
        samplingSpinner.adapter = spinnerAdapter(samplingOptions.map { it.first })
        formatSpinner.adapter = spinnerAdapter(SensorExportFormat.values().map { it.extension.uppercase() })

        fun selectedTypes(): Set<SensorType> =
            checks.filterValues { it.isChecked }.keys

        fun currentSampling(): ExportSampling =
            samplingOptions[samplingSpinner.selectedItemPosition].second

        fun currentFormat(): SensorExportFormat =
            SensorExportFormat.values()[formatSpinner.selectedItemPosition]

        fun refreshEstimate() {
            val selected = selectedTypes()
            if (selected.isEmpty()) {
                estimateView.text = "Select at least one sensor"
                return
            }
            val counts = selected.map { bySensor[it] ?: 0 }
            val outEvents = estimateSampledCount(currentSampling(), counts, sessionDurationMs)
            val bytes = estimateExportBytes(currentFormat(), outEvents)
            estimateView.text = "≈ $outEvents events · ${formatByteSize(bytes)}"
        }

        checks.values.forEach { it.setOnCheckedChangeListener { _, _ -> refreshEstimate() } }
        val onSelect = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = refreshEstimate()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
        }
        samplingSpinner.onItemSelectedListener = onSelect
        formatSpinner.onItemSelectedListener = onSelect
        refreshEstimate()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Export session")
            .setView(view)
            .setPositiveButton("Export", null)
            .setNegativeButton("Cancel", null)
            .create()
        configDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = selectedTypes()
                if (selected.isEmpty()) {
                    Toast.makeText(this, "Select at least one sensor", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.requestExport(currentFormat(), selected, currentSampling())
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
            ((state.processedEvents * 100) / state.totalEvents).toInt()
        } else 0
        progressBar?.progress = pct
        progressLabel?.text = "${state.processedEvents} / ${state.totalEvents} events"
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
        configDialog?.dismiss()
        configDialog = null
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

        /** Downsampling presets shown in the export config spinner. */
        private val SAMPLING_PRESETS: List<Pair<String, ExportSampling>> = listOf(
            "All samples" to ExportSampling.All,
            "1 of 2" to ExportSampling.EveryNth(2),
            "1 of 5" to ExportSampling.EveryNth(5),
            "1 of 10" to ExportSampling.EveryNth(10),
            "1 of 50" to ExportSampling.EveryNth(50),
            "Every 0.5 s" to ExportSampling.Interval(500),
            "Every 1 s" to ExportSampling.Interval(1000),
            "Every 2 s" to ExportSampling.Interval(2000),
            "Every 5 s" to ExportSampling.Interval(5000),
            "Every 10 s" to ExportSampling.Interval(10000),
        )
    }
}
