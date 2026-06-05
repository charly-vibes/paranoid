package dev.charly.paranoid.apps.sensorlogger.config

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 8.1 / 8.2 — `SamplingRate` mapping + persistent-string round-trip
 * including legacy `v0.10.0-rc.1` decode and malformed-input fallback.
 */
class SamplingRateTest {

    // -- toSamplingPeriodUs -----------------------------------------------

    @Test
    fun `Off maps to null period`() {
        assertNull(SamplingRate.Off.toSamplingPeriodUs())
    }

    @Test
    fun `Auto maps to SensorManager SENSOR_DELAY_NORMAL`() {
        assertEquals(
            SensorManager.SENSOR_DELAY_NORMAL,
            SamplingRate.Auto.toSamplingPeriodUs(),
        )
        // Mirror constant agrees with the platform.
        assertEquals(SensorManager.SENSOR_DELAY_NORMAL, SamplingRate.SENSOR_DELAY_NORMAL)
    }

    @Test
    fun `Hz value maps to inverse-frequency microseconds`() {
        assertEquals(20_000, SamplingRate.Hz(50).toSamplingPeriodUs())
        assertEquals(10_000, SamplingRate.Hz(100).toSamplingPeriodUs())
        assertEquals(5_000, SamplingRate.Hz(200).toSamplingPeriodUs())
    }

    // -- encode / decode --------------------------------------------------

    @Test
    fun `Off encodes to OFF`() {
        assertEquals("OFF", SamplingRate.Off.encode())
        assertEquals(SamplingRate.Off, SamplingRate.decode("OFF"))
    }

    @Test
    fun `Auto encodes to AUTO`() {
        assertEquals("AUTO", SamplingRate.Auto.encode())
        assertEquals(SamplingRate.Auto, SamplingRate.decode("AUTO"))
    }

    @Test
    fun `Hz encodes to HZ colon value`() {
        assertEquals("HZ:50", SamplingRate.Hz(50).encode())
        assertEquals(SamplingRate.Hz(50), SamplingRate.decode("HZ:50"))
    }

    @Test
    fun `legacy rate names decode to equivalent SamplingRate values`() {
        assertEquals(SamplingRate.Auto,   SamplingRate.decode("NORMAL"))
        assertEquals(SamplingRate.Hz(16), SamplingRate.decode("UI"))
        assertEquals(SamplingRate.Hz(50), SamplingRate.decode("GAME"))
        assertEquals(SamplingRate.Hz(200), SamplingRate.decode("FASTEST"))
    }

    @Test
    fun `malformed HZ encodings decode to null`() {
        assertNull(SamplingRate.decode("HZ:0"))
        assertNull(SamplingRate.decode("HZ:-5"))
        assertNull(SamplingRate.decode("HZ:abc"))
        assertNull(SamplingRate.decode("HZ:"))
    }

    @Test
    fun `unknown strings decode to null`() {
        assertNull(SamplingRate.decode("FOO"))
        assertNull(SamplingRate.decode(""))
        assertNull(SamplingRate.decode("off")) // case-sensitive on purpose
    }

    @Test
    fun `Hz constructor rejects non-positive values`() {
        var threw = false
        try { SamplingRate.Hz(0) } catch (_: IllegalArgumentException) { threw = true }
        assertTrue("Hz(0) must throw", threw)
        threw = false
        try { SamplingRate.Hz(-1) } catch (_: IllegalArgumentException) { threw = true }
        assertTrue("Hz(-1) must throw", threw)
    }
}
