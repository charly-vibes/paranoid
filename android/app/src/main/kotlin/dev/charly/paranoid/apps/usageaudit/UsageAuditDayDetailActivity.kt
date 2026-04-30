package dev.charly.paranoid.apps.usageaudit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R

/**
 * Day Detail screen — shows total foreground time and the ranked app list for a
 * specific past day. Slice A scope: total + ranked apps + zero-usage state.
 * Hourly distribution and overnight summary will be added in Slice B.
 */
class UsageAuditDayDetailActivity : AppCompatActivity() {

    private lateinit var dataProvider: UsageAuditDataProvider

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

        loadAndRender(targetDayStart)
    }

    private fun loadAndRender(targetDayStart: Long) {
        dataProvider.load(lifecycleScope) { data ->
            val summary = data.recentDays.firstOrNull { it.windowStartMillis == targetDayStart }
                ?: zeroUsagePlaceholder(targetDayStart)
            render(DayDetailPresenter.present(summary))
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

    companion object {
        const val EXTRA_DAY_START_MILLIS = "dev.charly.paranoid.usageaudit.day_start_millis"

        fun newIntent(context: Context, dayStartMillis: Long): Intent =
            Intent(context, UsageAuditDayDetailActivity::class.java).apply {
                putExtra(EXTRA_DAY_START_MILLIS, dayStartMillis)
            }
    }
}
