# netdiag-data Specification

## Purpose
TBD - created by archiving change add-netdiag-app. Update Purpose after archive.
## Requirements
### Requirement: Diagnostics Data Model
The system SHALL define Kotlin data classes matching the NetDiag diagnostics schema (see `DiagnosticsSchema.kt` for full field definitions): `DiagnosticsSnapshot`, `DiagnosticsComparison`, `DiagnosticsSession`, `ComparisonThresholds`, and all nested types (`IpConfig`, `WifiSnapshot`, `CellularSnapshot`, `DnsSnapshot`, `PingProbeResult`, `HttpTimingResult`, `ThroughputResult`, `TrafficDelta`, `DeviceHealth`, `ConnectivityDiagnosticsData`, `ComparisonSummary`, `ComparisonFinding`, `CategoryResult`). All leaf values that carry measurement quality SHALL use the `Measured<T>` wrapper with confidence (0.0–1.0) and source metadata.

#### Scenario: Nullable fields signal absence
- **WHEN** a collector cannot obtain a value (e.g., Wi-Fi data on a cellular-only connection)
- **THEN** the corresponding snapshot field is null rather than a default/sentinel value

#### Scenario: Measured wrapper carries provenance
- **WHEN** a value is collected from WifiInfo RSSI
- **THEN** the `Measured<Int>` wraps the dBm value with confidence=1.0 and source="WifiInfo"

### Requirement: Enum Definitions
The system SHALL define enums: `Severity` (INFO, WARNING, CRITICAL), `Transport` (WIFI, CELLULAR, ETHERNET, VPN, UNKNOWN), `WifiStandard` (LEGACY, N, AC, AX, BE, UNKNOWN), `CellRat` (GSM, CDMA, WCDMA, HSPA, LTE, LTE_CA, NR_NSA, NR_SA, UNKNOWN), `DnsRcode` (NOERROR, SERVFAIL, NXDOMAIN, REFUSED, TIMEOUT, HIJACKED, UNKNOWN), `PingStatus` (REACHABLE, TIMEOUT, UNREACHABLE, ERROR), `ProbeTarget` (GATEWAY, DNS_PRIMARY, DNS_SECONDARY, GOOGLE_DNS, CLOUDFLARE_DNS, CONNECTIVITY_CHECK, CLOUDFLARE_WEB, GOOGLE_WEB), `SignalCategory` (EXCELLENT, GOOD, FAIR, POOR, UNUSABLE), `ComparisonStatus` (IDENTICAL, MINOR_DIFF, ONE_DEGRADED, BOTH_DEGRADED, INCOMPARABLE), `FindingCategory` (CONNECTIVITY, IP_CONFIG, WIFI_SIGNAL, WIFI_CHANNEL, CELLULAR_SIGNAL, DNS, LATENCY, PACKET_LOSS, THROUGHPUT, JITTER, ROUTING, DEVICE_HEALTH), and `AffectedDevice` (A, B, BOTH, NEITHER).

#### Scenario: Severity ordering
- **WHEN** findings are sorted by severity
- **THEN** CRITICAL appears before WARNING, which appears before INFO

### Requirement: Session Persistence
The system SHALL persist diagnostics sessions in a Room database. Each session contains a label, creation timestamp, a list of snapshots (stored as JSON), and a list of comparisons (stored as JSON).

#### Scenario: Save a completed session
- **WHEN** a comparison is completed between two snapshots
- **THEN** the session (with both snapshots and the comparison) is persisted to Room

#### Scenario: List past sessions
- **WHEN** the user opens session history
- **THEN** sessions are listed in reverse chronological order with label, date, and overall comparison status

#### Scenario: Delete a session
- **WHEN** the user deletes a session
- **THEN** the session row and its stored snapshots and comparisons are removed

### Requirement: Serialization
All data model classes SHALL be annotated with `@Serializable` (kotlinx.serialization) for JSON encoding/decoding. Snapshot JSON SHALL be the unit of exchange between devices.

#### Scenario: Round-trip serialization
- **WHEN** a `DiagnosticsSnapshot` is serialized to JSON and deserialized back
- **THEN** the result is equal to the original
