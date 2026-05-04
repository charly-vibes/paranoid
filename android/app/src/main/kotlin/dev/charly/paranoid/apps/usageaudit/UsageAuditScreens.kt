package dev.charly.paranoid.apps.usageaudit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class AppRow(
    val label: String,
    val durationFormatted: String,
    /** Source package name; null for callers that don't carry it (e.g. legacy formatters). */
    val packageName: String? = null,
)

data class DailyHistoryEntry(
    val dayStartMillis: Long,
    val dateFormatted: String,
    val totalUsageFormatted: String,
)

sealed interface DailyHistoryScreenState {
    data object Empty : DailyHistoryScreenState
    data class Populated(val entries: List<DailyHistoryEntry>) : DailyHistoryScreenState
}

data class HourlyBar(
    val hourLabel: String,
    val foregroundDurationMillis: Long,
    val durationFormatted: String,
    /** Fill ratio, 0f..1f, relative to the busiest bucket of the day. */
    val intensity: Float,
)

data class OvernightSummaryRow(
    val windowFormatted: String,
    val batteryDelta: String,
    val activeAppsCount: Int,
    val warningCount: Int,
)

data class DayDetailScreenState(
    val dayStartMillis: Long,
    val dateFormatted: String,
    val totalUsageFormatted: String,
    val apps: List<AppRow>,
    val hourlyBars: List<HourlyBar>,
    val overnightSummary: OvernightSummaryRow?,
    val showZeroUsageMessage: Boolean,
)

object DailyHistoryPresenter {
    fun present(
        days: List<DailyUsageSummary>,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): DailyHistoryScreenState {
        if (days.isEmpty()) return DailyHistoryScreenState.Empty
        val fmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return DailyHistoryScreenState.Populated(
            entries = days.map { day ->
                DailyHistoryEntry(
                    dayStartMillis = day.windowStartMillis,
                    dateFormatted = fmt.format(Date(day.windowStartMillis)),
                    totalUsageFormatted = formatDayDuration(day.totalForegroundDurationMillis),
                )
            },
        )
    }
}

object DayDetailPresenter {
    fun present(
        summary: DailyUsageSummary,
        hourlyBuckets: List<HourlyBucket> = emptyList(),
        overnight: OvernightAudit? = null,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): DayDetailScreenState {
        val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val hourFmt = SimpleDateFormat("HH", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val isZero = summary.totalForegroundDurationMillis == 0L
        val maxBucketMillis = hourlyBuckets.maxOfOrNull { it.foregroundDurationMillis } ?: 0L
        val bars = hourlyBuckets.map { bucket ->
            HourlyBar(
                hourLabel = hourFmt.format(Date(bucket.hourStartMillis)),
                foregroundDurationMillis = bucket.foregroundDurationMillis,
                durationFormatted = formatDayDuration(bucket.foregroundDurationMillis),
                intensity = if (maxBucketMillis <= 0L) 0f else bucket.foregroundDurationMillis.toFloat() / maxBucketMillis.toFloat(),
            )
        }
        val overnightRow = overnight
            ?.takeIf { it.windowStartMillis in summary.windowStartMillis until summary.windowEndMillis }
            ?.let { audit ->
                OvernightSummaryRow(
                    windowFormatted = "${timeFmt.format(Date(audit.windowStartMillis))}–${timeFmt.format(Date(audit.windowEndMillis))}",
                    batteryDelta = audit.batteryDeltaPercent?.let(::formatBatteryDelta) ?: "—",
                    activeAppsCount = audit.activeAppsCount,
                    warningCount = audit.warningFlags.size,
                )
            }
        return DayDetailScreenState(
            dayStartMillis = summary.windowStartMillis,
            dateFormatted = dateFmt.format(Date(summary.windowStartMillis)),
            totalUsageFormatted = formatDayDuration(summary.totalForegroundDurationMillis),
            apps = summary.appsByForegroundDuration.map { app ->
                AppRow(
                    label = app.appLabel,
                    durationFormatted = formatDuration(app.foregroundDurationMillis),
                    packageName = app.packageName,
                )
            },
            hourlyBars = bars,
            overnightSummary = overnightRow,
            showZeroUsageMessage = isZero,
        )
    }
}

data class IntervalRow(
    val startFormatted: String,
    val endFormatted: String,
    val durationFormatted: String,
)

data class AppDetailScreenState(
    val packageName: String,
    val displayLabel: String,
    val isUninstalled: Boolean,
    val dateFormatted: String,
    val totalUsageFormatted: String,
    val intervals: List<IntervalRow>,
    val showNoActivityMessage: Boolean,
)

object AppDetailPresenter {
    fun present(
        detail: AppDayDetail,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): AppDetailScreenState {
        val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val (label, isUninstalled) = when (val l = detail.appLabel) {
            is AppLabel.Installed -> l.label to false
            AppLabel.Uninstalled -> detail.packageName to true
        }
        val intervalRows = detail.intervals.map { interval ->
            IntervalRow(
                startFormatted = timeFmt.format(Date(interval.startMillis)),
                endFormatted = timeFmt.format(Date(interval.endMillis)),
                durationFormatted = formatDuration(interval.endMillis - interval.startMillis),
            )
        }
        return AppDetailScreenState(
            packageName = detail.packageName,
            displayLabel = label,
            isUninstalled = isUninstalled,
            dateFormatted = dateFmt.format(Date(detail.dayStartMillis)),
            totalUsageFormatted = formatDayDuration(detail.totalForegroundDurationMillis),
            intervals = intervalRows,
            showNoActivityMessage = detail.totalForegroundDurationMillis == 0L && intervalRows.isEmpty(),
        )
    }
}

private fun formatBatteryDelta(delta: Int): String = when {
    delta < 0 -> "−${-delta}%"
    delta > 0 -> "+$delta%"
    else -> "0%"
}

/**
 * Like [formatDuration], but renders an exact zero as "0m" rather than "<1m".
 * Used for day-scoped totals where zero is a meaningful state ("no recorded usage").
 */
internal fun formatDayDuration(millis: Long): String =
    if (millis == 0L) "0m" else formatDuration(millis)

sealed interface TodayScreenState {
    data object Empty : TodayScreenState
    data class Populated(
        val totalUsageFormatted: String,
        val topApps: List<AppRow>,
    ) : TodayScreenState
}

sealed interface LastNightScreenState {
    data object Empty : LastNightScreenState
    data class Populated(
        val batteryStart: String,
        val batteryEnd: String,
        val batteryDelta: String,
        val activeAppsHeading: String,
        val activeApps: List<AppRow>,
        val warnings: List<String>,
    ) : LastNightScreenState
}

object TodayScreenPresenter {
    fun present(summary: DailyUsageSummary?): TodayScreenState {
        if (summary == null || summary.totalForegroundDurationMillis == 0L) {
            return TodayScreenState.Empty
        }
        return TodayScreenState.Populated(
            totalUsageFormatted = formatDuration(summary.totalForegroundDurationMillis),
            topApps = summary.appsByForegroundDuration.map { app ->
                AppRow(
                    label = app.appLabel,
                    durationFormatted = formatDuration(app.foregroundDurationMillis),
                )
            },
        )
    }
}

object LastNightScreenPresenter {
    fun present(audit: OvernightAudit?): LastNightScreenState {
        if (audit == null) return LastNightScreenState.Empty

        val warnings = buildList {
            if (WarningFlag.INCOMPLETE_BATTERY_DATA in audit.warningFlags) {
                add("Battery data may be incomplete for this window.")
            }
            if (WarningFlag.NO_OBSERVED_APP_ACTIVITY in audit.warningFlags) {
                add("No foreground app activity was observed during this window.")
            }
            if (WarningFlag.CHARGING_TRANSITION in audit.warningFlags) {
                add("This window included charging activity.")
            }
        }

        val heading = if (audit.batteryDeltaPercent != null && audit.batteryDeltaPercent < 0) {
            "Suspected contributors"
        } else {
            "Observed activity"
        }

        return LastNightScreenState.Populated(
            batteryStart = audit.batteryStartPercent?.let { "$it%" } ?: "—",
            batteryEnd = audit.batteryEndPercent?.let { "$it%" } ?: "—",
            batteryDelta = audit.batteryDeltaPercent?.let { formatDelta(it) } ?: "—",
            activeAppsHeading = heading,
            activeApps = audit.activeApps.map { app ->
                AppRow(
                    label = app.appLabel,
                    durationFormatted = formatDuration(app.foregroundDurationMillis),
                )
            },
            warnings = warnings,
        )
    }

    private fun formatDelta(delta: Int): String = when {
        delta < 0 -> "−${-delta}%"
        delta > 0 -> "+$delta%"
        else -> "0%"
    }
}

internal fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000L
    if (totalMinutes < 1L) return "<1m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h 0m"
        else -> "${minutes}m"
    }
}
