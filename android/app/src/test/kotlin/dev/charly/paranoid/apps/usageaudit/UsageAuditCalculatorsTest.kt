package dev.charly.paranoid.apps.usageaudit

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
