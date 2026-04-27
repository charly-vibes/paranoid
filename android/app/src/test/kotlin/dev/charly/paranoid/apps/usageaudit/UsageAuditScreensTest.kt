package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayScreenPresenterTest {

    @Test
    fun `empty state when no usage data is available`() {
        val result = TodayScreenPresenter.present(summary = null)

        assertTrue(result is TodayScreenState.Empty)
    }

    @Test
    fun `empty state when summary has zero foreground duration`() {
        val summary = DailyUsageSummary(
            windowStartMillis = 1_000L,
            windowEndMillis = 86_400_000L,
            totalForegroundDurationMillis = 0L,
            appsByForegroundDuration = emptyList(),
        )
        val result = TodayScreenPresenter.present(summary)

        assertTrue(result is TodayScreenState.Empty)
    }

    @Test
    fun `populated state shows formatted total and top apps`() {
        val summary = DailyUsageSummary(
            windowStartMillis = 1_000L,
            windowEndMillis = 86_400_000L,
            totalForegroundDurationMillis = 5_400_000L, // 1h 30m
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.chat", "Chat", 3_600_000L),
                AppUsageSummary("com.example.reader", "Reader", 1_800_000L),
            ),
        )
        val result = TodayScreenPresenter.present(summary)

        assertTrue(result is TodayScreenState.Populated)
        val populated = result as TodayScreenState.Populated
        assertEquals("1h 30m", populated.totalUsageFormatted)
        assertEquals(2, populated.topApps.size)
        assertEquals("Chat", populated.topApps[0].label)
        assertEquals("1h 0m", populated.topApps[0].durationFormatted)
        assertEquals("Reader", populated.topApps[1].label)
        assertEquals("30m", populated.topApps[1].durationFormatted)
    }

    @Test
    fun `populated state formats durations under one hour without hours`() {
        val summary = DailyUsageSummary(
            windowStartMillis = 0L,
            windowEndMillis = 86_400_000L,
            totalForegroundDurationMillis = 2_700_000L, // 45m
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.music", "Music", 2_700_000L),
            ),
        )
        val result = TodayScreenPresenter.present(summary) as TodayScreenState.Populated

        assertEquals("45m", result.totalUsageFormatted)
        assertEquals("45m", result.topApps[0].durationFormatted)
    }

    @Test
    fun `populated state formats durations under one minute as less than 1m`() {
        val summary = DailyUsageSummary(
            windowStartMillis = 0L,
            windowEndMillis = 86_400_000L,
            totalForegroundDurationMillis = 30_000L, // 30s
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.quick", "Quick", 30_000L),
            ),
        )
        val result = TodayScreenPresenter.present(summary) as TodayScreenState.Populated

        assertEquals("<1m", result.totalUsageFormatted)
    }
}

class LastNightScreenPresenterTest {

    @Test
    fun `empty state when no audit data is available`() {
        val result = LastNightScreenPresenter.present(audit = null)

        assertTrue(result is LastNightScreenState.Empty)
    }

    @Test
    fun `summary shows battery delta and observed activity`() {
        val audit = OvernightAudit(
            windowStartMillis = 100L,
            windowEndMillis = 400L,
            batteryStartPercent = 82,
            batteryEndPercent = 74,
            batteryDeltaPercent = -8,
            totalForegroundDurationMillis = 1_800_000L,
            activeApps = listOf(
                AppUsageSummary("com.example.chat", "Chat", 1_200_000L),
                AppUsageSummary("com.example.reader", "Reader", 600_000L),
            ),
            activeAppsCount = 2,
            hadChargingTransition = false,
            hasIncompleteBatteryCoverage = false,
            warningFlags = emptySet(),
        )
        val result = LastNightScreenPresenter.present(audit) as LastNightScreenState.Populated

        assertEquals("82%", result.batteryStart)
        assertEquals("74%", result.batteryEnd)
        assertEquals("−8%", result.batteryDelta)
        assertEquals(2, result.activeApps.size)
        assertEquals("Chat", result.activeApps[0].label)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `shows incomplete battery data warning`() {
        val audit = makeAudit(
            warningFlags = setOf(WarningFlag.INCOMPLETE_BATTERY_DATA),
            hasIncompleteBatteryCoverage = true,
        )
        val result = LastNightScreenPresenter.present(audit) as LastNightScreenState.Populated

        assertTrue(result.warnings.any { it.contains("Battery data may be incomplete") })
    }

    @Test
    fun `shows no observed activity wording when battery dropped without foreground apps`() {
        val audit = makeAudit(
            activeApps = emptyList(),
            activeAppsCount = 0,
            totalForegroundDurationMillis = 0L,
            batteryDeltaPercent = -8,
            warningFlags = setOf(WarningFlag.NO_OBSERVED_APP_ACTIVITY),
        )
        val result = LastNightScreenPresenter.present(audit) as LastNightScreenState.Populated

        assertTrue(result.warnings.any { it.contains("No foreground app activity was observed") })
    }

    @Test
    fun `shows charging transition warning`() {
        val audit = makeAudit(
            hadChargingTransition = true,
            warningFlags = setOf(WarningFlag.CHARGING_TRANSITION),
        )
        val result = LastNightScreenPresenter.present(audit) as LastNightScreenState.Populated

        assertTrue(result.warnings.any { it.contains("charging activity") })
    }

    @Test
    fun `active apps labeled as observed activity not suspected contributors when no drain`() {
        val audit = makeAudit(
            batteryDeltaPercent = 0,
            activeApps = listOf(
                AppUsageSummary("com.example.chat", "Chat", 600_000L),
            ),
            activeAppsCount = 1,
        )
        val result = LastNightScreenPresenter.present(audit) as LastNightScreenState.Populated

        assertEquals("Observed activity", result.activeAppsHeading)
    }

    @Test
    fun `active apps labeled as suspected contributors when battery dropped`() {
        val audit = makeAudit(
            batteryDeltaPercent = -5,
            activeApps = listOf(
                AppUsageSummary("com.example.chat", "Chat", 600_000L),
            ),
            activeAppsCount = 1,
        )
        val result = LastNightScreenPresenter.present(audit) as LastNightScreenState.Populated

        assertEquals("Suspected contributors", result.activeAppsHeading)
    }

    @Test
    fun `null battery values show unknown`() {
        val audit = makeAudit(
            batteryStartPercent = null,
            batteryEndPercent = null,
            batteryDeltaPercent = null,
        )
        val result = LastNightScreenPresenter.present(audit) as LastNightScreenState.Populated

        assertEquals("—", result.batteryStart)
        assertEquals("—", result.batteryEnd)
        assertEquals("—", result.batteryDelta)
    }

    private fun makeAudit(
        batteryStartPercent: Int? = 80,
        batteryEndPercent: Int? = 72,
        batteryDeltaPercent: Int? = -8,
        totalForegroundDurationMillis: Long = 1_800_000L,
        activeApps: List<AppUsageSummary> = listOf(
            AppUsageSummary("com.example.chat", "Chat", 1_800_000L),
        ),
        activeAppsCount: Int = activeApps.size,
        hadChargingTransition: Boolean = false,
        hasIncompleteBatteryCoverage: Boolean = false,
        warningFlags: Set<WarningFlag> = emptySet(),
    ) = OvernightAudit(
        windowStartMillis = 100L,
        windowEndMillis = 400L,
        batteryStartPercent = batteryStartPercent,
        batteryEndPercent = batteryEndPercent,
        batteryDeltaPercent = batteryDeltaPercent,
        totalForegroundDurationMillis = totalForegroundDurationMillis,
        activeApps = activeApps,
        activeAppsCount = activeAppsCount,
        hadChargingTransition = hadChargingTransition,
        hasIncompleteBatteryCoverage = hasIncompleteBatteryCoverage,
        warningFlags = warningFlags,
    )
}
