package dev.charly.paranoid.apps.screentime

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayProgressTest {

    private val minute = 60_000L
    private val tolerance = 0.0001f

    @Test
    fun `bar is empty at session start`() {
        assertEquals(0f, OverlayProgress.fillFraction(0L), tolerance)
    }

    @Test
    fun `bar is half full halfway to the first checkpoint`() {
        // First checkpoint is 7 min; halfway = 3.5 min.
        assertEquals(0.5f, OverlayProgress.fillFraction((3.5 * minute).toLong()), tolerance)
    }

    @Test
    fun `bar resets at each checkpoint`() {
        assertEquals(0f, OverlayProgress.fillFraction(7 * minute), tolerance)
        assertEquals(0f, OverlayProgress.fillFraction(13 * minute), tolerance)
        assertEquals(0f, OverlayProgress.fillFraction(29 * minute), tolerance)
    }

    @Test
    fun `bar fills proportionally between the 13 and 29 minute checkpoints`() {
        // Span is 16 min; at 21 min, 8 min elapsed within the span → 0.5.
        assertEquals(0.5f, OverlayProgress.fillFraction(21 * minute), tolerance)
    }

    @Test
    fun `bar fills proportionally within the recurring 29 minute cadence`() {
        // Between 29 and 58 min (span 29). At 43.5 min → 14.5/29 = 0.5.
        assertEquals(0.5f, OverlayProgress.fillFraction((43.5 * minute).toLong()), tolerance)
    }

    @Test
    fun `fraction is clamped to the valid range`() {
        val f = OverlayProgress.fillFraction(-1_000L)
        assertEquals(0f, f, tolerance)
    }

    private val green = 0xFF3DDC84.toInt()
    private val red = 0xFFE53935.toInt()

    @Test
    fun `bar stays green at or below the red threshold`() {
        assertEquals(green, OverlayProgress.fillColor(0f))
        assertEquals(green, OverlayProgress.fillColor(0.5f))
        assertEquals(green, OverlayProgress.fillColor(OverlayProgress.RED_THRESHOLD))
    }

    @Test
    fun `bar is fully red at full fill`() {
        assertEquals(red, OverlayProgress.fillColor(1f))
    }

    @Test
    fun `bar gets monotonically redder between threshold and full`() {
        // Above the threshold the red channel rises and the green channel falls each step.
        val samples = listOf(0.70f, 0.80f, 0.90f, 1.0f).map { OverlayProgress.fillColor(it) }
        val redChannels = samples.map { it ushr 16 and 0xFF }
        val greenChannels = samples.map { it ushr 8 and 0xFF }
        for (i in 1 until samples.size) {
            assert(redChannels[i] >= redChannels[i - 1]) { "red channel should not decrease" }
            assert(greenChannels[i] <= greenChannels[i - 1]) { "green channel should not increase" }
        }
    }

    @Test
    fun `midpoint above threshold is a blend of green and red`() {
        // At 85% we are halfway through the 70%..100% ramp.
        val color = OverlayProgress.fillColor(0.85f)
        val r = color ushr 16 and 0xFF
        val expectedR = (0x3D + (0xE5 - 0x3D) * 0.5f).toInt()
        assertEquals(expectedR, r)
    }
}
