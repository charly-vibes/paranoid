package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.data.ScreenTimeDao
import dev.charly.paranoid.apps.screentime.model.Session

/**
 * Merges permanently-stored daily history with freshly-computed live days. Persisted rows survive
 * forever (past the 31-day session retention); live days cover the recent window and today. When a
 * day appears in both, the live value wins (it is recomputed from still-present sessions). Pure and
 * unit-testable.
 */
object DailyHistoryMerge {
    fun merge(persisted: List<DayUsage>, live: List<DayUsage>): List<DayUsage> =
        (persisted.associateBy { it.startMillis } + live.associateBy { it.startMillis })
            .values
            .filter { it.totalForegroundMillis > 0L }
            .sortedByDescending { it.startMillis }
}

/**
 * Persists every completed day (within the retention window) that has activity and is not already
 * stored, so daily totals are preserved forever before the raw sessions are pruned. Skips:
 * - today (still partial), and
 * - any day still overlapped by an **open** session (its intervals aren't final yet); a later run
 *   persists it once that session has closed.
 * This means a day is persisted exactly once and only when its data is final. Safe to call from both
 * the morning-report job and the UI.
 */
suspend fun ScreenTimeDao.persistCompletedDays(sessions: List<Session>, nowMillis: Long) {
    val existing = allDailyUsage().map { it.dayStartMillis }.toSet()
    // Earliest open session start; any day that ends after this is still being written.
    val openCutoff = sessions.filter { it.isOpen }.minOfOrNull { it.startMillis } ?: Long.MAX_VALUE
    ReportAggregator.dailyHistory(sessions, nowMillis, RetentionPolicy.RETENTION_DAYS.toInt())
        .drop(1) // skip today (still partial)
        .filter {
            it.totalForegroundMillis > 0L && it.startMillis !in existing && it.endMillis <= openCutoff
        }
        .forEach { day ->
            persistDay(
                dayStartMillis = day.startMillis,
                dayEndMillis = day.endMillis,
                totalForegroundMillis = day.totalForegroundMillis,
                apps = day.appsByForeground.map { it.packageName to it.foregroundMillis },
            )
        }
}

/** Loads the permanently-stored daily history (newest first) as domain [DayUsage]. */
suspend fun ScreenTimeDao.loadPersistedHistory(): List<DayUsage> {
    // Single query for all app rows, grouped in memory, to avoid an N+1 that worsens as the table
    // grows "forever".
    val appsByDay = allDailyAppUsage().groupBy { it.dayStartMillis }
    return allDailyUsage().map { d ->
        DayUsage(
            startMillis = d.dayStartMillis,
            endMillis = d.dayEndMillis,
            totalForegroundMillis = d.totalForegroundMillis,
            appsByForeground = appsByDay[d.dayStartMillis].orEmpty()
                .map { AppUsageTotal(it.packageName, it.foregroundMillis) },
        )
    }
}
