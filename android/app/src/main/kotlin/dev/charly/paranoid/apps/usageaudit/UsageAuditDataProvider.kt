package dev.charly.paranoid.apps.usageaudit

import android.content.Context
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AndroidUsageAuditDataProvider(
    private val context: Context,
) : UsageAuditDataProvider {

    override fun load(scope: CoroutineScope, callback: (UsageAuditData) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { loadData() }
            callback(result)
        }
    }

    private suspend fun loadData(): UsageAuditData {
        val reader = AndroidUsageIntervalReader(context)
        val adapter = UsageQueryAdapter(reader)
        val db = ParanoidDatabase.getInstance(context.applicationContext)
        val dao = db.batterySnapshotDao()

        val todaySummary = loadToday(adapter)
        val lastNightAudit = loadLastNight(adapter, dao)
        val recentNights = loadRecentNights(adapter, dao)
        val recentDays = loadRecentDays(adapter)
        return UsageAuditData(todaySummary, lastNightAudit, recentNights, recentDays)
    }

    private fun loadRecentDays(adapter: UsageQueryAdapter): List<DailyUsageSummary> {
        val now = System.currentTimeMillis()
        val windows = RecentDaysEnumerator.pastDayWindows(
            nowMillis = now,
            daysBack = RECENT_DAYS_LOOKBACK,
        )
        return windows.map { window ->
            val slices = adapter.queryToday(window.startMillis, window.endMillis)
            DailyUsageAggregator.summarize(window.startMillis, window.endMillis, slices)
        }
    }

    private companion object {
        // Android's UsageStatsManager typically retains several days of usage data;
        // we cap at 7 to match the existing overnight-history depth.
        const val RECENT_DAYS_LOOKBACK = 7
    }

    private fun loadToday(adapter: UsageQueryAdapter): DailyUsageSummary? {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val now = System.currentTimeMillis()

        val slices = adapter.queryToday(dayStart, now)
        if (slices.isEmpty()) return null
        return DailyUsageAggregator.summarize(dayStart, now, slices)
    }

    private suspend fun loadLastNight(
        adapter: UsageQueryAdapter,
        dao: BatterySnapshotDao,
    ): OvernightAudit? {
        return loadNightAudit(adapter, dao, daysAgo = 0)
    }

    private suspend fun loadRecentNights(
        adapter: UsageQueryAdapter,
        dao: BatterySnapshotDao,
    ): List<OvernightAudit> {
        val audits = mutableListOf<OvernightAudit>()
        for (daysAgo in 1..6) {
            val audit = loadNightAudit(adapter, dao, daysAgo) ?: continue
            audits += audit
        }
        return audits
    }

    private suspend fun loadNightAudit(
        adapter: UsageQueryAdapter,
        dao: BatterySnapshotDao,
        daysAgo: Int,
    ): OvernightAudit? {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 7)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val windowEnd = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, 22)
        val windowStart = cal.timeInMillis

        if (windowEnd > System.currentTimeMillis()) return null

        val slices = adapter.queryOvernight(windowStart, windowEnd)
        val snapshotEntities = dao.between(windowStart - 3_600_000L, windowEnd + 3_600_000L)
        val snapshots = snapshotEntities.map { it.toDomain() }

        if (snapshots.isEmpty() && slices.isEmpty()) return null

        return OvernightAuditCalculator.summarize(windowStart, windowEnd, snapshots, slices)
    }
}
