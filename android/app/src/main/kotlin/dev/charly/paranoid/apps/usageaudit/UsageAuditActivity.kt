package dev.charly.paranoid.apps.usageaudit

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.charly.paranoid.R

class UsageAuditActivity : AppCompatActivity() {

    private lateinit var usageAccessChecker: UsageAccessChecker
    private lateinit var settingsNavigator: UsageAccessSettingsNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_audit)

        usageAccessChecker = UsageAuditDependencies.usageAccessCheckerFactory(this)
        settingsNavigator = UsageAuditDependencies.settingsNavigatorFactory(this)

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
    }
}
