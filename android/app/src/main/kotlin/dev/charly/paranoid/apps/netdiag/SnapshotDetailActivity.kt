package dev.charly.paranoid.apps.netdiag

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netdiag.data.CellularSnapshot
import dev.charly.paranoid.apps.netdiag.data.ConnectivityDiagnosticsData
import dev.charly.paranoid.apps.netdiag.data.DeviceHealth
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshot
import dev.charly.paranoid.apps.netdiag.data.DnsSnapshot
import dev.charly.paranoid.apps.netdiag.data.IpConfig
import dev.charly.paranoid.apps.netdiag.data.PingProbeResult
import dev.charly.paranoid.apps.netdiag.data.WifiSnapshot
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnapshotDetailActivity : AppCompatActivity() {

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snapshot_detail)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        val snapshotId = intent.getStringExtra("snapshot_id") ?: run {
            showError("No snapshot ID provided")
            return
        }

        val db = ParanoidDatabase.getInstance(this)

        lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) { db.snapshotDao().getById(snapshotId) }
            if (entity == null) {
                showError("Snapshot not found")
                return@launch
            }

            val snapshot = try {
                json.decodeFromString<DiagnosticsSnapshot>(entity.snapshotJson)
            } catch (e: Exception) {
                showError("Failed to load snapshot data")
                return@launch
            }

            findViewById<View>(R.id.loading_text).visibility = View.GONE
            populateHeader(snapshot)
            populateSections(snapshot)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateHeader(s: DiagnosticsSnapshot) {
        val header = findViewById<LinearLayout>(R.id.device_header)
        header.visibility = View.VISIBLE
        findViewById<TextView>(R.id.header_device).text = s.deviceLabel
        findViewById<TextView>(R.id.header_info).text =
            "${s.deviceModel} · Android ${s.androidVersion} · ${dateFmt.format(Date(s.capturedAtMs))}"
    }

    private fun populateSections(s: DiagnosticsSnapshot) {
        val container = findViewById<LinearLayout>(R.id.sections_container)
        container.visibility = View.VISIBLE

        addSection(container, "Network State", buildNetworkState(s))
        addSection(container, "IP Config", buildIpConfig(s.ipConfig))
        addSection(container, "Wi-Fi", buildWifi(s.wifi))
        addSection(container, "Cellular", buildCellular(s.cellular))
        addSection(container, "DNS", buildDns(s.dns))
        addSection(container, "Probes", buildProbes(s.probes))
        addSection(container, "Device Health", buildDeviceHealth(s.deviceHealth))
        if (s.connectivityDiagnostics != null) {
            addSection(container, "Connectivity Diagnostics", buildConnDiag(s.connectivityDiagnostics))
        }
    }

    private fun addSection(container: LinearLayout, title: String, rows: List<Pair<String, String>>) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dpToPxInt(8) }
        }

        // Header row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = resources.getDrawable(R.drawable.app_item_bg, theme)
            setPadding(dpToPxInt(16), dpToPxInt(12), dpToPxInt(16), dpToPxInt(12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val indicator = TextView(this).apply {
            text = "▶"
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dpToPxInt(8) }
        }
        headerRow.addView(indicator)

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleView)

        val countView = TextView(this).apply {
            text = "${rows.size}"
            setTextColor(Color.parseColor("#555555"))
            textSize = 12f
        }
        headerRow.addView(countView)

        section.addView(headerRow)

        // Detail rows
        val detailContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dpToPxInt(16), dpToPxInt(8), dpToPxInt(16), dpToPxInt(8))
        }

        for ((label, value) in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dpToPxInt(4) }
            }

            val labelView = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#888888"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
            }
            row.addView(labelView)

            val valueView = TextView(this).apply {
                text = value
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            }
            row.addView(valueView)

            detailContainer.addView(row)
        }

        section.addView(detailContainer)

        // Toggle
        headerRow.setOnClickListener {
            val visible = detailContainer.visibility == View.VISIBLE
            detailContainer.visibility = if (visible) View.GONE else View.VISIBLE
            indicator.text = if (visible) "▶" else "▼"
        }

        container.addView(section)
    }

    private fun buildNetworkState(s: DiagnosticsSnapshot): List<Pair<String, String>> = listOf(
        "Transport" to s.activeTransport.name,
        "Internet validated" to yn(s.isValidated),
        "Captive portal" to yn(s.isCaptivePortal),
        "Captive portal URL" to (s.captivePortalUrl ?: "—"),
        "VPN active" to yn(s.isVpnActive),
        "Metered" to yn(s.isMetered),
        "Doze mode" to yn(s.isDozed),
    )

    @SuppressLint("SetTextI18n")
    private fun buildIpConfig(ip: IpConfig): List<Pair<String, String>> = buildList {
        add("Interface" to (ip.interfaceName ?: "—"))
        add("IPv4" to ip.ipv4Addresses.ifEmpty { listOf("—") }.joinToString(", "))
        add("IPv6" to ip.ipv6Addresses.ifEmpty { listOf("—") }.joinToString(", "))
        add("Gateway (v4)" to (ip.gatewayIpv4 ?: "—"))
        add("Gateway (v6)" to (ip.gatewayIpv6 ?: "—"))
        add("MTU" to (ip.mtu.value?.toString() ?: "—"))
        add("Private DNS" to if (ip.isPrivateDnsActive) (ip.privateDnsServerName ?: "Active") else "Off")
        add("RFC 1918" to yn(ip.isRfc1918))
        add("APIPA" to yn(ip.isApipa))
        add("Subnet mask" to (ip.subnetMaskBits?.let { "/$it" } ?: "—"))
        if (ip.httpProxyHost != null) add("Proxy" to "${ip.httpProxyHost}:${ip.httpProxyPort}")
    }

    @SuppressLint("SetTextI18n")
    private fun buildWifi(wifi: WifiSnapshot?): List<Pair<String, String>> {
        if (wifi == null) return listOf("Status" to "Not applicable")
        return buildList {
            add("SSID" to (wifi.ssid ?: "—"))
            add("BSSID" to (wifi.bssid ?: "—"))
            add("RSSI" to "${wifi.rssi.value ?: "—"} dBm (${wifi.rssiCategory.name.lowercase().replaceFirstChar { it.uppercase() }})")
            add("Channel" to "Ch ${wifi.channel} (${wifi.frequencyMhz} MHz)")
            add("Band" to wifi.band)
            add("Width" to wifi.channelWidth)
            add("Standard" to wifi.standard.name)
            add("TX link speed" to "${wifi.txLinkSpeedMbps.value ?: "—"} Mbps")
            add("RX link speed" to "${wifi.rxLinkSpeedMbps.value ?: "—"} Mbps")
            add("Max TX" to "${wifi.maxTxLinkSpeedMbps.value ?: "—"} Mbps")
            add("Max RX" to "${wifi.maxRxLinkSpeedMbps.value ?: "—"} Mbps")
            add("WPA3" to yn(wifi.wpa3Used))
            wifi.environment?.let { env ->
                add("APs visible" to env.totalApsVisible.toString())
                add("APs same channel" to env.apsOnSameChannel.toString())
                add("APs adjacent" to env.apsOnAdjacentChannels.toString())
                env.channelUtilizationPercent?.let { add("Channel utilization" to "%.0f%%".format(it)) }
            }
            wifi.bssLoad?.let { bss ->
                add("BSS stations" to bss.stationCount.toString())
                add("BSS utilization" to "${bss.channelUtilization}/255")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildCellular(cell: CellularSnapshot?): List<Pair<String, String>> {
        if (cell == null) return listOf("Status" to "Not applicable")
        return buildList {
            add("RAT" to cell.rat.name)
            add("Display type" to (cell.displayType ?: "—"))
            add("Operator" to (cell.operatorName ?: "—"))
            add("MCC/MNC" to "${cell.mcc ?: "—"}/${cell.mnc ?: "—"}")
            add("Signal" to cell.signalCategory.name.lowercase().replaceFirstChar { it.uppercase() })
            cell.rsrp?.value?.let { add("RSRP" to "$it dBm") }
            cell.rsrq?.value?.let { add("RSRQ" to "$it dB") }
            cell.rssnr?.value?.let { add("SINR" to "$it dB") }
            cell.rssi?.value?.let { add("RSSI" to "$it dBm") }
            cell.ssRsrp?.value?.let { add("SS-RSRP" to "$it dBm") }
            cell.ssRsrq?.value?.let { add("SS-RSRQ" to "$it dB") }
            cell.ssSinr?.value?.let { add("SS-SINR" to "$it dB") }
            add("Roaming" to yn(cell.isRoaming))
            add("Carrier Aggregation" to yn(cell.isCarrierAggregation))
            cell.numComponentCarriers?.let { add("Component carriers" to it.toString()) }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildDns(dns: DnsSnapshot): List<Pair<String, String>> = buildList {
        add("Servers" to dns.servers.ifEmpty { listOf("—") }.joinToString(", "))
        add("Private DNS" to if (dns.isPrivateDnsActive) "${dns.privateDnsMode} (${dns.privateDnsServer ?: "auto"})" else "Off")
        for (probe in dns.probes) {
            add("${probe.target} → ${probe.server}" to "${probe.rcode.name} ${probe.latencyMs.value ?: "—"}ms")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildProbes(probes: List<PingProbeResult>): List<Pair<String, String>> {
        if (probes.isEmpty()) return listOf("Status" to "No probes")
        return probes.flatMap { p ->
            buildList {
                add(p.target.name to p.status.name)
                add("  Loss" to "${p.packetLossPercent.toInt()}% (${p.packetsReceived}/${p.packetsSent})")
                if (p.rttAvgMs != null) {
                    add("  RTT" to "%.1f / %.1f / %.1f ms".format(p.rttMinMs, p.rttAvgMs, p.rttMaxMs))
                }
            }
        }
    }

    private fun buildDeviceHealth(h: DeviceHealth): List<Pair<String, String>> = buildList {
        add("Battery" to "${h.batteryPercent}%${if (h.isCharging) " (charging)" else ""}")
        add("Doze mode" to yn(h.isDozeModeActive))
        add("Data Saver" to yn(h.isDataSaverActive))
        add("Airplane mode" to yn(h.isAirplaneModeOn))
        add("Wi-Fi enabled" to yn(h.isWifiEnabled))
        add("Cellular enabled" to yn(h.isCellularEnabled))
        h.memoryAvailableMb?.let { add("Available memory" to "${it} MB") }
        h.activeVpnPackage?.let { add("VPN package" to it) }
        add("Location services" to yn(h.locationServicesEnabled))
    }

    private fun buildConnDiag(cd: ConnectivityDiagnosticsData): List<Pair<String, String>> = buildList {
        cd.networkProbesAttempted?.let { add("Probes attempted" to it.toString()) }
        cd.networkProbesSucceeded?.let { add("Probes succeeded" to it.toString()) }
        cd.dnsConsecutiveTimeouts?.let { add("DNS timeouts" to it.toString()) }
        cd.tcpMetricsPacketsSent?.let { add("TCP packets sent" to it.toString()) }
        cd.tcpMetricsRetransmissions?.let { add("TCP retransmissions" to it.toString()) }
        cd.tcpMetricsLatencyMs?.let { add("TCP latency" to "${it}ms") }
        add("Data stall" to yn(cd.dataStallSuspected))
        cd.dataStallDetectionMethod?.let { add("Detection method" to it) }
    }

    private fun showError(message: String) {
        findViewById<View>(R.id.loading_text).visibility = View.GONE
        findViewById<TextView>(R.id.error_text).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun yn(b: Boolean) = if (b) "Yes" else "No"

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun dpToPxInt(dp: Int): Int = dpToPx(dp.toFloat()).toInt()
}
