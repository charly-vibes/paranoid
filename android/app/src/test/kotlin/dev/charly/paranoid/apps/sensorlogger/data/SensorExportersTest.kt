package dev.charly.paranoid.apps.sensorlogger.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorExportersTest {

    private val session = SensorSessionEntity(
        id = 7,
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_060_000L,
    )

    private val events = listOf(
        SensorEventEntity(
            id = 1, sessionId = 7, elapsedMs = 0,
            sensorType = "ACCELEROMETER", x = 1f, y = 2f, z = 3f, accuracy = 3,
        ),
        SensorEventEntity(
            id = 2, sessionId = 7, elapsedMs = 20,
            sensorType = "LIGHT", x = 100f, y = 0f, z = 0f, accuracy = 1,
        ),
    )

    @Test
    fun `CSV has header and one row per event`() {
        val csv = exportSensorCsv(session, events)
        val lines = csv.trim().split("\n")
        assertEquals("elapsed_ms,sensor_type,x,y,z,accuracy", lines[0])
        assertEquals(3, lines.size) // header + 2 rows
        assertTrue(lines[1].startsWith("0,ACCELEROMETER,"))
        assertTrue(lines[2].startsWith("20,LIGHT,"))
    }

    @Test
    fun `CSV of empty session has only header`() {
        val csv = exportSensorCsv(session, emptyList())
        assertEquals("elapsed_ms,sensor_type,x,y,z,accuracy", csv.trim())
    }

    @Test
    fun `JSON contains session metadata and events array`() {
        val json = JSONObject(exportSensorJson(session, events))
        assertEquals(7L, json.getLong("session_id"))
        assertEquals(1_700_000_000_000L, json.getLong("started_at"))
        assertEquals(2, json.getInt("event_count"))
        val arr = json.getJSONArray("events")
        assertEquals(2, arr.length())
        assertEquals("ACCELEROMETER", arr.getJSONObject(0).getString("sensor_type"))
    }
}
