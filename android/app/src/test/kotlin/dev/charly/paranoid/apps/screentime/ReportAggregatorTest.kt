package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.AppInterval
import dev.charly.paranoid.apps.screentime.model.SYSTEM_UNATTRIBUTED
import dev.charly.paranoid.apps.screentime.model.Session
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportAggregatorTest {

    private val utc = ZoneId.of("UTC")
    private val minute = 60_000L

    private fun millis(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    private fun session(vararg intervals: AppInterval): Session = Session(
        startMillis = intervals.first().startMillis,
        endMillis = intervals.last().endMillis,
        appIntervals = intervals.toList(),
    )

    @Test
    fun `yesterday totals foreground time and ranks apps, excluding unattributed from breakdown`() {
        val now = millis("2026-06-18T09:00:00Z") // today = 6-18, yesterday = 6-17
        val sessions = listOf(
            session(
                AppInterval("app.a", millis("2026-06-17T10:00:00Z"), millis("2026-06-17T10:30:00Z")),
                AppInterval("app.b", millis("2026-06-17T10:30:00Z"), millis("2026-06-17T10:40:00Z")),
                AppInterval(SYSTEM_UNATTRIBUTED, millis("2026-06-17T10:40:00Z"), millis("2026-06-17T10:45:00Z")),
            ),
            // A session on a different day must be ignored.
            session(AppInterval("app.a", millis("2026-06-16T10:00:00Z"), millis("2026-06-16T11:00:00Z"))),
        )

        val yesterday = ReportAggregator.yesterday(sessions, now, utc)

        // Total includes unattributed screen-on time: 30 + 10 + 5 = 45 min.
        assertEquals(45 * minute, yesterday.totalForegroundMillis)
        // Breakdown excludes system.unattributed and ranks by duration.
        assertEquals(listOf("app.a", "app.b"), yesterday.appsByForeground.map { it.packageName })
        assertEquals(30 * minute, yesterday.appsByForeground[0].foregroundMillis)
    }

    @Test
    fun `seven day average divides by 7 even when some days have zero usage`() {
        val now = millis("2026-06-18T09:00:00Z")
        // Usage only on 6-17 (60 min) and 6-15 (80 min); other 5 of the last 7 days are zero.
        val sessions = listOf(
            session(AppInterval("app.a", millis("2026-06-17T10:00:00Z"), millis("2026-06-17T11:00:00Z"))),
            session(AppInterval("app.a", millis("2026-06-15T10:00:00Z"), millis("2026-06-15T11:20:00Z"))),
        )

        val avg = ReportAggregator.sevenDayAverageMillis(sessions, now, utc)

        // (60 + 80) min / 7 = 20 min.
        assertEquals(20 * minute, avg)
    }

    @Test
    fun `month to date sums from the first through end of yesterday`() {
        val now = millis("2026-06-18T09:00:00Z")
        val sessions = listOf(
            session(AppInterval("app.a", millis("2026-06-01T08:00:00Z"), millis("2026-06-01T08:30:00Z"))),
            session(AppInterval("app.b", millis("2026-06-17T08:00:00Z"), millis("2026-06-17T08:15:00Z"))),
            // Today (6-18) must be excluded from month-to-date.
            session(AppInterval("app.a", millis("2026-06-18T08:00:00Z"), millis("2026-06-18T08:50:00Z"))),
            // Previous month must be excluded.
            session(AppInterval("app.a", millis("2026-05-31T08:00:00Z"), millis("2026-05-31T09:00:00Z"))),
        )

        val mtd = ReportAggregator.monthToDate(sessions, now, utc)

        assertEquals(45 * minute, mtd.totalForegroundMillis)
    }

    @Test
    fun `month to date is empty on the first of the month`() {
        val now = millis("2026-06-01T09:00:00Z")
        val sessions = listOf(
            session(AppInterval("app.a", millis("2026-05-31T08:00:00Z"), millis("2026-05-31T09:00:00Z"))),
        )

        val mtd = ReportAggregator.monthToDate(sessions, now, utc)

        assertEquals(0L, mtd.totalForegroundMillis)
    }
}
