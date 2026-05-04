package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDayDetailCalculatorTest {

    private val dayStart = 1_000_000L
    private val dayEnd = dayStart + 86_400_000L

    private val installedLookup: (String) -> AppLabel = { pkg ->
        AppLabel.Installed("Reader")
    }

    @Test
    fun `produces per-app intervals for the given day from synthetic intervals`() {
        val intervals = listOf(
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayStart + 1_000L,
                endMillis = dayStart + 4_000L,
            ),
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayStart + 10_000L,
                endMillis = dayStart + 12_000L,
            ),
            UsageIntervalRecord(
                packageName = "dev.example.chat",
                appLabel = "Chat",
                startMillis = dayStart + 5_000L,
                endMillis = dayStart + 7_000L,
            ),
        )

        val detail = AppDayDetailCalculator.forApp(
            packageName = "dev.example.reader",
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            intervals = intervals,
            labelLookup = installedLookup,
        )

        assertEquals("dev.example.reader", detail.packageName)
        assertEquals(AppLabel.Installed("Reader"), detail.appLabel)
        assertEquals(dayStart, detail.dayStartMillis)
        assertEquals(dayEnd, detail.dayEndMillis)
        assertEquals(5_000L, detail.totalForegroundDurationMillis)
        assertEquals(
            listOf(
                AppForegroundInterval(dayStart + 1_000L, dayStart + 4_000L),
                AppForegroundInterval(dayStart + 10_000L, dayStart + 12_000L),
            ),
            detail.intervals,
        )
    }

    @Test
    fun `clips intervals that straddle the day boundaries to the window`() {
        val intervals = listOf(
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayStart - 1_000L,
                endMillis = dayStart + 2_000L,
            ),
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayEnd - 1_000L,
                endMillis = dayEnd + 5_000L,
            ),
        )

        val detail = AppDayDetailCalculator.forApp(
            packageName = "dev.example.reader",
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            intervals = intervals,
            labelLookup = installedLookup,
        )

        assertEquals(3_000L, detail.totalForegroundDurationMillis)
        assertEquals(
            listOf(
                AppForegroundInterval(dayStart, dayStart + 2_000L),
                AppForegroundInterval(dayEnd - 1_000L, dayEnd),
            ),
            detail.intervals,
        )
    }

    @Test
    fun `intervals fully outside the window are excluded`() {
        val intervals = listOf(
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayStart - 5_000L,
                endMillis = dayStart - 1_000L,
            ),
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayEnd + 1_000L,
                endMillis = dayEnd + 5_000L,
            ),
        )

        val detail = AppDayDetailCalculator.forApp(
            packageName = "dev.example.reader",
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            intervals = intervals,
            labelLookup = installedLookup,
        )

        assertEquals(0L, detail.totalForegroundDurationMillis)
        assertTrue(detail.intervals.isEmpty())
    }

    @Test
    fun `intervals are returned sorted by start time`() {
        val intervals = listOf(
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayStart + 5_000L,
                endMillis = dayStart + 7_000L,
            ),
            UsageIntervalRecord(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                startMillis = dayStart + 1_000L,
                endMillis = dayStart + 2_000L,
            ),
        )

        val detail = AppDayDetailCalculator.forApp(
            packageName = "dev.example.reader",
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            intervals = intervals,
            labelLookup = installedLookup,
        )

        assertEquals(
            listOf(
                AppForegroundInterval(dayStart + 1_000L, dayStart + 2_000L),
                AppForegroundInterval(dayStart + 5_000L, dayStart + 7_000L),
            ),
            detail.intervals,
        )
    }

    @Test
    fun `app with no observed activity returns zero total and empty intervals`() {
        val intervals = listOf(
            UsageIntervalRecord(
                packageName = "dev.example.chat",
                appLabel = "Chat",
                startMillis = dayStart + 1_000L,
                endMillis = dayStart + 4_000L,
            ),
        )

        val detail = AppDayDetailCalculator.forApp(
            packageName = "dev.example.reader",
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            intervals = intervals,
            labelLookup = installedLookup,
        )

        assertEquals(0L, detail.totalForegroundDurationMillis)
        assertTrue(detail.intervals.isEmpty())
    }

    @Test
    fun `uninstalled package surfaces uninstalled label`() {
        val intervals = listOf(
            UsageIntervalRecord(
                packageName = "dev.example.gone",
                appLabel = "dev.example.gone",
                startMillis = dayStart + 1_000L,
                endMillis = dayStart + 3_000L,
            ),
        )

        val detail = AppDayDetailCalculator.forApp(
            packageName = "dev.example.gone",
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            intervals = intervals,
            labelLookup = { AppLabel.Uninstalled },
        )

        assertEquals(AppLabel.Uninstalled, detail.appLabel)
        assertEquals(2_000L, detail.totalForegroundDurationMillis)
        assertEquals(
            listOf(AppForegroundInterval(dayStart + 1_000L, dayStart + 3_000L)),
            detail.intervals,
        )
    }
}
