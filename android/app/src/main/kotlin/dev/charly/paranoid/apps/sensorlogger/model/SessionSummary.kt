package dev.charly.paranoid.apps.sensorlogger.model

fun countEventsBySensor(events: List<SensorEvent>): Map<SensorType, Int> =
    events.groupingBy { it.sensorType }.eachCount()

/**
 * Aggregate raw (sensorTypeName, count) rows — typically produced by a
 * `SELECT sensorType, COUNT(*) GROUP BY sensorType` SQL query — into a
 * `Map<SensorType, Int>`. Unknown sensor-type names are silently dropped
 * (forward-compatible with schemas that gain new sensor types).
 */
fun aggregateSensorCounts(rows: List<Pair<String, Int>>): Map<SensorType, Int> {
    val acc = mutableMapOf<SensorType, Int>()
    for ((name, count) in rows) {
        val type = runCatching { SensorType.valueOf(name) }.getOrNull() ?: continue
        acc[type] = (acc[type] ?: 0) + count
    }
    return acc
}

/** Display-friendly name, e.g. `LINEAR_ACCELERATION` → `Linear Acceleration`. */
fun prettySensorName(type: SensorType): String =
    type.name.lowercase().split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
