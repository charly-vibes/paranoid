## Context
NetDiag is a new mini-app in Paranoid. It captures comprehensive network diagnostics snapshots from two Android devices on the same network and compares them to identify which device (or the shared infrastructure) is responsible for connectivity problems. The data model is defined in `DiagnosticsSchema.kt`; the Android API surface is documented in `network-diag.md`.

Constraints from project.md:
- Pure Kotlin + Android Views (no Compose)
- Constructor injection (no Hilt/Dagger)
- Minimal dependencies per mini-app
- API 26+ minimum

## Goals / Non-Goals
- Goals: one-tap diagnostics capture, device-to-device snapshot transfer, automated comparison with severity-ranked findings and actionable recommendations
- Non-Goals (v1): speed-test integration, continuous monitoring, cloud sync, root/Shizuku, automated remediation

## Decisions

### Data Model
- Decision: Use the `DiagnosticsSchema.kt` data classes directly as the domain model. All fields are nullable; `Measured<T>` carries confidence and source metadata.
- Why: The schema is already designed for serialization and comparison. No mapping layer needed.

### Snapshot Capture Architecture
- Decision: A `SnapshotCaptureEngine` orchestrates independent collectors (`WifiCollector`, `CellularCollector`, `DnsCollector`, `IpConfigCollector`, `ProbeCollector`, `DeviceHealthCollector`, `ConnDiagCollector`). Each collector is a suspend function returning its slice of the snapshot. The engine runs all collectors concurrently via `coroutineScope { async {} }` and assembles the final `DiagnosticsSnapshot`.
- Why: Collectors are independent — Wi-Fi scan, DNS resolution, and ping can run in parallel. Async collection minimizes total capture time (~5-15 seconds).
- Alternative: Sequential collection — simpler but would take 30+ seconds as probes alone need multiple seconds.

### Active Probes
- Decision: Use icmp4a for ICMP ping (no NDK, coroutine Flow API). For HTTP timing, use OkHttp `EventListener` against `connectivitycheck.gstatic.com/generate_204`. Traceroute is deferred to v1.1 (requires NDK).
- Why: icmp4a is the lightest ICMP option; OkHttp is already available via Android's bundled HTTP stack. Traceroute adds NDK complexity for marginal v1 value.

### DNS Probing
- Decision: Use `android.net.DnsResolver` (API 29+) for per-server probes with rcode visibility. For API 26-28, fall back to `InetAddress.getAllByName()` (no rcode, just success/fail). DoH cross-check for hijack detection via OkHttp to `cloudflare-dns.com`.
- Why: DnsResolver is the only public API that reports rcodes and targets specific networks.

### Wi-Fi Scanning
- Decision: Passive consumption of system scan results via `SCAN_RESULTS_AVAILABLE_ACTION` broadcast, with one `startScan()` call at capture time (throttled to respect Android's 4-per-2-minute limit). Parse BSS Load IE (element ID 11) from `ScanResult.getInformationElements()` on API 30+.
- Why: Aggressive scanning is throttled; a single triggered scan plus passive results gives the best data within platform limits.

### Comparison Engine
- Decision: Pure Kotlin functions with no Android dependencies (`ComparisonEngine` object from schema). Takes two `DiagnosticsSnapshot` values, returns `DiagnosticsComparison` with findings sorted by severity then category, per-category scores, and a likely-cause hypothesis.
- Why: Pure functions are testable without Android instrumentation. The comparison logic is already designed in `DiagnosticsSchema.kt`.

### Snapshot Exchange
- Decision: Primary exchange via Bluetooth RFCOMM (both devices run the app). Fallback: QR code (encode snapshot JSON as a series of QR chunks at ~1.5 KB per code for reliable scanning), and manual file export/import (JSON via FileProvider + share sheet). NFC is deferred to v1.1 — Android Beam was deprecated in API 29 and removed in API 33.
- Why: Bluetooth is the most reliable for the ~10-50 KB snapshot payload. QR is a fallback for devices without Bluetooth. File export/import covers all remaining cases.

### Persistence
- Decision: Room database with `DiagnosticsSessionEntity`, `DiagnosticsSnapshotEntity` (snapshot JSON in a TEXT column), and `DiagnosticsComparisonEntity` (comparison JSON in a TEXT column).
- Why: Snapshots and comparisons are complex nested objects. Storing as JSON avoids a 20+ table relational schema while still enabling listing/deleting sessions via Room queries. Same pattern as netmap's `cellsJson`.

### UI Flow
- Decision: Four screens. (1) NetDiagActivity — capture wizard: shows collector progress, then prompts to receive second snapshot or compare with a saved one. (2) SnapshotDetailActivity — raw snapshot data in expandable sections. (3) ComparisonResultActivity — summary card (status, critical/warning/info counts), per-category breakdown with findings. (4) SessionHistoryActivity — list of past sessions. All screens use XML layouts with dark theme.
- Why: Minimal screen count. The wizard-style capture flow guides non-technical users through the process.

### Thresholds
- Decision: Use `ComparisonThresholds` data class with sensible defaults (from schema). Not user-configurable in v1.
- Why: Keeps v1 simple. Thresholds are tuned in the schema and can be exposed in settings in v1.1.

### Active Probes Timing
- Decision: 5 ICMP packets per target (not 10) to keep total probe time under 6 seconds when all 8 targets run concurrently. HTTP timing probe runs in parallel with ICMP probes.
- Why: 10 packets × 1-second interval = 10 seconds per target, which blows the 15-second capture budget even with concurrency. 5 packets is the industry standard for quick diagnostics and still gives meaningful loss/jitter statistics.

### Snapshot Size Estimate
- A realistic snapshot with 8 probe targets (5 packets each), 12 scan results, full IP/DNS/Wi-Fi/cellular data, and device health serializes to approximately 15-40 KB JSON. Bluetooth RFCOMM handles this in <2 seconds. QR at ~1.5 KB/code needs 10-27 codes — feasible but not ideal for large snapshots; the QR path is documented as a fallback.

## Risks / Trade-offs
- icmp4a adds a small dependency (~50 KB). Acceptable for the value it provides.
- Bluetooth exchange requires `BLUETOOTH_CONNECT` + `BLUETOOTH_ADVERTISE` + `BLUETOOTH_SCAN` on API 31+ — additional runtime permissions. QR/file fallbacks mitigate if user denies.
- Wi-Fi scan throttling means the captured snapshot may use stale scan results (up to 30 seconds old). Documented in UI as "scan age."
- `ConnectivityDiagnosticsManager` data is thin for third-party apps — used as bonus signal, not primary. UI labels it as "system diagnostics (limited)."
- Snapshot JSON payload is ~15-40 KB — fits comfortably in Bluetooth RFCOMM. QR needs chunking at ~1.5 KB per code (10-27 codes); acceptable as a fallback but not primary.
- Wi-Fi collector features degrade gracefully on API 26-29: `getWifiStandard()`, max link speeds, and BSS Load IE are API 30+ only — these fields are null on older devices.

## Open Questions (deferred to v1.1)
1. Speed-test integration (NDT7 or LibreSpeed) for the throughput slot?
2. Traceroute via NDK for hop-by-hop analysis?
3. Historical trend analysis across sessions?
4. Export comparison results as PDF report?
5. NFC snapshot exchange via tag-writing approach (Android Beam removed in API 33)?
