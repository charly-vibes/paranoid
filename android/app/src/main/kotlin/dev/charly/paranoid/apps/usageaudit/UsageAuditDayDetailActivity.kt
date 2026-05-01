package dev.charly.paranoid.apps.usageaudit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R

/**
 * Day Detail screen — shows total foreground time, hourly distribution, the
 * ranked app list, and (when available) the overnight summary attached to the
 * selected day. Slice B adds the hourly bars, the overnight panel, and
 * day-scoped Share/CSV.
 */
class UsageAuditDayDetailActivity : AppCompatActivity() {

    private lateinit var dataProvider: UsageAuditDataProvider
    private var currentSummary: DailyUsageSummary? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_audit_day_detail)

        dataProvider = UsageAuditDependencies.dataProviderFactory(this)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        val targetDayStart = intent.getLongExtra(EXTRA_DAY_START_MILLIS, -1L)
        if (targetDayStart < 0L) {
            finish()
            return
        }

        findViewById<Button>(R.id.day_detail_share_summary).setOnClickListener {
            currentSummary?.let { summary ->
                UsageAuditShare.shareText(this, TodaySummaryFormatter.format(summary))
            }
        }
        findViewById<Button>(R.id.day_detail_export_csv).setOnClickListener {
            currentSummary?.let { summary ->
                UsageAuditShare.shareCsv(
                    context = this,
                    csv = CsvExporter.exportToday(summary),
                    filename = "usage-audit-day-${summary.windowStartMillis}.csv",
                )
            }
        }

        loadAndRender(targetDayStart)
    }

    private fun loadAndRender(targetDayStart: Long) {
        dataProvider.load(lifecycleScope) { data ->
            val summary = data.recentDays.firstOrNull { it.windowStartMillis == targetDayStart }
                ?: zeroUsagePlaceholder(targetDayStart)
            currentSummary = summary
            val hourlyBuckets = data.recentDayHourlyBuckets[targetDayStart].orEmpty()
            val overnight = data.recentNights.firstOrNull {
                it.windowStartMillis in summary.windowStartMillis until summary.windowEndMillis
            }
            render(
                DayDetailPresenter.present(
                    summary = summary,
                    hourlyBuckets = hourlyBuckets,
                    overnight = overnight,
                ),
            )
        }
    }

    private fun zeroUsagePlaceholder(dayStart: Long): DailyUsageSummary {
        val dayEnd = dayStart + 24L * 60 * 60 * 1_000
        return DailyUsageSummary(
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            totalForegroundDurationMillis = 0L,
            appsByForegroundDuration = emptyList(),
        )
    }

    private fun render(state: DayDetailScreenState) {
        findViewById<TextView>(R.id.day_detail_date).text = state.dateFormatted
        findViewById<TextView>(R.id.day_detail_total).text = state.totalUsageFormatted

        val zeroUsage = findViewById<View>(R.id.day_detail_zero_usage)
        zeroUsage.visibility = if (state.showZeroUsageMessage) View.VISIBLE else View.GONE

        renderHourlyBars(state.hourlyBars)
        renderOvernight(state.overnightSummary)

        val list = findViewById<LinearLayout>(R.id.day_detail_apps_list)
        list.removeAllViews()
        for (app in state.apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                text = app.label
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            }
            val duration = TextView(this).apply {
                text = app.durationFormatted
                setTextColor(0xFF888888.toInt())
                textSize = 14f
            }
            row.addView(label)
            row.addView(duration)
            list.addView(row)
        }
    }

    private fun renderHourlyBars(bars: List<HourlyBar>) {
        val container = findViewById<LinearLayout>(R.id.day_detail_hourly_bars)
        container.removeAllViews()
        if (bars.isEmpty()) {
            container.visibility = View.GONE
            findViewById<View>(R.id.day_detail_hourly_heading).visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        findViewById<View>(R.id.day_detail_hourly_heading).visibility = View.VISIBLE
        for (bar in bars) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f,
                ).apply {
                    marginStart = 1
                    marginEnd = 1
                }
            }
            val rect = View(this).apply {
                setBackgroundColor(0xFFFFB74D.toInt())
                val heightDp = (4 + 56 * bar.intensity).coerceAtLeast(2f)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (heightDp * resources.displayMetrics.density).toInt(),
                )
            }
            column.addView(rect)
            container.addView(column)
        }
    }

    private fun renderOvernight(row: OvernightSummaryRow?) {
        val panel = findViewById<LinearLayout>(R.id.day_detail_overnight_panel)
        if (row == null) {
            panel.visibility = View.GONE
            return
        }
        panel.visibility = View.VISIBLE
        findViewById<TextView>(R.id.day_detail_overnight_window).text = row.windowFormatted
        findViewById<TextView>(R.id.day_detail_overnight_battery).text =
            "Battery delta: ${row.batteryDelta}"
        val warningSuffix = if (row.warningCount > 0) " · ${row.warningCount} warning(s)" else ""
        findViewById<TextView>(R.id.day_detail_overnight_apps).text =
            "${row.activeAppsCount} active app(s)$warningSuffix"
    }

    companion object {
        const val EXTRA_DAY_START_MILLIS = "dev.charly.paranoid.usageaudit.day_start_millis"

        fun newIntent(context: Context, dayStartMillis: Long): Intent =
            Intent(context, UsageAuditDayDetailActivity::class.java).apply {
                putExtra(EXTRA_DAY_START_MILLIS, dayStartMillis)
            }
    }
}
