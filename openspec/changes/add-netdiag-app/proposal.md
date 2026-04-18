# Change: Add NetDiag mini-app

## Why
Users troubleshooting Wi-Fi or cellular connectivity have no way to know whether the problem is their device, their network, or their ISP. NetDiag lets two phones on the same network each capture a diagnostics snapshot — Wi-Fi signal, cellular signal, DNS, IP config, active probes (ping/traceroute), throughput, and device health — and then compare the snapshots side-by-side to pinpoint whose phone (or the shared infrastructure) is at fault.

## Non-Goals (v1)
- Cloud sync / remote comparison
- Continuous background monitoring (single on-demand capture sessions)
- Root or Shizuku-enhanced data collection
- Speed-test integration (NDT7/LibreSpeed) — deferred to v1.1; throughput slot is optional and nullable
- Automated remediation (app explains and recommends but does not change settings)
- NFC snapshot exchange — Android Beam was deprecated in API 29 and removed in API 33; deferred to v1.1 if a tag-writing approach proves viable

## What Changes
- Add dependencies: icmp4a (ICMP ping), kotlinx.serialization (snapshot JSON)
- Add manifest permissions: ACCESS_FINE_LOCATION, READ_PHONE_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, NEARBY_WIFI_DEVICES (API 33+), BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN (API 31+), FOREGROUND_SERVICE, POST_NOTIFICATIONS, INTERNET, ACCESS_NETWORK_STATE
- Create data layer: Kotlin data classes matching `DiagnosticsSchema.kt` (snapshot, comparison, session, findings, thresholds)
- Create capture engine: collectors for Wi-Fi (WifiManager + ScanResult), cellular (TelephonyCallback), DNS (DnsResolver API 29+), IP config (LinkProperties), probes (ping via icmp4a, HTTP timing via OkHttp EventListener), device health, and ConnectivityDiagnosticsManager (API 30+)
- Create comparison engine: pure-function differ that produces `DiagnosticsComparison` with severity-ranked findings and per-category scores
- Create exchange layer: Bluetooth, QR code, and manual file export/import for snapshot transfer between devices
- Create UI: NetDiagActivity (capture wizard), SnapshotDetailActivity, ComparisonResultActivity, SessionHistoryActivity
- Register all activities in AndroidManifest.xml
- Note: The permission set is large (12+); the UI mitigates this with sequential runtime requests with rationale dialogs, and Bluetooth permissions are deferred until exchange time

## Impact
- Affected specs: netdiag-data, netdiag-snapshot, netdiag-comparison, netdiag-exchange, netdiag-ui (all new)
- Affected code:
  - `android/app/build.gradle.kts` (new dependencies)
  - `android/app/src/main/AndroidManifest.xml` (permissions + activities)
  - `android/app/src/main/kotlin/dev/charly/paranoid/apps/netdiag/` (all new code)
  - `android/app/src/main/res/layout/` (XML layouts for NetDiag screens)
  - `android/app/src/main/kotlin/dev/charly/paranoid/HubActivity.kt` (add NetDiag entry to app list)
