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
}
