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
import dev.charly.paranoid.R
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
                mapLibreMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(19.4326, -99.1332)) // Default: Mexico City
                    .zoom(12.0)
                    .build()
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

        requestPermissions()
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
