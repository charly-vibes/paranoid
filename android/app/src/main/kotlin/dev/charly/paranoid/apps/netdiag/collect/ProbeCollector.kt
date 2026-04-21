package dev.charly.paranoid.apps.netdiag.collect

import com.marsounjan.icmp4a.Icmp
import com.marsounjan.icmp4a.Icmp4a
import dev.charly.paranoid.apps.netdiag.data.HttpTimingResult
import dev.charly.paranoid.apps.netdiag.data.PingProbeResult
import dev.charly.paranoid.apps.netdiag.data.PingStatus
import dev.charly.paranoid.apps.netdiag.data.ProbeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.math.abs

class ProbeCollector {

    suspend fun collect(
        gatewayIp: String?,
        dnsServers: List<String>,
    ): Pair<List<PingProbeResult>, HttpTimingResult?> = coroutineScope {
        val pingDeferred = async { collectPings(gatewayIp, dnsServers) }
        val httpDeferred = async { collectHttp() }
        Pair(pingDeferred.await(), httpDeferred.await())
    }

    private suspend fun collectPings(
        gatewayIp: String?,
        dnsServers: List<String>,
    ): List<PingProbeResult> = coroutineScope {
        val targets = ProbeTarget.entries.mapNotNull { target ->
            val host = resolveHost(target, gatewayIp, dnsServers) ?: return@mapNotNull null
            target to host
        }

        targets.map { (target, host) ->
            async { pingTarget(target, host) }
        }.map { it.await() }
    }

    private fun resolveHost(
        target: ProbeTarget,
        gatewayIp: String?,
        dnsServers: List<String>,
    ): String? = when (target) {
        ProbeTarget.GATEWAY -> gatewayIp
        ProbeTarget.DNS_PRIMARY -> dnsServers.firstOrNull()
        ProbeTarget.DNS_SECONDARY -> dnsServers.getOrNull(1)
        else -> target.host
    }

    private suspend fun pingTarget(target: ProbeTarget, host: String): PingProbeResult {
        val resolvedIp = resolveIp(host)
        val pingHost = resolvedIp ?: host
        val icmp = Icmp4a()

        val statuses = withTimeoutOrNull(10_000L) {
            try {
                icmp.pingInterval(
                    host = pingHost,
                    count = 5,
                    timeoutMillis = 1500L,
                    intervalMillis = 500L,
                ).toList()
            } catch (_: Icmp.Error) {
                null
            }
        }

        if (statuses == null) {
            return PingProbeResult(
                target = target,
                resolvedIp = resolvedIp,
                packetsSent = 5,
                packetsReceived = 0,
                packetLossPercent = 100f,
                rttMinMs = null,
                rttAvgMs = null,
                rttMaxMs = null,
                rttMdevMs = null,
                status = PingStatus.TIMEOUT,
                hopCount = null,
                tracerouteHops = null,
            )
        }

        val last = statuses.lastOrNull()
        val packetsSent = last?.packetsTransmitted ?: statuses.size
        val packetsReceived = last?.packetsReceived ?: 0
        val lossPercent = if (packetsSent > 0) {
            ((packetsSent - packetsReceived).toFloat() / packetsSent) * 100f
        } else 100f

        val rtts = statuses
            .map { it.result }
            .filterIsInstance<Icmp.PingResult.Success>()
            .map { it.ms.toFloat() }
        val rttMin = rtts.minOrNull()
        val rttMax = rtts.maxOrNull()
        val rttAvg = if (rtts.isNotEmpty()) rtts.average().toFloat() else null
        val rttMdev = if (rtts.isNotEmpty() && rttAvg != null) {
            rtts.map { abs(it - rttAvg) }.average().toFloat()
        } else null

        val status = when {
            packetsReceived == 0 && statuses.any {
                it.result is Icmp.PingResult.Failed.Error
            } -> PingStatus.UNREACHABLE
            packetsReceived == 0 -> PingStatus.TIMEOUT
            else -> PingStatus.REACHABLE
        }

        return PingProbeResult(
            target = target,
            resolvedIp = resolvedIp,
            packetsSent = packetsSent,
            packetsReceived = packetsReceived,
            packetLossPercent = lossPercent,
            rttMinMs = rttMin,
            rttAvgMs = rttAvg,
            rttMaxMs = rttMax,
            rttMdevMs = rttMdev,
            status = status,
            hopCount = null,
            tracerouteHops = null,
        )
    }

    private suspend fun resolveIp(host: String): String? = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(host).hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun collectHttp(): HttpTimingResult? = withContext(Dispatchers.IO) {
        val url = "http://connectivitycheck.gstatic.com/generate_204"
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.useCaches = false

            val startMs = System.currentTimeMillis()
            conn.connect()
            val statusCode = conn.responseCode
            val bodyBytes = try {
                conn.inputStream.use { it.readBytes().size.toLong() }
            } catch (_: Exception) {
                null
            }
            val totalMs = System.currentTimeMillis() - startMs

            val protocol = conn.headerFields[null]
                ?.firstOrNull()
                ?.split(" ")
                ?.firstOrNull()
                ?: "HTTP/1.1"

            conn.disconnect()

            HttpTimingResult(
                url = url,
                protocol = protocol,
                statusCode = statusCode,
                dnsMs = null,
                tcpMs = null,
                tlsMs = null,
                ttfbMs = null,
                totalMs = totalMs,
                connectionReused = false,
                bodyBytes = bodyBytes,
                error = null,
            )
        } catch (e: Exception) {
            HttpTimingResult(
                url = url,
                protocol = "HTTP/1.1",
                statusCode = null,
                dnsMs = null,
                tcpMs = null,
                tlsMs = null,
                ttfbMs = null,
                totalMs = null,
                connectionReused = false,
                bodyBytes = null,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }
}
