package dev.charly.paranoid.apps.usageaudit

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLabelResolverTest {

    @Test
    fun `resolves to Installed with the looked-up label`() {
        val resolver = AppLabelResolver { _ -> "Reader" }

        assertEquals(AppLabel.Installed("Reader"), resolver("dev.example.reader"))
    }

    @Test
    fun `falls back to package name when the looked-up label is blank`() {
        val resolver = AppLabelResolver { _ -> "" }

        assertEquals(AppLabel.Installed("dev.example.reader"), resolver("dev.example.reader"))
    }

    @Test
    fun `returns Uninstalled when lookup throws NameNotFoundException`() {
        val resolver = AppLabelResolver { pkg ->
            throw PackageManager.NameNotFoundException(pkg)
        }

        assertEquals(AppLabel.Uninstalled, resolver("dev.example.gone"))
    }
}
