package dev.charly.paranoid.apps.usageaudit

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException

data class UsageIntervalRecord(
    val packageName: String,
    val appLabel: String,
    val startMillis: Long,
    val endMillis: Long,
)

fun interface UsageIntervalReader {
    fun readIntervals(windowStartMillis: Long, windowEndMillis: Long): List<UsageIntervalRecord>
}

/**
 * Looks up the human-readable label for [packageName].
 *
 * Implementations MUST throw [NameNotFoundException] when the package is not
 * installed; any other failure mode is the implementation's responsibility.
 */
fun interface ApplicationLabelLookup {
    @Throws(NameNotFoundException::class)
    fun getApplicationLabel(packageName: String): String
}

/**
 * Resolves a package name to an [AppLabel].
 *
 * - Returns [AppLabel.Installed] with the looked-up label, or with the package
 *   name itself when the lookup yields a blank string.
 * - Returns [AppLabel.Uninstalled] when the lookup throws
 *   [NameNotFoundException].
 *
 * Other exceptions propagate so callers can decide how to handle them.
 */
class AppLabelResolver(private val lookup: ApplicationLabelLookup) : (String) -> AppLabel {
    override fun invoke(packageName: String): AppLabel = try {
        val raw = lookup.getApplicationLabel(packageName)
        AppLabel.Installed(raw.ifBlank { packageName })
    } catch (_: NameNotFoundException) {
        AppLabel.Uninstalled
    }
}

/** [ApplicationLabelLookup] backed by a real [PackageManager]. */
class PackageManagerLabelLookup(
    private val packageManager: PackageManager,
) : ApplicationLabelLookup {
    override fun getApplicationLabel(packageName: String): String {
        val info = packageManager.getApplicationInfo(packageName, 0)
        return packageManager.getApplicationLabel(info).toString()
    }
}

object UsageEventIntervalMapper {
    private val noisyPackages = setOf(
        "android",
        "com.android.systemui",
    )

    fun toSlices(
        intervals: List<UsageIntervalRecord>,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): List<AppUsageSlice> = intervals
        .asSequence()
        .filterNot { interval -> interval.packageName in noisyPackages }
        .mapNotNull { interval ->
            val overlapStart = maxOf(windowStartMillis, interval.startMillis)
            val overlapEnd = minOf(windowEndMillis, interval.endMillis)
            val durationMillis = (overlapEnd - overlapStart).coerceAtLeast(0L)
            if (durationMillis == 0L) {
                null
            } else {
                AppUsageSlice(
                    packageName = interval.packageName,
                    appLabel = interval.appLabel.ifBlank { interval.packageName },
                    windowStartMillis = overlapStart,
                    windowEndMillis = overlapEnd,
                    foregroundDurationMillis = durationMillis,
                )
            }
        }
        .toList()
}

object AppDayDetailCalculator {
    /**
     * Builds an [AppDayDetail] for [packageName] over [windowStartMillis, windowEndMillis).
     *
     * - Filters [intervals] to the requested package.
     * - Clips each kept interval to the day window.
     * - Drops intervals that fall fully outside the window or collapse to zero length.
     * - Sorts the kept intervals by start time and sums their durations.
     * - Resolves the displayed label via [labelLookup]; an [AppLabel.Uninstalled]
     *   result signals the package is no longer installed.
     */
    fun forApp(
        packageName: String,
        windowStartMillis: Long,
        windowEndMillis: Long,
        intervals: List<UsageIntervalRecord>,
        labelLookup: (String) -> AppLabel,
    ): AppDayDetail {
        val clipped = intervals
            .asSequence()
            .filter { it.packageName == packageName }
            .mapNotNull { interval ->
                val start = maxOf(windowStartMillis, interval.startMillis)
                val end = minOf(windowEndMillis, interval.endMillis)
                if (end <= start) null else AppForegroundInterval(start, end)
            }
            .sortedBy { it.startMillis }
            .toList()

        val total = clipped.sumOf { it.endMillis - it.startMillis }

        return AppDayDetail(
            packageName = packageName,
            appLabel = labelLookup(packageName),
            dayStartMillis = windowStartMillis,
            dayEndMillis = windowEndMillis,
            totalForegroundDurationMillis = total,
            intervals = clipped,
        )
    }
}

class UsageQueryAdapter(
    private val reader: UsageIntervalReader,
) {
    fun queryToday(windowStartMillis: Long, windowEndMillis: Long): List<AppUsageSlice> =
        queryWindow(windowStartMillis, windowEndMillis)

    fun queryOvernight(windowStartMillis: Long, windowEndMillis: Long): List<AppUsageSlice> =
        queryWindow(windowStartMillis, windowEndMillis)

    private fun queryWindow(windowStartMillis: Long, windowEndMillis: Long): List<AppUsageSlice> =
        UsageEventIntervalMapper.toSlices(
            intervals = reader.readIntervals(windowStartMillis, windowEndMillis),
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
        )
}

class AndroidUsageIntervalReader(
    context: Context,
    private val packageManager: PackageManager = context.packageManager,
) : UsageIntervalReader {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override fun readIntervals(windowStartMillis: Long, windowEndMillis: Long): List<UsageIntervalRecord> {
        val events = usageStatsManager.queryEvents(windowStartMillis, windowEndMillis)
        val event = UsageEvents.Event()
        val activeStartByPackage = mutableMapOf<String, Long>()
        val intervals = mutableListOf<UsageIntervalRecord>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    activeStartByPackage[event.packageName] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val packageName = event.packageName ?: continue
                    val startMillis = activeStartByPackage.remove(packageName) ?: continue
                    intervals += UsageIntervalRecord(
                        packageName = packageName,
                        appLabel = resolveLabel(packageName),
                        startMillis = startMillis,
                        endMillis = event.timeStamp,
                    )
                }
            }
        }

        activeStartByPackage.forEach { (packageName, startMillis) ->
            intervals += UsageIntervalRecord(
                packageName = packageName,
                appLabel = resolveLabel(packageName),
                startMillis = startMillis,
                endMillis = windowEndMillis,
            )
        }

        return intervals
    }

    private fun resolveLabel(packageName: String): String = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString().ifBlank { packageName }
    } catch (_: Exception) {
        packageName
    }
}
