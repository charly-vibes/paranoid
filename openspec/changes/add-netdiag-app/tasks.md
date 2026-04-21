## 1. Project Setup
- [x] 1.1 Add dependencies to `android/app/build.gradle.kts`: icmp4a, kotlinx.serialization (if not already present)
- [x] 1.2 Add permissions to `AndroidManifest.xml`: ACCESS_FINE_LOCATION, READ_PHONE_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, NEARBY_WIFI_DEVICES (API 33+), BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN (API 31+), ACCESS_NETWORK_STATE, INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS
- [x] 1.3 Register NetDiagActivity, SnapshotDetailActivity, ComparisonResultActivity, SessionHistoryActivity in AndroidManifest.xml
- [x] 1.4 Register NetDiag entry in hub app list (`HubActivity.kt`)
- [x] 1.5 Verify project compiles with new dependencies

## 2. Data Layer (netdiag-data)
Depends on: 1.
- [x] 2.1 Create data classes from DiagnosticsSchema.kt under `apps/netdiag/data/`: all snapshot types, comparison types, session, thresholds, enums
- [x] 2.2 Create Room entities: `DiagnosticsSessionEntity`, `DiagnosticsSnapshotEntity` (JSON TEXT column), `DiagnosticsComparisonEntity` (JSON TEXT column)
- [x] 2.3 Create Room DAOs: SessionDao (CRUD, list by date desc), SnapshotDao, ComparisonDao
- [x] 2.4 Register entities in `ParanoidDatabase`
- [x] 2.5 Write unit tests for JSON serialization round-trip of DiagnosticsSnapshot
- [x] 2.6 Write unit tests for enum ordering (severity, signal category)

## 3. Snapshot Collectors (netdiag-snapshot)
Depends on: 2.
- [x] 3.1 Create `WifiCollector`: WifiManager + WifiInfo (API 31+: via `NetworkCapabilities.getTransportInfo()`) → WifiSnapshot, scan results → WifiEnvironment, BSS Load IE parsing (API 30+), graceful degradation on API 26-29 (standard=UNKNOWN, null max speeds/BSS load)
- [x] 3.2 Create `CellularCollector`: TelephonyCallback (API 31+) / PhoneStateListener → CellularSnapshot
- [x] 3.3 Create `DnsCollector`: DnsResolver (API 29+) probes with rcode + latency, fallback to InetAddress for API 26-28, DoH cross-check for hijack detection
- [x] 3.4 Create `IpConfigCollector`: LinkProperties → IpConfig with anomaly flags
- [x] 3.5 Create `ProbeCollector`: icmp4a ping to each ProbeTarget (5 packets, all targets concurrent), HttpURLConnection HTTP timing to connectivitycheck.gstatic.com
- [x] 3.6 Create `DeviceHealthCollector`: battery, Doze, Data Saver, airplane mode, VPN, memory, permissions
- [x] 3.7 Create `ConnDiagCollector`: ConnectivityDiagnosticsManager data (API 30+), null on API 26-29
- [x] 3.8 Create `SnapshotCaptureEngine`: orchestrate all collectors concurrently, assemble DiagnosticsSnapshot, detect transport changes during capture
- [x] 3.9 Write unit tests for each collector's data mapping logic (mock Android APIs where needed)
- [ ] 3.10 Write integration test for SnapshotCaptureEngine (instrumentation test on real device)

## 4. Comparison Engine (netdiag-comparison)
Depends on: 2. **Parallel track:** can run concurrently with sections 5 and 6.
- [ ] 4.1 Port `ComparisonEngine.compare()` from DiagnosticsSchema.kt (pure Kotlin, no Android deps)
- [ ] 4.2 Port all sub-comparisons: compareConnectivity, compareIpConfig, compareWifi, compareCellular, compareDns, compareProbes, compareThroughput, compareDeviceHealth
- [ ] 4.3 Add capture time delta warning (>5 minutes → WARNING finding)
- [ ] 4.4 Port scoring and status derivation logic
- [ ] 4.5 Write unit tests for each comparison function with known snapshot pairs
- [ ] 4.6 Write unit tests for edge cases: null fields, missing probes, incomparable transports, stale capture time delta

## 5. Snapshot Exchange (netdiag-exchange)
Depends on: 2, 3.
- [ ] 5.1 Implement Bluetooth RFCOMM exchange: server/client roles, snapshot JSON transfer, runtime permissions (API 31+), error handling for connection drops
- [ ] 5.2 Implement QR code encoding (chunked at ~1.5 KB/code with sequence headers) and decoding (sequential scan with missing-chunk prompts)
- [ ] 5.3 Implement file export via FileProvider + share sheet (JSON)
- [ ] 5.4 Implement file import via share intent and file picker with malformed-JSON error handling
- [ ] 5.5 Write unit tests for QR chunking/reassembly logic
- [ ] 5.6 Write instrumentation tests for Bluetooth and file exchange

## 6. UI (netdiag-ui)
Depends on: 2, 3, 4, 5.
- [ ] 6.1 Create NetDiagActivity layout (XML): permission prompts, capture button, progress indicators, receive-snapshot options, compare-with-saved option
- [ ] 6.2 Implement NetDiagActivity: permission flow, capture engine integration, exchange flow, device label assignment (auto from Build.MODEL, editable, "(A)"/"(B)" suffix for same model)
- [ ] 6.3 Create SnapshotDetailActivity layout + implementation: expandable sections for each data domain
- [ ] 6.4 Create ComparisonResultActivity layout + implementation: summary card, category breakdown, expandable findings with severity color coding
- [ ] 6.5 Create SessionHistoryActivity layout + implementation: RecyclerView list, long-press delete, empty state
- [ ] 6.6 Implement runtime permission request flow: capture permissions (sequential with rationale) + Bluetooth permissions (on-demand before exchange, fallback to QR/file on denial)
- [ ] 6.7 Apply dark theme (#121212 background, light text) to all NetDiag screens
- [ ] 6.8 Implement severity color coding: red (CRITICAL), amber (WARNING), blue (INFO)

## 7. Polish
Depends on: all above.
- [ ] 7.1 Empty states: no sessions, no signal, capture failed
- [ ] 7.2 Error handling: timeout during capture, Bluetooth failure, invalid JSON import, transport change mid-capture
- [ ] 7.3 Verify build and release workflow produces working APK with NetDiag
