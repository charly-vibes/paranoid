package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.SYSTEM_UNATTRIBUTED
import dev.charly.paranoid.apps.screentime.model.Session
import java.time.Instant
import java.time.ZoneId

/** Foreground usage for one date range: total screen-on time and the per-app breakdown. */
data class DayUsage(
    val startMillis: Long,
    val endMillis: Long,
    val totalForegroundMillis: Long,
    val appsByForeground: List<AppUsageTotal>,
)

/** The three sections of the morning report. */
data class MorningReport(
    val yesterday: DayUsage,
    val sevenDayAverageMillis: Long,
    val monthToDate: DayUsage,
)

/**
 * Pure aggregation for the morning report. Groups stored sessions into local calendar days and
 * computes yesterday's usage, a rolling 7-day daily average, and month-to-date cumulative totals.
 * Per-app breakdowns exclude [SYSTEM_UNATTRIBUTED]; totals include all foreground time (i.e. total
 * screen-on time). Android-free and unit-testable.
 */
object ReportAggregator {
    private const val DAYS_IN_WINDOW = 7L

    /** Usage for the full calendar day before the day containing [nowMillis]. */
    fun yesterday(sessions: List<Session>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): DayUsage {
        val todayStart = startOfDay(nowMillis, zone)
        val yesterdayStart = minusDays(todayStart, 1, zone)
        return dayUsage(sessions, yesterdayStart, todayStart)
    }

    /**
     * Average daily total foreground time over the 7 full days before today. Days with no usage
     * count as zero, so the divisor is always 7.
     */
    fun sevenDayAverageMillis(sessions: List<Session>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val todayStart = startOfDay(nowMillis, zone)
        var total = 0L
        for (offset in 1..DAYS_IN_WINDOW) {
            val dayStart = minusDays(todayStart, offset.toInt(), zone)
            val dayEnd = minusDays(todayStart, (offset - 1).toInt(), zone)
            total += dayUsage(sessions, dayStart, dayEnd).totalForegroundMillis
        }
        return total / DAYS_IN_WINDOW
    }

    /** Cumulative usage from the 1st of the current month through the end of yesterday. */
    fun monthToDate(sessions: List<Session>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): DayUsage {
        val todayStart = startOfDay(nowMillis, zone)
        val monthStart = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dayUsage(sessions, monthStart, todayStart)
    }

    /**
     * Per-day usage for the last [days] local calendar days, most recent first. The first entry is
     * today so far (its end is [nowMillis]); the rest are full past days. Used by the daily-activity
     * history view and export.
     */
    fun dailyHistory(
        sessions: List<Session>,
        nowMillis: Long,
        days: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayUsage> {
        if (days <= 0) return emptyList()
        val todayStart = startOfDay(nowMillis, zone)
        val result = ArrayList<DayUsage>(days)
        // Today is partial: [start of today, now).
        result.add(dayUsage(sessions, todayStart, nowMillis))
        for (offset in 1 until days) {
            val dayStart = minusDays(todayStart, offset, zone)
            val dayEnd = minusDays(todayStart, offset - 1, zone)
            result.add(dayUsage(sessions, dayStart, dayEnd))
        }
        return result
    }

    fun build(sessions: List<Session>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): MorningReport =
        MorningReport(
            yesterday = yesterday(sessions, nowMillis, zone),
            sevenDayAverageMillis = sevenDayAverageMillis(sessions, nowMillis, zone),
            monthToDate = monthToDate(sessions, nowMillis, zone),
        )

    private fun dayUsage(sessions: List<Session>, startMillis: Long, endMillis: Long): DayUsage {
        val all = SessionAggregator.foregroundByApp(sessions, startMillis, endMillis)
        return DayUsage(
            startMillis = startMillis,
            endMillis = endMillis,
            totalForegroundMillis = all.sumOf { it.foregroundMillis },
            appsByForeground = all.filter { it.packageName != SYSTEM_UNATTRIBUTED },
        )
    }

    private fun startOfDay(epochMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun minusDays(epochMillis: Long, days: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().minusDays(days.toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()
}
