package dev.charly.paranoid.apps.screentime

/**
 * Retention window for stored sessions. Sessions that ended before the cutoff are pruned; anything
 * within the last [RETENTION_DAYS] days is kept so reports (yesterday, 7-day, month-to-date) stay
 * intact. Pure and unit-testable; the cutoff is consumed by [data.ScreenTimeDao.pruneSessionsEndedBefore].
 */
object RetentionPolicy {
    const val RETENTION_DAYS = 31L
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    /** Sessions that ended strictly before this epoch-millis cutoff may be deleted. */
    fun cutoffMillis(nowMillis: Long): Long = nowMillis - RETENTION_DAYS * MILLIS_PER_DAY
}
