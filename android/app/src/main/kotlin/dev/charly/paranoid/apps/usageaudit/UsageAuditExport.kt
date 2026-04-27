package dev.charly.paranoid.apps.usageaudit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TodaySummaryFormatter {
    fun format(summary: DailyUsageSummary?): String {
        if (summary == null) return "Today — No usage data available."
        val sb = StringBuilder()
        sb.appendLine("Today")
        sb.appendLine("Total observed activity: ${formatDuration(summary.totalForegroundDurationMillis)}")
        if (summary.appsByForegroundDuration.isNotEmpty()) {
            sb.appendLine()
            for (app in summary.appsByForegroundDuration) {
                sb.appendLine("  ${app.appLabel}: ${formatDuration(app.foregroundDurationMillis)}")
            }
        }
        return sb.toString().trimEnd()
    }
}

object LastNightSummaryFormatter {
    fun format(audit: OvernightAudit?): String {
        if (audit == null) return "Last Night — No overnight data available."
        val sb = StringBuilder()
        sb.appendLine("Last Night")
        val startPct = audit.batteryStartPercent?.let { "$it%" } ?: "—"
        val endPct = audit.batteryEndPercent?.let { "$it%" } ?: "—"
        sb.appendLine("Battery: $startPct → $endPct (${formatDelta(audit.batteryDeltaPercent)})")

        for (flag in audit.warningFlags) {
            sb.appendLine("⚠ ${warningText(flag)}")
        }

        if (audit.activeApps.isNotEmpty()) {
            val heading = if (audit.batteryDeltaPercent != null && audit.batteryDeltaPercent < 0) {
                "Suspected contributors"
            } else {
                "Observed activity"
            }
            sb.appendLine()
            sb.appendLine(heading)
            for (app in audit.activeApps) {
                sb.appendLine("  ${app.appLabel}: ${formatDuration(app.foregroundDurationMillis)}")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun formatDelta(delta: Int?): String = when {
        delta == null -> "—"
        delta < 0 -> "−${-delta}%"
        delta > 0 -> "+$delta%"
        else -> "0%"
    }

    private fun warningText(flag: WarningFlag): String = when (flag) {
        WarningFlag.INCOMPLETE_BATTERY_DATA -> "Battery data may be incomplete for this window."
        WarningFlag.NO_OBSERVED_APP_ACTIVITY -> "No foreground app activity was observed during this window."
        WarningFlag.CHARGING_TRANSITION -> "This window included charging activity."
    }
}

object CsvExporter {
    private const val HEADER =
        "report_date,window_start,window_end,app_label,package_name,foreground_duration_millis,battery_start_percent,battery_end_percent,battery_delta_percent,warning_flags"

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun exportToday(summary: DailyUsageSummary?): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        if (summary == null) return sb.toString()

        val reportDate = dateFmt.format(Date(summary.windowStartMillis))
        for (app in summary.appsByForegroundDuration) {
            sb.appendLine(
                csvRow(
                    reportDate,
                    summary.windowStartMillis.toString(),
                    summary.windowEndMillis.toString(),
                    app.appLabel,
                    app.packageName,
                    app.foregroundDurationMillis.toString(),
                    "", "", "", "",
                )
            )
        }
        return sb.toString()
    }

    fun exportLastNight(audit: OvernightAudit?): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        if (audit == null) return sb.toString()

        val reportDate = dateFmt.format(Date(audit.windowStartMillis))
        val batteryStart = audit.batteryStartPercent?.toString() ?: ""
        val batteryEnd = audit.batteryEndPercent?.toString() ?: ""
        val batteryDelta = audit.batteryDeltaPercent?.toString() ?: ""
        val flags = audit.warningFlags.joinToString(";") { it.name.lowercase() }

        for (app in audit.activeApps) {
            sb.appendLine(
                csvRow(
                    reportDate,
                    audit.windowStartMillis.toString(),
                    audit.windowEndMillis.toString(),
                    app.appLabel,
                    app.packageName,
                    app.foregroundDurationMillis.toString(),
                    batteryStart, batteryEnd, batteryDelta, flags,
                )
            )
        }

        // Summary row when no apps or as a totals line
        sb.appendLine(
            csvRow(
                reportDate,
                audit.windowStartMillis.toString(),
                audit.windowEndMillis.toString(),
                "[summary]",
                "",
                audit.totalForegroundDurationMillis.toString(),
                batteryStart, batteryEnd, batteryDelta, flags,
            )
        )
        return sb.toString()
    }

    private fun csvRow(vararg fields: String): String =
        fields.joinToString(",") { escapeCsv(it) }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
