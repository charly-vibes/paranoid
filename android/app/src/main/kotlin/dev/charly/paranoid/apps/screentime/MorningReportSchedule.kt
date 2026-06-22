package dev.charly.paranoid.apps.screentime

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure scheduling math for the 08:00 morning report. Computing the delay to the next 08:00 and
 * re-enqueuing a one-time job (rather than a PeriodicWorkRequest) keeps the report aligned to the
 * wall clock and self-correcting across DST/timezone changes. Android-free and unit-testable.
 */
object MorningReportSchedule {
    /** Local time the report fires. */
    val REPORT_TIME: LocalTime = LocalTime.of(8, 0)

    /**
     * Milliseconds from [nowMillis] until the next occurrence of [REPORT_TIME]. If it is already
     * past (or exactly) 08:00 today, returns the delay to 08:00 tomorrow.
     */
    fun nextReportDelayMillis(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var next = now.toLocalDate().atTime(REPORT_TIME).atZone(zone)
        if (next.toInstant().toEpochMilli() <= nowMillis) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli() - nowMillis
    }
}
