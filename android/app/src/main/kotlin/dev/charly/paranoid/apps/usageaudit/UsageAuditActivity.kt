package dev.charly.paranoid.apps.usageaudit

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R

class UsageAuditActivity : AppCompatActivity() {

    private lateinit var usageAccessChecker: UsageAccessChecker
    private lateinit var settingsNavigator: UsageAccessSettingsNavigator
    private lateinit var dataProvider: UsageAuditDataProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_audit)

        usageAccessChecker = UsageAuditDependencies.usageAccessCheckerFactory(this)
        settingsNavigator = UsageAuditDependencies.settingsNavigatorFactory(this)
        dataProvider = UsageAuditDependencies.dataProviderFactory(this)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_open_usage_access_settings).setOnClickListener {
            settingsNavigator.open()
        }
    }

    override fun onResume() {
        super.onResume()
        renderUsageAccessState()
    }

    private fun renderUsageAccessState() {
        val hasUsageAccess = usageAccessChecker.hasUsageAccess()
        findViewById<View>(R.id.usage_access_gate).visibility = if (hasUsageAccess) View.GONE else View.VISIBLE
        findViewById<View>(R.id.usage_audit_content).visibility = if (hasUsageAccess) View.VISIBLE else View.GONE

        if (hasUsageAccess) {
            loadAndRender()
        }
    }

    private fun loadAndRender() {
        dataProvider.load(lifecycleScope) { today, lastNight ->
            renderToday(TodayScreenPresenter.present(today))
            renderLastNight(LastNightScreenPresenter.present(lastNight))
        }
    }

    private fun renderToday(state: TodayScreenState) {
        val empty = findViewById<View>(R.id.today_empty)
        val populated = findViewById<View>(R.id.today_populated)

        when (state) {
            is TodayScreenState.Empty -> {
                empty.visibility = View.VISIBLE
                populated.visibility = View.GONE
            }
            is TodayScreenState.Populated -> {
                empty.visibility = View.GONE
                populated.visibility = View.VISIBLE
                findViewById<TextView>(R.id.today_total_usage).text = state.totalUsageFormatted
                renderAppRows(findViewById(R.id.today_apps_list), state.topApps)
            }
        }
    }

    private fun renderLastNight(state: LastNightScreenState) {
        val empty = findViewById<View>(R.id.last_night_empty)
        val populated = findViewById<View>(R.id.last_night_populated)
        val disclaimer = findViewById<View>(R.id.attribution_disclaimer)

        when (state) {
            is LastNightScreenState.Empty -> {
                empty.visibility = View.VISIBLE
                populated.visibility = View.GONE
                disclaimer.visibility = View.GONE
            }
            is LastNightScreenState.Populated -> {
                empty.visibility = View.GONE
                populated.visibility = View.VISIBLE
                disclaimer.visibility = View.VISIBLE

                findViewById<TextView>(R.id.last_night_battery_start).text = state.batteryStart
                findViewById<TextView>(R.id.last_night_battery_end).text = state.batteryEnd
                findViewById<TextView>(R.id.last_night_battery_delta).text = state.batteryDelta
                findViewById<TextView>(R.id.last_night_apps_heading).text = state.activeAppsHeading

                renderWarnings(state.warnings)
                renderAppRows(findViewById(R.id.last_night_apps_list), state.activeApps)
            }
        }
    }

    private fun renderWarnings(warnings: List<String>) {
        val container = findViewById<LinearLayout>(R.id.last_night_warnings)
        container.removeAllViews()
        if (warnings.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        for (warning in warnings) {
            val tv = TextView(this).apply {
                text = "⚠ $warning"
                setTextColor(0xFFFFB74D.toInt())
                textSize = 13f
                setPadding(0, 0, 0, 8)
            }
            container.addView(tv)
        }
    }

    private fun renderAppRows(container: LinearLayout, apps: List<AppRow>) {
        container.removeAllViews()
        if (apps.isEmpty()) return
        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }
            val label = TextView(this).apply {
                text = app.label
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val duration = TextView(this).apply {
                text = app.durationFormatted
                setTextColor(0xFF888888.toInt())
                textSize = 14f
            }
            row.addView(label)
            row.addView(duration)
            container.addView(row)
        }
    }
}
