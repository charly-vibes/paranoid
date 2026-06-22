package dev.charly.paranoid.apps.screentime

/**
 * The checkpoint cadence within a session: 7, 13, 29 minutes from session start, then every
 * 29 minutes thereafter (58, 87, …). The clock resets when a session ends. Pure and
 * Android-free. See screen-time-notifications spec.
 */
object CheckpointSequence {
    /** One-off checkpoints, in minutes from session start, before the recurring cadence. */
    val FIXED_MINUTES: List<Long> = listOf(7L, 13L)

    /** Recurring cadence (minutes) once past the fixed checkpoints: 29, 58, 87, … */
    const val RECURRING_INTERVAL_MINUTES: Long = 29L

    private const val MILLIS_PER_MINUTE: Long = 60_000L

    /**
     * Elapsed time (millis from session start) of the first checkpoint strictly after
     * [elapsedMillis]. With [elapsedMillis] = 0 this returns 7 min; just after the 7-min
     * checkpoint it returns 13 min; after 13 it returns 29; after 29 it returns 58, and so on.
     */
    fun nextCheckpointAfterMillis(elapsedMillis: Long): Long {
        for (minutes in FIXED_MINUTES) {
            val checkpoint = minutes * MILLIS_PER_MINUTE
            if (checkpoint > elapsedMillis) return checkpoint
        }
        val interval = RECURRING_INTERVAL_MINUTES * MILLIS_PER_MINUTE
        val nextMultiple = (elapsedMillis / interval) + 1
        return nextMultiple * interval
    }

    /** Delay (millis) from [elapsedMillis] until the next checkpoint. */
    fun delayToNextCheckpointMillis(elapsedMillis: Long): Long =
        (nextCheckpointAfterMillis(elapsedMillis) - elapsedMillis).coerceAtLeast(0L)

    /**
     * Elapsed-millis marker of the most recent checkpoint at or before [elapsedMillis], or 0
     * (session start) if none has been reached yet. Used as the lower bound when computing the
     * overlay fill fraction toward the next checkpoint.
     */
    fun previousCheckpointAtOrBeforeMillis(elapsedMillis: Long): Long {
        if (elapsedMillis <= 0L) return 0L
        var previous = 0L
        for (minutes in FIXED_MINUTES) {
            val checkpoint = minutes * MILLIS_PER_MINUTE
            if (checkpoint <= elapsedMillis) previous = checkpoint else return previous
        }
        val interval = RECURRING_INTERVAL_MINUTES * MILLIS_PER_MINUTE
        if (elapsedMillis >= interval) {
            previous = (elapsedMillis / interval) * interval
        }
        return previous
    }
}
