package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AppDetailPresenterTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun dayStart(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(utc)
        cal.clear()
        cal.set(year, month, day, 0, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun `populated state shows total and intervals with start and end times`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val detail = AppDayDetail(
            packageName = "dev.example.reader",
            appLabel = AppLabel.Installed("Reader"),
            dayStartMillis = start,
            dayEndMillis = start + 86_400_000L,
            totalForegroundDurationMillis = 1_800_000L,
            intervals = listOf(
                AppForegroundInterval(start + 9 * 3_600_000L, start + 9 * 3_600_000L + 600_000L),
                AppForegroundInterval(start + 14 * 3_600_000L, start + 14 * 3_600_000L + 1_200_000L),
            ),
        )

        val state = AppDetailPresenter.present(detail, timeZone = utc)

        assertEquals("dev.example.reader", state.packageName)
        assertEquals("Reader", state.displayLabel)
        assertFalse(state.isUninstalled)
        assertEquals("Sat, Apr 25", state.dateFormatted)
        assertEquals("30m", state.totalUsageFormatted)
        assertFalse(state.showNoActivityMessage)
        assertEquals(2, state.intervals.size)
        assertEquals("09:00", state.intervals[0].startFormatted)
        assertEquals("09:10", state.intervals[0].endFormatted)
        assertEquals("10m", state.intervals[0].durationFormatted)
        assertEquals("14:00", state.intervals[1].startFormatted)
        assertEquals("14:20", state.intervals[1].endFormatted)
        assertEquals("20m", state.intervals[1].durationFormatted)
    }

    @Test
    fun `no-activity state shows zero total and explicit empty state`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val detail = AppDayDetail(
            packageName = "dev.example.reader",
            appLabel = AppLabel.Installed("Reader"),
            dayStartMillis = start,
            dayEndMillis = start + 86_400_000L,
            totalForegroundDurationMillis = 0L,
            intervals = emptyList(),
        )

        val state = AppDetailPresenter.present(detail, timeZone = utc)

        assertEquals("0m", state.totalUsageFormatted)
        assertTrue(state.intervals.isEmpty())
        assertTrue(state.showNoActivityMessage)
    }

    @Test
    fun `uninstalled package shows raw package name and uninstalled indicator with intervals still listed`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val detail = AppDayDetail(
            packageName = "dev.example.gone",
            appLabel = AppLabel.Uninstalled,
            dayStartMillis = start,
            dayEndMillis = start + 86_400_000L,
            totalForegroundDurationMillis = 600_000L,
            intervals = listOf(
                AppForegroundInterval(start + 8 * 3_600_000L, start + 8 * 3_600_000L + 600_000L),
            ),
        )

        val state = AppDetailPresenter.present(detail, timeZone = utc)

        assertEquals("dev.example.gone", state.packageName)
        assertEquals("dev.example.gone", state.displayLabel)
        assertTrue(state.isUninstalled)
        assertEquals("10m", state.totalUsageFormatted)
        assertEquals(1, state.intervals.size)
        assertEquals("08:00", state.intervals[0].startFormatted)
        assertEquals("08:10", state.intervals[0].endFormatted)
        assertFalse(state.showNoActivityMessage)
    }
}
