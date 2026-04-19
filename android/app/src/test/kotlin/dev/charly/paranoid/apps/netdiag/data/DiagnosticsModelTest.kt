package dev.charly.paranoid.apps.netdiag.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun minimalSnapshot() = DiagnosticsSnapshot(
        id = "snap-1",
        deviceLabel = "Device A",
        deviceModel = "Pixel 7",
        androidVersion = 34,
        capturedAtMs = 1_700_000_000_000L,
        sessionId = "session-1",
        activeTransport = Transport.WIFI,
        isValidated = true,
        isCaptivePortal = false,
        captivePortalUrl = null,
        isVpnActive = false,
        isMetered = false,
        isDozed = false,
        ipConfig = IpConfig(
            interfaceName = "wlan0",
            ipv4Addresses = listOf("192.168.1.10"),
            ipv6Addresses = emptyList(),
            gatewayIpv4 = "192.168.1.1",
            gatewayIpv6 = null,
            mtu = Measured(1500),
            nat64Prefix = null,
            httpProxyHost = null,
            httpProxyPort = null,
            httpProxyPacUrl = null,
            isPrivateDnsActive = false,
            privateDnsServerName = null,
            isRfc1918 = true,
            isApipa = false,
            hasIpv6 = false,
            subnetMaskBits = 24,
        ),
        wifi = WifiSnapshot(
            ssid = "HomeNetwork",
            bssid = "aa:bb:cc:dd:ee:ff",
            standard = WifiStandard.AX,
            frequencyMhz = 5180,
            channel = 36,
            band = "5 GHz",
            channelWidth = "80 MHz",
            rssi = Measured(-55, confidence = 1.0f, source = "WifiInfo"),
            rssiCategory = SignalCategory.EXCELLENT,
            txLinkSpeedMbps = Measured(400),
            rxLinkSpeedMbps = Measured(400),
            maxTxLinkSpeedMbps = Measured(600),
            maxRxLinkSpeedMbps = Measured(600),
            txLinkUtilization = null,
            wpa3Used = false,
            environment = null,
            bssLoad = null,
            rttDistanceMm = null,
        ),
        cellular = null,
        dns = DnsSnapshot(
            servers = listOf("8.8.8.8"),
            isPrivateDnsActive = false,
            privateDnsMode = "off",
            privateDnsServer = null,
            probes = emptyList(),
        ),
        probes = listOf(
            PingProbeResult(
                target = ProbeTarget.GOOGLE_DNS,
                resolvedIp = "8.8.8.8",
                packetsSent = 5,
                packetsReceived = 5,
                packetLossPercent = 0f,
                rttMinMs = 5f,
                rttAvgMs = 7f,
                rttMaxMs = 10f,
                rttMdevMs = 1f,
                status = PingStatus.REACHABLE,
                hopCount = null,
                tracerouteHops = null,
            )
        ),
        throughput = null,
        trafficDelta = null,
        deviceHealth = DeviceHealth(
            batteryPercent = 80,
            isCharging = false,
            isDozeModeActive = false,
            isDataSaverActive = false,
            isAirplaneModeOn = false,
            isWifiEnabled = true,
            isCellularEnabled = true,
            memoryAvailableMb = 2048L,
            cpuLoadPercent = null,
            activeVpnPackage = null,
            locationServicesEnabled = true,
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

    @Test
    fun `DiagnosticsSnapshot round-trip serialization preserves all fields`() {
        val original = minimalSnapshot()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<DiagnosticsSnapshot>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `DiagnosticsSnapshot round-trip with null optional fields`() {
        val original = minimalSnapshot().copy(
            wifi = null,
            cellular = null,
            throughput = null,
            trafficDelta = null,
            connectivityDiagnostics = null,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<DiagnosticsSnapshot>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Measured wrapper preserves confidence and source`() {
        val original = Measured(value = -55, confidence = 0.9f, source = "WifiInfo", timestampMs = 12345L)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Measured<Int>>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Measured wrapper with null value round-trips`() {
        val original = Measured<Int>(value = null, confidence = 0.0f, source = null)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Measured<Int>>(encoded)
        assertEquals(original, decoded)
    }
}

class SeverityOrderingTest {

    @Test
    fun `Severity ordinals place CRITICAL after WARNING after INFO`() {
        assertTrue(Severity.INFO.ordinal < Severity.WARNING.ordinal)
        assertTrue(Severity.WARNING.ordinal < Severity.CRITICAL.ordinal)
    }

    @Test
    fun `Severity sorted descending gives CRITICAL first`() {
        val findings = listOf(Severity.INFO, Severity.CRITICAL, Severity.WARNING)
        val sorted = findings.sortedDescending()
        assertEquals(listOf(Severity.CRITICAL, Severity.WARNING, Severity.INFO), sorted)
    }
}

class SignalCategoryOrderingTest {

    @Test
    fun `SignalCategory ordinals degrade from EXCELLENT to UNUSABLE`() {
        val expected = listOf(
            SignalCategory.EXCELLENT,
            SignalCategory.GOOD,
            SignalCategory.FAIR,
            SignalCategory.POOR,
            SignalCategory.UNUSABLE,
        )
        val sorted = SignalCategory.entries.sortedBy { it.ordinal }
        assertEquals(expected, sorted)
    }
}
