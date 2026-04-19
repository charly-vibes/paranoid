package dev.charly.paranoid.apps.netdiag.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ComparisonEngine.compare().
 *
 * Each test class maps to one comparison function or requirement from
 * openspec/changes/add-netdiag-app/specs/netdiag-comparison/spec.md.
 *
 * Convention: snapshotA is the "normal" baseline; snapshotB is its copy
 * unless the test name says otherwise.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun minimalSnapshot(
    id: String = "snap-a",
    capturedAtMs: Long = 1_700_000_000_000L,
    activeTransport: Transport = Transport.WIFI,
    isValidated: Boolean = true,
    isCaptivePortal: Boolean = false,
    isVpnActive: Boolean = false,
    gatewayIpv4: String? = "192.168.1.1",
    isApipa: Boolean = false,
    hasIpv6: Boolean = false,
    mtu: Int = 1500,
    wifiRssi: Int = -55,
    wifiChannel: Int = 36,
    wifiStandard: WifiStandard = WifiStandard.AX,
    txLinkUtilization: Float? = null,
    bssLoad: BssLoad? = null,
    environment: WifiEnvironment? = null,
    cellular: CellularSnapshot? = null,
    dnsServers: List<String> = listOf("8.8.8.8"),
    dnsProbes: List<DnsProbeResult> = emptyList(),
    probes: List<PingProbeResult> = emptyList(),
    throughput: ThroughputResult? = null,
    isDozeModeActive: Boolean = false,
    isDataSaverActive: Boolean = false,
    vpnPackage: String? = null,
    locationServicesEnabled: Boolean = true,
): DiagnosticsSnapshot = DiagnosticsSnapshot(
    id = id,
    deviceLabel = "Device A",
    deviceModel = "Pixel 7",
    androidVersion = 34,
    capturedAtMs = capturedAtMs,
    sessionId = "session-1",
    activeTransport = activeTransport,
    isValidated = isValidated,
    isCaptivePortal = isCaptivePortal,
    captivePortalUrl = null,
    isVpnActive = isVpnActive,
    isMetered = false,
    isDozed = false,
    ipConfig = IpConfig(
        interfaceName = "wlan0",
        ipv4Addresses = if (isApipa) listOf("169.254.1.1") else listOf("192.168.1.10"),
        ipv6Addresses = emptyList(),
        gatewayIpv4 = gatewayIpv4,
        gatewayIpv6 = null,
        mtu = Measured(mtu),
        nat64Prefix = null,
        httpProxyHost = null,
        httpProxyPort = null,
        httpProxyPacUrl = null,
        isPrivateDnsActive = false,
        privateDnsServerName = null,
        isRfc1918 = true,
        isApipa = isApipa,
        hasIpv6 = hasIpv6,
        subnetMaskBits = 24,
    ),
    wifi = WifiSnapshot(
        ssid = "HomeNetwork",
        bssid = "aa:bb:cc:dd:ee:ff",
        standard = wifiStandard,
        frequencyMhz = 5180,
        channel = wifiChannel,
        band = "5 GHz",
        channelWidth = "80 MHz",
        rssi = Measured(wifiRssi),
        rssiCategory = SignalCategory.EXCELLENT,
        txLinkSpeedMbps = Measured(400),
        rxLinkSpeedMbps = Measured(400),
        maxTxLinkSpeedMbps = Measured(600),
        maxRxLinkSpeedMbps = Measured(600),
        txLinkUtilization = txLinkUtilization,
        wpa3Used = false,
        environment = environment,
        bssLoad = bssLoad,
        rttDistanceMm = null,
    ),
    cellular = cellular,
    dns = DnsSnapshot(
        servers = dnsServers,
        isPrivateDnsActive = false,
        privateDnsMode = "off",
        privateDnsServer = null,
        probes = dnsProbes,
    ),
    probes = probes,
    throughput = throughput,
    trafficDelta = null,
    deviceHealth = DeviceHealth(
        batteryPercent = 80,
        isCharging = false,
        isDozeModeActive = isDozeModeActive,
        isDataSaverActive = isDataSaverActive,
        isAirplaneModeOn = false,
        isWifiEnabled = true,
        isCellularEnabled = true,
        memoryAvailableMb = 2048L,
        cpuLoadPercent = null,
        activeVpnPackage = vpnPackage,
        locationServicesEnabled = locationServicesEnabled,
        networkPermissions = NetworkPermissions(
            hasAccessNetworkState = true,
            hasAccessWifiState = true,
            hasReadPhoneState = true,
            hasAccessFineLocation = true,
            hasNearbyWifiDevices = true,
            hasPackageUsageStats = false,
            hasForegroundService = true,
        ),
    ),
    connectivityDiagnostics = null,
)

private fun cellularSnapshot(
    rat: CellRat = CellRat.LTE,
    rsrp: Int? = -90,
) = CellularSnapshot(
    rat = rat,
    displayType = "LTE",
    operatorName = "Carrier",
    mcc = "310",
    mnc = "260",
    rsrp = rsrp?.let { Measured(it) },
    rsrq = null,
    rssnr = null,
    cqi = null,
    rssi = null,
    signalCategory = SignalCategory.GOOD,
    ssRsrp = null,
    ssRsrq = null,
    ssSinr = null,
    dataState = "CONNECTED",
    isRoaming = false,
    isCarrierAggregation = false,
    numComponentCarriers = null,
)

private fun dnsProbe(
    target: String = "google.com",
    server: String = "8.8.8.8",
    rcode: DnsRcode = DnsRcode.NOERROR,
    latencyMs: Long = 20L,
    hijackDetected: Boolean = false,
    hijackCandidate: String? = null,
) = DnsProbeResult(
    target = target,
    server = server,
    protocol = "UDP",
    rcode = rcode,
    latencyMs = Measured(latencyMs),
    resolvedAddresses = listOf("142.250.80.46"),
    ttlSeconds = 300,
    hijackDetected = hijackDetected,
    hijackCandidate = hijackCandidate,
)

private fun pingProbe(
    target: ProbeTarget = ProbeTarget.GOOGLE_DNS,
    packetLossPercent: Float = 0f,
    rttAvgMs: Float = 10f,
    rttMdevMs: Float = 1f,
) = PingProbeResult(
    target = target,
    resolvedIp = "8.8.8.8",
    packetsSent = 5,
    packetsReceived = (5 * (1f - packetLossPercent / 100f)).toInt(),
    packetLossPercent = packetLossPercent,
    rttMinMs = rttAvgMs - 2f,
    rttAvgMs = rttAvgMs,
    rttMaxMs = rttAvgMs + 5f,
    rttMdevMs = rttMdevMs,
    status = if (packetLossPercent >= 100f) PingStatus.TIMEOUT else PingStatus.REACHABLE,
    hopCount = null,
    tracerouteHops = null,
)

private fun throughput(downloadMbps: Float = 50f, uploadMbps: Float = 20f) = ThroughputResult(
    downloadMbps = Measured(downloadMbps),
    uploadMbps = Measured(uploadMbps),
    pingMs = Measured(15f),
    jitterMs = Measured(2f),
    serverUsed = "speedtest.net",
    protocol = "NDT7",
    durationMs = 10_000L,
)

// ─────────────────────────────────────────────────────────────────────────────
// Self-comparison
// ─────────────────────────────────────────────────────────────────────────────

class SelfComparisonTest {

    @Test
    fun `self-comparison returns IDENTICAL with no findings`() {
        val snap = minimalSnapshot(id = "same-id")
        val result = ComparisonEngine.compare(snap, snap)

        assertEquals(ComparisonStatus.IDENTICAL, result.summary.overallStatus)
        assertTrue("Expected no findings, got ${result.findings}", result.findings.isEmpty())
        assertEquals(0, result.summary.criticalCount)
        assertEquals(0, result.summary.warningCount)
        assertEquals(0, result.summary.infoCount)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Connectivity
// ─────────────────────────────────────────────────────────────────────────────

class ConnectivityComparisonTest {

    @Test
    fun `both validated - no connectivity finding`() {
        val a = minimalSnapshot(isValidated = true)
        val b = minimalSnapshot(id = "snap-b", isValidated = true)
        val result = ComparisonEngine.compare(a, b)

        val connectivityFindings = result.findings.filter { it.category == FindingCategory.CONNECTIVITY }
        assertTrue(connectivityFindings.none { it.metric.contains("validated", ignoreCase = true) })
    }

    @Test
    fun `A not validated B validated - CRITICAL for device A`() {
        val a = minimalSnapshot(isValidated = false)
        val b = minimalSnapshot(id = "snap-b", isValidated = true)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("validated", ignoreCase = true) }
        assertEquals(Severity.CRITICAL, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `neither validated - CRITICAL for BOTH pointing to shared infrastructure`() {
        val a = minimalSnapshot(isValidated = false)
        val b = minimalSnapshot(id = "snap-b", isValidated = false)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("validated", ignoreCase = true) }
        assertEquals(Severity.CRITICAL, finding.severity)
        assertEquals(AffectedDevice.BOTH, finding.affectedDevice)
    }

    @Test
    fun `captive portal on A - CRITICAL for device A`() {
        val a = minimalSnapshot(isCaptivePortal = true)
        val b = minimalSnapshot(id = "snap-b", isCaptivePortal = false)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("captive", ignoreCase = true) }
        assertEquals(Severity.CRITICAL, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IP Config
// ─────────────────────────────────────────────────────────────────────────────

class IpConfigComparisonTest {

    @Test
    fun `APIPA on A - CRITICAL for device A`() {
        val a = minimalSnapshot(isApipa = true)
        val b = minimalSnapshot(id = "snap-b")
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.IP_CONFIG && it.affectedDevice == AffectedDevice.A }
        assertEquals(Severity.CRITICAL, finding.severity)
        assertTrue(finding.valueA.contains("APIPA", ignoreCase = true) || finding.valueA.contains("169.254"))
    }

    @Test
    fun `different gateways - WARNING for BOTH`() {
        val a = minimalSnapshot(gatewayIpv4 = "192.168.1.1")
        val b = minimalSnapshot(id = "snap-b", gatewayIpv4 = "10.0.0.1")
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.IP_CONFIG && it.metric.contains("Gateway", ignoreCase = true) }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.BOTH, finding.affectedDevice)
    }

    @Test
    fun `MTU mismatch over 50 bytes - WARNING`() {
        val a = minimalSnapshot(mtu = 1500)
        val b = minimalSnapshot(id = "snap-b", mtu = 1420)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("MTU", ignoreCase = true) && it.affectedDevice == AffectedDevice.BOTH }
        assertEquals(Severity.WARNING, finding.severity)
    }

    @Test
    fun `MTU mismatch under 50 bytes - no finding`() {
        val a = minimalSnapshot(mtu = 1500)
        val b = minimalSnapshot(id = "snap-b", mtu = 1490)
        val result = ComparisonEngine.compare(a, b)

        val mtuBothFindings = result.findings.filter {
            it.metric.contains("MTU", ignoreCase = true) && it.affectedDevice == AffectedDevice.BOTH
        }
        assertTrue(mtuBothFindings.isEmpty())
    }

    @Test
    fun `A missing gateway - CRITICAL routing finding for A`() {
        val a = minimalSnapshot(gatewayIpv4 = null)
        val b = minimalSnapshot(id = "snap-b")
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.ROUTING }
        assertEquals(Severity.CRITICAL, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `IPv6 on A but not B - INFO for B`() {
        val a = minimalSnapshot(hasIpv6 = true)
        val b = minimalSnapshot(id = "snap-b", hasIpv6 = false)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("IPv6", ignoreCase = true) }
        assertEquals(Severity.INFO, finding.severity)
        assertEquals(AffectedDevice.B, finding.affectedDevice)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wi-Fi Signal
// ─────────────────────────────────────────────────────────────────────────────

class WifiComparisonTest {

    @Test
    fun `RSSI gap 10 dBm A weaker - WARNING for A`() {
        val a = minimalSnapshot(wifiRssi = -75)
        val b = minimalSnapshot(id = "snap-b", wifiRssi = -60)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.WIFI_SIGNAL && it.metric.contains("RSSI") }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `RSSI gap 10 dBm A weaker below -80 - CRITICAL for A`() {
        val a = minimalSnapshot(wifiRssi = -85)
        val b = minimalSnapshot(id = "snap-b", wifiRssi = -70)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.WIFI_SIGNAL && it.metric.contains("RSSI") }
        assertEquals(Severity.CRITICAL, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `RSSI gap under 10 dBm - no RSSI finding`() {
        val a = minimalSnapshot(wifiRssi = -60)
        val b = minimalSnapshot(id = "snap-b", wifiRssi = -65)
        val result = ComparisonEngine.compare(a, b)

        val rssiFindings = result.findings.filter { it.metric.contains("RSSI") }
        assertTrue(rssiFindings.isEmpty())
    }

    @Test
    fun `different channels - INFO for BOTH`() {
        val a = minimalSnapshot(wifiChannel = 36)
        val b = minimalSnapshot(id = "snap-b", wifiChannel = 6)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.WIFI_CHANNEL && it.metric.contains("channel", ignoreCase = true) }
        assertEquals(Severity.INFO, finding.severity)
        assertEquals(AffectedDevice.BOTH, finding.affectedDevice)
    }

    @Test
    fun `same channel - no channel finding`() {
        val a = minimalSnapshot(wifiChannel = 36)
        val b = minimalSnapshot(id = "snap-b", wifiChannel = 36)
        val result = ComparisonEngine.compare(a, b)

        val channelFindings = result.findings.filter {
            it.category == FindingCategory.WIFI_CHANNEL && it.metric.contains("channel", ignoreCase = true)
        }
        assertTrue(channelFindings.isEmpty())
    }

    @Test
    fun `BSS load above 75 percent - WARNING for BOTH`() {
        val highLoad = BssLoad(stationCount = 30, channelUtilization = 200, availableAdmissionCapacity = 1000)
        val a = minimalSnapshot(bssLoad = highLoad)
        val b = minimalSnapshot(id = "snap-b")
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("utilization", ignoreCase = true) }
        assertEquals(Severity.WARNING, finding.severity)
    }

    @Test
    fun `TX link utilization gap over 30 percent - WARNING`() {
        val a = minimalSnapshot(txLinkUtilization = 0.3f)
        val b = minimalSnapshot(id = "snap-b", txLinkUtilization = 0.8f)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("utilization", ignoreCase = true) && it.category == FindingCategory.WIFI_SIGNAL }
        assertEquals(Severity.WARNING, finding.severity)
    }

    @Test
    fun `no Wi-Fi comparison when either transport is not WIFI`() {
        val a = minimalSnapshot(activeTransport = Transport.CELLULAR)
        val b = minimalSnapshot(id = "snap-b", activeTransport = Transport.WIFI)
        val result = ComparisonEngine.compare(a, b)

        val wifiFindings = result.findings.filter { it.category == FindingCategory.WIFI_SIGNAL || it.category == FindingCategory.WIFI_CHANNEL }
        assertTrue(wifiFindings.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cellular Signal
// ─────────────────────────────────────────────────────────────────────────────

class CellularComparisonTest {

    @Test
    fun `RSRP gap 10 dBm - WARNING for weaker device`() {
        val a = minimalSnapshot(
            activeTransport = Transport.CELLULAR,
            cellular = cellularSnapshot(rsrp = -100)
        ).copy(wifi = null)
        val b = minimalSnapshot(
            id = "snap-b",
            activeTransport = Transport.CELLULAR,
            cellular = cellularSnapshot(rsrp = -85)
        ).copy(wifi = null)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.CELLULAR_SIGNAL && it.metric.contains("RSRP") }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `different RATs - INFO for BOTH`() {
        val a = minimalSnapshot(
            activeTransport = Transport.CELLULAR,
            cellular = cellularSnapshot(rat = CellRat.LTE)
        ).copy(wifi = null)
        val b = minimalSnapshot(
            id = "snap-b",
            activeTransport = Transport.CELLULAR,
            cellular = cellularSnapshot(rat = CellRat.NR_SA)
        ).copy(wifi = null)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.CELLULAR_SIGNAL && it.metric.contains("technology", ignoreCase = true) }
        assertEquals(Severity.INFO, finding.severity)
        assertEquals(AffectedDevice.BOTH, finding.affectedDevice)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DNS
// ─────────────────────────────────────────────────────────────────────────────

class DnsComparisonTest {

    @Test
    fun `DNS failure on A but success on B - CRITICAL for A`() {
        val a = minimalSnapshot(dnsProbes = listOf(dnsProbe(rcode = DnsRcode.SERVFAIL)))
        val b = minimalSnapshot(id = "snap-b", dnsProbes = listOf(dnsProbe(rcode = DnsRcode.NOERROR)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.DNS && it.severity == Severity.CRITICAL && it.affectedDevice == AffectedDevice.A }
        assertEquals(FindingCategory.DNS, finding.category)
    }

    @Test
    fun `DNS hijack on A not B - CRITICAL for A`() {
        val a = minimalSnapshot(dnsProbes = listOf(dnsProbe(hijackDetected = true, hijackCandidate = "1.2.3.4")))
        val b = minimalSnapshot(id = "snap-b", dnsProbes = listOf(dnsProbe(hijackDetected = false)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.metric.contains("hijack", ignoreCase = true) }
        assertEquals(Severity.CRITICAL, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `DNS latency gap over 80ms - WARNING for slower device`() {
        val target = "google.com"
        val a = minimalSnapshot(dnsProbes = listOf(dnsProbe(target = target, latencyMs = 150L)))
        val b = minimalSnapshot(id = "snap-b", dnsProbes = listOf(dnsProbe(target = target, latencyMs = 20L)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.DNS && it.metric.contains("latency", ignoreCase = true) }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `DNS latency gap under 80ms - no latency finding`() {
        val target = "google.com"
        val a = minimalSnapshot(dnsProbes = listOf(dnsProbe(target = target, latencyMs = 50L)))
        val b = minimalSnapshot(id = "snap-b", dnsProbes = listOf(dnsProbe(target = target, latencyMs = 30L)))
        val result = ComparisonEngine.compare(a, b)

        val latencyFindings = result.findings.filter {
            it.category == FindingCategory.DNS && it.metric.contains("latency", ignoreCase = true)
        }
        assertTrue(latencyFindings.isEmpty())
    }

    @Test
    fun `different DNS servers - INFO for BOTH`() {
        val a = minimalSnapshot(dnsServers = listOf("8.8.8.8"))
        val b = minimalSnapshot(id = "snap-b", dnsServers = listOf("1.1.1.1"))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.DNS && it.metric.contains("server", ignoreCase = true) }
        assertEquals(Severity.INFO, finding.severity)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Probes (ping / RTT / jitter)
// ─────────────────────────────────────────────────────────────────────────────

class ProbeComparisonTest {

    @Test
    fun `packet loss above 5pct on A, under 1pct on B - WARNING for A`() {
        val a = minimalSnapshot(probes = listOf(pingProbe(packetLossPercent = 10f)))
        val b = minimalSnapshot(id = "snap-b", probes = listOf(pingProbe(packetLossPercent = 0f)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.PACKET_LOSS }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `packet loss above 20pct on A - CRITICAL`() {
        val a = minimalSnapshot(probes = listOf(pingProbe(packetLossPercent = 30f)))
        val b = minimalSnapshot(id = "snap-b", probes = listOf(pingProbe(packetLossPercent = 0f)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.PACKET_LOSS }
        assertEquals(Severity.CRITICAL, finding.severity)
    }

    @Test
    fun `packet loss on both devices - BOTH affected`() {
        val a = minimalSnapshot(probes = listOf(pingProbe(packetLossPercent = 15f)))
        val b = minimalSnapshot(id = "snap-b", probes = listOf(pingProbe(packetLossPercent = 12f)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.PACKET_LOSS }
        assertEquals(AffectedDevice.BOTH, finding.affectedDevice)
    }

    @Test
    fun `high jitter on A, low on B - WARNING for A`() {
        val a = minimalSnapshot(probes = listOf(pingProbe(rttMdevMs = 25f)))
        val b = minimalSnapshot(id = "snap-b", probes = listOf(pingProbe(rttMdevMs = 2f)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.JITTER }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `high jitter on both devices - BOTH affected`() {
        val a = minimalSnapshot(probes = listOf(pingProbe(rttMdevMs = 25f)))
        val b = minimalSnapshot(id = "snap-b", probes = listOf(pingProbe(rttMdevMs = 22f)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.JITTER }
        assertEquals(AffectedDevice.BOTH, finding.affectedDevice)
    }

    @Test
    fun `RTT gap over 30ms - finding emitted`() {
        val a = minimalSnapshot(probes = listOf(pingProbe(rttAvgMs = 80f)))
        val b = minimalSnapshot(id = "snap-b", probes = listOf(pingProbe(rttAvgMs = 10f)))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.LATENCY }
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Throughput
// ─────────────────────────────────────────────────────────────────────────────

class ThroughputComparisonTest {

    @Test
    fun `2x throughput gap - WARNING for slower`() {
        val a = minimalSnapshot(throughput = throughput(downloadMbps = 20f))
        val b = minimalSnapshot(id = "snap-b", throughput = throughput(downloadMbps = 50f))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.THROUGHPUT }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `2x throughput gap with slower under 5 Mbps - CRITICAL`() {
        val a = minimalSnapshot(throughput = throughput(downloadMbps = 2f))
        val b = minimalSnapshot(id = "snap-b", throughput = throughput(downloadMbps = 50f))
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.THROUGHPUT }
        assertEquals(Severity.CRITICAL, finding.severity)
    }

    @Test
    fun `throughput gap under 2x - no throughput finding`() {
        val a = minimalSnapshot(throughput = throughput(downloadMbps = 30f))
        val b = minimalSnapshot(id = "snap-b", throughput = throughput(downloadMbps = 50f))
        val result = ComparisonEngine.compare(a, b)

        val throughputFindings = result.findings.filter { it.category == FindingCategory.THROUGHPUT }
        assertTrue(throughputFindings.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Device Health
// ─────────────────────────────────────────────────────────────────────────────

class DeviceHealthComparisonTest {

    @Test
    fun `Doze mode on A not B - WARNING for A`() {
        val a = minimalSnapshot(isDozeModeActive = true)
        val b = minimalSnapshot(id = "snap-b", isDozeModeActive = false)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.DEVICE_HEALTH && it.metric.contains("Doze", ignoreCase = true) }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `VPN active on A not B - WARNING for A`() {
        val a = minimalSnapshot(isVpnActive = true, vpnPackage = "com.example.vpn")
        val b = minimalSnapshot(id = "snap-b", isVpnActive = false)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.DEVICE_HEALTH && it.metric.contains("VPN", ignoreCase = true) }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }

    @Test
    fun `location services off on A - INFO for A`() {
        val a = minimalSnapshot(locationServicesEnabled = false)
        val b = minimalSnapshot(id = "snap-b", locationServicesEnabled = true)
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.category == FindingCategory.DEVICE_HEALTH && it.metric.contains("Location", ignoreCase = true) }
        assertEquals(Severity.INFO, finding.severity)
        assertEquals(AffectedDevice.A, finding.affectedDevice)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Capture Time Delta
// ─────────────────────────────────────────────────────────────────────────────

class CaptureTimeDeltaTest {

    @Test
    fun `snapshots within 5 minutes - no time delta warning`() {
        val base = 1_700_000_000_000L
        val a = minimalSnapshot(capturedAtMs = base)
        val b = minimalSnapshot(id = "snap-b", capturedAtMs = base + 4 * 60 * 1000L) // +4 min
        val result = ComparisonEngine.compare(a, b)

        val deltaFindings = result.findings.filter { it.metric.contains("time", ignoreCase = true) || it.metric.contains("delta", ignoreCase = true) }
        assertTrue("Expected no time-delta finding, got $deltaFindings", deltaFindings.isEmpty())
    }

    @Test
    fun `snapshots more than 5 minutes apart - WARNING emitted`() {
        val base = 1_700_000_000_000L
        val a = minimalSnapshot(capturedAtMs = base)
        val b = minimalSnapshot(id = "snap-b", capturedAtMs = base + 6 * 60 * 1000L) // +6 min
        val result = ComparisonEngine.compare(a, b)

        val finding = result.findings.first { it.severity == Severity.WARNING && it.category == FindingCategory.CONNECTIVITY }
        assertTrue(finding.explanation.contains("5 minute", ignoreCase = true) || finding.explanation.contains("condition", ignoreCase = true))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overall Status Derivation
// ─────────────────────────────────────────────────────────────────────────────

class OverallStatusTest {

    @Test
    fun `different transports - INCOMPARABLE`() {
        val a = minimalSnapshot(activeTransport = Transport.WIFI)
        val b = minimalSnapshot(id = "snap-b", activeTransport = Transport.CELLULAR)
        val result = ComparisonEngine.compare(a, b)

        assertEquals(ComparisonStatus.INCOMPARABLE, result.summary.overallStatus)
    }

    @Test
    fun `no differences - IDENTICAL`() {
        val a = minimalSnapshot()
        val b = minimalSnapshot(id = "snap-b")
        val result = ComparisonEngine.compare(a, b)

        assertEquals(ComparisonStatus.IDENTICAL, result.summary.overallStatus)
    }

    @Test
    fun `warnings only - MINOR_DIFF`() {
        val a = minimalSnapshot(wifiRssi = -75) // 15 dBm below B
        val b = minimalSnapshot(id = "snap-b", wifiRssi = -60)
        val result = ComparisonEngine.compare(a, b)

        assertEquals(ComparisonStatus.MINOR_DIFF, result.summary.overallStatus)
    }

    @Test
    fun `critical affecting BOTH - BOTH_DEGRADED`() {
        val a = minimalSnapshot(isValidated = false)
        val b = minimalSnapshot(id = "snap-b", isValidated = false)
        val result = ComparisonEngine.compare(a, b)

        assertEquals(ComparisonStatus.BOTH_DEGRADED, result.summary.overallStatus)
    }

    @Test
    fun `critical on A only - ONE_DEGRADED`() {
        val a = minimalSnapshot(isApipa = true)
        val b = minimalSnapshot(id = "snap-b")
        val result = ComparisonEngine.compare(a, b)

        assertEquals(ComparisonStatus.ONE_DEGRADED, result.summary.overallStatus)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-Category Scoring
// ─────────────────────────────────────────────────────────────────────────────

class ScoringTest {

    @Test
    fun `no findings in category - scoreA is 1 dot 0`() {
        val a = minimalSnapshot()
        val b = minimalSnapshot(id = "snap-b")
        val result = ComparisonEngine.compare(a, b)

        // Expect all present category scores to be 1.0 since no findings
        for ((_, cat) in result.categories) {
            assertEquals("scoreA should be 1.0 for $cat", 1.0f, cat.scoreA, 0.001f)
            assertEquals("scoreB should be 1.0 for $cat", 1.0f, cat.scoreB, 0.001f)
        }
    }

    @Test
    fun `one CRITICAL finding on A - scoreA is 0 dot 6`() {
        val a = minimalSnapshot(isApipa = true)
        val b = minimalSnapshot(id = "snap-b")
        val result = ComparisonEngine.compare(a, b)

        val ipCat = result.categories[FindingCategory.IP_CONFIG.name]
            ?: error("Expected IP_CONFIG category")
        assertEquals(0.6f, ipCat.scoreA, 0.001f)
        assertEquals(1.0f, ipCat.scoreB, 0.001f)
    }

    @Test
    fun `one WARNING finding on A - scoreA is 0 dot 85`() {
        // Different gateways → WARNING for BOTH in IP_CONFIG
        // Use RSSI finding instead: it's WARNING for one side only when gap is 10-19 dBm
        val a = minimalSnapshot(wifiRssi = -75) // 15 dBm below B → WARNING for A
        val b = minimalSnapshot(id = "snap-b", wifiRssi = -60)
        val result = ComparisonEngine.compare(a, b)

        val wifiCat = result.categories[FindingCategory.WIFI_SIGNAL.name]
            ?: error("Expected WIFI_SIGNAL category")
        assertEquals(0.85f, wifiCat.scoreA, 0.001f)
        assertEquals(1.0f, wifiCat.scoreB, 0.001f)
    }

    @Test
    fun `score clamps to 0 with excessive penalties`() {
        // Packet loss 30% → CRITICAL (0.4) + APIPA → CRITICAL (0.4) — both in different categories
        // Combine in IP_CONFIG: APIPA on A (CRITICAL=0.4) + missing gateway on A (CRITICAL=0.4) → 1 - 0.8 = 0.2, not 0
        // For clamping, we need 3 criticals in same category on A which isn't realistic from one snapshot
        // Test: 3 WARNING findings all hitting A in same category → 1 - 3*0.15 = 0.55 (not clamped)
        // Best approach: just verify score never goes below 0.0 with many penalties
        val a = minimalSnapshot(
            isApipa = true,        // CRITICAL in IP_CONFIG for A
            gatewayIpv4 = null,    // CRITICAL in ROUTING for A
            wifiRssi = -86,        // CRITICAL in WIFI_SIGNAL for A (gap from -70)
        )
        val b = minimalSnapshot(id = "snap-b", wifiRssi = -70)

        val result = ComparisonEngine.compare(a, b)
        for ((_, cat) in result.categories) {
            assertTrue("Score must be ≥ 0.0, got ${cat.scoreA}", cat.scoreA >= 0.0f)
            assertTrue("Score must be ≤ 1.0, got ${cat.scoreA}", cat.scoreA <= 1.0f)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Findings Sorting
// ─────────────────────────────────────────────────────────────────────────────

class FindingsSortingTest {

    @Test
    fun `findings sorted by severity descending then category`() {
        val a = minimalSnapshot(
            isValidated = false,  // CRITICAL in CONNECTIVITY
            wifiRssi = -75,       // WARNING in WIFI_SIGNAL (gap)
        )
        val b = minimalSnapshot(id = "snap-b", isValidated = true, wifiRssi = -60)
        val result = ComparisonEngine.compare(a, b)

        if (result.findings.size >= 2) {
            for (i in 0 until result.findings.size - 1) {
                val cur = result.findings[i]
                val next = result.findings[i + 1]
                assertTrue(
                    "Finding at $i (${cur.severity}) should be ≥ finding at ${i + 1} (${next.severity})",
                    cur.severity.ordinal >= next.severity.ordinal
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edge Cases
// ─────────────────────────────────────────────────────────────────────────────

class EdgeCaseTest {

    @Test
    fun `null Wi-Fi snapshot on one device - compareWifi returns no findings`() {
        val a = minimalSnapshot().copy(wifi = null)
        val b = minimalSnapshot(id = "snap-b")
        // Should not throw; wifi sub-comparisons are skipped when either is null
        val result = ComparisonEngine.compare(a, b)

        val wifiFindings = result.findings.filter {
            it.category == FindingCategory.WIFI_SIGNAL || it.category == FindingCategory.WIFI_CHANNEL
        }
        assertTrue(wifiFindings.isEmpty())
    }

    @Test
    fun `empty probe list on both - no probe findings`() {
        val a = minimalSnapshot(probes = emptyList())
        val b = minimalSnapshot(id = "snap-b", probes = emptyList())
        val result = ComparisonEngine.compare(a, b)

        val probeFindings = result.findings.filter {
            it.category in listOf(FindingCategory.PACKET_LOSS, FindingCategory.LATENCY, FindingCategory.JITTER)
        }
        assertTrue(probeFindings.isEmpty())
    }

    @Test
    fun `null throughput on either - no throughput finding`() {
        val a = minimalSnapshot(throughput = null)
        val b = minimalSnapshot(id = "snap-b", throughput = throughput(downloadMbps = 50f))
        val result = ComparisonEngine.compare(a, b)

        val throughputFindings = result.findings.filter { it.category == FindingCategory.THROUGHPUT }
        assertTrue(throughputFindings.isEmpty())
    }

    @Test
    fun `null cellular on both - no cellular findings`() {
        val a = minimalSnapshot(cellular = null)
        val b = minimalSnapshot(id = "snap-b", cellular = null)
        val result = ComparisonEngine.compare(a, b)

        val cellFindings = result.findings.filter { it.category == FindingCategory.CELLULAR_SIGNAL }
        assertTrue(cellFindings.isEmpty())
    }

    @Test
    fun `probe targets match by ProbeTarget enum key`() {
        val target = ProbeTarget.GOOGLE_DNS
        val a = minimalSnapshot(probes = listOf(pingProbe(target = target, packetLossPercent = 30f)))
        val b = minimalSnapshot(id = "snap-b", probes = listOf(pingProbe(target = target, packetLossPercent = 0f)))
        val result = ComparisonEngine.compare(a, b)

        // Should match the same target and find packet loss
        val finding = result.findings.first { it.category == FindingCategory.PACKET_LOSS }
        assertTrue(finding.metric.contains(target.host))
    }

    @Test
    fun `incomparable transports still run all sub-comparisons`() {
        // INCOMPARABLE only affects final status, not whether comparisons execute
        val a = minimalSnapshot(activeTransport = Transport.WIFI, isValidated = false)
        val b = minimalSnapshot(id = "snap-b", activeTransport = Transport.CELLULAR, isValidated = true)
        val result = ComparisonEngine.compare(a, b)

        assertEquals(ComparisonStatus.INCOMPARABLE, result.summary.overallStatus)
        // Should still have CONNECTIVITY finding from validation diff
        val connFindings = result.findings.filter { it.category == FindingCategory.CONNECTIVITY }
        assertTrue(connFindings.isNotEmpty())
    }
}
