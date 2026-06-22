package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.Session

/** Total foreground time attributed to a single package over a query window. */
data class AppUsageTotal(
    val packageName: String,
    val foregroundMillis: Long,
)

/**
 * Pure, Android-free aggregation over stored [Session]s. Used by reports and the
 * today's-sessions UI; the persistence layer fetches sessions overlapping a window and
 * this computes the per-app totals so the logic stays unit-testable.
 */
object SessionAggregator {
    /**
     * Total foreground duration per package across [sessions], counting only the portion of
     * each app interval that overlaps the half-open window [startMillis, endMillis). Results
     * are sorted by duration descending, then package name ascending for stable ordering.
     */
    fun foregroundByApp(
        sessions: List<Session>,
        startMillis: Long,
        endMillis: Long,
    ): List<AppUsageTotal> {
        if (endMillis <= startMillis) return emptyList()
        val totals = HashMap<String, Long>()
        for (session in sessions) {
            for (interval in session.appIntervals) {
                val overlap = overlapMillis(interval.startMillis, interval.endMillis, startMillis, endMillis)
                if (overlap > 0L) {
                    totals[interval.packageName] = (totals[interval.packageName] ?: 0L) + overlap
                }
            }
        }
        return totals.entries
            .map { AppUsageTotal(packageName = it.key, foregroundMillis = it.value) }
            .sortedWith(compareByDescending<AppUsageTotal> { it.foregroundMillis }.thenBy { it.packageName })
    }

    private fun overlapMillis(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long {
        val start = maxOf(aStart, bStart)
        val end = minOf(aEnd, bEnd)
        return (end - start).coerceAtLeast(0L)
    }
}
