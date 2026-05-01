package dev.charly.paranoid.apps.usageaudit

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

data class DayWindow(
    val startMillis: Long,
    val endMillis: Long,
)

object RecentDaysEnumerator {
    /**
     * Returns past-day windows (midnight-to-midnight in [timeZone]) chronologically,
     * oldest first. The current local day is always excluded.
     *
     * @param daysBack how many past days to include; 0 yields an empty list.
     */
    fun pastDayWindows(
        nowMillis: Long,
        daysBack: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<DayWindow> {
        if (daysBack <= 0) return emptyList()

        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val windows = ArrayList<DayWindow>(daysBack)

        // Build oldest first.
        for (offset in daysBack downTo 1) {
            val dayCal = Calendar.getInstance(timeZone).apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val start = dayCal.timeInMillis
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
            val end = dayCal.timeInMillis
            windows += DayWindow(startMillis = start, endMillis = end)
        }
        return windows
    }
}

data class AppUsageSlice(
    val packageName: String,
    val appLabel: String,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val foregroundDurationMillis: Long,
    val launchCountEstimate: Int? = null,
)

data class AppUsageSummary(
    val packageName: String,
    val appLabel: String,
    val foregroundDurationMillis: Long,
)

data class DailyUsageSummary(
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val totalForegroundDurationMillis: Long,
    val appsByForegroundDuration: List<AppUsageSummary>,
)

/**
 * Foreground time inside a single wall-clock hour bucket of a local day window.
 *
 * [hourEndMillis] is exclusive. On daylight-saving boundaries the bucket count
 * for a local day reflects real elapsed hours: 23 on a spring-forward day, 24
 * normally, 25 on a fall-back day.
 */
data class HourlyBucket(
    val hourStartMillis: Long,
    val hourEndMillis: Long,
    val foregroundDurationMillis: Long,
)

enum class ChargingState {
    CHARGING,
    DISCHARGING,
    FULL,
    UNKNOWN,
}

data class BatterySnapshot(
    val timestampMillis: Long,
    val batteryPercent: Int,
    val chargingState: ChargingState,
    val batteryStatus: String? = null,
    val batteryHealth: String? = null,
)

enum class WarningFlag {
    INCOMPLETE_BATTERY_DATA,
    CHARGING_TRANSITION,
    NO_OBSERVED_APP_ACTIVITY,
}

data class OvernightAudit(
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val batteryStartPercent: Int?,
    val batteryEndPercent: Int?,
    val batteryDeltaPercent: Int?,
    val totalForegroundDurationMillis: Long,
    val activeApps: List<AppUsageSummary>,
    val activeAppsCount: Int,
    val hadChargingTransition: Boolean,
    val hasIncompleteBatteryCoverage: Boolean,
    val warningFlags: Set<WarningFlag>,
)

object DailyUsageAggregator {
    fun summarize(
        windowStartMillis: Long,
        windowEndMillis: Long,
        slices: List<AppUsageSlice>,
    ): DailyUsageSummary {
        val appsByForegroundDuration = summarizeApps(windowStartMillis, windowEndMillis, slices)
        return DailyUsageSummary(
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
            totalForegroundDurationMillis = appsByForegroundDuration.sumOf { it.foregroundDurationMillis },
            appsByForegroundDuration = appsByForegroundDuration,
        )
    }

    /**
     * Splits [windowStartMillis, windowEndMillis) into one-hour real-time buckets
     * and assigns each slice's overlap to the bucket(s) it falls in.
     *
     * Window length is preserved exactly: on a 23-hour spring-forward local day
     * the result has 23 buckets, on a 25-hour fall-back day 25 buckets, and 24
     * otherwise. Callers are expected to pass DST-aware day windows
     * (e.g. produced by [RecentDaysEnumerator]).
     */
    fun hourlyDistribution(
        windowStartMillis: Long,
        windowEndMillis: Long,
        slices: List<AppUsageSlice>,
    ): List<HourlyBucket> {
        if (windowEndMillis <= windowStartMillis) return emptyList()
        val buckets = ArrayList<HourlyBucket>(((windowEndMillis - windowStartMillis) / MILLIS_PER_HOUR + 1).toInt())
        var hourStart = windowStartMillis
        while (hourStart < windowEndMillis) {
            val hourEnd = (hourStart + MILLIS_PER_HOUR).coerceAtMost(windowEndMillis)
            val duration = slices.sumOf { it.overlapDuration(hourStart, hourEnd) }
            buckets += HourlyBucket(
                hourStartMillis = hourStart,
                hourEndMillis = hourEnd,
                foregroundDurationMillis = duration,
            )
            hourStart = hourEnd
        }
        return buckets
    }

    private const val MILLIS_PER_HOUR: Long = 60L * 60L * 1_000L

    internal fun summarizeApps(
        windowStartMillis: Long,
        windowEndMillis: Long,
        slices: List<AppUsageSlice>,
    ): List<AppUsageSummary> = slices
        .mapNotNull { slice ->
            val overlappingDuration = slice.overlapDuration(windowStartMillis, windowEndMillis)
            if (overlappingDuration <= 0L) {
                null
            } else {
                slice.packageName to AppUsageSummary(
                    packageName = slice.packageName,
                    appLabel = slice.appLabel,
                    foregroundDurationMillis = overlappingDuration,
                )
            }
        }
        .groupBy({ it.first }, { it.second })
        .values
        .map { summaries ->
            summaries.reduce { acc, summary ->
                acc.copy(foregroundDurationMillis = acc.foregroundDurationMillis + summary.foregroundDurationMillis)
            }
        }
        .sortedWith(compareByDescending<AppUsageSummary> { it.foregroundDurationMillis }.thenBy { it.appLabel.lowercase() })
}

object OvernightAuditCalculator {
    fun summarize(
        windowStartMillis: Long,
        windowEndMillis: Long,
        snapshots: List<BatterySnapshot>,
        usageSlices: List<AppUsageSlice>,
    ): OvernightAudit {
        val sortedSnapshots = snapshots.sortedBy { it.timestampMillis }
        val batteryStart = sortedSnapshots.lastOrNull { it.timestampMillis <= windowStartMillis }
            ?: sortedSnapshots.firstOrNull { it.timestampMillis >= windowStartMillis }
        val batteryEnd = sortedSnapshots.firstOrNull { it.timestampMillis >= windowEndMillis }
            ?: sortedSnapshots.lastOrNull { it.timestampMillis <= windowEndMillis }

        val activeApps = DailyUsageAggregator.summarizeApps(
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
            slices = usageSlices,
        )
        val hasIncompleteBatteryCoverage = batteryStart == null ||
            batteryEnd == null ||
            batteryStart.timestampMillis > windowStartMillis ||
            batteryEnd.timestampMillis < windowEndMillis

        val relevantSnapshots = sortedSnapshots.filter {
            it.timestampMillis in windowStartMillis..windowEndMillis
        }
        val hadChargingTransition = relevantSnapshots
            .zipWithNext()
            .any { (left, right) -> left.chargingState != right.chargingState }

        val warningFlags = buildSet {
            if (hasIncompleteBatteryCoverage) add(WarningFlag.INCOMPLETE_BATTERY_DATA)
            if (hadChargingTransition) add(WarningFlag.CHARGING_TRANSITION)
            if (activeApps.isEmpty() && batteryStart != null && batteryEnd != null && batteryEnd.batteryPercent < batteryStart.batteryPercent) {
                add(WarningFlag.NO_OBSERVED_APP_ACTIVITY)
            }
        }

        return OvernightAudit(
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
            batteryStartPercent = batteryStart?.batteryPercent,
            batteryEndPercent = batteryEnd?.batteryPercent,
            batteryDeltaPercent = batteryStart?.batteryPercent?.let { start ->
                batteryEnd?.batteryPercent?.minus(start)
            },
            totalForegroundDurationMillis = activeApps.sumOf { it.foregroundDurationMillis },
            activeApps = activeApps,
            activeAppsCount = activeApps.size,
            hadChargingTransition = hadChargingTransition,
            hasIncompleteBatteryCoverage = hasIncompleteBatteryCoverage,
            warningFlags = warningFlags,
        )
    }
}

private fun AppUsageSlice.overlapDuration(windowStartMillis: Long, windowEndMillis: Long): Long {
    if (windowEndMillis <= windowStartMillis || this.windowEndMillis <= this.windowStartMillis) {
        return 0L
    }
    val overlapStart = max(windowStartMillis, this.windowStartMillis)
    val overlapEnd = min(windowEndMillis, this.windowEndMillis)
    val overlapMillis = max(0L, overlapEnd - overlapStart)
    return min(overlapMillis, foregroundDurationMillis.coerceAtLeast(0L))
}
