package dev.charly.paranoid.apps.netdiag.collect

import android.annotation.SuppressLint
import android.content.Context
import android.net.DnsResolver
import android.net.LinkProperties
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import dev.charly.paranoid.apps.netdiag.data.DnsProbeResult
import dev.charly.paranoid.apps.netdiag.data.DnsRcode
import dev.charly.paranoid.apps.netdiag.data.DnsSnapshot
import dev.charly.paranoid.apps.netdiag.data.Measured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class DnsCollector(private val context: Context) {

    private val probeHostnames = listOf("google.com", "cloudflare.com", "amazon.com")

    suspend fun collect(linkProperties: LinkProperties?): DnsSnapshot = coroutineScope {
        val dnsServers = linkProperties?.dnsServers
            ?.mapNotNull { it.hostAddress }
            ?: emptyList()

        val isPrivateDns: Boolean
        val privateDnsServer: String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && linkProperties != null) {
            isPrivateDns = linkProperties.isPrivateDnsActive
            privateDnsServer = linkProperties.privateDnsServerName
        } else {
            isPrivateDns = false
            privateDnsServer = null
        }

        val privateDnsMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
                ?: "unknown"
        } else {
            "unsupported"
        }

        val protocol = when {
            isPrivateDns -> "DoT"
            else -> "UDP"
        }

        val dohDeferred = probeHostnames.associateWith { hostname ->
            async { dohQuery(hostname) }
        }

        val systemProbeDeferred = dnsServers.flatMap { server ->
            probeHostnames.map { hostname ->
                async { probeSystemDns(hostname, server, protocol) }
            }
        }

        val dohResults = dohDeferred.mapValues { (_, deferred) -> deferred.await() }
        val systemProbes = systemProbeDeferred.map { it.await() }

        val probes = systemProbes.map { probe ->
            val doh = dohResults[probe.target]
            if (doh != null && probe.rcode == DnsRcode.NOERROR && probe.resolvedAddresses.isNotEmpty()) {
                val dohAddresses = doh.resolvedAddresses.toSet()
                val systemAddresses = probe.resolvedAddresses.toSet()
                if (dohAddresses.isNotEmpty() && systemAddresses.intersect(dohAddresses).isEmpty()) {
                    probe.copy(
                        hijackDetected = true,
                        hijackCandidate = systemAddresses.first(),
                    )
                } else {
                    probe
                }
            } else {
                probe
            }
        }

        val dohProbes = dohResults.values.filterNotNull()

        DnsSnapshot(
            servers = dnsServers,
            isPrivateDnsActive = isPrivateDns,
            privateDnsMode = privateDnsMode,
            privateDnsServer = privateDnsServer,
            probes = probes + dohProbes,
        )
    }

    private suspend fun probeSystemDns(
        hostname: String,
        server: String,
        protocol: String,
    ): DnsProbeResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            probeWithDnsResolver(hostname, server, protocol)
        } else {
            probeFallback(hostname, server, protocol)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("NewApi")
    private suspend fun probeWithDnsResolver(
        hostname: String,
        server: String,
        protocol: String,
    ): DnsProbeResult {
        val resolver = DnsResolver.getInstance()
        val executor = Executors.newSingleThreadExecutor()

        val startMs = System.currentTimeMillis()
        val result = withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine<Pair<DnsRcode, List<String>>> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }

                resolver.query(
                    null,
                    hostname,
                    DnsResolver.FLAG_NO_RETRY,
                    executor,
                    signal,
                    object : DnsResolver.Callback<List<InetAddress>> {
                        override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                            val code = mapRcode(rcode)
                            val addresses = answer.mapNotNull { it.hostAddress }
                            if (cont.isActive) cont.resume(Pair(code, addresses))
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            if (cont.isActive) cont.resume(Pair(DnsRcode.SERVFAIL, emptyList()))
                        }
                    },
                )
            }
        }

        val elapsed = System.currentTimeMillis() - startMs
        return if (result != null) {
            DnsProbeResult(
                target = hostname,
                server = server,
                protocol = protocol,
                rcode = result.first,
                latencyMs = Measured(value = elapsed, confidence = 0.9f, source = "DnsResolver"),
                resolvedAddresses = result.second,
                ttlSeconds = null,
                hijackDetected = false,
                hijackCandidate = null,
            )
        } else {
            DnsProbeResult(
                target = hostname,
                server = server,
                protocol = protocol,
                rcode = DnsRcode.TIMEOUT,
                latencyMs = Measured(value = elapsed, confidence = 0.5f, source = "DnsResolver"),
                resolvedAddresses = emptyList(),
                ttlSeconds = null,
                hijackDetected = false,
                hijackCandidate = null,
            )
        }
    }

    private suspend fun probeFallback(
        hostname: String,
        server: String,
        protocol: String,
    ): DnsProbeResult {
        val startMs = System.currentTimeMillis()
        return withTimeoutOrNull(5_000L) {
            withContext(Dispatchers.IO) {
                try {
                    val addresses = InetAddress.getAllByName(hostname)
                        .mapNotNull { it.hostAddress }
                    val elapsed = System.currentTimeMillis() - startMs
                    DnsProbeResult(
                        target = hostname,
                        server = server,
                        protocol = protocol,
                        rcode = DnsRcode.UNKNOWN,
                        latencyMs = Measured(value = elapsed, confidence = 0.5f, source = "InetAddress"),
                        resolvedAddresses = addresses,
                        ttlSeconds = null,
                        hijackDetected = false,
                        hijackCandidate = null,
                    )
                } catch (_: Exception) {
                    val elapsed = System.currentTimeMillis() - startMs
                    DnsProbeResult(
                        target = hostname,
                        server = server,
                        protocol = protocol,
                        rcode = DnsRcode.SERVFAIL,
                        latencyMs = Measured(value = elapsed, confidence = 0.3f, source = "InetAddress"),
                        resolvedAddresses = emptyList(),
                        ttlSeconds = null,
                        hijackDetected = false,
                        hijackCandidate = null,
                    )
                }
            }
        } ?: run {
            val elapsed = System.currentTimeMillis() - startMs
            DnsProbeResult(
                target = hostname,
                server = server,
                protocol = protocol,
                rcode = DnsRcode.TIMEOUT,
                latencyMs = Measured(value = elapsed, confidence = 0.5f, source = "InetAddress"),
                resolvedAddresses = emptyList(),
                ttlSeconds = null,
                hijackDetected = false,
                hijackCandidate = null,
            )
        }
    }

    private suspend fun dohQuery(hostname: String): DnsProbeResult? = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val url = URL("https://cloudflare-dns.com/dns-query?name=$hostname&type=A")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/dns-json")
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000

            try {
                val code = conn.responseCode
                if (code != 200) {
                    val elapsed = System.currentTimeMillis() - startMs
                    return@withContext DnsProbeResult(
                        target = hostname,
                        server = "cloudflare-dns.com",
                        protocol = "DoH",
                        rcode = DnsRcode.SERVFAIL,
                        latencyMs = Measured(value = elapsed, confidence = 0.8f, source = "DoH"),
                        resolvedAddresses = emptyList(),
                        ttlSeconds = null,
                        hijackDetected = false,
                        hijackCandidate = null,
                    )
                }

                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val elapsed = System.currentTimeMillis() - startMs
                val json = JSONObject(body)

                val status = json.optInt("Status", -1)
                val rcode = mapRcode(status)

                val answers = json.optJSONArray("Answer")
                val addresses = mutableListOf<String>()
                var ttl: Int? = null
                if (answers != null) {
                    for (i in 0 until answers.length()) {
                        val entry = answers.getJSONObject(i)
                        if (entry.optInt("type") == 1) {
                            addresses.add(entry.getString("data"))
                            if (ttl == null) ttl = entry.optInt("TTL", -1).takeIf { it >= 0 }
                        }
                    }
                }

                DnsProbeResult(
                    target = hostname,
                    server = "cloudflare-dns.com",
                    protocol = "DoH",
                    rcode = rcode,
                    latencyMs = Measured(value = elapsed, confidence = 0.9f, source = "DoH"),
                    resolvedAddresses = addresses,
                    ttlSeconds = ttl,
                    hijackDetected = false,
                    hijackCandidate = null,
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            DnsProbeResult(
                target = hostname,
                server = "cloudflare-dns.com",
                protocol = "DoH",
                rcode = DnsRcode.TIMEOUT,
                latencyMs = Measured(value = elapsed, confidence = 0.3f, source = "DoH"),
                resolvedAddresses = emptyList(),
                ttlSeconds = null,
                hijackDetected = false,
                hijackCandidate = null,
            )
        }
    }

    private fun mapRcode(rcode: Int): DnsRcode = when (rcode) {
        0 -> DnsRcode.NOERROR
        2 -> DnsRcode.SERVFAIL
        3 -> DnsRcode.NXDOMAIN
        5 -> DnsRcode.REFUSED
        else -> DnsRcode.UNKNOWN
    }
}
