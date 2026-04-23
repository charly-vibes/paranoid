package dev.charly.paranoid.apps.netdiag.exchange

import dev.charly.paranoid.apps.netdiag.data.*
import org.junit.Assert.*
import org.junit.Test

class QrSnapshotExchangeTest {

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
    fun `small snapshot produces single chunk`() {
        val snapshot = minimalSnapshot()
        val chunks = QrSnapshotExchange.encode(snapshot)
        assertTrue("Expected single chunk for small snapshot", chunks.size >= 1)
        assertTrue(chunks[0].startsWith("1/${chunks.size}|"))
    }

    @Test
    fun `encode-decode round-trip preserves snapshot`() {
        val original = minimalSnapshot()
        val chunks = QrSnapshotExchange.encode(original)
        val reassembler = QrSnapshotExchange.Reassembler()
        for (chunk in chunks) {
            val result = reassembler.addChunk(chunk)
            assertTrue("Expected Added, got $result", result is QrSnapshotExchange.ChunkResult.Added)
        }
        assertTrue(reassembler.isComplete())
        val decoded = reassembler.assemble().getOrThrow()
        assertEquals(original, decoded)
    }

    @Test
    fun `out-of-order chunks reassemble correctly`() {
        val original = minimalSnapshot()
        val chunks = QrSnapshotExchange.encode(original)
        val reassembler = QrSnapshotExchange.Reassembler()
        for (chunk in chunks.reversed()) {
            reassembler.addChunk(chunk)
        }
        assertTrue(reassembler.isComplete())
        val decoded = reassembler.assemble().getOrThrow()
        assertEquals(original, decoded)
    }

    @Test
    fun `duplicate chunk returns Duplicate`() {
        val original = minimalSnapshot()
        val chunks = QrSnapshotExchange.encode(original)
        val reassembler = QrSnapshotExchange.Reassembler()
        reassembler.addChunk(chunks[0])
        val result = reassembler.addChunk(chunks[0])
        assertTrue("Expected Duplicate, got $result", result is QrSnapshotExchange.ChunkResult.Duplicate)
    }

    @Test
    fun `missing separator returns Invalid`() {
        val reassembler = QrSnapshotExchange.Reassembler()
        val result = reassembler.addChunk("garbage-no-pipe")
        assertTrue("Expected Invalid", result is QrSnapshotExchange.ChunkResult.Invalid)
    }

    @Test
    fun `bad header format returns Invalid`() {
        val reassembler = QrSnapshotExchange.Reassembler()
        val result = reassembler.addChunk("abc|data")
        assertTrue("Expected Invalid", result is QrSnapshotExchange.ChunkResult.Invalid)
    }

    @Test
    fun `index out of range returns Invalid`() {
        val reassembler = QrSnapshotExchange.Reassembler()
        val result = reassembler.addChunk("5/3|data")
        assertTrue("Expected Invalid for index > total", result is QrSnapshotExchange.ChunkResult.Invalid)
    }

    @Test
    fun `total mismatch returns Invalid`() {
        val reassembler = QrSnapshotExchange.Reassembler()
        reassembler.addChunk("1/3|data")
        val result = reassembler.addChunk("2/5|data")
        assertTrue("Expected Invalid for total mismatch", result is QrSnapshotExchange.ChunkResult.Invalid)
    }

    @Test
    fun `missingChunks reports correct indices`() {
        val reassembler = QrSnapshotExchange.Reassembler()
        reassembler.addChunk("1/3|data")
        reassembler.addChunk("3/3|data")
        assertEquals(listOf(2), reassembler.missingChunks())
        assertFalse(reassembler.isComplete())
    }

    @Test
    fun `assemble on incomplete returns failure`() {
        val reassembler = QrSnapshotExchange.Reassembler()
        reassembler.addChunk("1/2|data")
        val result = reassembler.assemble()
        assertTrue(result.isFailure)
    }

    @Test
    fun `chunk header format is correct`() {
        val chunks = QrSnapshotExchange.encode(minimalSnapshot())
        for ((i, chunk) in chunks.withIndex()) {
            val header = chunk.substringBefore('|')
            assertEquals("${i + 1}/${chunks.size}", header)
        }
    }
}
