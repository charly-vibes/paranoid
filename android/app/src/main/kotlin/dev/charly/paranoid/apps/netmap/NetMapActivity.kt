package dev.charly.paranoid.apps.netmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.estimate.AntennaEstimator
import dev.charly.paranoid.apps.netmap.model.AntennaEstimate
import dev.charly.paranoid.apps.netmap.model.Measurement
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import dev.charly.paranoid.apps.netmap.service.RecordingService
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class NetMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var service: RecordingService? = null
    private var bound = false

    // Live in-memory antenna estimator state (PARANOID-f0x.3, design D7).
    // Buffer mirrors RecordingService.latestMeasurement; recomputed every
    // [LIVE_RECOMPUTE_EVERY] new measurements while the toggle is enabled.
    // Estimates are NOT persisted from here — the service does that on
    // finalize.
    private val liveBuffer: MutableList<Measurement> = mutableListOf()
    private var lastLiveEstimates: List<AntennaEstimate> = emptyList()
    private var lastLiveEstimatesByKey: Map<String, AntennaEstimate> = emptyMap()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as RecordingService.LocalBinder).service
            bound = true
            observeService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        updatePermissionState()
        map?.let { centerOnLastKnownLocation(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_netmap)

        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.setStyle(MapHelper.TILE_URL) {
                centerOnLastKnownLocation(mapLibreMap)
            }
        }

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_recordings).setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }

        findViewById<TextView>(R.id.btn_record).setOnClickListener {
            val svc = service
            if (svc != null && svc.isRecording.value) {
                startService(RecordingService.stopIntent(this))
            } else {
                startService(RecordingService.startIntent(this))
                bindToService()
            }
        }

        wireAntennaToggle()

        requestPermissions()
    }

    private fun wireAntennaToggle() {
        val toggleBtn = findViewById<TextView>(R.id.btn_antenna_toggle)
        val disclosure = findViewById<TextView>(R.id.antenna_disclosure)

        fun applyVisuals() {
            val on = antennaToggleEnabled()
            toggleBtn.setTextColor(
                android.graphics.Color.parseColor(if (on) "#44CCCC" else "#666666")
            )
            disclosure.visibility = if (on) TextView.VISIBLE else TextView.GONE
        }
        applyVisuals()

        toggleBtn.setOnClickListener {
            setAntennaToggleEnabled(!antennaToggleEnabled())
            applyVisuals()
            // Toggle ON mid-recording: immediately recompute on the current
            // buffer (don't wait for the next 10-measurement boundary).
            if (antennaToggleEnabled()) {
                recomputeLiveEstimatesAndDraw()
            } else {
                map?.let {
                    MapHelper.drawAntennaLayer(it, emptyList(), it.cameraPosition.zoom)
                }
            }
        }

        // Long-press toggles low-confidence visibility (PARANOID-f0x rc.1
        // smoke: PCI-only neighbor cells overwhelmed the map by default).
        toggleBtn.setOnLongClickListener {
            val newVal = !showLowConfidence()
            setShowLowConfidence(newVal)
            android.widget.Toast.makeText(
                this@NetMapActivity,
                if (newVal) "Antennas: showing all (incl. low-confidence)"
                else "Antennas: high-confidence only",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            if (antennaToggleEnabled() && lastLiveEstimates.isNotEmpty()) {
                map?.let {
                    MapHelper.drawAntennaLayer(
                        it, lastLiveEstimates, it.cameraPosition.zoom, newVal
                    )
                }
            }
            true
        }

        // Tap handler for antenna markers.
        mapView.getMapAsync { m ->
            m.addOnMapClickListener { latLng ->
                if (!antennaToggleEnabled()) return@addOnMapClickListener false
                val screen = m.projection.toScreenLocation(latLng)
                val features = m.queryRenderedFeatures(screen, MapHelper.ANTENNA_MARKER_LAYER)
                val key = features.firstOrNull()?.getStringProperty(MapHelper.ANTENNA_PROP_KEY)
                val hit = key?.let { lastLiveEstimatesByKey[it] }
                if (hit != null) {
                    AntennaDetailSheet.show(this@NetMapActivity, hit)
                    true
                } else false
            }
            // Re-apply circle visibility on zoom changes.
            m.addOnCameraIdleListener {
                if (antennaToggleEnabled() && lastLiveEstimates.isNotEmpty()) {
                    MapHelper.drawAntennaLayer(
                        m, lastLiveEstimates, m.cameraPosition.zoom, showLowConfidence()
                    )
                }
            }
        }
    }

    /**
     * Recompute and render in-memory estimates from [liveBuffer].
     *
     * Runs the O(n²) clustering on [Dispatchers.Default] to keep the main
     * thread responsive on long recordings. The recordingId field is set
     * to "live" — the value is informational only because these estimates
     * are NEVER persisted (the canonical persisted estimates are written
     * by [RecordingService] on finalize).
     */
    private fun recomputeLiveEstimatesAndDraw() {
        val snapshot = liveBuffer.toList()
        lifecycleScope.launch {
            val computed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                AntennaEstimator.estimate("live", snapshot)
            }
            lastLiveEstimates = computed
            lastLiveEstimatesByKey = computed.associateBy { it.cellKey }
            map?.let {
                MapHelper.drawAntennaLayer(
                    it, computed, it.cameraPosition.zoom, showLowConfidence()
                )
            }
        }
    }

    private fun antennaToggleEnabled(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ANTENNA_LIVE, false)

    private fun setAntennaToggleEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ANTENNA_LIVE, enabled).apply()
    }

    private fun showLowConfidence(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ANTENNA_LOW_CONF, false)

    private fun setShowLowConfidence(enabled: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ANTENNA_LOW_CONF, enabled).apply()
    }

    companion object {
        private const val PREFS = "netmap_prefs"
        private const val KEY_ANTENNA_LIVE = "show_antennas_live"
        private const val KEY_ANTENNA_LOW_CONF = "show_antennas_low_conf_live"
        private const val LIVE_RECOMPUTE_EVERY = 10

        /**
         * Cap on the in-memory live buffer (≈20 minutes at 2 s sample
         * interval). Older measurements are dropped for live estimation
         * only — the canonical estimates computed on finalize see all
         * persisted measurements.
         */
        private const val LIVE_BUFFER_MAX = 600
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        bindToService()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun bindToService() {
        val intent = Intent(this, RecordingService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun observeService() {
        val svc = service ?: return
        val btnRecord = findViewById<TextView>(R.id.btn_record)
        val statusText = findViewById<TextView>(R.id.recording_status)

        lifecycleScope.launch {
            svc.isRecording.collect { recording ->
                btnRecord.text = if (recording) "Stop Recording" else "Start Recording"
                statusText.visibility = if (recording) TextView.VISIBLE else TextView.GONE
            }
        }

        lifecycleScope.launch {
            svc.count.collect { count ->
                val statusText2 = findViewById<TextView>(R.id.recording_status)
                statusText2.text = "$count measurements"
            }
        }

        lifecycleScope.launch {
            svc.latestMeasurement.collect { m ->
                val serving = m.cells.firstOrNull { it.isServing }
                updateHud(serving?.rsrp, serving?.signalLevel ?: SignalLevel.NONE, m.networkType.name)

                map?.let { mapLibreMap ->
                    mapLibreMap.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLng(
                            LatLng(m.location.lat, m.location.lng)
                        )
                    )
                }

                // Feed the live antenna estimator (PARANOID-f0x.3, design D7).
                // Bounded ring-buffer: cap at LIVE_BUFFER_MAX to keep memory
                // and per-recompute cost stable on entry-level devices.
                liveBuffer.add(m)
                while (liveBuffer.size > LIVE_BUFFER_MAX) liveBuffer.removeAt(0)
                if (antennaToggleEnabled() &&
                    liveBuffer.size % LIVE_RECOMPUTE_EVERY == 0
                ) {
                    recomputeLiveEstimatesAndDraw()
                }
            }
        }

        // Reset the live buffer when a recording stops (next session starts fresh).
        lifecycleScope.launch {
            svc.isRecording.collect { recording ->
                if (!recording) {
                    liveBuffer.clear()
                    lastLiveEstimates = emptyList()
                    lastLiveEstimatesByKey = emptyMap()
                    map?.let {
                        MapHelper.drawAntennaLayer(it, emptyList(), it.cameraPosition.zoom)
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateHud(rsrp: Int?, level: SignalLevel, network: String) {
        findViewById<TextView>(R.id.hud_rsrp).apply {
            text = if (rsrp != null) "$rsrp dBm" else "— dBm"
            setTextColor(MapHelper.signalColor(level))
        }
        findViewById<TextView>(R.id.hud_level).text = level.name.lowercase()
            .replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.hud_network).text = network
    }

    @SuppressLint("MissingPermission")
    private fun centerOnLastKnownLocation(mapLibreMap: MapLibreMap) {
        val hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasLocation) {
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { location ->
                    val target = if (location != null) {
                        LatLng(location.latitude, location.longitude)
                    } else {
                        LatLng(0.0, 0.0)
                    }
                    mapLibreMap.cameraPosition = CameraPosition.Builder()
                        .target(target)
                        .zoom(14.0)
                        .build()
                }
        } else {
            mapLibreMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(0.0, 0.0))
                .zoom(2.0)
                .build()
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            updatePermissionState()
        }
    }

    private fun updatePermissionState() {
        val hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasPhone = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val ready = hasLocation && hasPhone
        val btn = findViewById<TextView>(R.id.btn_record)
        btn.isEnabled = ready
        btn.alpha = if (ready) 1f else 0.5f

        val hint = findViewById<TextView>(R.id.permission_hint)
        if (ready) {
            hint.visibility = TextView.GONE
        } else if (!hasLocation && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            hint.text = "Location permission denied. Tap to open Settings."
            hint.setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
        }
    }
}
