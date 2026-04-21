package dev.charly.paranoid.apps.netdiag.collect

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshot
import dev.charly.paranoid.apps.netdiag.data.Severity
import dev.charly.paranoid.apps.netdiag.data.Transport
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID

class SnapshotCaptureEngine(private val context: Context) {

    sealed class CaptureResult {
        data class Success(val snapshot: DiagnosticsSnapshot) : CaptureResult()
        data class Error(val message: String) : CaptureResult()
    }

    suspend fun capture(
        deviceLabel: String,
        sessionId: String,
    ): CaptureResult = coroutineScope {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val networkCaps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProperties = activeNetwork?.let { cm.getLinkProperties(it) }

        val startTransport = detectTransport(networkCaps)

        val wifiCollector = WifiCollector(context)
        val cellularCollector = CellularCollector(context)
        val dnsCollector = DnsCollector(context)
        val ipConfigCollector = IpConfigCollector()
        val probeCollector = ProbeCollector()
        val deviceHealthCollector = DeviceHealthCollector(context)
        val connDiagCollector = ConnDiagCollector(context)

        val wifiDeferred = async { runCatching { wifiCollector.collect(networkCaps) }.getOrNull() }
        val cellularDeferred = async { runCatching { cellularCollector.collect() }.getOrNull() }
        val dnsDeferred = async { runCatching { dnsCollector.collect(linkProperties) }.getOrNull() }
        val ipConfigDeferred = async { runCatching { ipConfigCollector.collect(linkProperties, cm) }.getOrNull() }
        val deviceHealthDeferred = async { runCatching { deviceHealthCollector.collect() }.getOrNull() }
        val connDiagDeferred = async { runCatching { connDiagCollector.collect(activeNetwork) }.getOrNull() }

        val ipConfig = ipConfigDeferred.await()
        val gatewayIp = ipConfig?.gatewayIpv4
        val dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val probeDeferred = async { runCatching { probeCollector.collect(gatewayIp, dnsServers) }.getOrNull() }

        val wifi = wifiDeferred.await()
        val cellular = cellularDeferred.await()
        val dns = dnsDeferred.await()
        val deviceHealth = deviceHealthDeferred.await()
        val connDiag = connDiagDeferred.await()
        val probeResult = probeDeferred.await()

        if (ipConfig == null && wifi == null && cellular == null && dns == null &&
            deviceHealth == null && probeResult == null
        ) {
            return@coroutineScope CaptureResult.Error(
                "All collectors failed. Check permissions and network connectivity."
            )
        }

        val endTransport = detectTransport(
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        )

        val isValidated = networkCaps?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        ) == true
        val isCaptivePortal = networkCaps?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
        ) == true
        val captivePortalUrl = if (isCaptivePortal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            networkCaps?.let { extractCaptivePortalUrl(it) }
        } else null
        val isVpnActive = networkCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val isMetered = networkCaps?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED
        ) != true

        val snapshot = DiagnosticsSnapshot(
            id = UUID.randomUUID().toString(),
            deviceLabel = deviceLabel,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.SDK_INT,
            capturedAtMs = System.currentTimeMillis(),
            sessionId = sessionId,
            activeTransport = startTransport,
            isValidated = isValidated,
            isCaptivePortal = isCaptivePortal,
            captivePortalUrl = captivePortalUrl,
            isVpnActive = isVpnActive,
            isMetered = isMetered,
            isDozed = deviceHealth?.isDozeModeActive == true,
            ipConfig = ipConfig ?: defaultIpConfig(),
            wifi = wifi,
            cellular = cellular,
            dns = dns ?: defaultDnsSnapshot(),
            probes = probeResult?.first ?: emptyList(),
            throughput = null,
            trafficDelta = null,
            deviceHealth = deviceHealth ?: defaultDeviceHealth(),
            connectivityDiagnostics = connDiag,
        )

        CaptureResult.Success(snapshot)
    }

    private fun detectTransport(caps: NetworkCapabilities?): Transport {
        if (caps == null) return Transport.UNKNOWN
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            else -> Transport.UNKNOWN
        }
    }

    @Suppress("NewApi")
    private fun extractCaptivePortalUrl(caps: NetworkCapabilities): String? = try {
        val data = caps.networkSpecifier
        data?.toString()
    } catch (_: Exception) {
        null
    }

    private fun defaultIpConfig() = dev.charly.paranoid.apps.netdiag.data.IpConfig(
        interfaceName = null,
        ipv4Addresses = emptyList(),
        ipv6Addresses = emptyList(),
        gatewayIpv4 = null,
        gatewayIpv6 = null,
        mtu = dev.charly.paranoid.apps.netdiag.data.Measured(value = null, confidence = 0.0f),
        nat64Prefix = null,
        httpProxyHost = null,
        httpProxyPort = null,
        httpProxyPacUrl = null,
        isPrivateDnsActive = false,
        privateDnsServerName = null,
        isRfc1918 = false,
        isApipa = false,
        hasIpv6 = false,
        subnetMaskBits = null,
    )

    private fun defaultDnsSnapshot() = dev.charly.paranoid.apps.netdiag.data.DnsSnapshot(
        servers = emptyList(),
        isPrivateDnsActive = false,
        privateDnsMode = "unknown",
        privateDnsServer = null,
        probes = emptyList(),
    )

    private fun defaultDeviceHealth() = dev.charly.paranoid.apps.netdiag.data.DeviceHealth(
        batteryPercent = 0,
        isCharging = false,
        isDozeModeActive = false,
        isDataSaverActive = false,
        isAirplaneModeOn = false,
        isWifiEnabled = false,
        isCellularEnabled = false,
        memoryAvailableMb = null,
        cpuLoadPercent = null,
        activeVpnPackage = null,
        locationServicesEnabled = false,
        networkPermissions = dev.charly.paranoid.apps.netdiag.data.NetworkPermissions(
            hasAccessNetworkState = false,
            hasAccessWifiState = false,
            hasReadPhoneState = false,
            hasAccessFineLocation = false,
            hasNearbyWifiDevices = false,
            hasPackageUsageStats = false,
            hasForegroundService = false,
        ),
    )
}
