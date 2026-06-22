package dev.charly.paranoid.apps.screentime.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts [ScreenTimeService] after a reboot if the user had monitoring enabled. The session
 * left open before reboot is recovered and closed by the service on start (see
 * [StaleSessionResolver] / tasks 3.8–3.9).
 */
class ScreenTimeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ScreenTimeMonitoringPrefs.isEnabled(context)) return
        context.startForegroundService(ScreenTimeService.startIntent(context))
    }
}
