package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.AppInterval
import dev.charly.paranoid.apps.screentime.model.Session
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportAggregatorDailyHistoryTest {

    private val utc = ZoneId.of("UTC")
    private val minute = 60_000L

    private fun millis(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun `daily history returns most recent first, today partial to now`() {
        val now = millis("2026-06-18T12:00:00Z")
        val sessions = listOf(
            // Today: 30 min app.a (ends before now).
            Session(
                startMillis = millis("2026-06-18T08:00:00Z"),
                endMillis = millis("2026-06-18T08:30:00Z"),
                appIntervals = listOf(
                    AppInterval("app.a", millis("2026-06-18T08:00:00Z"), millis("2026-06-18T08:30:00Z")),
                ),
            ),
            // Yesterday: 60 min app.b.
            Session(
                startMillis = millis("2026-06-17T10:00:00Z"),
                endMillis = millis("2026-06-17T11:00:00Z"),
                appIntervals = listOf(
                    AppInterval("app.b", millis("2026-06-17T10:00:00Z"), millis("2026-06-17T11:00:00Z")),
                ),
            ),
        )

        val history = ReportAggregator.dailyHistory(sessions, now, days = 3, zone = utc)

        assertEquals(3, history.size)
        // Most recent first.
        assertEquals(30 * minute, history[0].totalForegroundMillis)
        assertEquals(now, history[0].endMillis) // today is partial
        assertEquals(60 * minute, history[1].totalForegroundMillis)
        assertEquals(0L, history[2].totalForegroundMillis) // two days ago: empty
    }

    @Test
    fun `non-positive days returns empty`() {
        assertEquals(emptyList<DayUsage>(), ReportAggregator.dailyHistory(emptyList(), 0L, days = 0, zone = utc))
    }
}
