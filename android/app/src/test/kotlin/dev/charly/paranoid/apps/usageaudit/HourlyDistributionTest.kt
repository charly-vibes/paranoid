package dev.charly.paranoid.apps.usageaudit

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class HourlyDistributionTest {

    @Test
    fun `normal 24-hour day produces 24 contiguous buckets covering the window`() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val (start, end) = localDayWindow(tz, year = 2026, month = Calendar.MAY, day = 1)

        val buckets = DailyUsageAggregator.hourlyDistribution(
            windowStartMillis = start,
            windowEndMillis = end,
            slices = emptyList(),
        )

        assertEquals(24, buckets.size)
        assertEquals(start, buckets.first().hourStartMillis)
        assertEquals(end, buckets.last().hourEndMillis)
        // Buckets are contiguous.
        buckets.zipWithNext().forEach { (left, right) ->
            assertEquals(left.hourEndMillis, right.hourStartMillis)
        }
    }

    @Test
    fun `spring-forward day produces 23 buckets matching the 23-hour local window`() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        // Sunday March 8, 2026: 02:00 PST jumps to 03:00 PDT.
        val (start, end) = localDayWindow(tz, year = 2026, month = Calendar.MARCH, day = 8)
        assertEquals(23L * MILLIS_PER_HOUR, end - start)

        val buckets = DailyUsageAggregator.hourlyDistribution(
            windowStartMillis = start,
            windowEndMillis = end,
            slices = emptyList(),
        )

        assertEquals(23, buckets.size)
        assertEquals(start, buckets.first().hourStartMillis)
        assertEquals(end, buckets.last().hourEndMillis)
    }

    @Test
    fun `fall-back day produces 25 buckets matching the 25-hour local window`() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        // Sunday November 1, 2026: 02:00 PDT falls back to 01:00 PST.
        val (start, end) = localDayWindow(tz, year = 2026, month = Calendar.NOVEMBER, day = 1)
        assertEquals(25L * MILLIS_PER_HOUR, end - start)

        val buckets = DailyUsageAggregator.hourlyDistribution(
            windowStartMillis = start,
            windowEndMillis = end,
            slices = emptyList(),
        )

        assertEquals(25, buckets.size)
        assertEquals(start, buckets.first().hourStartMillis)
        assertEquals(end, buckets.last().hourEndMillis)
    }

    @Test
    fun `bucket totals reconcile with overlapping slice on a normal day`() {
        val tz = TimeZone.getTimeZone("UTC")
        val (start, end) = localDayWindow(tz, year = 2026, month = Calendar.JUNE, day = 15)

        // Slice spans local 09:30 → 10:30, fully foreground (60 minutes).
        val slice = AppUsageSlice(
            packageName = "dev.example.reader",
            appLabel = "Reader",
            windowStartMillis = start + 9 * MILLIS_PER_HOUR + 30 * MILLIS_PER_MINUTE,
            windowEndMillis = start + 10 * MILLIS_PER_HOUR + 30 * MILLIS_PER_MINUTE,
            foregroundDurationMillis = MILLIS_PER_HOUR,
        )

        val buckets = DailyUsageAggregator.hourlyDistribution(
            windowStartMillis = start,
            windowEndMillis = end,
            slices = listOf(slice),
        )

        assertEquals(24, buckets.size)
        assertEquals(MILLIS_PER_HOUR, buckets.sumOf { it.foregroundDurationMillis })
        assertEquals(30 * MILLIS_PER_MINUTE, buckets[9].foregroundDurationMillis)
        assertEquals(30 * MILLIS_PER_MINUTE, buckets[10].foregroundDurationMillis)
        assertEquals(0L, buckets[8].foregroundDurationMillis)
        assertEquals(0L, buckets[11].foregroundDurationMillis)
    }

    @Test
    fun `bucket totals reconcile with the daily summary on a DST spring-forward day`() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val (start, end) = localDayWindow(tz, year = 2026, month = Calendar.MARCH, day = 8)

        // Slice covers local 01:00 → 04:00 wall-clock, but the 02:00 hour is skipped,
        // so real elapsed time is 2 hours.
        val sliceStart = start + 1 * MILLIS_PER_HOUR
        val sliceEnd = sliceStart + 2 * MILLIS_PER_HOUR // 04:00 PDT in real time
        val slice = AppUsageSlice(
            packageName = "dev.example.reader",
            appLabel = "Reader",
            windowStartMillis = sliceStart,
            windowEndMillis = sliceEnd,
            foregroundDurationMillis = 2 * MILLIS_PER_HOUR,
        )

        val buckets = DailyUsageAggregator.hourlyDistribution(
            windowStartMillis = start,
            windowEndMillis = end,
            slices = listOf(slice),
        )
        val daily = DailyUsageAggregator.summarize(
            windowStartMillis = start,
            windowEndMillis = end,
            slices = listOf(slice),
        )

        assertEquals(23, buckets.size)
        assertEquals(daily.totalForegroundDurationMillis, buckets.sumOf { it.foregroundDurationMillis })
    }

    private fun localDayWindow(tz: TimeZone, year: Int, month: Int, day: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance(tz).apply {
            clear()
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60L * 1_000L
        const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
    }
}
