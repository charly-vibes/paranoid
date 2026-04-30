package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyHistoryPresenterTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun dayStart(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(utc)
        cal.clear()
        cal.set(year, month, day, 0, 0, 0)
        return cal.timeInMillis
    }

    private fun makeDay(year: Int, month: Int, day: Int, totalMillis: Long): DailyUsageSummary {
        val start = dayStart(year, month, day)
        val end = start + 24L * 60 * 60 * 1_000
        return DailyUsageSummary(
            windowStartMillis = start,
            windowEndMillis = end,
            totalForegroundDurationMillis = totalMillis,
            appsByForegroundDuration = emptyList(),
        )
    }

    @Test
    fun `empty state when no past days are available`() {
        val state = DailyHistoryPresenter.present(
            days = emptyList(),
            timeZone = utc,
        )

        assertTrue(state is DailyHistoryScreenState.Empty)
    }

    @Test
    fun `populated state lists each past day with formatted total`() {
        val days = listOf(
            makeDay(2026, Calendar.APRIL, 25, totalMillis = 5_400_000L), // 1h 30m
            makeDay(2026, Calendar.APRIL, 26, totalMillis = 2_700_000L), // 45m
            makeDay(2026, Calendar.APRIL, 27, totalMillis = 0L),
        )

        val state = DailyHistoryPresenter.present(
            days = days,
            timeZone = utc,
        ) as DailyHistoryScreenState.Populated

        assertEquals(3, state.entries.size)
        assertEquals("1h 30m", state.entries[0].totalUsageFormatted)
        assertEquals("45m", state.entries[1].totalUsageFormatted)
        assertEquals("0m", state.entries[2].totalUsageFormatted)
    }

    @Test
    fun `populated state preserves the start of each day for navigation`() {
        val days = listOf(
            makeDay(2026, Calendar.APRIL, 25, totalMillis = 1_000L),
            makeDay(2026, Calendar.APRIL, 26, totalMillis = 1_000L),
        )

        val state = DailyHistoryPresenter.present(
            days = days,
            timeZone = utc,
        ) as DailyHistoryScreenState.Populated

        assertEquals(dayStart(2026, Calendar.APRIL, 25), state.entries[0].dayStartMillis)
        assertEquals(dayStart(2026, Calendar.APRIL, 26), state.entries[1].dayStartMillis)
    }
}
