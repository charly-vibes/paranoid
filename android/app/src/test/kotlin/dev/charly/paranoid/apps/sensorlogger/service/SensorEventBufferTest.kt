package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.model.SensorEvent
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorEventBufferTest {

    private fun event(elapsedMs: Long = 0) = SensorEvent(
        sessionId = 1,
        elapsedMs = elapsedMs,
        sensorType = SensorType.ACCELEROMETER,
        x = 0f, y = 0f, z = 0f, accuracy = 3,
    )

    @Test
    fun `flush returns all appended events`() {
        val buffer = SensorEventBuffer()
        buffer.append(event(100))
        buffer.append(event(200))
        val result = buffer.flush()
        assertEquals(2, result.size)
        assertEquals(100L, result[0].elapsedMs)
        assertEquals(200L, result[1].elapsedMs)
    }

    @Test
    fun `flush clears the buffer`() {
        val buffer = SensorEventBuffer()
        buffer.append(event(100))
        buffer.flush()
        assertTrue(buffer.flush().isEmpty())
    }

    @Test
    fun `flush on empty buffer returns empty list`() {
        val buffer = SensorEventBuffer()
        assertTrue(buffer.flush().isEmpty())
    }

    @Test
    fun `size reflects appended events`() {
        val buffer = SensorEventBuffer()
        assertEquals(0, buffer.size())
        buffer.append(event())
        buffer.append(event())
        assertEquals(2, buffer.size())
        buffer.flush()
        assertEquals(0, buffer.size())
    }
}
