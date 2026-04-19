package dev.charly.paranoid.apps.netdiag.data

/**
 * Pure-function comparison engine: diffs two DiagnosticsSnapshots.
 *
 * No Android dependencies. All sub-comparisons run regardless of
 * overall status; INCOMPARABLE only affects final status derivation.
 */
object ComparisonEngine {

    private const val CAPTURE_DELTA_WARN_MS = 5 * 60 * 1000L  // 5 minutes

    fun compare(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): DiagnosticsComparison {
        // Self-comparison: identical snapshots → IDENTICAL with no findings
        if (a.id == b.id) {
            return emptyComparison(a, b)
        }

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

        val captureTimeDeltaMs = kotlin.math.abs(a.capturedAtMs - b.capturedAtMs)
        if (captureTimeDeltaMs > CAPTURE_DELTA_WARN_MS) {
            findings += finding(
                category = FindingCategory.CONNECTIVITY,
                severity = Severity.WARNING,
                metric = "Capture time delta",
                valueA = formatDeltaMs(a.capturedAtMs),
                valueB = formatDeltaMs(b.capturedAtMs),
                delta = "${captureTimeDeltaMs / 60_000} min",
                affectedDevice = AffectedDevice.NEITHER,
                explanation = "Snapshots were captured more than 5 minutes apart. " +
                        "Network conditions may have changed between captures.",
                recommendation = "Capture both snapshots within the same session for a meaningful comparison."
            )
        }

        val sortedFindings = findings.sortedWith(
            compareByDescending<ComparisonFinding> { it.severity.ordinal }
                .thenBy { it.category.ordinal }
        )

        val criticals = sortedFindings.count { it.severity == Severity.CRITICAL }
        val warnings  = sortedFindings.count { it.severity == Severity.WARNING }
        val infos     = sortedFindings.count { it.severity == Severity.INFO }

        val overallStatus = when {
            a.activeTransport != b.activeTransport                                            -> ComparisonStatus.INCOMPARABLE
            criticals == 0 && warnings == 0                                                   -> ComparisonStatus.IDENTICAL
            criticals == 0                                                                    -> ComparisonStatus.MINOR_DIFF
            sortedFindings.any { it.affectedDevice == AffectedDevice.BOTH && it.severity == Severity.CRITICAL } -> ComparisonStatus.BOTH_DEGRADED
            else                                                                              -> ComparisonStatus.ONE_DEGRADED
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
            captureTimeDeltaMs = captureTimeDeltaMs,
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
                FindingCategory.CONNECTIVITY, Severity.CRITICAL,
                "Internet validated", "Validated", "NOT validated",
                affectedDevice = AffectedDevice.B,
                explanation = "Phone B cannot reach the internet probe endpoint.",
                recommendation = "Check Phone B's network configuration or try toggling Wi-Fi."
            )
        } else if (!a.isValidated && !b.isValidated) {
            findings += finding(
                FindingCategory.CONNECTIVITY, Severity.CRITICAL,
                "Internet validated", "NOT validated", "NOT validated",
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
                FindingCategory.CONNECTIVITY, Severity.CRITICAL,
                "Captive portal",
                if (a.isCaptivePortal) "DETECTED" else "None",
                if (b.isCaptivePortal) "DETECTED" else "None",
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

        if (ipA.gatewayIpv4 == null) findings += finding(
            FindingCategory.ROUTING, Severity.CRITICAL, "Default gateway",
            "MISSING", ipB.gatewayIpv4 ?: "Missing",
            affectedDevice = AffectedDevice.A,
            explanation = "Phone A has no default gateway — routing is broken.",
            recommendation = "Toggle airplane mode or reconnect to the network."
        )

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
        if (mtuA < 1400) findings += finding(
            FindingCategory.IP_CONFIG, Severity.WARNING, "MTU (Phone A)",
            "${mtuA}B", "${mtuB}B",
            affectedDevice = AffectedDevice.A,
            explanation = "MTU of ${mtuA}B is below 1400. Large packets may be fragmented, increasing latency.",
            recommendation = "Check if a VPN is reducing MTU. A VPN overhead is typically 60-80 bytes."
        )

        if (ipA.gatewayIpv4 != null && ipB.gatewayIpv4 != null && ipA.gatewayIpv4 != ipB.gatewayIpv4) {
            findings += finding(
                FindingCategory.IP_CONFIG, Severity.WARNING, "Gateway IP",
                ipA.gatewayIpv4, ipB.gatewayIpv4,
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Both phones see different default gateways. They may be on different SSIDs, subnets, or VLANs.",
                recommendation = "Ensure both phones are connected to the same SSID/band. Check if one is on a guest network."
            )
        }

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

        val rssiA = wA.rssi.value ?: return findings
        val rssiB = wB.rssi.value ?: return findings
        val rssiDelta = rssiA - rssiB

        if (kotlin.math.abs(rssiDelta) >= 10) {
            val worse = if (rssiDelta < 0) AffectedDevice.A else AffectedDevice.B
            val worseVal = if (rssiDelta < 0) rssiA else rssiB
            findings += finding(
                FindingCategory.WIFI_SIGNAL,
                if (worseVal < -80) Severity.CRITICAL else Severity.WARNING,
                "RSSI (signal strength)", "${rssiA} dBm", "${rssiB} dBm",
                delta = "${rssiDelta} dBm",
                affectedDevice = worse,
                explanation = "The weaker phone is ${kotlin.math.abs(rssiDelta)} dBm below the other. " +
                        "At ≥10 dBm difference, the weaker device will get significantly lower throughput and higher retransmissions.",
                recommendation = "Move the weaker phone closer to the router or eliminate physical obstructions."
            )
        }

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

        val envA = wA.environment
        val envB = wB.environment
        if (envA != null && envB != null && kotlin.math.abs(envA.apsOnSameChannel - envB.apsOnSameChannel) > 3) {
            findings += finding(
                FindingCategory.WIFI_CHANNEL, Severity.INFO, "APs on same channel",
                "${envA.apsOnSameChannel}", "${envB.apsOnSameChannel}",
                affectedDevice = AffectedDevice.BOTH,
                explanation = "Scan results differ significantly between phones. This could indicate scan timing differences or position effects.",
                recommendation = "Compare channel utilization over multiple scans."
            )
        }

        val bssA = wA.bssLoad
        if (bssA != null) {
            val util = bssA.channelUtilization * 100 / 255
            if (util > 75) findings += finding(
                FindingCategory.WIFI_CHANNEL, Severity.WARNING, "AP channel utilization",
                "$util%", wB.bssLoad?.let { "${it.channelUtilization * 100 / 255}%" } ?: "N/A",
                affectedDevice = AffectedDevice.BOTH,
                explanation = "The connected AP reports $util% channel utilization. Congestion on the AP itself is limiting both devices.",
                recommendation = "Switch to a less congested channel or AP, or reduce the number of connected clients."
            )
        }

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

        val probesA = a.dns.probes.associateBy { it.target }
        val probesB = b.dns.probes.associateBy { it.target }
        val allTargets = (a.dns.probes.map { it.target } + b.dns.probes.map { it.target }).toSet()

        for (target in allTargets) {
            val pA = probesA[target]
            val pB = probesB[target]

            if (pA?.rcode != DnsRcode.NOERROR && pB?.rcode == DnsRcode.NOERROR) {
                findings += finding(
                    FindingCategory.DNS, Severity.CRITICAL, "DNS resolution ($target)",
                    pA?.rcode?.name ?: "No probe", "NOERROR",
                    affectedDevice = AffectedDevice.A,
                    explanation = "DNS resolution for $target fails on Phone A but succeeds on Phone B. " +
                            "Likely DNS server unreachable or misconfigured on Phone A.",
                    recommendation = "Try switching DNS server on Phone A to 8.8.8.8 or 1.1.1.1."
                )
            } else if (pA?.rcode == DnsRcode.NOERROR && pB?.rcode != DnsRcode.NOERROR) {
                findings += finding(
                    FindingCategory.DNS, Severity.CRITICAL, "DNS resolution ($target)",
                    "NOERROR", pB?.rcode?.name ?: "No probe",
                    affectedDevice = AffectedDevice.B,
                    explanation = "DNS resolution for $target fails on Phone B but succeeds on Phone A.",
                    recommendation = "Try switching DNS server on Phone B to 8.8.8.8 or 1.1.1.1."
                )
            }

            if (pA?.hijackDetected == true && pB?.hijackDetected == false) {
                findings += finding(
                    FindingCategory.DNS, Severity.CRITICAL, "DNS hijacking ($target)",
                    "DETECTED (${pA.hijackCandidate})", "Clean",
                    affectedDevice = AffectedDevice.A,
                    explanation = "Phone A's DNS returns a different IP than the authoritative DoH result. " +
                            "This indicates ISP/captive-portal DNS interception.",
                    recommendation = "Enable Private DNS (DoT) on Phone A under Settings → Network → Private DNS."
                )
            } else if (pA?.hijackDetected == false && pB?.hijackDetected == true) {
                findings += finding(
                    FindingCategory.DNS, Severity.CRITICAL, "DNS hijacking ($target)",
                    "Clean", "DETECTED (${pB.hijackCandidate})",
                    affectedDevice = AffectedDevice.B,
                    explanation = "Phone B's DNS returns a different IP than the authoritative DoH result.",
                    recommendation = "Enable Private DNS (DoT) on Phone B under Settings → Network → Private DNS."
                )
            }

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

    // ── Probes (ping/RTT/jitter) ──────────────────────────────

    private fun compareProbes(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot): List<ComparisonFinding> {
        val findings = mutableListOf<ComparisonFinding>()

        val probeMapA = a.probes.associateBy { it.target }
        val probeMapB = b.probes.associateBy { it.target }
        val allTargets = (a.probes.map { it.target } + b.probes.map { it.target }).toSet()

        for (target in allTargets) {
            val pA = probeMapA[target]
            val pB = probeMapB[target]

            val lossA = pA?.packetLossPercent ?: 0f
            val lossB = pB?.packetLossPercent ?: 0f

            if (lossA > 5f && lossB <= 1f) {
                findings += finding(
                    FindingCategory.PACKET_LOSS,
                    if (lossA > 20f) Severity.CRITICAL else Severity.WARNING,
                    "Packet loss → ${target.host}",
                    "${lossA.toInt()}%", "${lossB.toInt()}%", delta = "+${(lossA - lossB).toInt()}%",
                    affectedDevice = AffectedDevice.A,
                    explanation = "Phone A is losing ${lossA.toInt()}% of packets to ${target.host} while Phone B has clean connectivity. " +
                            "Likely a Wi-Fi signal or interference issue on Phone A specifically.",
                    recommendation = "Check RSSI and channel utilization on Phone A."
                )
            } else if (lossB > 5f && lossA <= 1f) {
                findings += finding(
                    FindingCategory.PACKET_LOSS,
                    if (lossB > 20f) Severity.CRITICAL else Severity.WARNING,
                    "Packet loss → ${target.host}",
                    "${lossA.toInt()}%", "${lossB.toInt()}%", delta = "+${(lossB - lossA).toInt()}%",
                    affectedDevice = AffectedDevice.B,
                    explanation = "Phone B is losing ${lossB.toInt()}% of packets to ${target.host} while Phone A has clean connectivity.",
                    recommendation = "Check RSSI and channel utilization on Phone B."
                )
            } else if (lossA > 5f && lossB > 5f) {
                findings += finding(
                    FindingCategory.PACKET_LOSS,
                    if (maxOf(lossA, lossB) > 20f) Severity.CRITICAL else Severity.WARNING,
                    "Packet loss → ${target.host}",
                    "${lossA.toInt()}%", "${lossB.toInt()}%",
                    affectedDevice = AffectedDevice.BOTH,
                    explanation = "Both phones experience packet loss to ${target.host}. " +
                            "The problem is likely in shared infrastructure: router, ISP link, or WAN.",
                    recommendation = "Reboot the router and modem. If loss is only to internet (not gateway), suspect ISP or WAN congestion."
                )
            }

            val rttA = pA?.rttAvgMs
            val rttB = pB?.rttAvgMs
            if (rttA != null && rttB != null && kotlin.math.abs(rttA - rttB) > 30f) {
                val worse = if (rttA > rttB) AffectedDevice.A else AffectedDevice.B
                findings += finding(
                    FindingCategory.LATENCY,
                    if (maxOf(rttA, rttB) > 200f) Severity.WARNING else Severity.INFO,
                    "RTT → ${target.host}",
                    "%.1f ms".format(rttA), "%.1f ms".format(rttB),
                    delta = "%.1f ms".format(rttA - rttB),
                    affectedDevice = worse,
                    explanation = "One phone has noticeably higher latency to ${target.host}. " +
                            "May indicate it is on a different AP, band, or the RF environment differs.",
                    recommendation = "Check band steering and AP placement. Prefer 5 GHz or 6 GHz."
                )
            }

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
                if (minOf(dlA, dlB) < 5f) Severity.CRITICAL else Severity.WARNING,
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
        } else if (!a.deviceHealth.isDozeModeActive && b.deviceHealth.isDozeModeActive) {
            findings += finding(
                FindingCategory.DEVICE_HEALTH, Severity.WARNING, "Doze mode",
                "Inactive", "ACTIVE", affectedDevice = AffectedDevice.B,
                explanation = "Phone B is in Doze mode. Network access is restricted, scan results and connectivity checks may be stale.",
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
        } else if (b.deviceHealth.isDataSaverActive) {
            findings += finding(
                FindingCategory.DEVICE_HEALTH, Severity.WARNING, "Data Saver",
                "OFF", "ON",
                affectedDevice = AffectedDevice.B,
                explanation = "Data Saver on Phone B restricts background network access and may skew diagnostics results.",
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
        } else if (!a.isVpnActive && b.isVpnActive) {
            findings += finding(
                FindingCategory.DEVICE_HEALTH, Severity.WARNING, "VPN active",
                "NO", "YES (${b.deviceHealth.activeVpnPackage ?: "unknown"})",
                affectedDevice = AffectedDevice.B,
                explanation = "Phone B is running a VPN. All metrics will reflect VPN-tunneled performance, not the raw network.",
                recommendation = "Disable the VPN on Phone B before comparing network metrics."
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

    private fun emptyComparison(a: DiagnosticsSnapshot, b: DiagnosticsSnapshot) = DiagnosticsComparison(
        snapshotA = a,
        snapshotB = b,
        comparedAtMs = System.currentTimeMillis(),
        captureTimeDeltaMs = 0L,
        summary = ComparisonSummary(
            overallStatus = ComparisonStatus.IDENTICAL,
            criticalCount = 0,
            warningCount = 0,
            infoCount = 0,
            matchedTransport = true,
            likelyCause = null,
        ),
        findings = emptyList(),
        categories = emptyMap(),
    )

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

    private fun formatDeltaMs(capturedAtMs: Long): String {
        val minutes = capturedAtMs / 60_000
        return "$minutes min"
    }
}
