package dev.charly.paranoid.apps.usageaudit

import android.app.AppOpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAccessCheckerTest {

    @Test
    fun `usage access checker returns true only when app ops mode is allowed`() {
        val checker = AndroidUsageAccessChecker(modeReader = { AppOpsManager.MODE_ALLOWED })

        assertTrue(checker.hasUsageAccess())
    }

    @Test
    fun `usage access checker returns false for non-allowed app ops modes`() {
        val ignoredChecker = AndroidUsageAccessChecker(modeReader = { AppOpsManager.MODE_IGNORED })
        val erroredChecker = AndroidUsageAccessChecker(modeReader = { AppOpsManager.MODE_ERRORED })
        val defaultChecker = AndroidUsageAccessChecker(modeReader = { AppOpsManager.MODE_DEFAULT })

        assertFalse(ignoredChecker.hasUsageAccess())
        assertFalse(erroredChecker.hasUsageAccess())
        assertFalse(defaultChecker.hasUsageAccess())
    }
}
