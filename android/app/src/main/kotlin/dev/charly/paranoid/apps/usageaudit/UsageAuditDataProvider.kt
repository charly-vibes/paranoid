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

    override fun load(
        scope: CoroutineScope,
        callback: (today: DailyUsageSummary?, lastNight: OvernightAudit?) -> Unit,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { loadData() }
            callback(result.first, result.second)
        }
    }

    private suspend fun loadData(): Pair<DailyUsageSummary?, OvernightAudit?> {
        val reader = AndroidUsageIntervalReader(context)
        val adapter = UsageQueryAdapter(reader)
        val db = ParanoidDatabase.getInstance(context.applicationContext)
        val dao = db.batterySnapshotDao()

        val todaySummary = loadToday(adapter)
        val lastNightAudit = loadLastNight(adapter, dao)
        return todaySummary to lastNightAudit
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
        val cal = Calendar.getInstance()
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
