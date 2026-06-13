package dev.charly.paranoid.apps.sensorlogger

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import dev.charly.paranoid.apps.netmap.data.export.ShareHelper
import java.util.Date

/**
 * Lightweight diagnostics helper. Use [TAG] with android.util.Log so all
 * Paranoid logs share a prefix, and [dumpAndShare] to export the app's own
 * logcat buffer (own-UID only on Android 7+) for the user to send back.
 */
object DebugLog {
    const val TAG = "Paranoid"

    fun d(msg: String) = Log.d(TAG, msg)
    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
    }

    /** Dump recent logcat for this app and open the share sheet with it. */
    fun dumpAndShare(context: Context) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
            val logs = proc.inputStream.bufferedReader().use { it.readText() }
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "unknown"
            val header = buildString {
                appendLine("Paranoid debug log")
                appendLine("captured: ${Date()}")
                appendLine("app: $version")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
                appendLine("----------------------------------------")
            }
            ShareHelper.share(
                context,
                header + logs,
                "paranoid-log-${System.currentTimeMillis()}.txt",
                "text/plain",
            )
        } catch (ex: Exception) {
            e("dumpAndShare failed", ex)
            Toast.makeText(context, "Could not collect log: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }
}
