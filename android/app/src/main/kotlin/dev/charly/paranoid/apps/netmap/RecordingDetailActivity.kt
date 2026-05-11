package dev.charly.paranoid.apps.netmap

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.data.CellsJsonConverter
import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.netmap.data.RecordingEntity
import dev.charly.paranoid.apps.netmap.data.toDomain
import dev.charly.paranoid.apps.netmap.data.toEntity
import dev.charly.paranoid.apps.netmap.data.export.ShareHelper
import dev.charly.paranoid.apps.netmap.data.export.exportCellTowers
import dev.charly.paranoid.apps.netmap.data.export.exportCsv
import dev.charly.paranoid.apps.netmap.data.export.exportGeoJson
import dev.charly.paranoid.apps.netmap.data.export.exportGpx
import dev.charly.paranoid.apps.netmap.data.export.exportKml
import dev.charly.paranoid.apps.netmap.estimate.AntennaEstimator
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

            // Lazy backfill: legacy recordings created before PARANOID-f0x
            // have no antenna estimates. Compute and persist them once,
            // off the main thread. Subsequent opens use the cached rows.
            //
            // Race note: opening detail concurrently with stopRecording()
            // can briefly observe count == 0 here while the service is
            // still computing. The DAO uses REPLACE on conflict and inputs
            // are deterministic, so the duplicate work is harmless.
            withContext(Dispatchers.IO) {
                val dao = db.antennaEstimateDao()
                if (dao.countForRecording(recordingId) == 0 && measurements.isNotEmpty()) {
                    val estimates = AntennaEstimator.estimate(
                        recordingId,
                        measurements.map { it.toDomain() }
                    )
                    if (estimates.isNotEmpty()) {
                        dao.upsertAll(estimates.map { it.toEntity() })
                    }
                }
            }

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
                showExportDialog(recording, measurements)
            }
        }
    }

    private fun showExportDialog(recording: RecordingEntity, measurements: List<MeasurementEntity>) {
        val formats = arrayOf("GeoJSON", "CSV (all cells)", "Cell Towers (estimated)", "KML", "GPX")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Export as")
            .setItems(formats) { _, which ->
                val slug = recording.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                when (which) {
                    0 -> ShareHelper.share(this, exportGeoJson(recording, measurements), "$slug.geojson", "application/geo+json")
                    1 -> ShareHelper.share(this, exportCsv(recording, measurements), "$slug.csv", "text/csv")
                    2 -> ShareHelper.share(this, exportCellTowers(measurements), "${slug}_towers.csv", "text/csv")
                    3 -> ShareHelper.share(this, exportKml(recording, measurements), "$slug.kml", "application/vnd.google-earth.kml+xml")
                    4 -> ShareHelper.share(this, exportGpx(recording, measurements), "$slug.gpx", "application/gpx+xml")
                }
            }
            .show()
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
