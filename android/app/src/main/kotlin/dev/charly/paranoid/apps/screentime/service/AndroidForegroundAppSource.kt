package dev.charly.paranoid.apps.screentime.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dev.charly.paranoid.apps.screentime.ForegroundApp
import dev.charly.paranoid.apps.screentime.ForegroundAppSource
import dev.charly.paranoid.apps.screentime.ForegroundSample
import dev.charly.paranoid.apps.usageaudit.AndroidUsageAccessChecker
import dev.charly.paranoid.apps.usageaudit.UsageAccessChecker

/**
 * [ForegroundAppSource] backed by [UsageStatsManager]. On each sample it queries the events in a
 * short trailing window and returns the package of the most recent foreground transition.
 *
 * Returns [ForegroundSample.AccessUnavailable] when usage access is not granted (so the sampler
 * can surface a warning and skip), and [ForegroundSample.Unresolved] when no foreground app can
 * be attributed in the window.
 */
class AndroidForegroundAppSource(
    context: Context,
    private val accessChecker: UsageAccessChecker = AndroidUsageAccessChecker(context),
    private val lookbackMillis: Long = DEFAULT_LOOKBACK_MILLIS,
) : ForegroundAppSource {

    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override fun sample(nowMillis: Long): ForegroundSample {
        if (!accessChecker.hasUsageAccess()) return ForegroundSample.AccessUnavailable

        val latest = try {
            latestForegroundEvent(nowMillis - lookbackMillis, nowMillis)
        } catch (_: Exception) {
            // Defensive: any platform failure (incl. revocation race) is treated as unavailable.
            return ForegroundSample.AccessUnavailable
        }

        return latest?.let { ForegroundSample.Resolved(it) } ?: ForegroundSample.Unresolved
    }

    private fun latestForegroundEvent(startMillis: Long, endMillis: Long): ForegroundApp? {
        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        var latest: ForegroundApp? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (!isForeground) continue
            val pkg = event.packageName ?: continue
            if (latest == null || event.timeStamp >= latest.sinceMillis) {
                latest = ForegroundApp(packageName = pkg, sinceMillis = event.timeStamp)
            }
        }
        return latest
    }

    companion object {
        /** Trailing window queried each sample; comfortably wider than the 5 s poll interval. */
        const val DEFAULT_LOOKBACK_MILLIS: Long = 60_000L
    }
}
