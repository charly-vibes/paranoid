package dev.charly.paranoid.apps.netdiag.collect

import android.content.Context
import android.net.ConnectivityDiagnosticsManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.PersistableBundle
import dev.charly.paranoid.apps.netdiag.data.ConnectivityDiagnosticsData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class ConnDiagCollector(private val context: Context) {

    suspend fun collect(network: Network?): ConnectivityDiagnosticsData? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (network == null) return null
        return collectApi30(network)
    }

    @Suppress("NewApi")
    private suspend fun collectApi30(network: Network): ConnectivityDiagnosticsData? {
        val cdm = context.getSystemService(ConnectivityDiagnosticsManager::class.java)
            ?: return null
        val executor = Executors.newSingleThreadExecutor()

        return withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback() {
                    override fun onConnectivityReportAvailable(report: ConnectivityDiagnosticsManager.ConnectivityReport) {
                        if (report.network != network) return
                        val result = parseReport(report)
                        try { cdm.unregisterConnectivityDiagnosticsCallback(this) } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onDataStallSuspected(report: ConnectivityDiagnosticsManager.DataStallReport) {
                        if (report.network != network) return
                        val result = parseDataStall(report)
                        try { cdm.unregisterConnectivityDiagnosticsCallback(this) } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(result)
                    }
                }

                val request = NetworkRequest.Builder().build()
                try {
                    cdm.registerConnectivityDiagnosticsCallback(request, executor, callback)
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    try { cdm.unregisterConnectivityDiagnosticsCallback(callback) } catch (_: Exception) {}
                }
            }
        }
    }

    @Suppress("NewApi")
    private fun parseReport(report: ConnectivityDiagnosticsManager.ConnectivityReport): ConnectivityDiagnosticsData {
        val bundle = report.additionalInfo
        val attempted = bundle.getIntOrNull(
            ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_PROBES_ATTEMPTED_BITMASK
        )
        val succeeded = bundle.getIntOrNull(
            ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_PROBES_SUCCEEDED_BITMASK
        )
        return ConnectivityDiagnosticsData(
            networkProbesAttempted = attempted?.let { Integer.bitCount(it) },
            networkProbesSucceeded = succeeded?.let { Integer.bitCount(it) },
            dnsConsecutiveTimeouts = null,
            tcpMetricsPacketsSent = null,
            tcpMetricsRetransmissions = null,
            tcpMetricsLatencyMs = null,
            dataStallSuspected = false,
            dataStallDetectionMethod = null,
        )
    }

    @Suppress("NewApi")
    private fun parseDataStall(report: ConnectivityDiagnosticsManager.DataStallReport): ConnectivityDiagnosticsData {
        val bundle = report.stallDetails
        val method = report.detectionMethod
        val methodName = when (method) {
            ConnectivityDiagnosticsManager.DataStallReport.DETECTION_METHOD_DNS_EVENTS -> "DNS_EVENTS"
            ConnectivityDiagnosticsManager.DataStallReport.DETECTION_METHOD_TCP_METRICS -> "TCP_METRICS"
            else -> "UNKNOWN($method)"
        }
        val dnsTimeouts = bundle.getIntOrNull(
            ConnectivityDiagnosticsManager.DataStallReport.KEY_DNS_CONSECUTIVE_TIMEOUTS
        )
        val tcpFailRate = bundle.getIntOrNull(
            ConnectivityDiagnosticsManager.DataStallReport.KEY_TCP_PACKET_FAIL_RATE
        )
        val tcpCollectionPeriod = bundle.getLongOrNull(
            ConnectivityDiagnosticsManager.DataStallReport.KEY_TCP_METRICS_COLLECTION_PERIOD_MILLIS
        )
        return ConnectivityDiagnosticsData(
            networkProbesAttempted = null,
            networkProbesSucceeded = null,
            dnsConsecutiveTimeouts = dnsTimeouts,
            tcpMetricsPacketsSent = null,
            tcpMetricsRetransmissions = tcpFailRate?.toLong(),
            tcpMetricsLatencyMs = tcpCollectionPeriod,
            dataStallSuspected = true,
            dataStallDetectionMethod = methodName,
        )
    }

    private fun PersistableBundle.getIntOrNull(key: String): Int? =
        if (containsKey(key)) getInt(key) else null

    private fun PersistableBundle.getLongOrNull(key: String): Long? =
        if (containsKey(key)) getLong(key) else null
}
