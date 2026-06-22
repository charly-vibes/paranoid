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
}
