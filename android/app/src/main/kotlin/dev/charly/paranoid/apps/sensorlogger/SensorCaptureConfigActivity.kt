package dev.charly.paranoid.apps.sensorlogger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.service.SensorManagerPresenceProbe
import dev.charly.paranoid.apps.sensorlogger.service.SensorPresenceProbe
import dev.charly.paranoid.apps.sensorlogger.service.SensorRecordingService
import dev.charly.paranoid.apps.sensorlogger.ui.SensorCaptureConfigViewModel
import dev.charly.paranoid.apps.sensorlogger.ui.buildRowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SensorCaptureConfigActivity : AppCompatActivity() {

    private val viewModel: SensorCaptureConfigViewModel by viewModels()
    private lateinit var adapter: ConfigAdapter
    private lateinit var probe: SensorPresenceProbe

    // Mirrored from the bound service so the banner reactively flips when the
    // service starts or stops a recording without screen recreation.
    private val isRecordingMirror = MutableStateFlow(false)
    private var serviceBound: Boolean = false
    private var recordingService: SensorRecordingService? = null
    private var recordingObserveJob: kotlinx.coroutines.Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            val svc = (binder as SensorRecordingService.LocalBinder).service
            recordingService = svc
            recordingObserveJob = lifecycleScope.launch {
                svc.isRecording.collect { isRecordingMirror.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingObserveJob?.cancel()
            recordingObserveJob = null
            recordingService = null
            isRecordingMirror.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_capture_config)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        probe = SensorManagerPresenceProbe(sensorManager)

        val list = findViewById<RecyclerView>(R.id.sensor_list)
        val banner = findViewById<TextView>(R.id.banner_active_session)
        val saveBtn = findViewById<TextView>(R.id.btn_save)
        val cancelBtn = findViewById<TextView>(R.id.btn_cancel)
        val backBtn = findViewById<TextView>(R.id.btn_back)

        adapter = ConfigAdapter(
            probe = probe,
            onEnabledChanged = { type, on -> viewModel.state.setEnabled(type, on) },
            onVisibleChanged = { type, on -> viewModel.state.setVisibleOnGraph(type, on) },
            onRateChanged = { type, rate -> viewModel.state.setRate(type, rate) },
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        backBtn.setOnClickListener { finish() }
        cancelBtn.setOnClickListener { finish() }
        saveBtn.setOnClickListener { viewModel.save { finish() } }

        // Collect working profile -> adapter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.workingProfile.collect { profile ->
                    adapter.submit(SensorType.values().toList(), profile)
                }
            }
        }

        // Collect isRecording -> banner
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                isRecordingMirror.collect { recording ->
                    banner.visibility = if (recording) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            // BIND_AUTO_CREATE intentionally OMITTED: we observe the service
            // only if it's already running. We do not want to start the
            // foreground recorder just because the config screen opens.
            bindService(Intent(this, SensorRecordingService::class.java), serviceConnection, 0)
            serviceBound = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            recordingService = null
            recordingObserveJob?.cancel()
            recordingObserveJob = null
        }
    }

    // Exposed for testing the banner-reactivity contract.
    internal val bannerIsRecordingState: StateFlow<Boolean> get() = isRecordingMirror
}

private class ConfigAdapter(
    private val probe: SensorPresenceProbe,
    private val onEnabledChanged: (SensorType, Boolean) -> Unit,
    private val onVisibleChanged: (SensorType, Boolean) -> Unit,
    private val onRateChanged: (SensorType, SensorRateLevel) -> Unit,
) : RecyclerView.Adapter<ConfigAdapter.VH>() {

    private var types: List<SensorType> = emptyList()
    private var profile: RecordingProfile = RecordingProfile.Default

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun submit(types: List<SensorType>, profile: RecordingProfile) {
        this.types = types
        this.profile = profile
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.row_root)
        val label: TextView = view.findViewById(R.id.tv_sensor_label)
        val record: CheckBox = view.findViewById(R.id.cb_record)
        val visible: CheckBox = view.findViewById(R.id.cb_visible)
        val rate: Spinner = view.findViewById(R.id.sp_rate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sensor_capture_config, parent, false)
        )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val type = types[position]
        val setting = profile[type]
        val deviceHas = probe.hasSensor(type)
        bind(holder, type, setting, deviceHas)
    }

    private fun bind(
        holder: VH,
        type: SensorType,
        setting: SensorCaptureSetting,
        deviceHas: Boolean,
    ) {
        val state = buildRowState(type, setting, deviceHas)
        holder.label.text = state.label
        holder.root.alpha = state.alpha

        // Clear listeners before setting state to avoid spurious callbacks.
        holder.record.setOnCheckedChangeListener(null)
        holder.visible.setOnCheckedChangeListener(null)
        holder.rate.onItemSelectedListener = null

        holder.record.isChecked = setting.enabled
        holder.visible.isChecked = setting.visibleOnGraph
        holder.record.isEnabled = state.enabledControls
        holder.visible.isEnabled = state.enabledControls
        holder.rate.isEnabled = state.enabledControls

        // Spinner adapter + selection
        val rateLevels = SensorRateLevel.values()
        val rateLabels = rateLevels.map { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        val spinnerAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_item,
            rateLabels,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        holder.rate.adapter = spinnerAdapter
        holder.rate.setSelection(rateLevels.indexOf(setting.rateLevel))

        if (state.enabledControls) {
            holder.record.setOnCheckedChangeListener { _, checked ->
                onEnabledChanged(type, checked)
            }
            holder.visible.setOnCheckedChangeListener { _, checked ->
                onVisibleChanged(type, checked)
            }
            holder.rate.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        onRateChanged(type, rateLevels[position])
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                }
        }
    }

    override fun getItemCount(): Int = types.size
}
