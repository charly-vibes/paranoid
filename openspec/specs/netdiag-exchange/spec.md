# netdiag-exchange Specification

## Purpose
TBD - created by archiving change add-netdiag-app. Update Purpose after archive.
## Requirements
### Requirement: Bluetooth Snapshot Exchange
The system SHALL transfer a serialized `DiagnosticsSnapshot` between two devices using Bluetooth RFCOMM. One device acts as server (listening) and the other as client (connecting). The exchange SHALL complete within 10 seconds for a typical 30 KB payload. On API 31+, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, and `BLUETOOTH_SCAN` permissions SHALL be requested at runtime.

#### Scenario: Successful Bluetooth transfer
- **WHEN** device A captures a snapshot and device B is listening via Bluetooth
- **THEN** device B receives the full snapshot JSON and deserializes it successfully

#### Scenario: Bluetooth not available
- **WHEN** Bluetooth is disabled or not supported
- **THEN** the UI offers QR code or file export as alternatives

#### Scenario: Bluetooth transfer interrupted
- **WHEN** the Bluetooth connection drops during transfer
- **THEN** the UI shows an error message with a "Retry" button

#### Scenario: Bluetooth permissions denied on API 31+
- **WHEN** the user denies BLUETOOTH_CONNECT or BLUETOOTH_ADVERTISE
- **THEN** the UI falls back to QR code or file export and shows rationale for Bluetooth permissions

### Requirement: QR Code Snapshot Exchange
The system SHALL encode a snapshot as a series of QR codes (chunked at ~1.5 KB per code for reliable scanning) and decode them on the receiving device by scanning sequentially. Each chunk SHALL include a sequence number and total count header.

#### Scenario: Small snapshot fits in one QR code
- **WHEN** the snapshot JSON is under 1.5 KB
- **THEN** a single QR code is generated and scanned

#### Scenario: Large snapshot requires multiple QR codes
- **WHEN** the snapshot JSON is 15 KB
- **THEN** approximately 11 QR codes are generated (accounting for sequence header overhead per chunk), and the receiver prompts for each in order

#### Scenario: Missed or out-of-order QR chunk
- **WHEN** the receiver scans chunk 3 before chunk 2
- **THEN** the receiver prompts to scan the missing chunk and does not proceed until all chunks are received in order

### Requirement: File Export and Import
The system SHALL export a snapshot as a JSON file via FileProvider and Android share sheet, and import a snapshot JSON file from the file system or received via share intent.

#### Scenario: Export snapshot
- **WHEN** the user taps "Export" on a captured snapshot
- **THEN** a JSON file is shared via the system share sheet

#### Scenario: Import snapshot
- **WHEN** the user opens a `.json` snapshot file via share intent or file picker
- **THEN** the snapshot is deserialized and available for comparison

#### Scenario: Import malformed JSON
- **WHEN** the user imports a file that is not valid snapshot JSON
- **THEN** the UI shows an error message explaining the file format is invalid

#### Scenario: Import oversized file
- **WHEN** the user imports a file that exceeds 1 MB
- **THEN** the UI shows an error message explaining the file is too large for a valid snapshot
