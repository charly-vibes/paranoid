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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App Detail screen — drills into a single package on a chosen day to show
 * total observed foreground time and the list of observed foreground
 * intervals (start/end) within that day.
 *
 * Source data is read directly via [AndroidUsageIntervalReader] because
 * [UsageAuditDataProvider] aggregates by day and discards per-interval data.
 */
class UsageAuditAppDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_audit_app_detail)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val dayStart = intent.getLongExtra(EXTRA_DAY_START_MILLIS, -1L)
        if (packageName.isNullOrBlank() || dayStart < 0L) {
            finish()
            return
        }

        loadAndRender(packageName, dayStart)
    }

    private fun loadAndRender(packageName: String, dayStart: Long) {
        val dayEnd = dayStart + DAY_MILLIS
        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                val reader = AndroidUsageIntervalReader(this@UsageAuditAppDetailActivity)
                val labelResolver = AppLabelResolver(
                    PackageManagerLabelLookup(this@UsageAuditAppDetailActivity.packageManager),
                )
                AppDayDetailCalculator.forApp(
                    packageName = packageName,
                    windowStartMillis = dayStart,
                    windowEndMillis = dayEnd,
                    intervals = reader.readIntervals(dayStart, dayEnd),
                    labelLookup = labelResolver,
                )
            }
            render(AppDetailPresenter.present(detail))
        }
    }

    private fun render(state: AppDetailScreenState) {
        findViewById<TextView>(R.id.app_detail_label).text = state.displayLabel
        findViewById<TextView>(R.id.app_detail_date).text = state.dateFormatted
        findViewById<TextView>(R.id.app_detail_total).text = state.totalUsageFormatted

        val uninstalled = findViewById<View>(R.id.app_detail_uninstalled_indicator)
        uninstalled.visibility = if (state.isUninstalled) View.VISIBLE else View.GONE

        val noActivity = findViewById<View>(R.id.app_detail_no_activity)
        noActivity.visibility = if (state.showNoActivityMessage) View.VISIBLE else View.GONE

        val heading = findViewById<View>(R.id.app_detail_intervals_heading)
        val list = findViewById<LinearLayout>(R.id.app_detail_intervals_list)
        list.removeAllViews()
        if (state.intervals.isEmpty()) {
            heading.visibility = View.GONE
            list.visibility = View.GONE
            return
        }
        heading.visibility = View.VISIBLE
        list.visibility = View.VISIBLE
        for (row in state.intervals) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val window = TextView(this).apply {
                text = "${row.startFormatted}–${row.endFormatted}"
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            }
            val duration = TextView(this).apply {
                text = row.durationFormatted
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
            }
            rowView.addView(window)
            rowView.addView(duration)
            list.addView(rowView)
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "dev.charly.paranoid.usageaudit.app_package_name"
        const val EXTRA_DAY_START_MILLIS = "dev.charly.paranoid.usageaudit.app_day_start_millis"

        private const val DAY_MILLIS = 24L * 60 * 60 * 1_000

        fun newIntent(context: Context, packageName: String, dayStartMillis: Long): Intent =
            Intent(context, UsageAuditAppDetailActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_DAY_START_MILLIS, dayStartMillis)
            }
    }
}
