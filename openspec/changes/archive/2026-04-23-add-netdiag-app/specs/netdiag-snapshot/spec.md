## ADDED Requirements

Depends on: netdiag-data

### Requirement: Snapshot Capture Engine
The system SHALL provide a `SnapshotCaptureEngine` that orchestrates all collectors concurrently via Kotlin coroutines and assembles a complete `DiagnosticsSnapshot` within a target time of 15 seconds.

#### Scenario: Full capture on Wi-Fi
- **WHEN** the device is connected to Wi-Fi and all permissions are granted
- **THEN** the engine produces a snapshot with non-null wifi, dns, probes, ipConfig, and deviceHealth fields

#### Scenario: Full capture on cellular
- **WHEN** the device is on cellular and all permissions are granted
- **THEN** the engine produces a snapshot with non-null cellular, dns, probes, ipConfig, and deviceHealth fields, and wifi is null

#### Scenario: Partial capture on permission denial
- **WHEN** READ_PHONE_STATE is denied but location is granted
- **THEN** the cellular field is null but all other collectors run successfully

#### Scenario: Transport changes during capture
- **WHEN** the active network transport changes mid-capture (e.g., Wi-Fi drops to cellular)
- **THEN** the engine records the transport at capture-start in `activeTransport` and flags the snapshot with a WARNING-level note that conditions changed during collection

### Requirement: Wi-Fi Collector
The system SHALL collect Wi-Fi data from `WifiManager` and `WifiInfo` including: SSID, BSSID, standard (via `getWifiStandard()` API 30+), frequency, channel (derived), band, channel width, RSSI, signal category, TX/RX link speeds (API 29+), max link speeds (API 30+), WPA3 detection. On API 31+, `WifiInfo` SHALL be obtained from `NetworkCapabilities.getTransportInfo()` rather than the deprecated `WifiManager.getConnectionInfo()`. The collector SHALL also enumerate scan results to populate `WifiEnvironment` (AP counts per channel, interference metrics) and parse BSS Load IE (element ID 11, API 30+) for channel utilization.

#### Scenario: Collect connected AP info
- **WHEN** the device is connected to a Wi-Fi network
- **THEN** the WifiSnapshot includes SSID, BSSID, RSSI, frequency, channel, and standard

#### Scenario: Channel environment from scan results
- **WHEN** scan results contain 12 APs with 4 on the same channel
- **THEN** `WifiEnvironment.apsOnSameChannel` is 4 and `totalApsVisible` is 12

#### Scenario: BSS Load parsing on API 30+
- **WHEN** the connected AP advertises BSS Load IE
- **THEN** `BssLoad.stationCount` and `channelUtilization` are populated

#### Scenario: Wi-Fi not connected
- **WHEN** the device is not connected to Wi-Fi
- **THEN** the wifi field of the snapshot is null

#### Scenario: Graceful degradation on API 26-29
- **WHEN** the device is on API 26-29
- **THEN** `standard` is UNKNOWN, max link speeds are null, `getTxLinkSpeedMbps()`/`getRxLinkSpeedMbps()` are null (API 29+ only; legacy `getLinkSpeed()` TX value is still available on all APIs), and `bssLoad` is null; the `Measured` wrapper SHALL have confidence=0.0 for any estimated values

#### Scenario: All collectors fail
- **WHEN** all collectors return null (e.g., airplane mode with all permissions denied)
- **THEN** the engine returns an error rather than an empty snapshot with all-null fields

### Requirement: Cellular Collector
The system SHALL collect cellular data from `TelephonyManager` and `TelephonyCallback` (API 31+) or `PhoneStateListener` (API 26-30) including: RAT, display type, operator name, MCC/MNC, LTE signal (RSRP, RSRQ, RSSNR, CQI, RSSI), NR signal (ssRsrp, ssRsrq, ssSinr), data state, roaming status, and carrier aggregation info.

#### Scenario: LTE signal snapshot
- **WHEN** the device is on LTE with RSRP=-92 dBm
- **THEN** the CellularSnapshot includes rat=LTE, rsrp=Measured(-92, confidence=1.0), and signalCategory=GOOD

#### Scenario: 5G NR detection
- **WHEN** the device is on NR SA
- **THEN** the CellularSnapshot includes rat=NR_SA with ssRsrp, ssRsrq, and ssSinr populated

#### Scenario: Cellular not available
- **WHEN** the device has no SIM or cellular radio
- **THEN** the cellular field of the snapshot is null

### Requirement: DNS Collector
The system SHALL probe DNS using `android.net.DnsResolver` (API 29+) for each configured DNS server and a set of known hostnames. Each probe SHALL record: target hostname, server used, protocol (UDP/DoT/DoH), rcode, latency, resolved addresses, and TTL. The collector SHALL detect DNS hijacking by cross-checking system DNS results against a DoH query to `cloudflare-dns.com`.

#### Scenario: Successful DNS probe
- **WHEN** resolving "google.com" via the primary DNS server succeeds
- **THEN** the DnsProbeResult has rcode=NOERROR, resolved addresses populated, and latencyMs measured

#### Scenario: DNS hijack detection
- **WHEN** the system DNS returns a different IP than the DoH cross-check for a known domain
- **THEN** hijackDetected is true and hijackCandidate contains the system DNS answer

#### Scenario: Private DNS active
- **WHEN** the device has Private DNS enabled in strict mode
- **THEN** `DnsSnapshot.isPrivateDnsActive` is true and `privateDnsMode` is "strict"

#### Scenario: DNS on API 26-28 fallback
- **WHEN** the device is on API 26-28
- **THEN** the collector falls back to `InetAddress.getAllByName()` with rcode=UNKNOWN (no rcode visibility)

### Requirement: IP Config Collector
The system SHALL collect IP configuration from `LinkProperties` including: interface name, IPv4/IPv6 addresses (CIDR), gateway, MTU, NAT64 prefix (API 30+), HTTP proxy, Private DNS status, and derived anomaly flags (isRfc1918, isApipa, hasIpv6, subnetMaskBits).

#### Scenario: APIPA detection
- **WHEN** the device has an IPv4 address in the 169.254.0.0/16 range
- **THEN** isApipa is true (indicates DHCP failure)

#### Scenario: Normal IP config
- **WHEN** the device has a private IPv4 address with a gateway
- **THEN** isRfc1918 is true, isApipa is false, and gatewayIpv4 is populated

### Requirement: Probe Collector
The system SHALL run ICMP ping probes using icmp4a to each `ProbeTarget` (gateway, primary DNS, secondary DNS, 8.8.8.8, 1.1.1.1, connectivitycheck.gstatic.com, cloudflare.com, google.com) with 5 packets per target, all targets concurrently. Each probe SHALL record: packets sent/received, packet loss percent, RTT min/avg/max/mdev, and ping status. The collector SHALL also run an HTTP timing probe via OkHttp `EventListener` to `connectivitycheck.gstatic.com/generate_204` recording DNS, TCP, TLS, TTFB, and total timing. HTTP timing comparison between devices is deferred to v1.1.

#### Scenario: Ping to gateway succeeds
- **WHEN** the gateway is reachable
- **THEN** PingProbeResult has status=REACHABLE, packetLossPercent near 0, and RTT values populated

#### Scenario: Ping to internet target fails
- **WHEN** 8.8.8.8 is unreachable but gateway responds
- **THEN** the gateway probe has status=REACHABLE and the 8.8.8.8 probe has status=TIMEOUT, suggesting a WAN or ISP issue

#### Scenario: HTTP timing breakdown
- **WHEN** an HTTP probe to connectivitycheck.gstatic.com completes
- **THEN** HttpTimingResult includes dnsMs, tcpMs, tlsMs, ttfbMs, totalMs, and statusCode=204

### Requirement: Device Health Collector
The system SHALL collect device health data including: battery percent, charging state, Doze mode, Data Saver, airplane mode, Wi-Fi/cellular enabled state, available memory, CPU load (if accessible), active VPN package, location services state, and network permissions granted.

#### Scenario: Doze mode detection
- **WHEN** the device is in Doze mode
- **THEN** `DeviceHealth.isDozeModeActive` is true

#### Scenario: VPN detection
- **WHEN** a VPN app is active
- **THEN** `DeviceHealth.activeVpnPackage` contains the VPN app's package name

#### Scenario: Permissions audit
- **WHEN** the snapshot is captured
- **THEN** `NetworkPermissions` reflects the actual granted state of each permission

### Requirement: Connectivity Diagnostics Collector
The system SHALL collect data from `ConnectivityDiagnosticsManager` (API 30+) when available, including: network probes attempted/succeeded, DNS consecutive timeouts, TCP metrics (packets sent, retransmissions, latency), and data stall detection.

#### Scenario: Data stall detected
- **WHEN** ConnectivityDiagnosticsManager reports a data stall
- **THEN** `ConnectivityDiagnosticsData.dataStallSuspected` is true with the detection method

#### Scenario: API below 30
- **WHEN** the device is on API 26-29
- **THEN** the connectivityDiagnostics field is null
