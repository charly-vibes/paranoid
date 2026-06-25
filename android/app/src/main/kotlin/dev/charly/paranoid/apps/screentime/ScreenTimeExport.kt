package dev.charly.paranoid.apps.screentime

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure builders for exporting screen-time daily activity as human-readable text or CSV. App labels
 * are resolved through an injected [labelFor] function so this stays Android-free and unit-testable;
 * the UI passes a PackageManager-backed resolver, tests pass the identity function (package names).
 */
object ScreenTimeExport {

    const val CSV_FILENAME = "screentime-activity.csv"

    private const val CSV_HEADER =
        "date,window_start_millis,window_end_millis,total_foreground_millis,package_name,app_label,app_foreground_millis"

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Human-readable summary: one line per day with total and the top apps. */
    fun summaryText(
        history: List<DayUsage>,
        topApps: Int = 3,
        labelFor: (String) -> String = { it },
    ): String {
        val sb = StringBuilder()
        sb.appendLine("ScreenTime activity")
        if (history.isEmpty()) {
            sb.append("No activity recorded.")
            return sb.toString()
        }
        for (day in history) {
            sb.append(dateFmt.format(Date(day.startMillis)))
            sb.append(": ")
            sb.append(formatDuration(day.totalForegroundMillis))
            val tops = day.appsByForeground.take(topApps)
            if (tops.isNotEmpty()) {
                sb.append(" — ")
                sb.append(tops.joinToString(", ") { "${labelFor(it.packageName)} ${formatDuration(it.foregroundMillis)}" })
            }
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    /** CSV with one row per app per day; days with no app activity get a single total-only row. */
    fun csv(
        history: List<DayUsage>,
        labelFor: (String) -> String = { it },
    ): String {
        val sb = StringBuilder()
        sb.appendLine(CSV_HEADER)
        for (day in history) {
            val date = dateFmt.format(Date(day.startMillis))
            if (day.appsByForeground.isEmpty()) {
                sb.appendLine(
                    row(date, day.startMillis, day.endMillis, day.totalForegroundMillis, "", "", ""),
                )
            } else {
                for (app in day.appsByForeground) {
                    sb.appendLine(
                        row(
                            date,
                            day.startMillis,
                            day.endMillis,
                            day.totalForegroundMillis,
                            app.packageName,
                            labelFor(app.packageName),
                            app.foregroundMillis.toString(),
                        ),
                    )
                }
            }
        }
        return sb.toString().trimEnd('\n')
    }

    private fun row(
        date: String,
        start: Long,
        end: Long,
        total: Long,
        packageName: String,
        appLabel: String,
        appMillis: String,
    ): String = listOf(
        date,
        start.toString(),
        end.toString(),
        total.toString(),
        escape(packageName),
        escape(appLabel),
        appMillis,
    ).joinToString(",")

    /** Minimal CSV escaping: quote fields containing a comma, quote, or newline. */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
