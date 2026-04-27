package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryScreenPresenterTest {

    @Test
    fun `empty state when no history entries`() {
        val result = HistoryScreenPresenter.present(emptyList())

        assertTrue(result is HistoryScreenState.Empty)
    }

    @Test
    fun `populated state with formatted entries`() {
        val audits = listOf(
            OvernightAudit(
                windowStartMillis = 1_713_736_800_000L, // 2024-04-21T22:00Z
                windowEndMillis = 1_713_769_200_000L,   // 2024-04-22T07:00Z
                batteryStartPercent = 82,
                batteryEndPercent = 74,
                batteryDeltaPercent = -8,
                totalForegroundDurationMillis = 1_800_000L,
                activeApps = listOf(
                    AppUsageSummary("com.example.chat", "Chat", 1_800_000L),
                ),
                activeAppsCount = 1,
                hadChargingTransition = false,
                hasIncompleteBatteryCoverage = false,
                warningFlags = emptySet(),
            ),
            OvernightAudit(
                windowStartMillis = 1_713_650_400_000L, // 2024-04-20T22:00Z
                windowEndMillis = 1_713_682_800_000L,   // 2024-04-21T07:00Z
                batteryStartPercent = 90,
                batteryEndPercent = 85,
                batteryDeltaPercent = -5,
                totalForegroundDurationMillis = 0L,
                activeApps = emptyList(),
                activeAppsCount = 0,
                hadChargingTransition = false,
                hasIncompleteBatteryCoverage = false,
                warningFlags = setOf(WarningFlag.NO_OBSERVED_APP_ACTIVITY),
            ),
        )

        val result = HistoryScreenPresenter.present(audits) as HistoryScreenState.Populated

        assertEquals(2, result.entries.size)
        assertEquals("−8%", result.entries[0].batteryDelta)
        assertEquals(1, result.entries[0].appCount)
        assertEquals("−5%", result.entries[1].batteryDelta)
        assertEquals(0, result.entries[1].appCount)
        assertTrue(result.entries[1].hasWarnings)
    }

    @Test
    fun `entry without battery data shows unknown delta`() {
        val audits = listOf(
            OvernightAudit(
                windowStartMillis = 100L,
                windowEndMillis = 400L,
                batteryStartPercent = null,
                batteryEndPercent = null,
                batteryDeltaPercent = null,
                totalForegroundDurationMillis = 0L,
                activeApps = emptyList(),
                activeAppsCount = 0,
                hadChargingTransition = false,
                hasIncompleteBatteryCoverage = true,
                warningFlags = setOf(WarningFlag.INCOMPLETE_BATTERY_DATA),
            ),
        )

        val result = HistoryScreenPresenter.present(audits) as HistoryScreenState.Populated

        assertEquals("—", result.entries[0].batteryDelta)
        assertTrue(result.entries[0].hasWarnings)
    }
}
