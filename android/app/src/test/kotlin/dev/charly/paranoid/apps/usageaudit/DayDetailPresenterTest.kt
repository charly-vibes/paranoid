package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayDetailPresenterTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun dayStart(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(utc)
        cal.clear()
        cal.set(year, month, day, 0, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun `populated state shows total and ranked apps`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val summary = DailyUsageSummary(
            windowStartMillis = start,
            windowEndMillis = start + 86_400_000L,
            totalForegroundDurationMillis = 5_400_000L,
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.chat", "Chat", 3_600_000L),
                AppUsageSummary("com.example.reader", "Reader", 1_800_000L),
            ),
        )

        val state = DayDetailPresenter.present(summary, timeZone = utc)

        assertEquals("1h 30m", state.totalUsageFormatted)
        assertEquals(2, state.apps.size)
        assertEquals("Chat", state.apps[0].label)
        assertEquals("1h 0m", state.apps[0].durationFormatted)
        assertEquals("Reader", state.apps[1].label)
        assertEquals("30m", state.apps[1].durationFormatted)
        assertFalse(state.showZeroUsageMessage)
    }

    @Test
    fun `zero-usage day shows zero total, no app rows, and explicit zero-usage message`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val summary = DailyUsageSummary(
            windowStartMillis = start,
            windowEndMillis = start + 86_400_000L,
            totalForegroundDurationMillis = 0L,
            appsByForegroundDuration = emptyList(),
        )

        val state = DayDetailPresenter.present(summary, timeZone = utc)

        assertEquals("0m", state.totalUsageFormatted)
        assertTrue(state.apps.isEmpty())
        assertTrue(state.showZeroUsageMessage)
    }

    @Test
    fun `date is formatted from the window start`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val summary = DailyUsageSummary(
            windowStartMillis = start,
            windowEndMillis = start + 86_400_000L,
            totalForegroundDurationMillis = 0L,
            appsByForegroundDuration = emptyList(),
        )

        val state = DayDetailPresenter.present(summary, timeZone = utc)

        // Use contains so we don't lock the formatter to a specific locale layout.
        assertTrue(
            "expected formatted date to mention the day; got '${state.dateFormatted}'",
            state.dateFormatted.contains("Apr") || state.dateFormatted.contains("25"),
        )
    }
}
