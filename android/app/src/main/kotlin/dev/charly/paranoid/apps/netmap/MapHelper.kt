package dev.charly.paranoid.apps.netmap

import android.graphics.Color
import dev.charly.paranoid.apps.netmap.data.CellsJsonConverter
import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.model.AntennaEstimate
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
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

    // ---- Antenna layer (PARANOID-f0x.3) ---------------------------------

    const val ANTENNA_MARKER_LAYER = "antenna-marker-layer"
    const val ANTENNA_MARKER_SOURCE = "antenna-marker-source"
    const val ANTENNA_CIRCLE_LAYER = "antenna-circle-layer"
    const val ANTENNA_CIRCLE_SOURCE = "antenna-circle-source"

    /** GeoJSON property carrying the JSON-serialized AntennaEstimate.cellKey. */
    const val ANTENNA_PROP_KEY = "cellKey"

    /**
     * Draw the antenna layer.
     *
     * - Always renders a colored marker at each (rendered) estimate's location.
     * - When `zoomLevel >= 12`, also renders a translucent confidence
     *   polygon (24-vertex circle approximation) of `radiusM` meters,
     *   but only for high-confidence estimates (PARANOID-f0x rc.1 smoke).
     *   At lower zooms the circles are skipped to keep the map readable.
     * - By default `showLowConfidence = false` filters out PCI-only and
     *   single-sample neighbor estimates that produced visual noise on
     *   real recordings. Pass `true` to include everything.
     */
    fun drawAntennaLayer(
        map: MapLibreMap,
        estimates: List<AntennaEstimate>,
        zoomLevel: Double,
        showLowConfidence: Boolean = false
    ) {
        val style = map.style ?: return

        // Always rebuild — caller decides when to call this.
        style.removeLayer(ANTENNA_MARKER_LAYER)
        style.removeLayer(ANTENNA_CIRCLE_LAYER)
        style.removeSource(ANTENNA_MARKER_SOURCE)
        style.removeSource(ANTENNA_CIRCLE_SOURCE)

        val visible = if (showLowConfidence) estimates else estimates.filter { !it.isLowConfidence }
        if (visible.isEmpty()) return

        // Marker source: one Point feature per estimate.
        val markerFeatures = JSONArray()
        for (e in visible) {
            markerFeatures.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply {
                        put(e.location.lng); put(e.location.lat)
                    })
                })
                put("properties", JSONObject().apply {
                    put(ANTENNA_PROP_KEY, e.cellKey)
                    put("color", signalColorHex(e.strongestSignal))
                })
            })
        }
        style.addSource(GeoJsonSource(ANTENNA_MARKER_SOURCE, JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", markerFeatures)
        }.toString()))

        style.addLayer(
            CircleLayer(ANTENNA_MARKER_LAYER, ANTENNA_MARKER_SOURCE).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF")
            )
        )

        // Confidence circles only at zoomLevel >= 12 to avoid overlap soup.
        if (zoomLevel < ANTENNA_CIRCLE_MIN_ZOOM) return

        // Only draw circles for confidently-located estimates, even when
        // showLowConfidence is on — radii from one or two samples are
        // misleading and create huge overlapping orange blobs.
        val circleSource = visible.filter { !it.isLowConfidence }
        if (circleSource.isEmpty()) return

        val circleFeatures = JSONArray()
        for (e in circleSource) {
            val coords = circlePolygonCoordinates(e.location.lat, e.location.lng, e.radiusM.toDouble())
            circleFeatures.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Polygon")
                    put("coordinates", JSONArray().apply { put(coords) })
                })
                put("properties", JSONObject().apply {
                    put("color", signalColorHex(e.strongestSignal))
                })
            })
        }
        style.addSource(GeoJsonSource(ANTENNA_CIRCLE_SOURCE, JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", circleFeatures)
        }.toString()))

        style.addLayerBelow(
            org.maplibre.android.style.layers.FillLayer(ANTENNA_CIRCLE_LAYER, ANTENNA_CIRCLE_SOURCE)
                .withProperties(
                    PropertyFactory.fillColor(Expression.get("color")),
                    PropertyFactory.fillOpacity(0.15f)
                ),
            ANTENNA_MARKER_LAYER
        )
    }

    /** Below this zoom level, antenna confidence circles are not drawn. */
    const val ANTENNA_CIRCLE_MIN_ZOOM: Double = 12.0

    /**
     * Approximate a geographic circle as a 24-vertex polygon ring.
     * Good enough for visual confidence-radius display at city scale.
     * Returned as a JSONArray of [lng, lat] pairs, ring closed.
     */
    private fun circlePolygonCoordinates(
        lat: Double, lng: Double, radiusM: Double, segments: Int = 24
    ): JSONArray {
        val ring = JSONArray()
        val rEarth = 6_371_000.0
        val angularRadius = radiusM / rEarth
        val latRad = Math.toRadians(lat)
        for (i in 0..segments) {
            val theta = 2 * Math.PI * i / segments
            // Equirectangular offset, accurate within a few % at small radii.
            val dLat = angularRadius * kotlin.math.cos(theta)
            val dLng = angularRadius * kotlin.math.sin(theta) / kotlin.math.cos(latRad)
            ring.put(JSONArray().apply {
                put(lng + Math.toDegrees(dLng))
                put(lat + Math.toDegrees(dLat))
            })
        }
        return ring
    }
}
