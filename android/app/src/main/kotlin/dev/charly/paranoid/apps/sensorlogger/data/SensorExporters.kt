package dev.charly.paranoid.apps.sensorlogger.data

import org.json.JSONObject

/**
 * Streaming serializers for a recorded SensorLogger session. The writers append
 * incrementally to any [Appendable] (a buffered file writer in production) so a
 * multi-hour session never materializes its full output in memory. Kept free of
 * Android framework types so they can be unit-tested on the JVM.
 */

enum class SensorExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
}

const val SENSOR_CSV_HEADER = "elapsed_ms,sensor_type,x,y,z,accuracy"

// --- CSV ---------------------------------------------------------------------

fun writeSensorCsvHeader(out: Appendable) {
    out.append(SENSOR_CSV_HEADER).append('\n')
}

fun writeSensorCsvEvents(events: Iterable<SensorEventEntity>, out: Appendable) {
    for (e in events) {
        out.append(e.elapsedMs.toString()).append(',')
            .append(e.sensorType).append(',')
            .append(e.x.toString()).append(',')
            .append(e.y.toString()).append(',')
            .append(e.z.toString()).append(',')
            .append(e.accuracy.toString()).append('\n')
    }
}

// --- JSON (compact, escaped) -------------------------------------------------

fun writeSensorJsonStart(session: SensorSessionEntity, totalEvents: Long, out: Appendable) {
    out.append("{\"session_id\":").append(session.id.toString())
        .append(",\"started_at\":").append(session.startedAt.toString())
    session.endedAt?.let { out.append(",\"ended_at\":").append(it.toString()) }
    out.append(",\"event_count\":").append(totalEvents.toString())
        .append(",\"events\":[")
}

/** Writes a single event object; [first] controls the leading comma separator. */
fun writeSensorJsonEvent(event: SensorEventEntity, first: Boolean, out: Appendable) {
    if (!first) out.append(',')
    out.append("{\"elapsed_ms\":").append(event.elapsedMs.toString())
        .append(",\"sensor_type\":").append(JSONObject.quote(event.sensorType))
        .append(",\"x\":").append(jsonNum(event.x))
        .append(",\"y\":").append(jsonNum(event.y))
        .append(",\"z\":").append(jsonNum(event.z))
        .append(",\"accuracy\":").append(event.accuracy.toString())
        .append('}')
}

fun writeSensorJsonEnd(out: Appendable) {
    out.append("]}")
}

/** JSON has no NaN/Infinity; emit `null` for non-finite values. */
private fun jsonNum(v: Float): String =
    if (v.isFinite()) v.toString() else "null"

// --- Convenience String builders (small sessions / tests) --------------------

fun exportSensorCsv(session: SensorSessionEntity, events: List<SensorEventEntity>): String {
    val sb = StringBuilder()
    writeSensorCsvHeader(sb)
    writeSensorCsvEvents(events, sb)
    return sb.toString()
}

fun exportSensorJson(session: SensorSessionEntity, events: List<SensorEventEntity>): String {
    val sb = StringBuilder()
    writeSensorJsonStart(session, events.size.toLong(), sb)
    events.forEachIndexed { i, e -> writeSensorJsonEvent(e, first = i == 0, out = sb) }
    writeSensorJsonEnd(sb)
    return sb.toString()
}

// --- Size estimation ---------------------------------------------------------

// Calibrated against real exports: 3-axis float rows dominate. CSV rows average
// ~55 bytes; compact JSON event objects average ~150 bytes.
private const val CSV_BYTES_PER_EVENT = 55L
private const val JSON_BYTES_PER_EVENT = 150L
private const val ENVELOPE_BYTES = 128L

/** Cheap upper-ish estimate of the exported file size in bytes. */
fun estimateExportBytes(format: SensorExportFormat, eventCount: Long): Long {
    val perEvent = when (format) {
        SensorExportFormat.CSV -> CSV_BYTES_PER_EVENT
        SensorExportFormat.JSON -> JSON_BYTES_PER_EVENT
    }
    return ENVELOPE_BYTES + eventCount.coerceAtLeast(0) * perEvent
}

/** Human-readable size, e.g. `6.0 MB`, `812 KB`. */
fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
