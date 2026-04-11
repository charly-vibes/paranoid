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

fun exportCsv(recording: RecordingEntity, measurements: List<MeasurementEntity>): String {
    val sb = StringBuilder()
    sb.appendLine("timestamp,lat,lng,accuracy_m,speed_kmh,altitude,bearing,network_type,carrier,serving,technology,mcc,mnc,cell_id,tac,pci,earfcn,band,rsrp,rsrq,rssi,rssnr,sinr,signal_level,neighbor_count")
    for (m in measurements) {
        val cells = CellsJsonConverter.fromJson(m.cellsJson)
        val neighborCount = cells.count { !it.isServing }
        if (cells.isEmpty()) {
            // No cell info — still emit a row with GPS data
            sb.appendLine(buildString {
                append(isoFormat.format(Date(m.timestamp))); append(',')
                append(m.lat); append(',')
                append(m.lng); append(',')
                append(m.accuracyM); append(',')
                append(m.speedKmh ?: ""); append(',')
                append(m.altitude ?: ""); append(',')
                append(m.bearing ?: ""); append(',')
                append(m.networkType); append(',')
                append(escapeCsv(recording.carrier ?: "")); append(',')
                // empty cell columns
                append(",,,,,,,,,,,,,0")
            })
        } else {
            for (c in cells) {
                sb.appendLine(buildString {
                    append(isoFormat.format(Date(m.timestamp))); append(',')
                    append(m.lat); append(',')
                    append(m.lng); append(',')
                    append(m.accuracyM); append(',')
                    append(m.speedKmh ?: ""); append(',')
                    append(m.altitude ?: ""); append(',')
                    append(m.bearing ?: ""); append(',')
                    append(m.networkType); append(',')
                    append(escapeCsv(recording.carrier ?: "")); append(',')
                    append(c.isServing); append(',')
                    append(c.technology.name); append(',')
                    append(c.mcc ?: ""); append(',')
                    append(c.mnc ?: ""); append(',')
                    append(c.cellId ?: ""); append(',')
                    append(c.tac ?: ""); append(',')
                    append(c.pci ?: ""); append(',')
                    append(c.earfcn ?: ""); append(',')
                    append(c.band ?: ""); append(',')
                    append(c.rsrp ?: ""); append(',')
                    append(c.rsrq ?: ""); append(',')
                    append(c.rssi ?: ""); append(',')
                    append(c.rssnr ?: ""); append(',')
                    append(c.sinr ?: ""); append(',')
                    append(c.signalLevel.name); append(',')
                    append(neighborCount)
                })
            }
        }
    }
    return sb.toString()
}

fun exportCellTowers(measurements: List<MeasurementEntity>): String {
    // Estimate cell tower positions from GPS measurements weighted by signal strength
    data class CellAccumulator(
        var weightedLat: Double = 0.0,
        var weightedLng: Double = 0.0,
        var totalWeight: Double = 0.0,
        var count: Int = 0,
        var minRsrp: Int = Int.MAX_VALUE,
        var maxRsrp: Int = Int.MIN_VALUE,
        var technology: String = "",
        var mcc: Int? = null,
        var mnc: Int? = null,
        var tac: Int? = null,
        var pci: Int? = null,
        var earfcn: Int? = null,
        var band: String? = null
    )

    val cells = mutableMapOf<Long, CellAccumulator>()

    for (m in measurements) {
        val parsed = CellsJsonConverter.fromJson(m.cellsJson)
        for (c in parsed) {
            val id = c.cellId ?: continue
            val rsrp = c.rsrp ?: continue
            val acc = cells.getOrPut(id) { CellAccumulator() }
            // Weight: stronger signal = closer to tower = higher weight
            val weight = Math.pow(10.0, rsrp / 10.0) // linear power from dBm
            acc.weightedLat += m.lat * weight
            acc.weightedLng += m.lng * weight
            acc.totalWeight += weight
            acc.count++
            if (rsrp < acc.minRsrp) acc.minRsrp = rsrp
            if (rsrp > acc.maxRsrp) acc.maxRsrp = rsrp
            acc.technology = c.technology.name
            acc.mcc = acc.mcc ?: c.mcc
            acc.mnc = acc.mnc ?: c.mnc
            acc.tac = acc.tac ?: c.tac
            acc.pci = acc.pci ?: c.pci
            acc.earfcn = acc.earfcn ?: c.earfcn
            acc.band = acc.band ?: c.band
        }
    }

    val sb = StringBuilder()
    sb.appendLine("cell_id,estimated_lat,estimated_lng,technology,mcc,mnc,tac,pci,earfcn,band,observations,min_rsrp,max_rsrp")
    for ((id, acc) in cells.entries.sortedByDescending { it.value.count }) {
        if (acc.totalWeight == 0.0) continue
        sb.appendLine(buildString {
            append(id); append(',')
            append(acc.weightedLat / acc.totalWeight); append(',')
            append(acc.weightedLng / acc.totalWeight); append(',')
            append(acc.technology); append(',')
            append(acc.mcc ?: ""); append(',')
            append(acc.mnc ?: ""); append(',')
            append(acc.tac ?: ""); append(',')
            append(acc.pci ?: ""); append(',')
            append(acc.earfcn ?: ""); append(',')
            append(acc.band ?: ""); append(',')
            append(acc.count); append(',')
            append(acc.minRsrp); append(',')
            append(acc.maxRsrp)
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

private fun escapeCsv(s: String): String =
    if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s
