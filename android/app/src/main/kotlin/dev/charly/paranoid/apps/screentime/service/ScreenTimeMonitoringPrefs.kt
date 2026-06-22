package dev.charly.paranoid.apps.screentime.service

import android.content.Context

/**
 * Persists whether the user has enabled screen-time monitoring. The flag survives reboots so
 * [ScreenTimeBootReceiver] can decide whether to restart [ScreenTimeService] on BOOT_COMPLETED.
 */
object ScreenTimeMonitoringPrefs {
    private const val PREFS_NAME = "screentime_prefs"
    private const val KEY_ENABLED = "monitoring_enabled"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
