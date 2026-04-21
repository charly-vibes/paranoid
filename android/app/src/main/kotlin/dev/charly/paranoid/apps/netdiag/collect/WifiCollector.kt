package dev.charly.paranoid.apps.netdiag.collect

import android.annotation.SuppressLint
import android.content.Context
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import dev.charly.paranoid.apps.netdiag.data.BssLoad
import dev.charly.paranoid.apps.netdiag.data.Measured
import dev.charly.paranoid.apps.netdiag.data.NeighborAp
import dev.charly.paranoid.apps.netdiag.data.SignalCategory
import dev.charly.paranoid.apps.netdiag.data.WifiEnvironment
import dev.charly.paranoid.apps.netdiag.data.WifiSnapshot
import dev.charly.paranoid.apps.netdiag.data.WifiStandard
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WifiCollector(private val context: Context) {

    private val wifiManager: WifiManager
        get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    suspend fun collect(networkCapabilities: NetworkCapabilities?): WifiSnapshot? {
        val wifiInfo = obtainWifiInfo(networkCapabilities) ?: return null
        if (wifiInfo.networkId == -1 && wifiInfo.bssid == null) return null

        val now = System.currentTimeMillis()
        val frequency = wifiInfo.frequency
        val channel = frequencyToChannel(frequency)
        val band = frequencyToBand(frequency)
        val rssi = wifiInfo.rssi
        val standard = extractStandard(wifiInfo)

        val scanResults = try { wifiManager.scanResults.orEmpty() } catch (_: Exception) { emptyList() }
        val connectedScan = scanResults.find { it.BSSID == wifiInfo.bssid }
        val channelWidth = connectedScan?.let { channelWidthString(it) } ?: "unknown"

        val txSpeed = wifiInfo.linkSpeed.takeIf { it > 0 }
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo.rxLinkSpeedMbps.takeIf { it > 0 }
        } else null
        val txSpeedApi29 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo.txLinkSpeedMbps.takeIf { it > 0 }
        } else null
        val effectiveTx = txSpeedApi29 ?: txSpeed

        val maxTx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiInfo.maxSupportedTxLinkSpeedMbps.takeIf { it > 0 }
        } else null
        val maxRx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiInfo.maxSupportedRxLinkSpeedMbps.takeIf { it > 0 }
        } else null

        val txUtilization = if (effectiveTx != null && maxTx != null && maxTx > 0) {
            effectiveTx.toFloat() / maxTx.toFloat()
        } else null

        val wpa3 = detectWpa3(wifiInfo, scanResults)
        val environment = buildEnvironment(wifiInfo.bssid, channel, frequency, scanResults)
        val bssLoad = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            connectedScan?.let { parseBssLoad(it) }
        } else null

        return WifiSnapshot(
            ssid = wifiInfo.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" },
            bssid = wifiInfo.bssid,
            standard = standard,
            frequencyMhz = frequency,
            channel = channel,
            band = band,
            channelWidth = channelWidth,
            rssi = Measured(
                value = rssi,
                confidence = 1.0f,
                source = "WifiInfo",
                timestampMs = now,
            ),
            rssiCategory = rssiToCategory(rssi),
            txLinkSpeedMbps = Measured(
                value = effectiveTx,
                confidence = if (effectiveTx != null) 1.0f else 0.0f,
                source = "WifiInfo",
                timestampMs = now,
            ),
            rxLinkSpeedMbps = Measured(
                value = rxSpeed,
                confidence = if (rxSpeed != null) 1.0f else 0.0f,
                source = "WifiInfo",
                timestampMs = now,
            ),
            maxTxLinkSpeedMbps = Measured(
                value = maxTx,
                confidence = if (maxTx != null) 1.0f else 0.0f,
                source = "WifiInfo",
                timestampMs = now,
            ),
            maxRxLinkSpeedMbps = Measured(
                value = maxRx,
                confidence = if (maxRx != null) 1.0f else 0.0f,
                source = "WifiInfo",
                timestampMs = now,
            ),
            txLinkUtilization = txUtilization,
            wpa3Used = wpa3,
            environment = environment,
            bssLoad = bssLoad,
            rttDistanceMm = null,
        )
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun obtainWifiInfo(networkCapabilities: NetworkCapabilities?): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val info = networkCapabilities?.transportInfo as? WifiInfo
            if (info != null) return info
        }
        return wifiManager.connectionInfo
    }

    private fun extractStandard(wifiInfo: WifiInfo): WifiStandard {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return WifiStandard.UNKNOWN
        return when (wifiInfo.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
            ScanResult.WIFI_STANDARD_11N -> WifiStandard.N
            ScanResult.WIFI_STANDARD_11AC -> WifiStandard.AC
            ScanResult.WIFI_STANDARD_11AX -> WifiStandard.AX
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when (wifiInfo.wifiStandard) {
                        ScanResult.WIFI_STANDARD_11BE -> WifiStandard.BE
                        else -> WifiStandard.UNKNOWN
                    }
                } else WifiStandard.UNKNOWN
            }
        }
    }

    private fun rssiToCategory(rssi: Int): SignalCategory = when {
        rssi >= -50 -> SignalCategory.EXCELLENT
        rssi >= -60 -> SignalCategory.GOOD
        rssi >= -70 -> SignalCategory.FAIR
        rssi >= -80 -> SignalCategory.POOR
        else -> SignalCategory.UNUSABLE
    }

    @SuppressLint("MissingPermission")
    private fun detectWpa3(wifiInfo: WifiInfo, scanResults: List<ScanResult>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (wifiInfo.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_SAE,
                WifiInfo.SECURITY_TYPE_OWE,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> true
                else -> false
            }
        }
        val connected = scanResults.find { it.BSSID == wifiInfo.bssid }
        val caps = connected?.capabilities ?: return false
        return caps.contains("[SAE]") || caps.contains("[OWE]") ||
            caps.contains("SAE") || caps.contains("OWE")
    }

    @SuppressLint("MissingPermission")
    private fun buildEnvironment(
        connectedBssid: String?,
        connectedChannel: Int,
        connectedFrequency: Int,
        scanResults: List<ScanResult>,
    ): WifiEnvironment? {
        if (scanResults.isEmpty()) return null

        val neighbors = scanResults.map { sr ->
            val ch = frequencyToChannel(sr.frequency)
            NeighborAp(
                ssid = sr.SSID?.takeIf { it.isNotBlank() },
                bssid = sr.BSSID,
                frequencyMhz = sr.frequency,
                channel = ch,
                rssi = sr.level,
                standard = scanResultStandard(sr),
                channelWidth = channelWidthString(sr),
                isSameBss = connectedBssid != null && sr.BSSID != connectedBssid &&
                    sr.SSID == scanResults.find { it.BSSID == connectedBssid }?.SSID,
            )
        }

        val otherAps = neighbors.filter { it.bssid != connectedBssid }
        val is24Ghz = connectedFrequency in 2400..2500
        val adjacentRange = if (is24Ghz) 2 else 1
        val sameChannel = otherAps.count { it.channel == connectedChannel }
        val adjacentChannels = otherAps.count {
            it.channel != connectedChannel &&
                kotlin.math.abs(it.channel - connectedChannel) <= adjacentRange
        }
        val strongestNeighbor = otherAps.maxByOrNull { it.rssi }?.rssi

        val channelUtil = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scanResults.find { it.BSSID == connectedBssid }?.let { parseBssLoad(it) }
                ?.channelUtilization?.let { it.toFloat() / 255f * 100f }
        } else null

        val suggestion = suggestNonOverlappingChannel(connectedFrequency, neighbors)

        return WifiEnvironment(
            totalApsVisible = scanResults.size,
            apsOnSameChannel = sameChannel,
            apsOnAdjacentChannels = adjacentChannels,
            channelUtilizationPercent = channelUtil,
            strongestNeighborRssi = strongestNeighbor,
            nonOverlappingChannelSuggestion = suggestion,
            scanResults = neighbors,
        )
    }

    private fun scanResultStandard(sr: ScanResult): WifiStandard {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return WifiStandard.UNKNOWN
        return when (sr.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
            ScanResult.WIFI_STANDARD_11N -> WifiStandard.N
            ScanResult.WIFI_STANDARD_11AC -> WifiStandard.AC
            ScanResult.WIFI_STANDARD_11AX -> WifiStandard.AX
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when (sr.wifiStandard) {
                        ScanResult.WIFI_STANDARD_11BE -> WifiStandard.BE
                        else -> WifiStandard.UNKNOWN
                    }
                } else WifiStandard.UNKNOWN
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun channelWidthString(sr: ScanResult): String = when (sr.channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
        ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
        ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when (sr.channelWidth) {
                    ScanResult.CHANNEL_WIDTH_320MHZ -> "320 MHz"
                    else -> "unknown"
                }
            } else "unknown"
        }
    }

    @Suppress("NewApi")
    private fun parseBssLoad(sr: ScanResult): BssLoad? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val ies = sr.informationElements ?: return null
        for (ie in ies) {
            if (ie.id != 11) continue
            val bytes = ie.bytes ?: continue
            if (bytes.remaining() < 5) continue
            val buf = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val stationCount = buf.short.toInt() and 0xFFFF
            val channelUtilization = buf.get().toInt() and 0xFF
            val availableCapacity = buf.short.toInt() and 0xFFFF
            return BssLoad(
                stationCount = stationCount,
                channelUtilization = channelUtilization,
                availableAdmissionCapacity = availableCapacity,
            )
        }
        return null
    }

    private fun suggestNonOverlappingChannel(
        currentFrequency: Int,
        neighbors: List<NeighborAp>,
    ): Int? {
        val nonOverlapping24 = listOf(1, 6, 11)
        val is24Ghz = currentFrequency in 2400..2500
        if (!is24Ghz) return null
        val channelCounts = neighbors
            .filter { it.frequencyMhz in 2400..2500 }
            .groupingBy { it.channel }
            .eachCount()
        return nonOverlapping24.minByOrNull { channelCounts[it] ?: 0 }
    }

    companion object {
        fun frequencyToChannel(frequencyMhz: Int): Int = when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2412) / 5 + 1
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5955..7115 -> (frequencyMhz - 5955) / 5
            frequencyMhz == 5935 -> 2
            else -> 0
        }

        fun frequencyToBand(frequencyMhz: Int): String = when {
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            frequencyMhz in 5150..5850 -> "5 GHz"
            frequencyMhz in 5925..7125 -> "6 GHz"
            frequencyMhz in 58000..71000 -> "60 GHz"
            else -> "unknown"
        }
    }
}
