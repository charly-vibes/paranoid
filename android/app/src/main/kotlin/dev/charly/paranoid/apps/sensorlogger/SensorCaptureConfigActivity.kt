package dev.charly.paranoid.apps.sensorlogger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
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
import dev.charly.paranoid.apps.sensorlogger.config.SamplingRate
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.service.SensorManagerPresenceProbe
import dev.charly.paranoid.apps.sensorlogger.service.SensorPresenceProbe
import dev.charly.paranoid.apps.sensorlogger.service.SensorRecordingService
import dev.charly.paranoid.apps.sensorlogger.ui.RateDraft
import dev.charly.paranoid.apps.sensorlogger.ui.RateMode
import dev.charly.paranoid.apps.sensorlogger.ui.SensorCaptureConfigViewModel
import dev.charly.paranoid.apps.sensorlogger.ui.buildRowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
            onRateModeChanged = { type, mode -> viewModel.state.setRateMode(type, mode) },
            onCustomHzChanged = { type, raw -> viewModel.state.setCustomHzInput(type, raw) },
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        backBtn.setOnClickListener { finish() }
        cancelBtn.setOnClickListener { finish() }
        saveBtn.setOnClickListener {
            if (viewModel.state.canSave.value) {
                viewModel.save { finish() }
            }
        }

        // Collect (workingProfile, drafts) -> adapter; combine ensures the
        // adapter always sees a coherent snapshot of both.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.state.workingProfile,
                    viewModel.state.drafts,
                ) { profile, drafts -> profile to drafts }
                    .collect { (profile, drafts) ->
                        adapter.submit(SensorType.values().toList(), profile, drafts)
                    }
            }
        }

        // Save button enabled state mirrors canSave
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.canSave.collect { ok ->
                    saveBtn.isEnabled = ok
                    saveBtn.alpha = if (ok) 1f else 0.4f
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
            // Per the bindService contract, the ServiceConnection is registered
            // regardless of the return value, so we always pair this with an
            // unbindService in onStop (tracked via serviceBound) to release it.
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
    private val onRateModeChanged: (SensorType, RateMode) -> Unit,
    private val onCustomHzChanged: (SensorType, String) -> Unit,
) : RecyclerView.Adapter<ConfigAdapter.VH>() {

    private var types: List<SensorType> = emptyList()
    private var profile: RecordingProfile = RecordingProfile.Default
    private var drafts: Map<SensorType, RateDraft> = emptyMap()

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun submit(
        types: List<SensorType>,
        profile: RecordingProfile,
        drafts: Map<SensorType, RateDraft>,
    ) {
        this.types = types
        this.profile = profile
        this.drafts = drafts
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.row_root)
        val label: TextView = view.findViewById(R.id.tv_sensor_label)
        val enable: CheckBox = view.findViewById(R.id.cb_enable)
        val visible: CheckBox = view.findViewById(R.id.cb_visible)
        val rateGroup: RadioGroup = view.findViewById(R.id.rg_rate_mode)
        val rbOff: RadioButton = view.findViewById(R.id.rb_off)
        val rbAuto: RadioButton = view.findViewById(R.id.rb_auto)
        val rbCustom: RadioButton = view.findViewById(R.id.rb_custom)
        val customRow: View = view.findViewById(R.id.row_custom_hz)
        val hzInput: EditText = view.findViewById(R.id.et_custom_hz)
        val hzError: TextView = view.findViewById(R.id.tv_hz_error)
        var hzWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sensor_capture_config, parent, false)
        )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val type = types[position]
        val setting = profile[type]
        val draft = drafts[type] ?: RateDraft.from(setting.samplingRate)
        val deviceHas = probe.hasSensor(type)
        bind(holder, type, setting, draft, deviceHas)
    }

    private fun bind(
        holder: VH,
        type: SensorType,
        setting: SensorCaptureSetting,
        draft: RateDraft,
        deviceHas: Boolean,
    ) {
        val state = buildRowState(type, setting, deviceHas)
        holder.label.text = state.label
        holder.root.alpha = state.alpha

        // Clear listeners before setting state to avoid spurious callbacks.
        holder.enable.setOnCheckedChangeListener(null)
        holder.visible.setOnCheckedChangeListener(null)
        holder.rateGroup.setOnCheckedChangeListener(null)
        holder.hzWatcher?.let { holder.hzInput.removeTextChangedListener(it) }
        holder.hzWatcher = null

        holder.enable.isChecked = setting.enabled
        holder.visible.isChecked = setting.visibleOnGraph
        holder.enable.isEnabled = state.enabledControls
        holder.visible.isEnabled = state.enabledControls
        holder.rbOff.isEnabled = state.enabledControls
        holder.rbAuto.isEnabled = state.enabledControls
        holder.rbCustom.isEnabled = state.enabledControls

        when (draft.mode) {
            RateMode.Off -> holder.rateGroup.check(R.id.rb_off)
            RateMode.Auto -> holder.rateGroup.check(R.id.rb_auto)
            RateMode.Custom -> holder.rateGroup.check(R.id.rb_custom)
        }

        val showCustom = draft.mode == RateMode.Custom
        holder.customRow.visibility = if (showCustom) View.VISIBLE else View.GONE
        holder.hzInput.isEnabled = state.enabledControls && showCustom
        if (holder.hzInput.text.toString() != draft.customInput) {
            holder.hzInput.setText(draft.customInput)
            // Use the EditText's ACTUAL text length (a maxLength filter may have
            // truncated draft.customInput) so setSelection never overruns.
            holder.hzInput.setSelection(holder.hzInput.text?.length ?: 0)
        }
        holder.hzError.visibility = if (showCustom && !draft.isValid) View.VISIBLE else View.GONE

        if (!state.enabledControls) return

        holder.enable.setOnCheckedChangeListener { _, checked ->
            onEnabledChanged(type, checked)
        }
        holder.visible.setOnCheckedChangeListener { _, checked ->
            onVisibleChanged(type, checked)
        }
        holder.rateGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_off -> RateMode.Off
                R.id.rb_auto -> RateMode.Auto
                R.id.rb_custom -> RateMode.Custom
                else -> return@setOnCheckedChangeListener
            }
            onRateModeChanged(type, mode)
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                onCustomHzChanged(type, s?.toString().orEmpty())
            }
        }
        holder.hzInput.addTextChangedListener(watcher)
        holder.hzWatcher = watcher
    }

    override fun getItemCount(): Int = types.size
}
