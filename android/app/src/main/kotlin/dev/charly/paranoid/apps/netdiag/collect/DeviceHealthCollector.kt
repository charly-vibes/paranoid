package dev.charly.paranoid.apps.netdiag.collect

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import dev.charly.paranoid.apps.netdiag.data.DeviceHealth
import dev.charly.paranoid.apps.netdiag.data.Measured
import dev.charly.paranoid.apps.netdiag.data.NetworkPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DeviceHealthCollector(private val context: Context) {

    suspend fun collect(): DeviceHealth = withContext(Dispatchers.IO) {
        val battery = collectBattery()
        DeviceHealth(
            batteryPercent = battery.first,
            isCharging = battery.second,
            isDozeModeActive = collectDozeMode(),
            isDataSaverActive = collectDataSaver(),
            isAirplaneModeOn = collectAirplaneMode(),
            isWifiEnabled = collectWifiEnabled(),
            isCellularEnabled = collectCellularEnabled(),
            memoryAvailableMb = collectAvailableMemory(),
            cpuLoadPercent = collectCpuLoad(),
            activeVpnPackage = collectActiveVpnPackage(),
            locationServicesEnabled = collectLocationServices(),
            networkPermissions = collectNetworkPermissions(),
        )
    }

    private fun collectBattery(): Pair<Int, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) return Pair(0, false)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(percent, charging)
    }

    private fun collectDozeMode(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isDeviceIdleMode
    }

    private fun collectDataSaver(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.restrictBackgroundStatus ==
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    private fun collectAirplaneMode(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun collectWifiEnabled(): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.isWifiEnabled
    }

    private fun collectCellularEnabled(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.simState == TelephonyManager.SIM_STATE_READY &&
            Settings.Global.getInt(context.contentResolver, "mobile_data", 1) != 0
    }

    private fun collectAvailableMemory(): Long? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    private fun collectCpuLoad(): Measured<Float>? {
        return tryLoadAvg() ?: tryProcStat()
    }

    private fun tryLoadAvg(): Measured<Float>? = try {
        val content = File("/proc/loadavg").readText()
        val load1m = content.split(" ").firstOrNull()?.toFloatOrNull()
        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        if (load1m != null) {
            val percent = (load1m / cpuCount * 100f).coerceIn(0f, 100f)
            Measured(
                value = percent,
                confidence = 0.6f,
                source = "/proc/loadavg",
                timestampMs = System.currentTimeMillis(),
            )
        } else null
    } catch (_: Exception) {
        null
    }

    private fun tryProcStat(): Measured<Float>? = try {
        val lines = File("/proc/stat").readLines()
        val cpuLine = lines.firstOrNull { it.startsWith("cpu ") }
        if (cpuLine != null) {
            val parts = cpuLine.trim().split("\\s+".toRegex()).drop(1).map { it.toLong() }
            if (parts.size >= 4) {
                val idle = parts[3]
                val total = parts.sum()
                if (total > 0) {
                    val usage = ((total - idle).toFloat() / total * 100f).coerceIn(0f, 100f)
                    Measured(
                        value = usage,
                        confidence = 0.4f,
                        source = "/proc/stat",
                        timestampMs = System.currentTimeMillis(),
                    )
                } else null
            } else null
        } else null
    } catch (_: Exception) {
        null
    }

    private fun collectActiveVpnPackage(): String? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val pm = context.packageManager
        cm.allNetworks
            .asSequence()
            .mapNotNull { network -> cm.getNetworkCapabilities(network) }
            .filter { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
            .mapNotNull { caps ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val uid = caps.ownerUid
                        if (uid > 0) {
                            pm.getPackagesForUid(uid)?.firstOrNull()
                        } else null
                    } catch (_: Exception) { null }
                } else null
            }
            .firstOrNull()
    } catch (_: Exception) {
        null
    }

    private fun collectLocationServices(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE)
                as android.location.LocationManager
            lm.isLocationEnabled
        } else {
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF,
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }

    private fun collectNetworkPermissions(): NetworkPermissions {
        val pm = context.packageManager
        val pkg = context.packageName
        fun has(perm: String): Boolean =
            pm.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED
        return NetworkPermissions(
            hasAccessNetworkState = has(android.Manifest.permission.ACCESS_NETWORK_STATE),
            hasAccessWifiState = has(android.Manifest.permission.ACCESS_WIFI_STATE),
            hasReadPhoneState = has(android.Manifest.permission.READ_PHONE_STATE),
            hasAccessFineLocation = has(android.Manifest.permission.ACCESS_FINE_LOCATION),
            hasNearbyWifiDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                has("android.permission.NEARBY_WIFI_DEVICES")
            } else false,
            hasPackageUsageStats = has("android.permission.PACKAGE_USAGE_STATS"),
            hasForegroundService = has(android.Manifest.permission.FOREGROUND_SERVICE),
        )
    }
}
