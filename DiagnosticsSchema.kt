/**
 * NetProbe — Diagnostics Snapshot & Comparison Schema
 *
 * Design principles:
 *  - Every field is nullable: absence of data is itself a signal.
 *  - Each leaf value carries a `confidence` (0.0–1.0) so the comparison
 *    engine can weight anomalies appropriately.
 *  - DiagnosticsSnapshot is the unit of exchange between devices
 *    (Bluetooth, NFC, nearby API, QR, or manual export).
 *  - DiagnosticsComparison is the result of diffing two snapshots.
 *  - Severity is attached to each finding so the UI can triage immediately.
 */

package com.netprobe.schema

import kotlinx.serialization.Serializable
import java.util.UUID


// ─────────────────────────────────────────────────────────────
// 0. Cross-cutting primitives
// ─────────────────────────────────────────────────────────────

enum class Severity { INFO, WARNING, CRITICAL }
enum class Transport { WIFI, CELLULAR, ETHERNET, VPN, UNKNOWN }
enum class WifiStandard { LEGACY, N, AC, AX, BE, UNKNOWN }
enum class CellRat { GSM, CDMA, WCDMA, HSPA, LTE, LTE_CA, NR_NSA, NR_SA, UNKNOWN }
enum class DnsRcode { NOERROR, SERVFAIL, NXDOMAIN, REFUSED, TIMEOUT, HIJACKED, UNKNOWN }
enum class PingStatus { REACHABLE, TIMEOUT, UNREACHABLE, ERROR }
enum class ProbeTarget(val host: String) {
    GATEWAY("__gateway__"),           // resolved from LinkProperties
    DNS_PRIMARY("__dns_primary__"),   // first DNS server
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
    val confidence: Float = 1.0f,   // 0.0 = guessed, 1.0 = authoritative
    val source: String? = null,      // e.g. "WifiInfo", "ping", "DnsResolver"
    val timestampMs: Long = 0L
)


// ─────────────────────────────────────────────────────────────
// 1. Top-level snapshot (one per device per session)
// ─────────────────────────────────────────────────────────────

@Serializable
data class DiagnosticsSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val deviceLabel: String,            // "Phone A", user-assigned
    val deviceModel: String,            // Build.MODEL
    val androidVersion: Int,            // Build.VERSION.SDK_INT
    val capturedAtMs: Long,             // SystemClock.currentNetworkTimeClock or wall clock
    val sessionId: String,              // shared across devices in same comparison run

    // ── Network state ──────────────────────────────────────────
    val activeTransport: Transport,
    val isValidated: Boolean,           // NET_CAPABILITY_VALIDATED
    val isCaptivePortal: Boolean,       // NET_CAPABILITY_CAPTIVE_PORTAL
    val captivePortalUrl: String?,
    val isVpnActive: Boolean,
    val isMetered: Boolean,
    val isDozed: Boolean,               // onBlockedStatusChanged

    // ── IP / routing ───────────────────────────────────────────
    val ipConfig: IpConfig,

    // ── Wi-Fi (null when on cellular/ethernet) ─────────────────
    val wifi: WifiSnapshot?,

    // ── Cellular (null when on Wi-Fi only) ─────────────────────
    val cellular: CellularSnapshot?,

    // ── DNS ────────────────────────────────────────────────────
    val dns: DnsSnapshot,

    // ── Active probes ──────────────────────────────────────────
    val probes: List<PingProbeResult>,

    // ── Throughput ─────────────────────────────────────────────
    val throughput: ThroughputResult?,

    // ── Traffic counters (delta since previous snapshot) ───────
    val trafficDelta: TrafficDelta?,

    // ── System / device health ─────────────────────────────────
    val deviceHealth: DeviceHealth,

    // ── Raw findings from ConnectivityDiagnosticsManager ───────
    val connectivityDiagnostics: ConnectivityDiagnosticsData?,
)


// ─────────────────────────────────────────────────────────────
// 2. IP / routing
// ─────────────────────────────────────────────────────────────

@Serializable
data class IpConfig(
    val interfaceName: String?,
    val ipv4Addresses: List<String>,        // CIDR, e.g. "192.168.1.42/24"
    val ipv6Addresses: List<String>,        // link-local + global
    val gatewayIpv4: String?,
    val gatewayIpv6: String?,
    val mtu: Measured<Int>,                 // 0 = default 1500, getLinkProperties().mtu
    val nat64Prefix: String?,               // API 30+
    val httpProxyHost: String?,
    val httpProxyPort: Int?,
    val httpProxyPacUrl: String?,
    val isPrivateDnsActive: Boolean,        // DoT/DoH at system level
    val privateDnsServerName: String?,      // null = opportunistic
    // Derived anomaly flags (computed at capture time)
    val isRfc1918: Boolean,                 // is it a private IP?
    val isApipa: Boolean,                   // 169.254.x.x → DHCP failure
    val hasIpv6: Boolean,
    val subnetMaskBits: Int?,               // from address prefix; /24, /25 etc
)


// ─────────────────────────────────────────────────────────────
// 3. Wi-Fi
// ─────────────────────────────────────────────────────────────

@Serializable
data class WifiSnapshot(
    // ── Connected AP ──────────────────────────────────────────
    val ssid: String?,
    val bssid: String?,                     // randomized on API 29+
    val standard: WifiStandard,
    val frequencyMhz: Int,                  // 2412, 5180, 6135, etc.
    val channel: Int,                       // derived from frequency
    val band: String,                       // "2.4 GHz" / "5 GHz" / "6 GHz"
    val channelWidth: String,               // 20/40/80/160/320 MHz
    val rssi: Measured<Int>,                // dBm, from WifiInfo
    val rssiCategory: SignalCategory,       // EXCELLENT/GOOD/FAIR/POOR/UNUSABLE
    val txLinkSpeedMbps: Measured<Int>,     // API 29+
    val rxLinkSpeedMbps: Measured<Int>,     // API 29+
    val maxTxLinkSpeedMbps: Measured<Int>,  // API 30+ (theoretical max for the standard)
    val maxRxLinkSpeedMbps: Measured<Int>,
    val txLinkUtilization: Float?,          // txLinkSpeed / maxTxLinkSpeed
    val wpa3Used: Boolean,                  // parsed from capabilities string

    // ── Channel environment (from WifiManager.getScanResults) ─
    val environment: WifiEnvironment?,

    // ── BSS load (from Information Elements, element ID 11) ───
    val bssLoad: BssLoad?,

    // ── RTT ranging (WifiRttManager, API 28+) ─────────────────
    val rttDistanceMm: Measured<Int>?,
)

enum class SignalCategory { EXCELLENT, GOOD, FAIR, POOR, UNUSABLE }

@Serializable
data class WifiEnvironment(
    val totalApsVisible: Int,
    val apsOnSameChannel: Int,             // direct co-channel interference
    val apsOnAdjacentChannels: Int,        // adjacent-channel interference (2.4 GHz)
    val channelUtilizationPercent: Float?, // from BSS Load IE, 0–100
    val strongestNeighborRssi: Int?,       // most powerful competing AP on same channel
    val nonOverlappingChannelSuggestion: Int?, // least-congested alternative channel
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
    val isSameBss: Boolean,               // same network, different AP (roaming candidate)
)

@Serializable
data class BssLoad(
    val stationCount: Int,                // # clients on this AP
    val channelUtilization: Int,          // 0–255, from IE, ×100/255 = %
    val availableAdmissionCapacity: Int,  // 0–65535 μs/s
)


// ─────────────────────────────────────────────────────────────
// 4. Cellular
// ─────────────────────────────────────────────────────────────

@Serializable
data class CellularSnapshot(
    val rat: CellRat,
    val displayType: String?,             // TelephonyDisplayInfo "5G", "LTE", "4G+" etc.
    val operatorName: String?,
    val mcc: String?,
    val mnc: String?,
    // ── Signal (LTE) ──────────────────────────────────────────
    val rsrp: Measured<Int>?,             // dBm, reference signal received power
    val rsrq: Measured<Int>?,             // dB
    val rssnr: Measured<Int>?,            // dB
    val cqi: Measured<Int>?,
    val rssi: Measured<Int>?,
    val signalCategory: SignalCategory,
    // ── Signal (NR) ───────────────────────────────────────────
    val ssRsrp: Measured<Int>?,
    val ssRsrq: Measured<Int>?,
    val ssSinr: Measured<Int>?,
    // ── Data state ────────────────────────────────────────────
    val dataState: String?,               // CONNECTED / DISCONNECTED / SUSPENDED etc.
    val isRoaming: Boolean,
    // ── Carrier aggregation ───────────────────────────────────
    val isCarrierAggregation: Boolean,
    val numComponentCarriers: Int?,
)


// ─────────────────────────────────────────────────────────────
// 5. DNS
// ─────────────────────────────────────────────────────────────

@Serializable
data class DnsSnapshot(
    val servers: List<String>,            // from LinkProperties.getDnsServers()
    val isPrivateDnsActive: Boolean,
    val privateDnsMode: String,           // "off" / "opportunistic" / "strict"
    val privateDnsServer: String?,
    val probes: List<DnsProbeResult>,
)

@Serializable
data class DnsProbeResult(
    val target: String,                   // hostname resolved
    val server: String,                   // which DNS server was used
    val protocol: String,                 // "UDP" / "DoT" / "DoH"
    val rcode: DnsRcode,
    val latencyMs: Measured<Long>,
    val resolvedAddresses: List<String>,
    val ttlSeconds: Int?,
    val hijackDetected: Boolean,          // resolved IP ≠ expected via DoH cross-check
    val hijackCandidate: String?,         // what the system DNS returned
)


// ─────────────────────────────────────────────────────────────
// 6. ICMP / TCP probes
// ─────────────────────────────────────────────────────────────

@Serializable
data class PingProbeResult(
    val target: ProbeTarget,
    val resolvedIp: String?,
    val packetsSent: Int,
    val packetsReceived: Int,
    val packetLossPercent: Float,         // (sent-received)/sent*100
    val rttMinMs: Float?,
    val rttAvgMs: Float?,
    val rttMaxMs: Float?,
    val rttMdevMs: Float?,                // mean deviation ≈ jitter
    val status: PingStatus,
    val hopCount: Int?,                   // from traceroute if run
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

// HTTP timing breakdown (one per target URL)
@Serializable
data class HttpTimingResult(
    val url: String,
    val protocol: String,                 // "h2" / "h3" / "http/1.1"
    val statusCode: Int?,
    val dnsMs: Long?,
    val tcpMs: Long?,
    val tlsMs: Long?,
    val ttfbMs: Long?,                    // time-to-first-byte
    val totalMs: Long?,
    val connectionReused: Boolean,
    val bodyBytes: Long?,
    val error: String?,
)


// ─────────────────────────────────────────────────────────────
// 7. Throughput
// ─────────────────────────────────────────────────────────────

@Serializable
data class ThroughputResult(
    val downloadMbps: Measured<Float>,
    val uploadMbps: Measured<Float>,
    val pingMs: Measured<Float>,
    val jitterMs: Measured<Float>,
    val serverUsed: String,
    val protocol: String,                 // "NDT7" / "LibreSpeed" / "iperf3"
    val durationMs: Long,
)


// ─────────────────────────────────────────────────────────────
// 8. Traffic delta
// ─────────────────────────────────────────────────────────────

@Serializable
data class TrafficDelta(
    val periodMs: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val txPackets: Long,
    // Derived
    val rxRateBytesPerSec: Float,
    val txRateBytesPerSec: Float,
)


// ─────────────────────────────────────────────────────────────
// 9. Device health
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
    val cpuLoadPercent: Measured<Float>?,  // from /proc/stat if accessible
    val activeVpnPackage: String?,         // package name if VPN is active
    val locationServicesEnabled: Boolean,  // affects Wi-Fi scan quality
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
// 10. ConnectivityDiagnosticsManager data (API 30+)
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
    val dataStallDetectionMethod: String?,  // "DNS" / "TCP"
)


// ─────────────────────────────────────────────────────────────
// 11. Comparison result — the output of diffing two snapshots
// ─────────────────────────────────────────────────────────────

@Serializable
data class DiagnosticsComparison(
    val id: String = UUID.randomUUID().toString(),
    val snapshotA: DiagnosticsSnapshot,
    val snapshotB: DiagnosticsSnapshot,
    val comparedAtMs: Long,
    val captureTimeDeltaMs: Long,          // how far apart the captures are

    val summary: ComparisonSummary,
    val findings: List<ComparisonFinding>, // ordered by severity desc, then category
    val categories: Map<String, CategoryResult>,
)

@Serializable
data class ComparisonSummary(
    val overallStatus: ComparisonStatus,
    val criticalCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val matchedTransport: Boolean,         // both on Wi-Fi? both on LTE?
    val likelyCause: String?,              // top-ranked hypothesis, e.g. "DNS failure on Phone A"
)

enum class ComparisonStatus {
    IDENTICAL,      // essentially no differences
    MINOR_DIFF,     // differences but neither explains connectivity loss
    ONE_DEGRADED,   // one device has a clear problem, the other is fine
    BOTH_DEGRADED,  // both have issues (points to shared infra: AP, ISP, router)
    INCOMPARABLE,   // different transports / locations — comparison not meaningful
}

@Serializable
data class ComparisonFinding(
    val id: String = UUID.randomUUID().toString(),
    val category: FindingCategory,
    val severity: Severity,
    val metric: String,                    // human label, e.g. "DNS latency"
    val valueA: String,                    // rendered value for device A
    val valueB: String,                    // rendered value for device B
    val delta: String?,                    // "+42 ms", "−18 dBm", etc.
    val deltaPercent: Float?,
    val threshold: String?,                // what we expected, e.g. "< 100 ms"
    val affectedDevice: AffectedDevice,
    val explanation: String,               // plain-English cause
    val recommendation: String,            // what to do about it
)

enum class FindingCategory {
    CONNECTIVITY,    // validated, captive portal, VPN
    IP_CONFIG,       // APIPA, wrong gateway, MTU, proxy
    WIFI_SIGNAL,     // RSSI, standard, channel width
    WIFI_CHANNEL,    // co-channel / adjacent interference, BSS load
    CELLULAR_SIGNAL, // RSRP, SINR
    DNS,             // latency, rcode, hijacking, private DNS
    LATENCY,         // ping RTT to gateway / DNS / internet
    PACKET_LOSS,     // packet loss %
    THROUGHPUT,      // download / upload
    JITTER,
    ROUTING,         // gateway unreachable, no default route
    DEVICE_HEALTH,   // battery, Doze, Data Saver, VPN
}

enum class AffectedDevice { A, B, BOTH, NEITHER }


// ─────────────────────────────────────────────────────────────
// 12. Per-category results (detailed breakdown per domain)
// ─────────────────────────────────────────────────────────────

@Serializable
data class CategoryResult(
    val category: FindingCategory,
    val scoreA: Float,     // 0.0 (broken) – 1.0 (perfect)
    val scoreB: Float,
    val findings: List<ComparisonFinding>,
)


// ─────────────────────────────────────────────────────────────
// 13. Comparison engine — pure functions, no Android deps here
// ─────────────────────────────────────────────────────────────

object ComparisonEngine {

    fun compare(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): DiagnosticsComparison {
        val findings = mutableListOf<ComparisonFinding>()

        findings += compareConnectivity(a, b)
        findings += compareIpConfig(a, b)
        if (a.activeTransport == Transport.WIFI && b.activeTransport == Transport.WIFI) {
            findings += compareWifi(a, b)
        }
        if (a.activeTransport == Transport.CELLULAR || b.activeTransport == Transport.CELLULAR) {
            findings += compareCellular(a, b)
        }
        findings += compareDns(a, b)
        findings += compareProbes(a, b)
        a.throughput?.let { tA -> b.throughput?.let { tB -> findings += compareThroughput(tA, tB) } }
        findings += compareDeviceHealth(a, b)

        val sortedFindings = findings.sortedWith(
            compareByDescending<ComparisonFinding> { it.severity.ordinal }
                .thenBy { it.category.ordinal }
        )

        val criticals = sortedFindings.count { it.severity == Severity.CRITICAL }
        val warnings  = sortedFindings.count { it.severity == Severity.WARNING }
        val infos     = sortedFindings.count { it.severity == Severity.INFO }

        val overallStatus = when {
            a.activeTransport != b.activeTransport -> ComparisonStatus.INCOMPARABLE
            criticals == 0 && warnings == 0       -> ComparisonStatus.IDENTICAL
            criticals == 0                        -> ComparisonStatus.MINOR_DIFF
            sortedFindings.any { it.affectedDevice == AffectedDevice.BOTH } -> ComparisonStatus.BOTH_DEGRADED
            else                                  -> ComparisonStatus.ONE_DEGRADED
        }

        val likelyCause = sortedFindings.firstOrNull { it.severity == Severity.CRITICAL }
            ?.let { "${it.explanation} (${it.metric})" }

        val categories = sortedFindings
            .groupBy { it.category }
            .map { (cat, fs) ->
                val scoreA = scoreForDevice(fs, AffectedDevice.A)
                val scoreB = scoreForDevice(fs, AffectedDevice.B)
                cat.name to CategoryResult(cat, scoreA, scoreB, fs)
            }.toMap()

        return DiagnosticsComparison(
            snapshotA = a,
            snapshotB = b,
            comparedAtMs = System.currentTimeMillis(),
            captureTimeDeltaMs = kotlin.math.abs(a.capturedAtMs - b.capturedAtMs),
            summary = ComparisonSummary(
                overallStatus, criticals, warnings, infos,
                a.activeTransport == b.activeTransport,
                likelyCause
            ),
            findings = sortedFindings,
            categories = categories,
        )
    }

    // ── Connectivity ──────────────────────────────────────────

    private fun compareConnectivity(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()

        if (!a.isValidated && b.isValidated) {
            findings += finding(
                category = FindingCategory.CONNECTIVITY,
                severity = Severity.CRITICAL,
                metric = "Internet validated",
                valueA = "NOT validated",
                valueB = "Validated",
                affectedDevice = AffectedDevice.A,
                explanation = "Phone A's network passes connectivity probe but has no internet — " +
                        "likely a captive portal, NAT loop, or DNS-only block.",
                recommendation = "Check for a captive portal prompt on Phone A. Try forgetting and reconnecting to the Wi-Fi."
            )
        } else if (a.isValidated && !b.isValidated) {
            findings += finding(
                category = FindingCategory.CONNECTIVITY, severity = Severity.CRITICAL,
                metric = "Internet validated", valueA = "Validated", valueB = "NOT validated",
                affectedDevice = AffectedDevice.B,
                explanation = "Phone B cannot reach the internet probe endpoint.",
                recommendation = "Check Phone B's network configuration or try toggling Wi-Fi."
            )
        } else if (!a.isValidated && !b.isValidated) {
            findings += finding(
                category = FindingCategory.CONNECTIVITY, severity = Severity.CRITICAL,
                metric = "Internet validated", valueA = "NOT validated", valueB = "NOT validated",
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Neither phone can reach the internet. Problem is likely in the shared infrastructure: router, ISP, or captive portal.",
                recommendation = "Reboot your router/modem. Check if other devices on the network can access the internet."
            )
        }

        if (a.isCaptivePortal || b.isCaptivePortal) {
            val affected = when {
                a.isCaptivePortal && b.isCaptivePortal -> AffectedDevice.BOTH
                a.isCaptivePortal -> AffectedDevice.A
                else -> AffectedDevice.B
            }
            findings += finding(
                category = FindingCategory.CONNECTIVITY, severity = Severity.CRITICAL,
                metric = "Captive portal",
                valueA = if (a.isCaptivePortal) "DETECTED" else "None",
                valueB = if (b.isCaptivePortal) "DETECTED" else "None",
                affectedDevice = affected,
                explanation = "A captive portal is intercepting traffic. You must sign in to the network via a browser.",
                recommendation = "Open a browser and complete the captive portal login."
            )
        }

        return findings
    }

    // ── IP config ─────────────────────────────────────────────

    private fun compareIpConfig(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()
        val ipA = a.ipConfig
        val ipB = b.ipConfig

        // APIPA detection
        if (ipA.isApipa) findings += finding(
            FindingCategory.IP_CONFIG, Severity.CRITICAL, "IP address",
            "169.254.x.x (APIPA)", ipB.ipv4Addresses.firstOrNull() ?: "Unknown",
            affectedDevice = AffectedDevice.A,
            explanation = "Phone A got an APIPA address — DHCP lease failed.",
            recommendation = "Forget and rejoin the Wi-Fi network. If it persists, check that the router DHCP pool isn't exhausted."
        )
        if (ipB.isApipa) findings += finding(
            FindingCategory.IP_CONFIG, Severity.CRITICAL, "IP address",
            ipA.ipv4Addresses.firstOrNull() ?: "Unknown", "169.254.x.x (APIPA)",
            affectedDevice = AffectedDevice.B,
            explanation = "Phone B got an APIPA address — DHCP lease failed.",
            recommendation = "Forget and rejoin the Wi-Fi on Phone B, or check router DHCP."
        )

        // Gateway reachability
        if (ipA.gatewayIpv4 == null) findings += finding(
            FindingCategory.ROUTING, Severity.CRITICAL, "Default gateway",
            "MISSING", ipB.gatewayIpv4 ?: "Missing",
            affectedDevice = AffectedDevice.A,
            explanation = "Phone A has no default gateway — routing is broken.",
            recommendation = "Toggle airplane mode or reconnect to the network."
        )

        // MTU mismatch (both on same network)
        val mtuA = ipA.mtu.value ?: 1500
        val mtuB = ipB.mtu.value ?: 1500
        if (mtuA != mtuB && kotlin.math.abs(mtuA - mtuB) > 50) {
            findings += finding(
                FindingCategory.IP_CONFIG, Severity.WARNING, "MTU",
                "${mtuA}B", "${mtuB}B",
                delta = "${mtuA - mtuB}B",
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Both phones have different MTU values on the same network, which is unusual and may cause fragmentation.",
                recommendation = "Check if a VPN or manual network config is active on one device."
            )
        }
        // Low MTU
        if (mtuA < 1400) findings += finding(
            FindingCategory.IP_CONFIG, Severity.WARNING, "MTU (Phone A)",
            "${mtuA}B", "${mtuB}B",
            affectedDevice = AffectedDevice.A,
            explanation = "MTU of ${mtuA}B is below 1400. Large packets may be fragmented, increasing latency.",
            recommendation = "Check if a VPN is reducing MTU. A VPN overhead is typically 60-80 bytes."
        )

        // Subnet conflict (both have IPs but different gateways despite being on same physical network)
        if (ipA.gatewayIpv4 != null && ipB.gatewayIpv4 != null &&
            ipA.gatewayIpv4 != ipB.gatewayIpv4) {
            findings += finding(
                FindingCategory.IP_CONFIG, Severity.WARNING, "Gateway IP",
                ipA.gatewayIpv4, ipB.gatewayIpv4,
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Both phones see different default gateways. They may be on different SSIDs, subnets, or VLANs.",
                recommendation = "Ensure both phones are connected to the same SSID/band. Check if one is on a guest network."
            )
        }

        // IPv6 availability
        if (ipA.hasIpv6 && !ipB.hasIpv6) findings += finding(
            FindingCategory.IP_CONFIG, Severity.INFO, "IPv6",
            "Available", "Not available",
            affectedDevice = AffectedDevice.B,
            explanation = "Phone B lacks an IPv6 address while Phone A has one. This can cause slower connections to IPv6-preferred CDNs.",
            recommendation = "Check if IPv6 is disabled in Phone B's network settings."
        )

        return findings
    }

    // ── Wi-Fi ─────────────────────────────────────────────────

    private fun compareWifi(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()
        val wA = a.wifi ?: return findings
        val wB = b.wifi ?: return findings

        // RSSI comparison
        val rssiA = wA.rssi.value ?: return findings
        val rssiB = wB.rssi.value ?: return findings
        val rssiDelta = rssiA - rssiB

        if (kotlin.math.abs(rssiDelta) >= 10) {
            val worse = if (rssiDelta < 0) AffectedDevice.A else AffectedDevice.B
            val worseVal = if (rssiDelta < 0) rssiA else rssiB
            findings += finding(
                FindingCategory.WIFI_SIGNAL, Severity.if_(worseVal < -80, Severity.CRITICAL, Severity.WARNING),
                "RSSI (signal strength)", "${rssiA} dBm", "${rssiB} dBm",
                delta = "${rssiDelta} dBm",
                affectedDevice = worse,
                explanation = "The weaker phone is ${kotlin.math.abs(rssiDelta)} dBm below the other. " +
                        "At ≥10 dBm difference, the weaker device will get significantly lower throughput and higher retransmissions.",
                recommendation = "Move the weaker phone closer to the router or eliminate physical obstructions."
            )
        }

        // Different channels (both should be on same AP if co-located)
        if (wA.channel != wB.channel) {
            findings += finding(
                FindingCategory.WIFI_CHANNEL, Severity.INFO, "Wi-Fi channel",
                "Ch ${wA.channel} (${wA.frequencyMhz} MHz)", "Ch ${wB.channel} (${wB.frequencyMhz} MHz)",
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Phones are on different channels. This usually means different bands (2.4/5/6 GHz) or different APs. " +
                        "Band steering inconsistency can explain speed differences.",
                recommendation = "Check if both phones are connected to the same AP/band. Prefer 5 GHz or 6 GHz for speed, 2.4 GHz for range."
            )
        }

        // Wi-Fi standard mismatch
        if (wA.standard != wB.standard) {
            val worse = if (wA.standard.ordinal < wB.standard.ordinal) AffectedDevice.A else AffectedDevice.B
            findings += finding(
                FindingCategory.WIFI_SIGNAL, Severity.INFO, "Wi-Fi standard",
                wA.standard.name, wB.standard.name,
                affectedDevice = worse,
                explanation = "One device negotiated an older Wi-Fi standard. This limits its maximum throughput.",
                recommendation = "Older devices may not support newer standards. No action needed unless significant speed difference is observed."
            )
        }

        // Channel environment comparison (both see same environment since co-located)
        val envA = wA.environment
        val envB = wB.environment
        if (envA != null && envB != null) {
            if (kotlin.math.abs(envA.apsOnSameChannel - envB.apsOnSameChannel) > 3) {
                findings += finding(
                    FindingCategory.WIFI_CHANNEL, Severity.INFO, "APs on same channel",
                    "${envA.apsOnSameChannel}", "${envB.apsOnSameChannel}",
                    affectedDevice = AffectedDevice.BOTH,
                    explanation = "Scan results differ significantly between phones. This could indicate scan timing differences or position effects.",
                    recommendation = "Compare channel utilization over multiple scans."
                )
            }
        }

        // BSS load
        val bssA = wA.bssLoad
        val bssB = wB.bssLoad
        if (bssA != null) {
            val util = bssA.channelUtilization * 100 / 255
            if (util > 75) findings += finding(
                FindingCategory.WIFI_CHANNEL, Severity.WARNING, "AP channel utilization",
                "$util%", bssB?.let { "${it.channelUtilization * 100 / 255}%" } ?: "N/A",
                affectedDevice = AffectedDevice.BOTH,
                explanation = "The connected AP reports $util% channel utilization. Congestion on the AP itself is limiting both devices.",
                recommendation = "Switch to a less congested channel or AP, or reduce the number of connected clients."
            )
        }

        // Effective throughput utilization
        val txUtilA = wA.txLinkUtilization
        val txUtilB = wB.txLinkUtilization
        if (txUtilA != null && txUtilB != null && kotlin.math.abs(txUtilA - txUtilB) > 0.3f) {
            val worse = if (txUtilA < txUtilB) AffectedDevice.A else AffectedDevice.B
            findings += finding(
                FindingCategory.WIFI_SIGNAL, Severity.WARNING, "TX link utilization (actual/max)",
                "${(txUtilA * 100).toInt()}%", "${(txUtilB * 100).toInt()}%",
                affectedDevice = worse,
                explanation = "One device is using a much smaller fraction of its theoretical max link speed. " +
                        "This often indicates interference, poor SNR, or MCS index downgrade.",
                recommendation = "Check RSSI on the weaker device and ensure no microwave/Bluetooth interference on 2.4 GHz."
            )
        }

        return findings
    }

    // ── Cellular ──────────────────────────────────────────────

    private fun compareCellular(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()
        val cA = a.cellular ?: return findings
        val cB = b.cellular ?: return findings

        val rsrpA = cA.rsrp?.value
        val rsrpB = cB.rsrp?.value
        if (rsrpA != null && rsrpB != null && kotlin.math.abs(rsrpA - rsrpB) >= 10) {
            val worse = if (rsrpA < rsrpB) AffectedDevice.A else AffectedDevice.B
            findings += finding(
                FindingCategory.CELLULAR_SIGNAL, Severity.WARNING, "LTE RSRP",
                "${rsrpA} dBm", "${rsrpB} dBm", delta = "${rsrpA - rsrpB} dBm",
                affectedDevice = worse,
                explanation = "Significant RSRP difference between devices in the same location. " +
                        "Could be device antenna quality, SIM band support, or carrier differences.",
                recommendation = "Check if both SIMs are on the same carrier and band configuration."
            )
        }

        if (cA.rat != cB.rat) {
            findings += finding(
                FindingCategory.CELLULAR_SIGNAL, Severity.INFO, "Radio access technology",
                cA.rat.name, cB.rat.name,
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Phones are connected to different cellular generations. Speed will differ accordingly.",
                recommendation = "If both phones support 5G/LTE-CA, check if carrier aggregation is enabled in each phone's network settings."
            )
        }

        return findings
    }

    // ── DNS ───────────────────────────────────────────────────

    private fun compareDns(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()

        // Server configuration
        val serversA = a.dns.servers.toSet()
        val serversB = b.dns.servers.toSet()
        if (serversA != serversB) {
            findings += finding(
                FindingCategory.DNS, Severity.INFO, "DNS servers",
                serversA.joinToString(), serversB.joinToString(),
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Phones are using different DNS servers. Results and latency may vary.",
                recommendation = "Consider setting a consistent private DNS (e.g. dns.google or cloudflare-dns.com) in both devices."
            )
        }

        // Probe comparison (match by target)
        val probeMap = mapOf(
            "A" to a.dns.probes.associateBy { it.target },
            "B" to b.dns.probes.associateBy { it.target }
        )
        val allTargets = (a.dns.probes.map { it.target } + b.dns.probes.map { it.target }).toSet()

        for (target in allTargets) {
            val pA = probeMap["A"]?.get(target)
            val pB = probeMap["B"]?.get(target)

            // Failure on one side
            if (pA?.rcode != DnsRcode.NOERROR && pB?.rcode == DnsRcode.NOERROR) {
                findings += finding(
                    FindingCategory.DNS, Severity.CRITICAL, "DNS resolution ($target)",
                    pA?.rcode?.name ?: "No probe", "NOERROR",
                    affectedDevice = AffectedDevice.A,
                    explanation = "DNS resolution for $target fails on Phone A but succeeds on Phone B. " +
                            "Likely DNS server unreachable or misconfigured on Phone A.",
                    recommendation = "Try switching DNS server on Phone A to 8.8.8.8 or 1.1.1.1."
                )
            }

            // Hijack on one side
            if (pA?.hijackDetected == true && pB?.hijackDetected == false) {
                findings += finding(
                    FindingCategory.DNS, Severity.CRITICAL, "DNS hijacking ($target)",
                    "DETECTED (${pA.hijackCandidate})", "Clean",
                    affectedDevice = AffectedDevice.A,
                    explanation = "Phone A's DNS returns a different IP than the authoritative DoH result. " +
                            "This indicates ISP/captive-portal DNS interception.",
                    recommendation = "Enable Private DNS (DoT) on Phone A under Settings → Network → Private DNS."
                )
            }

            // Latency gap
            val latA = pA?.latencyMs?.value
            val latB = pB?.latencyMs?.value
            if (latA != null && latB != null) {
                val delta = latA - latB
                if (kotlin.math.abs(delta) > 80) {
                    val worse = if (delta > 0) AffectedDevice.A else AffectedDevice.B
                    findings += finding(
                        FindingCategory.DNS, Severity.WARNING, "DNS latency ($target)",
                        "${latA} ms", "${latB} ms", delta = "${delta} ms",
                        affectedDevice = worse,
                        explanation = "DNS is significantly slower on one device. " +
                                "Possible causes: different DNS servers, overloaded resolver, or poor routing.",
                        recommendation = "Switch the slower device to a faster public DNS resolver."
                    )
                }
            }
        }

        return findings
    }

    // ── Probes (ping/traceroute) ──────────────────────────────

    private fun compareProbes(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()

        val probeMapA = a.probes.associateBy { it.target }
        val probeMapB = b.probes.associateBy { it.target }
        val allTargets = (a.probes.map { it.target } + b.probes.map { it.target }).toSet()

        for (target in allTargets) {
            val pA = probeMapA[target]
            val pB = probeMapB[target]

            // Packet loss
            val lossA = pA?.packetLossPercent ?: 0f
            val lossB = pB?.packetLossPercent ?: 0f

            if (lossA > 5f && lossB <= 1f) {
                findings += finding(
                    FindingCategory.PACKET_LOSS,
                    Severity.if_(lossA > 20f, Severity.CRITICAL, Severity.WARNING),
                    "Packet loss → ${target.host}",
                    "${lossA.toInt()}%", "${lossB.toInt()}%", delta = "+${(lossA - lossB).toInt()}%",
                    affectedDevice = AffectedDevice.A,
                    explanation = "Phone A is losing ${lossA.toInt()}% of packets to ${target.host} while Phone B has clean connectivity. " +
                            "Likely a Wi-Fi signal or interference issue on Phone A specifically.",
                    recommendation = "Check RSSI and channel utilization on Phone A."
                )
            } else if (lossA > 5f && lossB > 5f) {
                findings += finding(
                    FindingCategory.PACKET_LOSS,
                    Severity.if_(maxOf(lossA, lossB) > 20f, Severity.CRITICAL, Severity.WARNING),
                    "Packet loss → ${target.host}",
                    "${lossA.toInt()}%", "${lossB.toInt()}%",
                    affectedDevice = AffectedDevice.BOTH,
                    explanation = "Both phones experience packet loss to ${target.host}. " +
                            "The problem is likely in shared infrastructure: router, ISP link, or WAN.",
                    recommendation = "Reboot the router and modem. If loss is only to internet (not gateway), suspect ISP or WAN congestion."
                )
            }

            // RTT comparison
            val rttA = pA?.rttAvgMs
            val rttB = pB?.rttAvgMs
            if (rttA != null && rttB != null && kotlin.math.abs(rttA - rttB) > 30f) {
                val worse = if (rttA > rttB) AffectedDevice.A else AffectedDevice.B
                findings += finding(
                    FindingCategory.LATENCY,
                    Severity.if_(maxOf(rttA, rttB) > 200f, Severity.WARNING, Severity.INFO),
                    "RTT → ${target.host}",
                    "%.1f ms".format(rttA), "%.1f ms".format(rttB),
                    delta = "%.1f ms".format(rttA - rttB),
                    affectedDevice = worse,
                    explanation = "One phone has noticeably higher latency to ${target.host}. " +
                            "May indicate it is on a different AP, band, or the RF environment differs.",
                    recommendation = "Check band steering and AP placement. Prefer 5 GHz or 6 GHz."
                )
            }

            // Jitter
            val jittA = pA?.rttMdevMs
            val jittB = pB?.rttMdevMs
            if (jittA != null && jittB != null) {
                if (jittA > 20f && jittB < 5f) {
                    findings += finding(
                        FindingCategory.JITTER, Severity.WARNING, "Jitter → ${target.host}",
                        "%.1f ms".format(jittA), "%.1f ms".format(jittB),
                        affectedDevice = AffectedDevice.A,
                        explanation = "High jitter on Phone A indicates inconsistent delivery, likely due to Wi-Fi retransmissions or interference.",
                        recommendation = "Switch to a less congested channel or move closer to the router."
                    )
                } else if (jittA > 20f && jittB > 20f) {
                    findings += finding(
                        FindingCategory.JITTER, Severity.WARNING, "Jitter → ${target.host}",
                        "%.1f ms".format(jittA), "%.1f ms".format(jittB),
                        affectedDevice = AffectedDevice.BOTH,
                        explanation = "Both phones experience high jitter. Likely shared problem: congested AP, router bufferbloat, or WAN instability.",
                        recommendation = "Enable SQM/QoS on your router if available. Check for background traffic."
                    )
                }
            }
        }
        return findings
    }

    // ── Throughput ────────────────────────────────────────────

    private fun compareThroughput(a: ThroughputResult, b: ThroughputResult): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()

        val dlA = a.downloadMbps.value ?: return findings
        val dlB = b.downloadMbps.value ?: return findings
        val ratio = if (dlB > 0) dlA / dlB else 0f

        if (ratio < 0.5f || ratio > 2f) {
            val worse = if (ratio < 1f) AffectedDevice.A else AffectedDevice.B
            findings += finding(
                FindingCategory.THROUGHPUT,
                Severity.if_(minOf(dlA, dlB) < 5f, Severity.CRITICAL, Severity.WARNING),
                "Download throughput",
                "%.1f Mbps".format(dlA), "%.1f Mbps".format(dlB),
                delta = "%.1f Mbps".format(dlA - dlB),
                deltaPercent = (ratio - 1f) * 100f,
                affectedDevice = worse,
                explanation = "Significant throughput gap between devices. Likely causes: different Wi-Fi bands, " +
                        "antenna capability, or driver/firmware differences.",
                recommendation = "Ensure both devices are on the same band (5/6 GHz preferred). Check for background downloads."
            )
        }

        return findings
    }

    // ── Device health ─────────────────────────────────────────

    private fun compareDeviceHealth(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()

        if (a.deviceHealth.isDozeModeActive && !b.deviceHealth.isDozeModeActive) {
            findings += finding(
                FindingCategory.DEVICE_HEALTH, Severity.WARNING, "Doze mode",
                "ACTIVE", "Inactive", affectedDevice = AffectedDevice.A,
                explanation = "Phone A is in Doze mode. Network access is restricted, scan results and connectivity checks may be stale.",
                recommendation = "Keep the screen on or plug into power during diagnostics."
            )
        }

        if (a.deviceHealth.isDataSaverActive) {
            findings += finding(
                FindingCategory.DEVICE_HEALTH, Severity.WARNING, "Data Saver",
                "ON", if (b.deviceHealth.isDataSaverActive) "ON" else "OFF",
                affectedDevice = if (b.deviceHealth.isDataSaverActive) AffectedDevice.BOTH else AffectedDevice.A,
                explanation = "Data Saver restricts background network access and may skew diagnostics results.",
                recommendation = "Disable Data Saver during the diagnostic session."
            )
        }

        if (a.isVpnActive && !b.isVpnActive) {
            findings += finding(
                FindingCategory.DEVICE_HEALTH, Severity.WARNING, "VPN active",
                "YES (${a.deviceHealth.activeVpnPackage ?: "unknown"})", "NO",
                affectedDevice = AffectedDevice.A,
                explanation = "Phone A is running a VPN. All metrics will reflect VPN-tunneled performance, not the raw network.",
                recommendation = "Disable the VPN on Phone A before comparing network metrics."
            )
        }

        if (!a.deviceHealth.locationServicesEnabled) {
            findings += finding(
                FindingCategory.DEVICE_HEALTH, Severity.INFO, "Location services",
                "OFF", if (b.deviceHealth.locationServicesEnabled) "ON" else "OFF",
                affectedDevice = AffectedDevice.A,
                explanation = "Location services are off on Phone A. Wi-Fi scan results may be throttled or SSID/BSSID may be redacted.",
                recommendation = "Enable Location Services for accurate Wi-Fi diagnostics."
            )
        }

        return findings
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun finding(
        category: FindingCategory,
        severity: Severity,
        metric: String,
        valueA: String,
        valueB: String,
        delta: String? = null,
        deltaPercent: Float? = null,
        threshold: String? = null,
        affectedDevice: AffectedDevice,
        explanation: String,
        recommendation: String,
    ) = ComparisonFinding(
        category = category,
        severity = severity,
        metric = metric,
        valueA = valueA,
        valueB = valueB,
        delta = delta,
        deltaPercent = deltaPercent,
        threshold = threshold,
        affectedDevice = affectedDevice,
        explanation = explanation,
        recommendation = recommendation,
    )

    private fun scoreForDevice(findings: List<ComparisonFinding>, device: AffectedDevice): Float {
        val relevant = findings.filter { it.affectedDevice == device || it.affectedDevice == AffectedDevice.BOTH }
        if (relevant.isEmpty()) return 1.0f
        val penalty = relevant.sumOf {
            when (it.severity) {
                Severity.CRITICAL -> 0.4
                Severity.WARNING  -> 0.15
                Severity.INFO     -> 0.05
            }
        }
        return (1.0 - penalty).coerceIn(0.0, 1.0).toFloat()
    }
}

// ── Extension helpers ─────────────────────────────────────────

private fun Severity.Companion.if_(condition: Boolean, ifTrue: Severity, ifFalse: Severity) =
    if (condition) ifTrue else ifFalse


// ─────────────────────────────────────────────────────────────
// 14. Session — groups snapshots and comparisons over time
// ─────────────────────────────────────────────────────────────

@Serializable
data class DiagnosticsSession(
    val id: String = UUID.randomUUID().toString(),
    val label: String,                     // e.g. "Living room vs Bedroom"
    val createdAtMs: Long,
    val snapshots: List<DiagnosticsSnapshot>,      // one per (device, point-in-time)
    val comparisons: List<DiagnosticsComparison>,  // pairwise results
    val notes: String? = null,
)

// ─────────────────────────────────────────────────────────────
// 15. Thresholds — tunable per comparison context
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
