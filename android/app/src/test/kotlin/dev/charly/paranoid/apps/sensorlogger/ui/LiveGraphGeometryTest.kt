package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SamplingRate
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.service.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 5 RED tests for `update-sensor-logger-config-and-graph`.
 *
 * Per amendment EXEC-002, test 5.1 is a behavioral assertion against the
 * geometry primitive the View consumes — not a Canvas mock. The contract:
 * for a non-empty sample list mapped into a [Band], the produced segments
 * are (a) finite, (b) at least one segment, and (c) contained within the
 * band's bounds rectangle.
 *
 * Tests 5.2 (empty/singleton placeholder) and 5.3 (frozen-profile filter)
 * follow the same decomposition pattern used in tickets 2 and 4.
 */
class LiveGraphGeometryTest {

    private val accelBand = Band(top = 0f, bottom = 100f, left = 0f, right = 800f)

    private fun sample(t: Long, x: Float, y: Float, z: Float) =
        SensorSample(elapsedMs = t, values = floatArrayOf(x, y, z))

    // ----- 5.1 RED: non-empty samples → finite, in-bounds segments -----

    @Test
    fun `non-empty samples produce N minus 1 finite segments per channel within band bounds`() {
        val samples = (0 until 600).map { i ->
            val v = i.toFloat() / 10f
            sample(i.toLong(), v, -v, v * 0.5f)
        }
        val channel0 = computeChannelStrokes(samples, channel = 0, band = accelBand)
        val channel1 = computeChannelStrokes(samples, channel = 1, band = accelBand)
        val channel2 = computeChannelStrokes(samples, channel = 2, band = accelBand)

        // exactly (N - 1) segments per channel
        assertEquals(599, channel0.size)
        assertEquals(599, channel1.size)
        assertEquals(599, channel2.size)

        for (s in (channel0 + channel1 + channel2)) {
            assertTrue("non-finite coord in $s",
                s.x1.isFinite() && s.y1.isFinite() && s.x2.isFinite() && s.y2.isFinite())
            assertTrue("x1 OOB in $s",
                s.x1 in accelBand.left..accelBand.right)
            assertTrue("x2 OOB in $s",
                s.x2 in accelBand.left..accelBand.right)
            assertTrue("y1 OOB in $s",
                s.y1 in accelBand.top..accelBand.bottom)
            assertTrue("y2 OOB in $s",
                s.y2 in accelBand.top..accelBand.bottom)
        }
    }

    @Test
    fun `single-value sensor samples produce one channel of segments`() {
        // pressure has 1 channel (values[0])
        val samples = (0 until 10).map { sample(it.toLong(), it.toFloat(), 0f, 0f) }
        val ch0 = computeChannelStrokes(samples, channel = 0, band = accelBand)
        assertEquals(9, ch0.size)
    }

    @Test
    fun `constant channel values render at the band midpoint without division by zero`() {
        val samples = (0 until 5).map { sample(it.toLong(), 7f, 7f, 7f) }
        val ch0 = computeChannelStrokes(samples, channel = 0, band = accelBand)
        assertEquals(4, ch0.size)
        for (s in ch0) {
            assertTrue("y1 should be band midpoint, was ${s.y1}", s.y1 == accelBand.midY)
            assertTrue("y2 should be band midpoint, was ${s.y2}", s.y2 == accelBand.midY)
        }
    }

    // ----- 5.2 RED: empty / singleton → flat placeholder line at band midpoint -----

    @Test
    fun `zero samples produces no segments and the placeholder helper returns a midline`() {
        val empty = emptyList<SensorSample>()
        assertTrue(computeChannelStrokes(empty, 0, accelBand).isEmpty())
        val placeholder = placeholderStroke(accelBand)
        assertEquals(accelBand.left, placeholder.x1)
        assertEquals(accelBand.right, placeholder.x2)
        assertEquals(accelBand.midY, placeholder.y1)
        assertEquals(accelBand.midY, placeholder.y2)
    }

    @Test
    fun `single sample produces no segments (uses placeholder)`() {
        val one = listOf(sample(0, 1f, 2f, 3f))
        assertTrue(computeChannelStrokes(one, 0, accelBand).isEmpty())
    }

    // ----- 5.3 RED: frozen-profile filter -----

    @Test
    fun `filterVisibleSensors keeps only sensors with frozen visibleOnGraph true`() {
        val snapshot = mapOf(
            SensorType.ACCELEROMETER to listOf(sample(0, 1f, 1f, 1f)),
            SensorType.GYROSCOPE to listOf(sample(0, 1f, 1f, 1f)),
            SensorType.MAGNETIC_FIELD to listOf(sample(0, 1f, 1f, 1f)),
        )
        val frozen = RecordingProfile(
            mapOf(
                SensorType.ACCELEROMETER to SensorCaptureSetting(true, SamplingRate.Auto, true),
                SensorType.GYROSCOPE to SensorCaptureSetting(true, SamplingRate.Auto, false),
                SensorType.MAGNETIC_FIELD to SensorCaptureSetting(false, SamplingRate.Auto, true),
            )
        )
        val filtered = filterVisibleSensors(snapshot, frozen)
        assertEquals(setOf(SensorType.ACCELEROMETER, SensorType.MAGNETIC_FIELD), filtered.keys)
    }

    @Test
    fun `filterVisibleSensors returns empty map when sessionProfile is null`() {
        val snapshot = mapOf(SensorType.ACCELEROMETER to listOf(sample(0, 1f, 1f, 1f)))
        assertEquals(emptyMap<SensorType, List<SensorSample>>(), filterVisibleSensors(snapshot, null))
    }

    // ----- 5.4 RED: rotation/recreate — initial snapshot is available synchronously -----

    @Test
    fun `initial setData payload from latest StateFlow value is non-null and applies the filter`() {
        // Models the rotation contract: on Activity.onStart the view must read
        // BOTH liveStream.value AND sessionProfile.value once before the
        // collector launches, so a configuration change does not blank the
        // graph until the next emission arrives.
        val live = mapOf(
            SensorType.ACCELEROMETER to listOf(sample(0, 1f, 0f, 0f)),
            SensorType.PRESSURE to listOf(sample(0, 1013f, 0f, 0f)),
        )
        val frozen = RecordingProfile(
            mapOf(
                SensorType.ACCELEROMETER to SensorCaptureSetting(true, SamplingRate.Auto, true),
                SensorType.PRESSURE to SensorCaptureSetting(true, SamplingRate.Auto, false),
            )
        )
        val initial = filterVisibleSensors(live, frozen)
        assertNotNull(initial)
        assertEquals(setOf(SensorType.ACCELEROMETER), initial.keys)
    }

    // ----- band layout helpers -----

    @Test
    fun `stackBands divides the view height equally between sensors`() {
        val bands = stackBands(viewWidth = 800f, viewHeight = 600f, sensorCount = 3)
        assertEquals(3, bands.size)
        assertEquals(200f, bands[0].height)
        assertEquals(200f, bands[1].height)
        assertEquals(200f, bands[2].height)
        assertEquals(0f, bands[0].top)
        assertEquals(200f, bands[1].top)
        assertEquals(400f, bands[2].top)
        for (b in bands) {
            assertEquals(0f, b.left)
            assertEquals(800f, b.right)
        }
    }

    @Test
    fun `stackBands with zero sensors returns empty list`() {
        assertEquals(emptyList<Band>(), stackBands(800f, 600f, 0))
    }
}
