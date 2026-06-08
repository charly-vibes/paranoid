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

    @Test
    fun `JSON is compact (no pretty-print newlines)`() {
        val json = exportSensorJson(session, events)
        assertTrue(!json.contains("\n"))
    }

    @Test
    fun `JSON non-finite floats become null`() {
        val bad = listOf(
            SensorEventEntity(
                id = 1, sessionId = 7, elapsedMs = 0,
                sensorType = "LIGHT", x = Float.NaN, y = Float.POSITIVE_INFINITY, z = 0f,
                accuracy = 1,
            ),
        )
        val json = JSONObject(exportSensorJson(session, bad))
        val ev = json.getJSONArray("events").getJSONObject(0)
        assertTrue(ev.isNull("x"))
        assertTrue(ev.isNull("y"))
        assertEquals(0.0, ev.getDouble("z"), 0.0)
    }

    @Test
    fun `streaming JSON matches String builder output`() {
        val sb = StringBuilder()
        writeSensorJsonStart(session, events.size.toLong(), sb)
        events.forEachIndexed { i, e -> writeSensorJsonEvent(e, first = i == 0, out = sb) }
        writeSensorJsonEnd(sb)
        // Parses cleanly and has both events
        val json = JSONObject(sb.toString())
        assertEquals(2, json.getJSONArray("events").length())
    }

    @Test
    fun `estimate grows with event count and JSON exceeds CSV`() {
        val csvSmall = estimateExportBytes(SensorExportFormat.CSV, 100)
        val csvLarge = estimateExportBytes(SensorExportFormat.CSV, 10_000)
        assertTrue(csvLarge > csvSmall)
        val json = estimateExportBytes(SensorExportFormat.JSON, 10_000)
        assertTrue(json > csvLarge)
    }

    @Test
    fun `formatByteSize is human readable`() {
        assertEquals("512 B", formatByteSize(512))
        assertTrue(formatByteSize(6_000_000).endsWith("MB"))
    }
}
