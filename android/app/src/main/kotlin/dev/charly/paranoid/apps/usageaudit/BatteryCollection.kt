package dev.charly.paranoid.apps.usageaudit

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class BatterySignal(
    val timestampMillis: Long,
    val level: Int,
    val status: String?,
    val health: String?,
    val isCharging: Boolean,
)

object BatterySignalMapper {
    fun map(signal: BatterySignal): BatterySnapshot = BatterySnapshot(
        timestampMillis = signal.timestampMillis,
        batteryPercent = signal.level,
        chargingState = if (signal.isCharging) ChargingState.CHARGING else ChargingState.DISCHARGING,
        batteryStatus = signal.status,
        batteryHealth = signal.health,
    )
}

fun interface BatterySnapshotRepository {
    fun save(snapshot: BatterySnapshot)
}

fun interface SnapshotCollector {
    fun capture()
}

class BatterySnapshotCollector(
    private val signalProvider: () -> BatterySignal?,
    private val repository: BatterySnapshotRepository,
) : SnapshotCollector {
    override fun capture() {
        signalProvider()?.let { signal -> repository.save(BatterySignalMapper.map(signal)) }
    }
}

class RoomBatterySnapshotRepository(
    private val dao: BatterySnapshotDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BatterySnapshotRepository {
    override fun save(snapshot: BatterySnapshot) {
        scope.launch {
            dao.insert(BatterySnapshotEntity.fromDomain(snapshot))
        }
    }
}

class AppOpenBatterySnapshotHook(
    private val collector: SnapshotCollector,
) {
    fun onActivityResumed() {
        collector.capture()
    }
}

class PowerEventBatterySnapshotReceiver(
    private val collector: SnapshotCollector,
) {
    fun onReceive(action: String?) {
        if (action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_POWER_DISCONNECTED) {
            collector.capture()
        }
    }
}

class BootCompletedBatterySnapshotReceiver(
    private val collector: SnapshotCollector,
) {
    fun onReceive(action: String?) {
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            collector.capture()
        }
    }
}

class UsageAuditPowerEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        UsageAuditRuntime.powerEventReceiver(context).onReceive(intent?.action)
    }
}

class UsageAuditBootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        UsageAuditRuntime.bootReceiver(context).onReceive(intent?.action)
    }
}

class ParanoidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val hook = UsageAuditRuntime.appOpenHook(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) {
                hook.onActivityResumed()
            }
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }
}

object UsageAuditRuntime {
    fun batterySnapshotCollector(context: Context): SnapshotCollector {
        val appContext = context.applicationContext
        val database = ParanoidDatabase.getInstance(appContext)
        return BatterySnapshotCollector(
            signalProvider = { AndroidBatterySignalProvider(appContext).read() },
            repository = RoomBatterySnapshotRepository(database.batterySnapshotDao()),
        )
    }

    fun appOpenHook(context: Context): AppOpenBatterySnapshotHook =
        AppOpenBatterySnapshotHook(batterySnapshotCollector(context))

    fun powerEventReceiver(context: Context): PowerEventBatterySnapshotReceiver =
        PowerEventBatterySnapshotReceiver(batterySnapshotCollector(context))

    fun bootReceiver(context: Context): BootCompletedBatterySnapshotReceiver =
        BootCompletedBatterySnapshotReceiver(batterySnapshotCollector(context))
}

class AndroidBatterySignalProvider(
    private val context: Context,
) {
    fun read(): BatterySignal? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        if (level < 0) return null
        val statusValue = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val healthValue = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val isCharging = statusValue == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusValue == BatteryManager.BATTERY_STATUS_FULL
        return BatterySignal(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            status = batteryStatusName(statusValue),
            health = batteryHealthName(healthValue),
            isCharging = isCharging,
        )
    }

    private fun batteryStatusName(value: Int): String = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
        else -> "unknown"
    }

    private fun batteryHealthName(value: Int): String = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        else -> "unknown"
    }
}
