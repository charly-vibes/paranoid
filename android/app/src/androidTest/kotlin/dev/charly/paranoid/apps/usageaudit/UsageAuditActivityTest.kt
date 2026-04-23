package dev.charly.paranoid.apps.usageaudit

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.charly.paranoid.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageAuditActivityTest {

    @After
    fun tearDown() {
        UsageAuditDependencies.reset()
    }

    @Test
    fun showsMissingUsageAccessStateAndHandsOffToSettings() {
        val fakeChecker = FakeUsageAccessChecker(false)
        val fakeNavigator = FakeUsageAccessSettingsNavigator()
        UsageAuditDependencies.usageAccessCheckerFactory = { _ -> fakeChecker }
        UsageAuditDependencies.settingsNavigatorFactory = { _ -> fakeNavigator }

        ActivityScenario.launch(UsageAuditActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val gateCard = activity.findViewById<android.view.View>(R.id.usage_access_gate)
                val content = activity.findViewById<android.view.View>(R.id.usage_audit_content)
                val message = activity.findViewById<android.widget.TextView>(R.id.usage_access_message)
                val button = activity.findViewById<android.widget.TextView>(R.id.btn_open_usage_access_settings)

                assertEquals(android.view.View.VISIBLE, gateCard.visibility)
                assertEquals(android.view.View.GONE, content.visibility)
                assertTrue(message.text.contains("Usage access is required"))
                assertEquals("Open Usage Access Settings", button.text.toString())

                button.performClick()
                assertTrue(fakeNavigator.wasOpened)
            }
        }
    }

    @Test
    fun hidesMissingAccessGateWhenUsageAccessIsGranted() {
        UsageAuditDependencies.usageAccessCheckerFactory = { _ -> FakeUsageAccessChecker(true) }
        UsageAuditDependencies.settingsNavigatorFactory = { _ -> FakeUsageAccessSettingsNavigator() }

        ActivityScenario.launch(UsageAuditActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val gateCard = activity.findViewById<android.view.View>(R.id.usage_access_gate)
                val content = activity.findViewById<android.view.View>(R.id.usage_audit_content)
                val button = activity.findViewById<android.view.View>(R.id.btn_open_usage_access_settings)

                assertEquals(android.view.View.GONE, gateCard.visibility)
                assertEquals(android.view.View.VISIBLE, content.visibility)
                assertFalse(button.isShown)
            }
        }
    }
}

private class FakeUsageAccessChecker(
    private val allowed: Boolean,
) : UsageAccessChecker {
    override fun hasUsageAccess(): Boolean = allowed
}

private class FakeUsageAccessSettingsNavigator : UsageAccessSettingsNavigator {
    var wasOpened: Boolean = false

    override fun open() {
        wasOpened = true
    }
}
