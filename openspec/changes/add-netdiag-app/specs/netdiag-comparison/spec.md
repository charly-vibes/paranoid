## ADDED Requirements

Depends on: netdiag-data

### Requirement: Comparison Engine
The system SHALL provide a pure-function `ComparisonEngine.compare(a, b)` that takes two `DiagnosticsSnapshot` values and returns a `DiagnosticsComparison` with findings sorted by severity (descending) then category, per-category scores (0.0–1.0), and an overall status. All sub-comparisons SHALL run regardless of the derived overall status; `INCOMPARABLE` affects only the final status derivation, not whether individual comparisons execute.

#### Scenario: Both devices validated
- **WHEN** both snapshots have isValidated=true
- **THEN** no CONNECTIVITY finding for validation is emitted

#### Scenario: One device not validated
- **WHEN** snapshot A has isValidated=false and snapshot B has isValidated=true
- **THEN** a CRITICAL finding is emitted with affectedDevice=A and a recommendation to check captive portal

#### Scenario: Both not validated
- **WHEN** neither snapshot is validated
- **THEN** a CRITICAL finding with affectedDevice=BOTH is emitted pointing to shared infrastructure

### Requirement: Connectivity Comparison
The system SHALL compare internet validation status and captive portal detection between snapshots.

#### Scenario: Captive portal on one device
- **WHEN** snapshot A has isCaptivePortal=true and snapshot B does not
- **THEN** a CRITICAL finding is emitted for device A with recommendation to complete captive portal login

### Requirement: IP Config Comparison
The system SHALL compare IP configuration between snapshots, detecting: APIPA addresses (DHCP failure), missing default gateway, MTU mismatches, subnet/gateway conflicts, and IPv6 availability differences.

#### Scenario: APIPA on one device
- **WHEN** one snapshot has isApipa=true
- **THEN** a CRITICAL finding is emitted for that device indicating DHCP lease failure

#### Scenario: Different gateways
- **WHEN** both snapshots have different gatewayIpv4 values
- **THEN** a WARNING finding is emitted suggesting they may be on different subnets or VLANs

### Requirement: Wi-Fi Signal Comparison
The system SHALL compare Wi-Fi RSSI, standard, channel, channel width, BSS load, and TX link utilization between snapshots when both are on TRANSPORT_WIFI.

#### Scenario: RSSI gap of 10+ dBm
- **WHEN** RSSI differs by 10 dBm or more
- **THEN** a WARNING (or CRITICAL if the weaker is below -80 dBm) finding is emitted for the weaker device

#### Scenario: Different Wi-Fi channels
- **WHEN** phones are on different channels
- **THEN** an INFO finding is emitted noting possible band steering inconsistency

### Requirement: Cellular Signal Comparison
The system SHALL compare LTE RSRP and RAT between snapshots when either is on TRANSPORT_CELLULAR.

#### Scenario: RSRP gap of 10+ dBm
- **WHEN** RSRP differs by 10 dBm or more
- **THEN** a WARNING finding is emitted for the weaker device

#### Scenario: Different RATs
- **WHEN** phones are on different radio access technologies
- **THEN** an INFO finding is emitted noting speed will differ accordingly

### Requirement: DNS Comparison
The system SHALL compare DNS server configuration, per-target probe results (rcode, latency), and hijack detection between snapshots.

#### Scenario: DNS failure on one device
- **WHEN** DNS resolution for a target fails on device A but succeeds on device B
- **THEN** a CRITICAL finding is emitted for device A

#### Scenario: DNS hijack on one device
- **WHEN** device A's DNS is hijacked but device B's is clean
- **THEN** a CRITICAL finding is emitted for device A recommending Private DNS

#### Scenario: DNS latency gap over 80ms
- **WHEN** DNS latency for a target differs by more than 80ms
- **THEN** a WARNING finding is emitted for the slower device

### Requirement: Probe Comparison
The system SHALL compare ping packet loss, RTT, and jitter between snapshots for each probe target.

#### Scenario: Packet loss on one device
- **WHEN** device A has >5% packet loss and device B has ≤1%
- **THEN** a WARNING (or CRITICAL if >20%) finding is emitted for device A

#### Scenario: Both devices have packet loss
- **WHEN** both devices have >5% packet loss to the same target
- **THEN** a finding with affectedDevice=BOTH is emitted pointing to shared infrastructure

#### Scenario: High jitter on one device
- **WHEN** device A has jitter >20ms and device B has <5ms
- **THEN** a WARNING finding is emitted for device A suggesting Wi-Fi retransmissions

### Requirement: Throughput Comparison
The system SHALL compare download throughput when both snapshots include throughput data, flagging ratios below 0.5 or above 2.0.

#### Scenario: Significant throughput gap
- **WHEN** one device has 2x or more download speed than the other
- **THEN** a WARNING (or CRITICAL if the slower is below 5 Mbps) finding is emitted

### Requirement: Device Health Comparison
The system SHALL compare device health factors that affect diagnostics accuracy: Doze mode, Data Saver, VPN active, and location services.

#### Scenario: Doze mode on one device
- **WHEN** device A is in Doze mode and device B is not
- **THEN** a WARNING finding is emitted noting metrics may be stale

#### Scenario: VPN active on one device
- **WHEN** device A has an active VPN and device B does not
- **THEN** a WARNING finding is emitted noting metrics reflect VPN-tunneled performance

### Requirement: Overall Status Derivation
The system SHALL derive `ComparisonStatus` as: INCOMPARABLE if transports differ, IDENTICAL if no warnings or criticals, MINOR_DIFF if warnings only, BOTH_DEGRADED if any critical affects both devices, ONE_DEGRADED otherwise.

#### Scenario: Different transports
- **WHEN** device A is on Wi-Fi and device B is on cellular
- **THEN** overallStatus is INCOMPARABLE

#### Scenario: One device degraded
- **WHEN** device A has critical findings and device B is fine
- **THEN** overallStatus is ONE_DEGRADED

### Requirement: Capture Time Delta Warning
The system SHALL emit a WARNING finding when `captureTimeDeltaMs` exceeds 5 minutes, noting that network conditions may have changed between the two snapshots.

#### Scenario: Snapshots captured within 5 minutes
- **WHEN** both snapshots were captured less than 5 minutes apart
- **THEN** no time-delta warning is emitted

#### Scenario: Snapshots captured more than 5 minutes apart
- **WHEN** the capture time delta exceeds 5 minutes
- **THEN** a WARNING finding is emitted with category=CONNECTIVITY and explanation that conditions may have changed

### Requirement: Per-Category Scoring
The system SHALL compute a 0.0–1.0 score per device per category using penalty weights: CRITICAL=0.4, WARNING=0.15, INFO=0.05. Score = max(0.0, 1.0 − sum of penalties).

#### Scenario: No findings in category
- **WHEN** a category has no findings for device A
- **THEN** scoreA is 1.0

#### Scenario: One critical finding
- **WHEN** a category has one CRITICAL finding for device A
- **THEN** scoreA is 0.6

#### Scenario: Self-comparison
- **WHEN** both snapshots have the same ID
- **THEN** the engine returns a comparison with status IDENTICAL and no findings, and the UI warns that comparison requires two different snapshots

Note: Penalties are additive — multiple lower-severity findings can produce the same score as fewer critical findings (e.g., 7 warnings = 0.0, same as 3 CRITICALs). This is intentional; accumulated minor issues indicate a category is as degraded as one with critical failures.
