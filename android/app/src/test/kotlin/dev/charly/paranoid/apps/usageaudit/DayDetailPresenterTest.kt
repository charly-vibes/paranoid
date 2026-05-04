package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayDetailPresenterTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val MS_MINUTE = 60L * 1_000L
    private val MS_HOUR = 60L * MS_MINUTE

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
    fun `app rows carry their package name so day-to-app-detail navigation can be wired`() {
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

        assertEquals("com.example.chat", state.apps[0].packageName)
        assertEquals("com.example.reader", state.apps[1].packageName)
    }

    @Test
    fun `hourly bars render one entry per real hour on a normal day and total reconciles`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val end = start + 86_400_000L
        val slice = AppUsageSlice(
            packageName = "com.example.chat",
            appLabel = "Chat",
            windowStartMillis = start + 9 * MS_HOUR + 30 * MS_MINUTE,
            windowEndMillis = start + 10 * MS_HOUR + 30 * MS_MINUTE,
            foregroundDurationMillis = MS_HOUR,
        )
        val summary = DailyUsageAggregator.summarize(start, end, listOf(slice))
        val buckets = DailyUsageAggregator.hourlyDistribution(start, end, listOf(slice))

        val state = DayDetailPresenter.present(summary, hourlyBuckets = buckets, timeZone = utc)

        assertEquals(24, state.hourlyBars.size)
        assertEquals(
            summary.totalForegroundDurationMillis,
            state.hourlyBars.sumOf { it.foregroundDurationMillis },
        )
    }

    @Test
    fun `hourly bars render one entry per real hour on a spring-forward day`() {
        val pacific = TimeZone.getTimeZone("America/Los_Angeles")
        // Sunday March 8, 2026 in Pacific is a 23-hour spring-forward day.
        val cal = Calendar.getInstance(pacific).apply {
            clear()
            set(2026, Calendar.MARCH, 8, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        val slice = AppUsageSlice(
            packageName = "com.example.chat",
            appLabel = "Chat",
            windowStartMillis = start + 1 * MS_HOUR,
            windowEndMillis = start + 3 * MS_HOUR,
            foregroundDurationMillis = 2 * MS_HOUR,
        )
        val summary = DailyUsageAggregator.summarize(start, end, listOf(slice))
        val buckets = DailyUsageAggregator.hourlyDistribution(start, end, listOf(slice))

        val state = DayDetailPresenter.present(summary, hourlyBuckets = buckets, timeZone = pacific)

        assertEquals(23, state.hourlyBars.size)
        assertEquals(
            summary.totalForegroundDurationMillis,
            state.hourlyBars.sumOf { it.foregroundDurationMillis },
        )
    }

    @Test
    fun `overnight summary appears when its window starts within the selected day`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val end = start + 86_400_000L
        val summary = DailyUsageSummary(
            windowStartMillis = start,
            windowEndMillis = end,
            totalForegroundDurationMillis = 0L,
            appsByForegroundDuration = emptyList(),
        )
        // Overnight window starts at 23:00 of the selected day.
        val overnightStart = start + 23 * MS_HOUR
        val overnightEnd = overnightStart + 8 * MS_HOUR
        val overnight = OvernightAudit(
            windowStartMillis = overnightStart,
            windowEndMillis = overnightEnd,
            batteryStartPercent = 80,
            batteryEndPercent = 62,
            batteryDeltaPercent = -18,
            totalForegroundDurationMillis = 0L,
            activeApps = emptyList(),
            activeAppsCount = 0,
            hadChargingTransition = false,
            hasIncompleteBatteryCoverage = false,
            warningFlags = emptySet(),
        )

        val state = DayDetailPresenter.present(
            summary = summary,
            hourlyBuckets = emptyList(),
            overnight = overnight,
            timeZone = utc,
        )

        val row = state.overnightSummary
        assertNotNull("expected overnight summary to be present", row)
        assertEquals("−18%", row!!.batteryDelta)
    }

    @Test
    fun `overnight summary is omitted when its window starts on a different day`() {
        val start = dayStart(2026, Calendar.APRIL, 25)
        val end = start + 86_400_000L
        val summary = DailyUsageSummary(
            windowStartMillis = start,
            windowEndMillis = end,
            totalForegroundDurationMillis = 0L,
            appsByForegroundDuration = emptyList(),
        )
        // Overnight window starts on the next day.
        val overnightStart = end + MS_HOUR
        val overnight = OvernightAudit(
            windowStartMillis = overnightStart,
            windowEndMillis = overnightStart + 8 * MS_HOUR,
            batteryStartPercent = 90,
            batteryEndPercent = 80,
            batteryDeltaPercent = -10,
            totalForegroundDurationMillis = 0L,
            activeApps = emptyList(),
            activeAppsCount = 0,
            hadChargingTransition = false,
            hasIncompleteBatteryCoverage = false,
            warningFlags = emptySet(),
        )

        val state = DayDetailPresenter.present(
            summary = summary,
            hourlyBuckets = emptyList(),
            overnight = overnight,
            timeZone = utc,
        )

        assertNull(state.overnightSummary)
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
