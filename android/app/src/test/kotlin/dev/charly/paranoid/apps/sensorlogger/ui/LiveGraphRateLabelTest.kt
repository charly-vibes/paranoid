package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.service.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 11.1 / 11.2 — per-band actual delivered rate label primitives.
 */
class LiveGraphRateLabelTest {

    private fun sample(elapsedMs: Long, v: Float = 0f) =
        SensorSample(elapsedMs, floatArrayOf(v))

    // -- computeRollingHz -------------------------------------------------

    @Test
    fun `zero samples returns null`() {
        assertNull(computeRollingHz(emptyList()))
    }

    @Test
    fun `single sample returns null`() {
        assertNull(computeRollingHz(listOf(sample(0))))
    }

    @Test
    fun `clock anomaly (non-positive span) returns null`() {
        // last <= first: spanMs is zero or negative; we refuse to divide.
        assertNull(computeRollingHz(listOf(sample(100), sample(100))))
        assertNull(computeRollingHz(listOf(sample(100), sample(50))))
    }

    @Test
    fun `51 samples spanning 1000 ms is approximately 50 Hz`() {
        // (N - 1) intervals over the spanMs interval — 50 gaps in 1000 ms.
        val samples = (0..50).map { sample(it * 20L) }
        val hz = computeRollingHz(samples)!!
        assertTrue("expected ~50 Hz, got $hz", kotlin.math.abs(hz - 50.0) < 0.5)
    }

    @Test
    fun `201 samples spanning 1000 ms is approximately 200 Hz`() {
        // 200 intervals spanning 1000 ms → 200 Hz.
        val samples = (0..200).map { sample(it * 5L) }
        val hz = computeRollingHz(samples)!!
        assertTrue("expected ~200 Hz, got $hz", kotlin.math.abs(hz - 200.0) < 0.5)
    }

    // -- formatRateLabel --------------------------------------------------

    @Test
    fun `null Hz formats to em-dash`() {
        assertEquals("\u2014", formatRateLabel(null))
    }

    @Test
    fun `integer rounding is half-up`() {
        assertEquals("~49 Hz", formatRateLabel(48.7))
        assertEquals("~48 Hz", formatRateLabel(48.4))
        assertEquals("~50 Hz", formatRateLabel(50.0))
    }
}
