package dev.charly.paranoid.apps.netmap.data.export

import dev.charly.paranoid.apps.netmap.data.CellsJsonConverter
import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.data.RecordingEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun exportGeoJson(recording: RecordingEntity, measurements: List<MeasurementEntity>): String {
    val features = JSONArray()
    for (m in measurements) {
        val cells = CellsJsonConverter.fromJson(m.cellsJson)
        val serving = cells.firstOrNull { it.isServing }
        features.put(JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply { put(m.lng); put(m.lat) })
            })
            put("properties", JSONObject().apply {
                put("timestamp", isoFormat.format(Date(m.timestamp)))
                put("accuracy_m", m.accuracyM)
                m.speedKmh?.let { put("speed_kmh", it) }
                put("network_type", m.networkType)
                serving?.rsrp?.let { put("rsrp", it) }
                serving?.rsrq?.let { put("rsrq", it) }
                serving?.cellId?.let { put("cell_id", it) }
                serving?.pci?.let { put("pci", it) }
                serving?.let { put("signal_level", it.signalLevel.name) }
            })
        })
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("name", recording.name)
        put("features", features)
    }.toString(2)
}

fun exportCsv(measurements: List<MeasurementEntity>): String {
    val sb = StringBuilder()
    sb.appendLine("timestamp,lat,lng,accuracy_m,speed_kmh,network_type,rsrp,rsrq,rssi,cell_id,pci,signal_level")
    for (m in measurements) {
        val cells = CellsJsonConverter.fromJson(m.cellsJson)
        val s = cells.firstOrNull { it.isServing }
        sb.appendLine(buildString {
            append(isoFormat.format(Date(m.timestamp))); append(',')
            append(m.lat); append(',')
            append(m.lng); append(',')
            append(m.accuracyM); append(',')
            append(m.speedKmh ?: ""); append(',')
            append(m.networkType); append(',')
            append(s?.rsrp ?: ""); append(',')
            append(s?.rsrq ?: ""); append(',')
            append(s?.rssi ?: ""); append(',')
            append(s?.cellId ?: ""); append(',')
            append(s?.pci ?: ""); append(',')
            append(s?.signalLevel?.name ?: "")
        })
    }
    return sb.toString()
}

fun exportKml(recording: RecordingEntity, measurements: List<MeasurementEntity>): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
    sb.appendLine("<Document>")
    sb.appendLine("<name>${escapeXml(recording.name)}</name>")

    // Styles
    for (level in listOf("EXCELLENT" to "ff44cc44", "GOOD" to "ff44cccc", "FAIR" to "ff44aacc", "POOR" to "ff4444cc", "NONE" to "ff888888")) {
        sb.appendLine("""<Style id="${level.first}"><IconStyle><color>${level.second}</color></IconStyle></Style>""")
    }

    for (m in measurements) {
        val cells = CellsJsonConverter.fromJson(m.cellsJson)
        val s = cells.firstOrNull { it.isServing }
        val level = s?.signalLevel?.name ?: "NONE"
        sb.appendLine("<Placemark>")
        sb.appendLine("<name>${isoFormat.format(Date(m.timestamp))}</name>")
        sb.appendLine("<styleUrl>#$level</styleUrl>")
        sb.appendLine("<description>RSRP: ${s?.rsrp ?: "N/A"} dBm, ${m.networkType}</description>")
        sb.appendLine("<Point><coordinates>${m.lng},${m.lat}</coordinates></Point>")
        sb.appendLine("</Placemark>")
    }

    sb.appendLine("</Document>")
    sb.appendLine("</kml>")
    return sb.toString()
}

fun exportGpx(recording: RecordingEntity, measurements: List<MeasurementEntity>): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<gpx version="1.1" creator="paranoid-netmap" xmlns="http://www.topografix.com/GPX/1/1">""")
    sb.appendLine("<trk>")
    sb.appendLine("<name>${escapeXml(recording.name)}</name>")
    sb.appendLine("<trkseg>")
    for (m in measurements) {
        val cells = CellsJsonConverter.fromJson(m.cellsJson)
        val s = cells.firstOrNull { it.isServing }
        sb.append("""<trkpt lat="${m.lat}" lon="${m.lng}">""")
        m.altitude?.let { sb.append("<ele>$it</ele>") }
        sb.append("<time>${isoFormat.format(Date(m.timestamp))}</time>")
        if (s != null) {
            sb.append("<extensions>")
            s.rsrp?.let { sb.append("<rsrp>$it</rsrp>") }
            s.rsrq?.let { sb.append("<rsrq>$it</rsrq>") }
            sb.append("<network>${m.networkType}</network>")
            sb.append("<signal_level>${s.signalLevel.name}</signal_level>")
            sb.append("</extensions>")
        }
        sb.appendLine("</trkpt>")
    }
    sb.appendLine("</trkseg>")
    sb.appendLine("</trk>")
    sb.appendLine("</gpx>")
    return sb.toString()
}

private fun escapeXml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
