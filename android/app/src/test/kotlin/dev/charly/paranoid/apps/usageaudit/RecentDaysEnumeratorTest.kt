package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RecentDaysEnumeratorTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun calAt(year: Int, month: Int, day: Int, hour: Int = 12): Calendar {
        val cal = Calendar.getInstance(utc)
        cal.clear()
        cal.set(year, month, day, hour, 0, 0)
        return cal
    }

    @Test
    fun `enumerates the requested number of past days, today excluded`() {
        val now = calAt(2026, Calendar.APRIL, 28, hour = 14).timeInMillis

        val windows = RecentDaysEnumerator.pastDayWindows(
            nowMillis = now,
            daysBack = 5,
            timeZone = utc,
        )

        assertEquals(5, windows.size)
        // Today (Apr 28) MUST NOT appear; the most recent window must be Apr 27.
        val expectedNewestStart = calAt(2026, Calendar.APRIL, 27, hour = 0).timeInMillis
        val expectedNewestEnd = calAt(2026, Calendar.APRIL, 28, hour = 0).timeInMillis
        assertEquals(expectedNewestStart, windows.last().startMillis)
        assertEquals(expectedNewestEnd, windows.last().endMillis)

        // Oldest window: 5 days back from today's start = Apr 23.
        val expectedOldestStart = calAt(2026, Calendar.APRIL, 23, hour = 0).timeInMillis
        assertEquals(expectedOldestStart, windows.first().startMillis)
    }

    @Test
    fun `each window covers exactly one local day`() {
        val now = calAt(2026, Calendar.APRIL, 28, hour = 9).timeInMillis

        val windows = RecentDaysEnumerator.pastDayWindows(
            nowMillis = now,
            daysBack = 3,
            timeZone = utc,
        )

        windows.forEach { window ->
            val durationMillis = window.endMillis - window.startMillis
            assertEquals(24 * 60 * 60 * 1_000L, durationMillis)
        }
    }

    @Test
    fun `windows are returned in chronological order, oldest first`() {
        val now = calAt(2026, Calendar.APRIL, 28).timeInMillis

        val windows = RecentDaysEnumerator.pastDayWindows(
            nowMillis = now,
            daysBack = 4,
            timeZone = utc,
        )

        val starts = windows.map { it.startMillis }
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `zero days back yields empty list`() {
        val now = calAt(2026, Calendar.APRIL, 28).timeInMillis

        val windows = RecentDaysEnumerator.pastDayWindows(
            nowMillis = now,
            daysBack = 0,
            timeZone = utc,
        )

        assertTrue(windows.isEmpty())
    }
}
