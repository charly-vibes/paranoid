package dev.charly.paranoid.apps.netdiag.data

import kotlinx.serialization.Serializable
import java.util.UUID

// ─────────────────────────────────────────────────────────────
// Cross-cutting primitives
// ─────────────────────────────────────────────────────────────

@Serializable
enum class Severity { INFO, WARNING, CRITICAL }

@Serializable
enum class Transport { WIFI, CELLULAR, ETHERNET, VPN, UNKNOWN }

@Serializable
enum class WifiStandard { LEGACY, N, AC, AX, BE, UNKNOWN }

@Serializable
enum class CellRat { GSM, CDMA, WCDMA, HSPA, LTE, LTE_CA, NR_NSA, NR_SA, UNKNOWN }

@Serializable
enum class DnsRcode { NOERROR, SERVFAIL, NXDOMAIN, REFUSED, TIMEOUT, HIJACKED, UNKNOWN }

@Serializable
enum class PingStatus { REACHABLE, TIMEOUT, UNREACHABLE, ERROR }

@Serializable
enum class SignalCategory { EXCELLENT, GOOD, FAIR, POOR, UNUSABLE }

@Serializable
enum class ComparisonStatus { IDENTICAL, MINOR_DIFF, ONE_DEGRADED, BOTH_DEGRADED, INCOMPARABLE }

@Serializable
enum class AffectedDevice { A, B, BOTH, NEITHER }

@Serializable
enum class FindingCategory {
    CONNECTIVITY, IP_CONFIG, WIFI_SIGNAL, WIFI_CHANNEL, CELLULAR_SIGNAL,
    DNS, LATENCY, PACKET_LOSS, THROUGHPUT, JITTER, ROUTING, DEVICE_HEALTH
}

@Serializable
enum class ProbeTarget(val host: String) {
    GATEWAY("__gateway__"),
    DNS_PRIMARY("__dns_primary__"),
    DNS_SECONDARY("__dns_secondary__"),
    GOOGLE_DNS("8.8.8.8"),
    CLOUDFLARE_DNS("1.1.1.1"),
    CONNECTIVITY_CHECK("connectivitycheck.gstatic.com"),
    CLOUDFLARE_WEB("cloudflare.com"),
    GOOGLE_WEB("google.com"),
}

@Serializable
data class Measured<T>(
    val value: T?,
    val confidence: Float = 1.0f,
    val source: String? = null,
    val timestampMs: Long = 0L
)

// ─────────────────────────────────────────────────────────────
// Snapshot (one per device per session)
// ─────────────────────────────────────────────────────────────

@Serializable
data class DiagnosticsSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val deviceLabel: String,
    val deviceModel: String,
    val androidVersion: Int,
    val capturedAtMs: Long,
    val sessionId: String,

    val activeTransport: Transport,
    val isValidated: Boolean,
    val isCaptivePortal: Boolean,
    val captivePortalUrl: String?,
    val isVpnActive: Boolean,
    val isMetered: Boolean,
    val isDozed: Boolean,

    val ipConfig: IpConfig,
    val wifi: WifiSnapshot?,
    val cellular: CellularSnapshot?,
    val dns: DnsSnapshot,
    val probes: List<PingProbeResult>,
    val throughput: ThroughputResult?,
    val trafficDelta: TrafficDelta?,
    val deviceHealth: DeviceHealth,
    val connectivityDiagnostics: ConnectivityDiagnosticsData?,
)

// ─────────────────────────────────────────────────────────────
// IP / routing
// ─────────────────────────────────────────────────────────────

@Serializable
data class IpConfig(
    val interfaceName: String?,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val gatewayIpv4: String?,
    val gatewayIpv6: String?,
    val mtu: Measured<Int>,
    val nat64Prefix: String?,
    val httpProxyHost: String?,
    val httpProxyPort: Int?,
    val httpProxyPacUrl: String?,
    val isPrivateDnsActive: Boolean,
    val privateDnsServerName: String?,
    val isRfc1918: Boolean,
    val isApipa: Boolean,
    val hasIpv6: Boolean,
    val subnetMaskBits: Int?,
)

// ─────────────────────────────────────────────────────────────
// Wi-Fi
// ─────────────────────────────────────────────────────────────

@Serializable
data class WifiSnapshot(
    val ssid: String?,
    val bssid: String?,
    val standard: WifiStandard,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val channelWidth: String,
    val rssi: Measured<Int>,
    val rssiCategory: SignalCategory,
    val txLinkSpeedMbps: Measured<Int>,
    val rxLinkSpeedMbps: Measured<Int>,
    val maxTxLinkSpeedMbps: Measured<Int>,
    val maxRxLinkSpeedMbps: Measured<Int>,
    val txLinkUtilization: Float?,
    val wpa3Used: Boolean,
    val environment: WifiEnvironment?,
    val bssLoad: BssLoad?,
    val rttDistanceMm: Measured<Int>?,
)

@Serializable
data class WifiEnvironment(
    val totalApsVisible: Int,
    val apsOnSameChannel: Int,
    val apsOnAdjacentChannels: Int,
    val channelUtilizationPercent: Float?,
    val strongestNeighborRssi: Int?,
    val nonOverlappingChannelSuggestion: Int?,
    val scanResults: List<NeighborAp>,
)

@Serializable
data class NeighborAp(
    val ssid: String?,
    val bssid: String,
    val frequencyMhz: Int,
    val channel: Int,
    val rssi: Int,
    val standard: WifiStandard,
    val channelWidth: String,
    val isSameBss: Boolean,
)

@Serializable
data class BssLoad(
    val stationCount: Int,
    val channelUtilization: Int,
    val availableAdmissionCapacity: Int,
)

// ─────────────────────────────────────────────────────────────
// Cellular
// ─────────────────────────────────────────────────────────────

@Serializable
data class CellularSnapshot(
    val rat: CellRat,
    val displayType: String?,
    val operatorName: String?,
    val mcc: String?,
    val mnc: String?,
    val rsrp: Measured<Int>?,
    val rsrq: Measured<Int>?,
    val rssnr: Measured<Int>?,
    val cqi: Measured<Int>?,
    val rssi: Measured<Int>?,
    val signalCategory: SignalCategory,
    val ssRsrp: Measured<Int>?,
    val ssRsrq: Measured<Int>?,
    val ssSinr: Measured<Int>?,
    val dataState: String?,
    val isRoaming: Boolean,
    val isCarrierAggregation: Boolean,
    val numComponentCarriers: Int?,
)

// ─────────────────────────────────────────────────────────────
// DNS
// ─────────────────────────────────────────────────────────────

@Serializable
data class DnsSnapshot(
    val servers: List<String>,
    val isPrivateDnsActive: Boolean,
    val privateDnsMode: String,
    val privateDnsServer: String?,
    val probes: List<DnsProbeResult>,
)

@Serializable
data class DnsProbeResult(
    val target: String,
    val server: String,
    val protocol: String,
    val rcode: DnsRcode,
    val latencyMs: Measured<Long>,
    val resolvedAddresses: List<String>,
    val ttlSeconds: Int?,
    val hijackDetected: Boolean,
    val hijackCandidate: String?,
)

// ─────────────────────────────────────────────────────────────
// ICMP / TCP probes
// ─────────────────────────────────────────────────────────────

@Serializable
data class PingProbeResult(
    val target: ProbeTarget,
    val resolvedIp: String?,
    val packetsSent: Int,
    val packetsReceived: Int,
    val packetLossPercent: Float,
    val rttMinMs: Float?,
    val rttAvgMs: Float?,
    val rttMaxMs: Float?,
    val rttMdevMs: Float?,
    val status: PingStatus,
    val hopCount: Int?,
    val tracerouteHops: List<TracerouteHop>?,
)

@Serializable
data class TracerouteHop(
    val hop: Int,
    val ip: String?,
    val rttMs: Float?,
    val isPrivate: Boolean,
    val isTimeout: Boolean,
)

@Serializable
data class HttpTimingResult(
    val url: String,
    val protocol: String,
    val statusCode: Int?,
    val dnsMs: Long?,
    val tcpMs: Long?,
    val tlsMs: Long?,
    val ttfbMs: Long?,
    val totalMs: Long?,
    val connectionReused: Boolean,
    val bodyBytes: Long?,
    val error: String?,
)

// ─────────────────────────────────────────────────────────────
// Throughput
// ─────────────────────────────────────────────────────────────

@Serializable
data class ThroughputResult(
    val downloadMbps: Measured<Float>,
    val uploadMbps: Measured<Float>,
    val pingMs: Measured<Float>,
    val jitterMs: Measured<Float>,
    val serverUsed: String,
    val protocol: String,
    val durationMs: Long,
)

// ─────────────────────────────────────────────────────────────
// Traffic delta
// ─────────────────────────────────────────────────────────────

@Serializable
data class TrafficDelta(
    val periodMs: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val txPackets: Long,
    val rxRateBytesPerSec: Float,
    val txRateBytesPerSec: Float,
)

// ─────────────────────────────────────────────────────────────
// Device health
// ─────────────────────────────────────────────────────────────

@Serializable
data class DeviceHealth(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isDozeModeActive: Boolean,
    val isDataSaverActive: Boolean,
    val isAirplaneModeOn: Boolean,
    val isWifiEnabled: Boolean,
    val isCellularEnabled: Boolean,
    val memoryAvailableMb: Long?,
    val cpuLoadPercent: Measured<Float>?,
    val activeVpnPackage: String?,
    val locationServicesEnabled: Boolean,
    val networkPermissions: NetworkPermissions,
)

@Serializable
data class NetworkPermissions(
    val hasAccessNetworkState: Boolean,
    val hasAccessWifiState: Boolean,
    val hasReadPhoneState: Boolean,
    val hasAccessFineLocation: Boolean,
    val hasNearbyWifiDevices: Boolean,
    val hasPackageUsageStats: Boolean,
    val hasForegroundService: Boolean,
)

// ─────────────────────────────────────────────────────────────
// ConnectivityDiagnosticsManager data (API 30+)
// ─────────────────────────────────────────────────────────────

@Serializable
data class ConnectivityDiagnosticsData(
    val networkProbesAttempted: Int?,
    val networkProbesSucceeded: Int?,
    val dnsConsecutiveTimeouts: Int?,
    val tcpMetricsPacketsSent: Long?,
    val tcpMetricsRetransmissions: Long?,
    val tcpMetricsLatencyMs: Long?,
    val dataStallSuspected: Boolean,
    val dataStallDetectionMethod: String?,
)

// ─────────────────────────────────────────────────────────────
// Comparison result
// ─────────────────────────────────────────────────────────────

@Serializable
data class DiagnosticsComparison(
    val id: String = UUID.randomUUID().toString(),
    val snapshotA: DiagnosticsSnapshot,
    val snapshotB: DiagnosticsSnapshot,
    val comparedAtMs: Long,
    val captureTimeDeltaMs: Long,
    val summary: ComparisonSummary,
    val findings: List<ComparisonFinding>,
    val categories: Map<String, CategoryResult>,
)

@Serializable
data class ComparisonSummary(
    val overallStatus: ComparisonStatus,
    val criticalCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val matchedTransport: Boolean,
    val likelyCause: String?,
)

@Serializable
data class ComparisonFinding(
    val id: String = UUID.randomUUID().toString(),
    val category: FindingCategory,
    val severity: Severity,
    val metric: String,
    val valueA: String,
    val valueB: String,
    val delta: String?,
    val deltaPercent: Float?,
    val threshold: String?,
    val affectedDevice: AffectedDevice,
    val explanation: String,
    val recommendation: String,
)

@Serializable
data class CategoryResult(
    val category: FindingCategory,
    val scoreA: Float,
    val scoreB: Float,
    val findings: List<ComparisonFinding>,
)

// ─────────────────────────────────────────────────────────────
// Session
// ─────────────────────────────────────────────────────────────

@Serializable
data class DiagnosticsSession(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val createdAtMs: Long,
    val snapshots: List<DiagnosticsSnapshot>,
    val comparisons: List<DiagnosticsComparison>,
    val notes: String? = null,
)

// ─────────────────────────────────────────────────────────────
// Thresholds
// ─────────────────────────────────────────────────────────────

data class ComparisonThresholds(
    val rssiDeltaWarnDdBm: Int = 10,
    val rssiDeltaCriticalDdBm: Int = 20,
    val pingLossWarnPercent: Float = 5f,
    val pingLossCriticalPercent: Float = 20f,
    val dnsLatencyWarnMs: Long = 80,
    val dnsLatencyCriticalMs: Long = 300,
    val rttDeltaWarnMs: Float = 30f,
    val rttDeltaCriticalMs: Float = 100f,
    val jitterWarnMs: Float = 20f,
    val throughputRatioWarn: Float = 0.5f,
    val mtuDiffWarnBytes: Int = 50,
    val bssLoadCriticalPercent: Int = 75,
)
