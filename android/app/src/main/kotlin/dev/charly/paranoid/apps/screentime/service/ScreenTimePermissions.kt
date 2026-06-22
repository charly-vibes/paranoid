package dev.charly.paranoid.apps.screentime.service

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The three permissions screen-time monitoring needs. Usage access and overlay are special-access
 * permissions granted from dedicated Settings screens; notifications is a runtime permission on
 * Android 13+ and implicitly granted below it.
 */
enum class ScreenTimePermission { USAGE_ACCESS, NOTIFICATIONS, OVERLAY }

/**
 * Checks and deep-links the permissions required by screen-time monitoring. Android-coupled (queries
 * AppOpsManager / Settings.canDrawOverlays), kept small and behind a simple API so [ScreenTimeActivity]
 * stays thin.
 */
object ScreenTimePermissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotifications(context: Context): Boolean {
        // POST_NOTIFICATIONS is only enforced on Android 13+; granted implicitly below that.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isGranted(context: Context, permission: ScreenTimePermission): Boolean = when (permission) {
        ScreenTimePermission.USAGE_ACCESS -> hasUsageAccess(context)
        ScreenTimePermission.NOTIFICATIONS -> hasNotifications(context)
        ScreenTimePermission.OVERLAY -> hasOverlay(context)
    }

    /** True only when every permission is granted; monitoring should be gated on this. */
    fun allGranted(context: Context): Boolean =
        ScreenTimePermission.entries.all { isGranted(context, it) }

    /** Opens the relevant Settings screen for [permission]. */
    fun openSettings(context: Context, permission: ScreenTimePermission) {
        val intent = when (permission) {
            ScreenTimePermission.USAGE_ACCESS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            ScreenTimePermission.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            ScreenTimePermission.OVERLAY ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
