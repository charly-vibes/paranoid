package dev.charly.paranoid.apps.usageaudit

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyUsageAggregatorTest {

    @Test
    fun `daily summary totals overlapping foreground time inside the requested window`() {
        val windowStart = 1_000L
        val windowEnd = 5_000L

        val summary = DailyUsageAggregator.summarize(
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
            slices = listOf(
                AppUsageSlice(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    windowStartMillis = 500L,
                    windowEndMillis = 2_000L,
                    foregroundDurationMillis = 1_500L,
                ),
                AppUsageSlice(
                    packageName = "dev.example.chat",
                    appLabel = "Chat",
                    windowStartMillis = 4_000L,
                    windowEndMillis = 6_500L,
                    foregroundDurationMillis = 2_500L,
                ),
                AppUsageSlice(
                    packageName = "dev.example.music",
                    appLabel = "Music",
                    windowStartMillis = 6_500L,
                    windowEndMillis = 7_000L,
                    foregroundDurationMillis = 500L,
                ),
            ),
        )

        assertEquals(2_000L, summary.totalForegroundDurationMillis)
    }

    @Test
    fun `daily summary ranks apps by normalized foreground duration`() {
        val summary = DailyUsageAggregator.summarize(
            windowStartMillis = 1_000L,
            windowEndMillis = 10_000L,
            slices = listOf(
                AppUsageSlice(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    windowStartMillis = 1_000L,
                    windowEndMillis = 5_000L,
                    foregroundDurationMillis = 4_000L,
                ),
                AppUsageSlice(
                    packageName = "dev.example.chat",
                    appLabel = "Chat",
                    windowStartMillis = 2_000L,
                    windowEndMillis = 9_000L,
                    foregroundDurationMillis = 7_000L,
                ),
                AppUsageSlice(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    windowStartMillis = 7_000L,
                    windowEndMillis = 9_000L,
                    foregroundDurationMillis = 2_000L,
                ),
            ),
        )

        assertEquals(listOf("Chat", "Reader"), summary.appsByForegroundDuration.map { it.appLabel })
        assertEquals(listOf(7_000L, 6_000L), summary.appsByForegroundDuration.map { it.foregroundDurationMillis })
    }

    @Test
    fun `daily summary never counts more than the slice foreground duration`() {
        val summary = DailyUsageAggregator.summarize(
            windowStartMillis = 1_000L,
            windowEndMillis = 5_000L,
            slices = listOf(
                AppUsageSlice(
                    packageName = "dev.example.music",
                    appLabel = "Music",
                    windowStartMillis = 1_000L,
                    windowEndMillis = 5_000L,
                    foregroundDurationMillis = 1_200L,
                ),
            ),
        )

        assertEquals(1_200L, summary.totalForegroundDurationMillis)
    }

    @Test
    fun `daily summary aggregates over an arbitrary past day window from RecentDaysEnumerator`() {
        // Anchor "now" to 2026-05-04 12:00 local; pick the day from 3 days ago.
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val now = epochMillis(tz, 2026, Calendar.MAY, 4, 12, 0)
        val pastDay = RecentDaysEnumerator.pastDayWindows(now, daysBack = 5, timeZone = tz)
            .first { window ->
                // Pick the day three calendar days before "today" (2026-05-01).
                window.startMillis == epochMillis(tz, 2026, Calendar.MAY, 1, 0, 0)
            }

        val slices = listOf(
            // Reader: 09:00 → 09:30 = 30 min
            AppUsageSlice(
                packageName = "dev.example.reader",
                appLabel = "Reader",
                windowStartMillis = pastDay.startMillis + 9 * MILLIS_PER_HOUR,
                windowEndMillis = pastDay.startMillis + 9 * MILLIS_PER_HOUR + 30 * MILLIS_PER_MINUTE,
                foregroundDurationMillis = 30 * MILLIS_PER_MINUTE,
            ),
            // Chat: 14:15 → 15:00 = 45 min
            AppUsageSlice(
                packageName = "dev.example.chat",
                appLabel = "Chat",
                windowStartMillis = pastDay.startMillis + 14 * MILLIS_PER_HOUR + 15 * MILLIS_PER_MINUTE,
                windowEndMillis = pastDay.startMillis + 15 * MILLIS_PER_HOUR,
                foregroundDurationMillis = 45 * MILLIS_PER_MINUTE,
            ),
            // Distractor on the *next* day, must not be counted.
            AppUsageSlice(
                packageName = "dev.example.music",
                appLabel = "Music",
                windowStartMillis = pastDay.endMillis + MILLIS_PER_HOUR,
                windowEndMillis = pastDay.endMillis + 2 * MILLIS_PER_HOUR,
                foregroundDurationMillis = MILLIS_PER_HOUR,
            ),
        )

        val summary = DailyUsageAggregator.summarize(
            windowStartMillis = pastDay.startMillis,
            windowEndMillis = pastDay.endMillis,
            slices = slices,
        )

        assertEquals(75 * MILLIS_PER_MINUTE, summary.totalForegroundDurationMillis)
        assertEquals(listOf("Chat", "Reader"), summary.appsByForegroundDuration.map { it.appLabel })
    }

    @Test
    fun `daily summary on a DST spring-forward past day equals the sum of overlapping slices`() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        // 2026-03-08 is the DST spring-forward day in Los Angeles (23-hour local day).
        val dayStart = epochMillis(tz, 2026, Calendar.MARCH, 8, 0, 0)
        val dayEnd = epochMillis(tz, 2026, Calendar.MARCH, 9, 0, 0)
        assertEquals(23L * MILLIS_PER_HOUR, dayEnd - dayStart)

        // Slice spans local 01:00 → 04:00 wall-clock; the 02:00 hour is skipped,
        // so real elapsed foreground time is 2 hours.
        val slice = AppUsageSlice(
            packageName = "dev.example.reader",
            appLabel = "Reader",
            windowStartMillis = dayStart + 1 * MILLIS_PER_HOUR,
            windowEndMillis = dayStart + 1 * MILLIS_PER_HOUR + 2 * MILLIS_PER_HOUR,
            foregroundDurationMillis = 2 * MILLIS_PER_HOUR,
        )

        val summary = DailyUsageAggregator.summarize(
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            slices = listOf(slice),
        )

        assertEquals(2 * MILLIS_PER_HOUR, summary.totalForegroundDurationMillis)
        assertEquals(listOf("Reader"), summary.appsByForegroundDuration.map { it.appLabel })
    }

    private fun epochMillis(tz: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private companion object {
        const val MILLIS_PER_MINUTE = 60L * 1_000L
        const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
    }
}

class OvernightAuditCalculatorTest {

    @Test
    fun `overnight audit computes battery start end and delta from snapshots`() {
        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = 100L,
            windowEndMillis = 400L,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = 90L, batteryPercent = 82, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 410L, batteryPercent = 74, chargingState = ChargingState.DISCHARGING),
            ),
            usageSlices = emptyList(),
        )

        assertEquals(82, audit.batteryStartPercent)
        assertEquals(74, audit.batteryEndPercent)
        assertEquals(-8, audit.batteryDeltaPercent)
    }

    @Test
    fun `overnight audit lists apps with observed foreground usage overlapping the window`() {
        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = 1_000L,
            windowEndMillis = 5_000L,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = 900L, batteryPercent = 80, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 5_100L, batteryPercent = 76, chargingState = ChargingState.DISCHARGING),
            ),
            usageSlices = listOf(
                AppUsageSlice(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    windowStartMillis = 800L,
                    windowEndMillis = 1_500L,
                    foregroundDurationMillis = 700L,
                ),
                AppUsageSlice(
                    packageName = "dev.example.chat",
                    appLabel = "Chat",
                    windowStartMillis = 2_000L,
                    windowEndMillis = 4_500L,
                    foregroundDurationMillis = 2_500L,
                ),
                AppUsageSlice(
                    packageName = "dev.example.clock",
                    appLabel = "Clock",
                    windowStartMillis = 6_000L,
                    windowEndMillis = 7_000L,
                    foregroundDurationMillis = 1_000L,
                ),
            ),
        )

        assertEquals(listOf("Chat", "Reader"), audit.activeApps.map { it.appLabel })
        assertEquals(listOf(2_500L, 500L), audit.activeApps.map { it.foregroundDurationMillis })
        assertEquals(3_000L, audit.totalForegroundDurationMillis)
    }

    @Test
    fun `overnight audit warns when snapshots do not fully cover the requested window`() {
        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = 100L,
            windowEndMillis = 500L,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = 150L, batteryPercent = 80, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 450L, batteryPercent = 75, chargingState = ChargingState.DISCHARGING),
            ),
            usageSlices = emptyList(),
        )

        assertTrue(audit.hasIncompleteBatteryCoverage)
        assertTrue(audit.warningFlags.contains(WarningFlag.INCOMPLETE_BATTERY_DATA))
        assertEquals(80, audit.batteryStartPercent)
        assertEquals(75, audit.batteryEndPercent)
    }

    @Test
    fun `overnight audit reports no observed activity when battery drops without overlapping app slices`() {
        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = 100L,
            windowEndMillis = 300L,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = 100L, batteryPercent = 80, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 300L, batteryPercent = 72, chargingState = ChargingState.DISCHARGING),
            ),
            usageSlices = emptyList(),
        )

        assertTrue(audit.activeApps.isEmpty())
        assertTrue(audit.warningFlags.contains(WarningFlag.NO_OBSERVED_APP_ACTIVITY))
        assertEquals(-8, audit.batteryDeltaPercent)
    }

    @Test
    fun `overnight audit flags charging transitions inside the window`() {
        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = 100L,
            windowEndMillis = 400L,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = 90L, batteryPercent = 60, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 200L, batteryPercent = 55, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 300L, batteryPercent = 58, chargingState = ChargingState.CHARGING),
                BatterySnapshot(timestampMillis = 410L, batteryPercent = 61, chargingState = ChargingState.CHARGING),
            ),
            usageSlices = emptyList(),
        )

        assertTrue(audit.hadChargingTransition)
        assertTrue(audit.warningFlags.contains(WarningFlag.CHARGING_TRANSITION))
        assertEquals(60, audit.batteryStartPercent)
        assertEquals(61, audit.batteryEndPercent)
        assertEquals(1, audit.batteryDeltaPercent)
    }

    @Test
    fun `overnight audit does not flag charging transitions that happen after the requested window`() {
        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = 100L,
            windowEndMillis = 400L,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = 90L, batteryPercent = 60, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 350L, batteryPercent = 55, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 410L, batteryPercent = 58, chargingState = ChargingState.CHARGING),
            ),
            usageSlices = emptyList(),
        )

        assertFalse(audit.hadChargingTransition)
        assertFalse(audit.warningFlags.contains(WarningFlag.CHARGING_TRANSITION))
    }

    @Test
    fun `overnight audit only warns about no observed activity when battery actually drops`() {
        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = 100L,
            windowEndMillis = 300L,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = 100L, batteryPercent = 80, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = 300L, batteryPercent = 82, chargingState = ChargingState.DISCHARGING),
            ),
            usageSlices = emptyList(),
        )

        assertFalse(audit.warningFlags.contains(WarningFlag.NO_OBSERVED_APP_ACTIVITY))
    }

    @Test
    fun `overnight audit is safe for cross-midnight windows expressed as epoch millis`() {
        val windowStart = 1_713_736_800_000L // 2024-04-21T22:00:00Z
        val windowEnd = 1_713_769_200_000L   // 2024-04-22T07:00:00Z

        val audit = OvernightAuditCalculator.summarize(
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
            snapshots = listOf(
                BatterySnapshot(timestampMillis = windowStart - 60_000L, batteryPercent = 81, chargingState = ChargingState.DISCHARGING),
                BatterySnapshot(timestampMillis = windowEnd + 60_000L, batteryPercent = 73, chargingState = ChargingState.DISCHARGING),
            ),
            usageSlices = listOf(
                AppUsageSlice(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    windowStartMillis = windowStart + 30 * 60_000L,
                    windowEndMillis = windowStart + 90 * 60_000L,
                    foregroundDurationMillis = 60 * 60_000L,
                ),
            ),
        )

        assertFalse(audit.hasIncompleteBatteryCoverage)
        assertEquals(windowStart, audit.windowStartMillis)
        assertEquals(windowEnd, audit.windowEndMillis)
        assertEquals(81, audit.batteryStartPercent)
        assertEquals(73, audit.batteryEndPercent)
        assertEquals(1, audit.activeAppsCount)
    }
}
