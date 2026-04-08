package dev.charly.paranoid.apps.netmap

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.data.CellsJsonConverter
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapView

class RecordingDetailActivity : AppCompatActivity() {

    private lateinit var mapView: MapView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_recording_detail)

        val recordingId = intent.getStringExtra("recording_id") ?: run {
            finish(); return
        }

        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        val db = ParanoidDatabase.getInstance(this)

        lifecycleScope.launch {
            val recording = withContext(Dispatchers.IO) { db.recordingDao().getById(recordingId) }
            val measurements = withContext(Dispatchers.IO) { db.measurementDao().getByRecording(recordingId) }

            if (recording == null) { finish(); return@launch }

            findViewById<TextView>(R.id.detail_title).text = recording.name

            // Stats
            val duration = if (recording.endedAt != null) {
                val secs = (recording.endedAt - recording.startedAt) / 1000
                val h = secs / 3600
                val m = (secs % 3600) / 60
                val s = secs % 60
                if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
            } else "—"
            findViewById<TextView>(R.id.stat_duration).text = duration
            findViewById<TextView>(R.id.stat_points).text = measurements.size.toString()

            val avgRsrp = measurements.mapNotNull { m ->
                CellsJsonConverter.fromJson(m.cellsJson)
                    .firstOrNull { it.isServing }?.rsrp
            }.takeIf { it.isNotEmpty() }?.average()?.toInt()
            findViewById<TextView>(R.id.stat_signal).text =
                if (avgRsrp != null) "$avgRsrp dBm" else "—"

            // Map
            mapView.getMapAsync { map ->
                map.setStyle(MapHelper.TILE_URL) {
                    MapHelper.addTrackLayer(map, measurements)
                    MapHelper.boundsForMeasurements(measurements)?.let { bounds ->
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
                    }
                }
            }

            // Export button
            findViewById<TextView>(R.id.btn_export).setOnClickListener {
                // TODO: wire to export (PARANOID-59h)
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}
