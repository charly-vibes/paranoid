package dev.charly.paranoid.apps.sensorlogger.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 3.1 (RED) for `update-sensor-logger-config-and-graph`:
 * pure-JVM tests of the live-stream ring buffer covering append, snapshot,
 * overflow-drops-oldest, and the capacity boundary.
 */
class FixedSizeRingBufferTest {

    private fun sample(t: Long, v: Float = t.toFloat()) =
        SensorSample(elapsedMs = t, values = floatArrayOf(v, v, v))

    @Test
    fun `new buffer has size 0 and empty snapshot`() {
        val buf = FixedSizeRingBuffer<SensorSample>(capacity = 4)
        assertEquals(0, buf.size())
        assertTrue(buf.snapshot().isEmpty())
    }

    @Test
    fun `appended items are returned in insertion order while below capacity`() {
        val buf = FixedSizeRingBuffer<SensorSample>(capacity = 4)
        buf.append(sample(1))
        buf.append(sample(2))
        buf.append(sample(3))
        val snap = buf.snapshot()
        assertEquals(listOf(1L, 2L, 3L), snap.map { it.elapsedMs })
        assertEquals(3, buf.size())
    }

    @Test
    fun `snapshot at exact capacity returns all items in order`() {
        val buf = FixedSizeRingBuffer<SensorSample>(capacity = 3)
        buf.append(sample(1))
        buf.append(sample(2))
        buf.append(sample(3))
        assertEquals(3, buf.size())
        assertEquals(listOf(1L, 2L, 3L), buf.snapshot().map { it.elapsedMs })
    }

    @Test
    fun `appending past capacity drops the oldest sample`() {
        val buf = FixedSizeRingBuffer<SensorSample>(capacity = 3)
        buf.append(sample(1))
        buf.append(sample(2))
        buf.append(sample(3))
        buf.append(sample(4))
        assertEquals(3, buf.size())
        assertEquals(listOf(2L, 3L, 4L), buf.snapshot().map { it.elapsedMs })
    }

    @Test
    fun `appending far past capacity retains only the most recent capacity items`() {
        val buf = FixedSizeRingBuffer<SensorSample>(capacity = 4)
        for (i in 1..1000) buf.append(sample(i.toLong()))
        assertEquals(4, buf.size())
        assertEquals(listOf(997L, 998L, 999L, 1000L), buf.snapshot().map { it.elapsedMs })
    }

    @Test
    fun `snapshot preserves the values FloatArray contents`() {
        val buf = FixedSizeRingBuffer<SensorSample>(capacity = 2)
        buf.append(SensorSample(1, floatArrayOf(1f, 2f, 3f)))
        buf.append(SensorSample(2, floatArrayOf(4f, 5f, 6f)))
        val snap = buf.snapshot()
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), snap[0].values, 0f)
        assertArrayEquals(floatArrayOf(4f, 5f, 6f), snap[1].values, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `capacity must be positive`() {
        FixedSizeRingBuffer<SensorSample>(capacity = 0)
    }
}
