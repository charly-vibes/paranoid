package dev.charly.paranoid.apps.sensorlogger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorExportFilterTest {

    private fun event(type: String, elapsedMs: Long, id: Long) =
        SensorEventEntity(
            id = id, sessionId = 1, elapsedMs = elapsedMs,
            sensorType = type, x = 0f, y = 0f, z = 0f, accuracy = 3,
        )

    @Test
    fun `All keeps everything`() {
        val f = SensorExportFilter(emptySet(), ExportSampling.All)
        val kept = (0L until 10L).count { f.accept(event("LIGHT", it, it)) }
        assertEquals(10, kept)
    }

    @Test
    fun `EveryNth keeps one of N per sensor independently`() {
        val f = SensorExportFilter(emptySet(), ExportSampling.EveryNth(3))
        // Interleave two sensors; each should be decimated on its own counter.
        val light = (0L until 9L).count { f.accept(event("LIGHT", it, it)) }
        val accel = (0L until 9L).count { f.accept(event("ACCELEROMETER", it, it + 100)) }
        assertEquals(3, light) // indices 0,3,6
        assertEquals(3, accel)
    }

    @Test
    fun `Interval throttles per sensor by elapsed time`() {
        val f = SensorExportFilter(emptySet(), ExportSampling.Interval(1000))
        assertTrue(f.accept(event("LIGHT", 0, 1)))     // first kept
        assertFalse(f.accept(event("LIGHT", 500, 2)))  // within 1s
        assertTrue(f.accept(event("LIGHT", 1000, 3)))  // exactly 1s later
        assertFalse(f.accept(event("LIGHT", 1500, 4)))
        assertTrue(f.accept(event("LIGHT", 2000, 5)))
    }

    @Test
    fun `type filter drops non-selected sensors`() {
        val f = SensorExportFilter(setOf("LIGHT"), ExportSampling.All)
        assertTrue(f.accept(event("LIGHT", 0, 1)))
        assertFalse(f.accept(event("GYROSCOPE", 0, 2)))
    }

    @Test
    fun `estimateSampledCount matches EveryNth ceil per sensor`() {
        // 10 and 7 events, 1 of 3 -> ceil(10/3)=4, ceil(7/3)=3
        val est = estimateSampledCount(ExportSampling.EveryNth(3), listOf(10, 7), null)
        assertEquals(7L, est)
    }

    @Test
    fun `estimateSampledCount caps interval by duration`() {
        // 1000 events over 10s, every 1s -> ~11 cap, min(1000,11)=11
        val est = estimateSampledCount(ExportSampling.Interval(1000), listOf(1000), 10_000)
        assertEquals(11L, est)
    }
}
