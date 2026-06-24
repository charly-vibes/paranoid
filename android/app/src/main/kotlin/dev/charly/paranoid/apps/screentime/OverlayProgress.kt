package dev.charly.paranoid.apps.screentime

/**
 * Pure progress math for the overlay bar: how full the bar should be as it fills from the
 * previous checkpoint toward the next one. Android-free and unit-testable.
 */
object OverlayProgress {
    /**
     * Fill fraction in [0, 1] at [elapsedMillis] from session start. The bar fills linearly from
     * the previous checkpoint (0 at session start) to the next checkpoint, resetting to 0 each
     * time a checkpoint is reached.
     */
    fun fillFraction(elapsedMillis: Long): Float {
        val previous = CheckpointSequence.previousCheckpointAtOrBeforeMillis(elapsedMillis)
        val next = CheckpointSequence.nextCheckpointAfterMillis(elapsedMillis)
        val span = (next - previous).toFloat()
        if (span <= 0f) return 0f
        return ((elapsedMillis - previous).toFloat() / span).coerceIn(0f, 1f)
    }

    /** Fill fraction above which the bar starts shifting from green toward red. */
    const val RED_THRESHOLD: Float = 0.70f

    // Bar colours as ARGB ints (pure Kotlin, no Android dependency). Green below the threshold,
    // interpolating channel-by-channel to red as the fill approaches full.
    private const val GREEN = 0xFF3DDC84.toInt()
    private const val RED = 0xFFE53935.toInt()

    /**
     * Bar colour (ARGB) at [fraction]. Stays [GREEN] up to [RED_THRESHOLD], then interpolates
     * linearly to [RED], reaching full red at fraction = 1. The closer to full, the redder.
     */
    fun fillColor(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        if (f <= RED_THRESHOLD) return GREEN
        val t = (f - RED_THRESHOLD) / (1f - RED_THRESHOLD)
        return lerpColor(GREEN, RED, t)
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val a = lerpChannel(from ushr 24 and 0xFF, to ushr 24 and 0xFF, clamped)
        val r = lerpChannel(from ushr 16 and 0xFF, to ushr 16 and 0xFF, clamped)
        val g = lerpChannel(from ushr 8 and 0xFF, to ushr 8 and 0xFF, clamped)
        val b = lerpChannel(from and 0xFF, to and 0xFF, clamped)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)
}
