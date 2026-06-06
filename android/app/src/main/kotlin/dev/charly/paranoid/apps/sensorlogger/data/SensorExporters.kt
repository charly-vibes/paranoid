package dev.charly.paranoid.apps.sensorlogger.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure serializers for a recorded SensorLogger session. Kept free of Android
 * framework types so they can be unit-tested on the JVM.
 */

fun exportSensorCsv(session: SensorSessionEntity, events: List<SensorEventEntity>): String {
    val sb = StringBuilder()
    sb.appendLine("elapsed_ms,sensor_type,x,y,z,accuracy")
    for (e in events) {
        sb.append(e.elapsedMs).append(',')
            .append(e.sensorType).append(',')
            .append(e.x).append(',')
            .append(e.y).append(',')
            .append(e.z).append(',')
            .append(e.accuracy)
        sb.append('\n')
    }
    return sb.toString()
}

fun exportSensorJson(session: SensorSessionEntity, events: List<SensorEventEntity>): String {
    val arr = JSONArray()
    for (e in events) {
        arr.put(JSONObject().apply {
            put("elapsed_ms", e.elapsedMs)
            put("sensor_type", e.sensorType)
            put("x", e.x.toDouble())
            put("y", e.y.toDouble())
            put("z", e.z.toDouble())
            put("accuracy", e.accuracy)
        })
    }
    return JSONObject().apply {
        put("session_id", session.id)
        put("started_at", session.startedAt)
        session.endedAt?.let { put("ended_at", it) }
        put("event_count", events.size)
        put("events", arr)
    }.toString(2)
}
