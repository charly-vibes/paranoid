package dev.charly.paranoid.apps.usageaudit

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

data class UsageIntervalRecord(
    val packageName: String,
    val appLabel: String,
    val startMillis: Long,
    val endMillis: Long,
)

fun interface UsageIntervalReader {
    fun readIntervals(windowStartMillis: Long, windowEndMillis: Long): List<UsageIntervalRecord>
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
