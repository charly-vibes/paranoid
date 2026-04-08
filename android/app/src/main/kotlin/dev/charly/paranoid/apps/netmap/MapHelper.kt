package dev.charly.paranoid.apps.netmap

import android.graphics.Color
import dev.charly.paranoid.apps.netmap.data.CellsJsonConverter
import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject

object MapHelper {
    const val TILE_URL = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

    fun signalColor(level: SignalLevel): Int = when (level) {
        SignalLevel.EXCELLENT -> Color.parseColor("#44CC44")
        SignalLevel.GOOD -> Color.parseColor("#44CCCC")
        SignalLevel.FAIR -> Color.parseColor("#CCAA44")
        SignalLevel.POOR -> Color.parseColor("#CC4444")
        SignalLevel.NONE -> Color.parseColor("#888888")
    }

    fun signalColorHex(level: SignalLevel): String = when (level) {
        SignalLevel.EXCELLENT -> "#44CC44"
        SignalLevel.GOOD -> "#44CCCC"
        SignalLevel.FAIR -> "#CCAA44"
        SignalLevel.POOR -> "#CC4444"
        SignalLevel.NONE -> "#888888"
    }

    fun addTrackLayer(map: MapLibreMap, measurements: List<MeasurementEntity>) {
        val style = map.style ?: return
        if (measurements.size < 2) return

        // Build GeoJSON with colored line segments
        val features = JSONArray()
        for (i in 0 until measurements.size - 1) {
            val a = measurements[i]
            val b = measurements[i + 1]
            val cells = CellsJsonConverter.fromJson(a.cellsJson)
            val serving = cells.firstOrNull { it.isServing }
            val level = serving?.signalLevel ?: SignalLevel.NONE

            val feature = JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", JSONArray().apply {
                        put(JSONArray().apply { put(a.lng); put(a.lat) })
                        put(JSONArray().apply { put(b.lng); put(b.lat) })
                    })
                })
                put("properties", JSONObject().apply {
                    put("color", signalColorHex(level))
                })
            }
            features.put(feature)
        }

        val geojson = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()

        // Remove old layers/sources if present
        style.removeLayer("track-layer")
        style.removeSource("track-source")

        style.addSource(GeoJsonSource("track-source", geojson))
        style.addLayer(
            LineLayer("track-layer", "track-source")
                .withProperties(
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineColor(
                        org.maplibre.android.style.expressions.Expression.get("color")
                    ),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round")
                )
        )
    }

    fun boundsForMeasurements(measurements: List<MeasurementEntity>): LatLngBounds? {
        if (measurements.isEmpty()) return null
        val builder = LatLngBounds.Builder()
        measurements.forEach { builder.include(LatLng(it.lat, it.lng)) }
        return builder.build()
    }
}
