## ADDED Requirements

Depends on: netdiag-data, netdiag-snapshot, netdiag-comparison, netdiag-exchange

### Requirement: Capture Wizard
The system SHALL provide a `NetDiagActivity` with a step-by-step capture flow: (1) request permissions, (2) run collectors with progress indicators, (3) display capture summary, (4) prompt to receive second snapshot or select a saved one for comparison.

#### Scenario: Capture with all permissions granted
- **WHEN** the user taps "Capture" and all permissions are granted
- **THEN** collectors run concurrently with a progress bar and the snapshot summary is displayed on completion

#### Scenario: Missing permissions
- **WHEN** required permissions are not granted
- **THEN** the UI requests each permission in sequence with rationale dialogs before starting capture

#### Scenario: Receive second snapshot
- **WHEN** the user taps "Receive from other phone"
- **THEN** the UI opens a Bluetooth/QR/file chooser to import the second snapshot

#### Scenario: Compare with saved snapshot
- **WHEN** the user taps "Compare with saved snapshot"
- **THEN** a list of previously captured local snapshots is shown for selection

### Requirement: Snapshot Detail View
The system SHALL provide a `SnapshotDetailActivity` that displays all captured data in expandable sections: Network State, IP Config, Wi-Fi, Cellular, DNS, Probes, Device Health, Connectivity Diagnostics.

#### Scenario: View Wi-Fi details
- **WHEN** the user expands the Wi-Fi section
- **THEN** SSID, BSSID, RSSI, channel, standard, link speeds, and environment data are shown

#### Scenario: Section absent
- **WHEN** the snapshot has null cellular data (device on Wi-Fi)
- **THEN** the Cellular section shows "Not applicable" rather than being hidden

### Requirement: Comparison Results View
The system SHALL provide a `ComparisonResultActivity` that displays: (1) a summary card with overall status (color-coded), critical/warning/info counts, and likely cause; (2) per-category breakdown with scores for each device; (3) expandable findings with metric, values, delta, explanation, and recommendation.

#### Scenario: View comparison summary
- **WHEN** a comparison completes
- **THEN** the summary card shows the overall status (e.g., "ONE_DEGRADED"), finding counts, and the top-ranked likely cause

#### Scenario: Expand a finding
- **WHEN** the user taps a finding row
- **THEN** the full explanation and recommendation are shown

#### Scenario: Color coding by severity
- **WHEN** findings are displayed
- **THEN** CRITICAL findings have a red indicator, WARNING has amber, INFO has blue

### Requirement: Session History
The system SHALL provide a `SessionHistoryActivity` listing past comparison sessions in reverse chronological order with label, date, and overall status badge.

#### Scenario: Open past session
- **WHEN** the user taps a session in the list
- **THEN** the ComparisonResultActivity opens with that session's data

#### Scenario: Delete a session
- **WHEN** the user long-presses a session
- **THEN** a confirmation dialog appears and the session is deleted on confirmation

#### Scenario: Empty state
- **WHEN** no sessions exist
- **THEN** an empty state message is shown with a prompt to start a new capture

### Requirement: Dark Theme
All NetDiag screens SHALL follow the Paranoid dark theme (#121212 background, light text) with OLED-friendly colors.

#### Scenario: Dark theme consistency
- **WHEN** any NetDiag screen is displayed
- **THEN** the background is #121212 and text is light-colored

### Requirement: Device Label Assignment
The system SHALL auto-assign a device label from `Build.MODEL` (e.g., "Pixel 8") at capture time. The user MAY edit the label before initiating comparison. If both devices have the same model name, the system SHALL append "(A)" and "(B)" to distinguish them.

#### Scenario: Different device models
- **WHEN** device A is "Pixel 8" and device B is "Galaxy S24"
- **THEN** labels are "Pixel 8" and "Galaxy S24"

#### Scenario: Same device model
- **WHEN** both devices are "Pixel 8"
- **THEN** labels are "Pixel 8 (A)" and "Pixel 8 (B)"

### Requirement: Runtime Permissions
The system SHALL request runtime permissions in order: ACCESS_FINE_LOCATION (or NEARBY_WIFI_DEVICES on API 33+), READ_PHONE_STATE, POST_NOTIFICATIONS (API 33+). Normal permissions (ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE, INTERNET) are auto-granted and do not require runtime requests. For Bluetooth exchange on API 31+, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, and BLUETOOTH_SCAN SHALL be requested before initiating Bluetooth transfer. Capture SHALL NOT start until location permission is granted.

#### Scenario: All permissions granted
- **WHEN** all required permissions are granted
- **THEN** the Capture button is enabled

#### Scenario: Permission permanently denied
- **WHEN** a permission is permanently denied
- **THEN** the UI shows a dialog with an "Open Settings" button

#### Scenario: Bluetooth permissions denied
- **WHEN** Bluetooth permissions are denied on API 31+
- **THEN** the Bluetooth exchange option is disabled and the UI offers QR code or file export instead
