package dev.charly.paranoid.apps.sensorlogger.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSummaryTest {

    private fun event(type: SensorType, elapsedMs: Long = 0) = SensorEvent(
        sessionId = 1,
        elapsedMs = elapsedMs,
        sensorType = type,
        x = 0f, y = 0f, z = 0f, accuracy = 3,
    )

    @Test
    fun `empty event list returns empty map`() {
        assertEquals(emptyMap<SensorType, Int>(), countEventsBySensor(emptyList()))
    }

    @Test
    fun `counts single sensor type`() {
        val events = listOf(event(SensorType.ACCELEROMETER), event(SensorType.ACCELEROMETER))
        assertEquals(mapOf(SensorType.ACCELEROMETER to 2), countEventsBySensor(events))
    }

    @Test
    fun `counts multiple sensor types independently`() {
        val events = listOf(
            event(SensorType.ACCELEROMETER),
            event(SensorType.GYROSCOPE),
            event(SensorType.ACCELEROMETER),
            event(SensorType.PRESSURE),
        )
        val result = countEventsBySensor(events)
        assertEquals(2, result[SensorType.ACCELEROMETER])
        assertEquals(1, result[SensorType.GYROSCOPE])
        assertEquals(1, result[SensorType.PRESSURE])
    }

    @Test
    fun `durationMs returns elapsed time between start and end`() {
        val session = SensorSession(startedAt = 1_000L, endedAt = 61_000L)
        assertEquals(60_000L, session.durationMs)
    }

    @Test
    fun `durationMs returns null when session is incomplete`() {
        val session = SensorSession(startedAt = 1_000L, endedAt = null)
        assertNull(session.durationMs)
    }
}
