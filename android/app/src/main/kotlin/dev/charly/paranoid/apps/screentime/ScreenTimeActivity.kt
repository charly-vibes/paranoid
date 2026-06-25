package dev.charly.paranoid.apps.screentime

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.screentime.data.toDomain
import dev.charly.paranoid.apps.screentime.service.ScreenTimeMonitoringPrefs
import dev.charly.paranoid.apps.screentime.service.ScreenTimePermission
import dev.charly.paranoid.apps.screentime.service.ScreenTimePermissions
import dev.charly.paranoid.apps.screentime.service.ScreenTimeService
import dev.charly.paranoid.apps.screentime.service.ScreenTimeShare
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point for the ScreenTime mini-app. Shows the status of the three required permissions with
 * deep-link buttons, a start/stop toggle for [ScreenTimeService], today's recorded sessions, and an
 * inline warning when a permission is revoked while monitoring is active.
 */
class ScreenTimeActivity : AppCompatActivity() {

    private val timeFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    private val dayFormat by lazy { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    private val permissionLabels = mapOf(
        ScreenTimePermission.USAGE_ACCESS to "Usage access",
        ScreenTimePermission.NOTIFICATIONS to "Notifications",
        ScreenTimePermission.OVERLAY to "Display over other apps",
    )

    /** Daily activity (most recent first) and resolved app labels, cached for export. */
    private var exportHistory: List<DayUsage> = emptyList()
    private var labelCache: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_time)
        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.screentime_toggle).setOnClickListener { onToggleClicked() }
        findViewById<TextView>(R.id.screentime_share_summary).setOnClickListener { onShareSummary() }
        findViewById<TextView>(R.id.screentime_export_csv).setOnClickListener { onExportCsv() }
    }

    override fun onResume() {
        super.onResume()
        renderPermissions()
        renderToggle()
        renderWarning()
        loadAndRender()
    }

    private fun onToggleClicked() {
        if (ScreenTimeMonitoringPrefs.isEnabled(this)) {
            startService(ScreenTimeService.stopIntent(this))
            // Update the flag optimistically so the UI flips immediately; the service writes the
            // same value when it stops. Without this the toggle reads a stale pref until onResume.
            ScreenTimeMonitoringPrefs.setEnabled(this, false)
        } else {
            if (!ScreenTimePermissions.allGranted(this)) {
                renderWarning(force = "Grant all permissions above before starting.")
                return
            }
            startForegroundService(ScreenTimeService.startIntent(this))
            ScreenTimeMonitoringPrefs.setEnabled(this, true)
        }
        renderToggle()
        renderWarning()
    }

    private fun renderPermissions() {
        val container = findViewById<LinearLayout>(R.id.screentime_permissions)
        container.removeAllViews()
        for (permission in ScreenTimePermission.entries) {
            val granted = ScreenTimePermissions.isGranted(this, permission)
            container.addView(buildPermissionRow(permission, granted))
        }
    }

    private fun buildPermissionRow(permission: ScreenTimePermission, granted: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val label = TextView(this).apply {
            text = "${permissionLabels[permission]}: ${if (granted) "Granted" else "Not granted"}"
            setTextColor(if (granted) 0xFF81C784.toInt() else 0xFFFF8A80.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)
        if (!granted) {
            val grant = TextView(this).apply {
                text = "Grant"
                setTextColor(0xFF64B5F6.toInt())
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { ScreenTimePermissions.openSettings(this@ScreenTimeActivity, permission) }
            }
            row.addView(grant)
        }
        return row
    }

    private fun renderToggle() {
        val enabled = ScreenTimeMonitoringPrefs.isEnabled(this)
        findViewById<TextView>(R.id.screentime_toggle).setText(
            if (enabled) R.string.screentime_stop else R.string.screentime_start,
        )
    }

    private fun renderWarning(force: String? = null) {
        val warning = findViewById<TextView>(R.id.screentime_warning)
        if (force != null) {
            warning.text = force
            warning.visibility = View.VISIBLE
            return
        }
        val monitoring = ScreenTimeMonitoringPrefs.isEnabled(this)
        val revoked = ScreenTimePermission.entries.filterNot { ScreenTimePermissions.isGranted(this, it) }
        if (monitoring && revoked.isNotEmpty()) {
            val names = revoked.joinToString(", ") { permissionLabels[it] ?: it.name }
            warning.text = "Monitoring is degraded: $names revoked. Re-grant to restore full tracking."
            warning.visibility = View.VISIBLE
        } else {
            warning.visibility = View.GONE
        }
    }

    /** Loaded view models built off the main thread. */
    private class LoadedData(
        val sessionLines: List<String>,
        val historyLines: List<String>,
        val exportHistory: List<DayUsage>,
        val labels: Map<String, String>,
    )

    private fun loadAndRender() {
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            // All DB work and PackageManager label resolution happen off the main thread; resolving
            // labels on the UI thread is what made the list feel laggy.
            val data = withContext(Dispatchers.IO) {
                val dao = ParanoidDatabase.getInstance(applicationContext).screenTimeDao()
                // Load the full retention window once; derive both today's sessions and the daily
                // history from it so we hit the DB a single time.
                val windowStart = now - RetentionPolicy.RETENTION_DAYS * MILLIS_PER_DAY
                val sessions = dao.sessionsOverlapping(windowStart, now)
                    .map { it.toDomain(dao.intervalsForSession(it.id)) }

                // Resolve every package label once into a cache.
                val labels = sessions
                    .flatMap { s -> s.appIntervals.map { it.packageName } }
                    .distinct()
                    .associateWith { appLabel(it) }

                val sessionLines = TodaySessionsPresenter.present(sessions, now).map { row ->
                    val time = timeFormat.format(Date(row.startMillis))
                    val duration = formatDuration(row.durationMillis)
                    val openMarker = if (row.isOpen) " (active)" else ""
                    val top = row.topAppPackage?.let { labels[it] ?: it } ?: "—"
                    "$time · $duration$openMarker\n$top"
                }

                // Display the last two weeks of days that actually have activity; export keeps the
                // full retained window (also activity-only).
                val displayHistory = ReportAggregator.dailyHistory(sessions, now, days = 14)
                    .filter { it.totalForegroundMillis > 0L }
                val exportHistory = ReportAggregator.dailyHistory(
                    sessions, now, days = RetentionPolicy.RETENTION_DAYS.toInt(),
                ).filter { it.totalForegroundMillis > 0L }

                val historyLines = displayHistory.map { day ->
                    val date = dayFormat.format(Date(day.startMillis))
                    val total = formatDuration(day.totalForegroundMillis)
                    val top = day.appsByForeground.firstOrNull()
                        ?.let { "${labels[it.packageName] ?: it.packageName} ${formatDuration(it.foregroundMillis)}" }
                        ?: "—"
                    "$date · $total\n$top"
                }

                LoadedData(sessionLines, historyLines, exportHistory, labels)
            }

            exportHistory = data.exportHistory
            labelCache = data.labels
            renderSessions(data.sessionLines)
            renderHistory(data.historyLines)
        }
    }

    private fun renderSessions(lines: List<String>) {
        val empty = findViewById<TextView>(R.id.screentime_sessions_empty)
        val container = findViewById<LinearLayout>(R.id.screentime_sessions)
        container.removeAllViews()
        empty.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        for (line in lines) {
            container.addView(buildSessionRow(line))
        }
    }

    private fun renderHistory(lines: List<String>) {
        val empty = findViewById<TextView>(R.id.screentime_history_empty)
        val container = findViewById<LinearLayout>(R.id.screentime_history)
        container.removeAllViews()
        empty.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        for (line in lines) {
            container.addView(buildSessionRow(line))
        }
    }

    private fun onShareSummary() {
        if (exportHistory.isEmpty()) {
            Toast.makeText(this, "No activity to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val text = ScreenTimeExport.summaryText(exportHistory) { labelCache[it] ?: it }
        ScreenTimeShare.shareText(this, text)
    }

    private fun onExportCsv() {
        if (exportHistory.isEmpty()) {
            Toast.makeText(this, "No activity to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val csv = ScreenTimeExport.csv(exportHistory) { labelCache[it] ?: it }
        ScreenTimeShare.shareCsv(this, csv, ScreenTimeExport.CSV_FILENAME)
    }

    private fun buildSessionRow(line: String): View {
        val view = TextView(this).apply {
            text = line
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.app_item_bg)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) }
        view.layoutParams = params
        return view
    }

    private fun appLabel(packageName: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().ifBlank { packageName }
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}
