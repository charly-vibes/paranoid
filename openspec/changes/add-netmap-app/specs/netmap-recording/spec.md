## ADDED Requirements

Depends on: netmap-data

### Requirement: Location Data Source
The system SHALL provide a LocationSource that emits GPS coordinates as a Kotlin Flow using FusedLocationProviderClient with high-accuracy priority, configurable interval (default 2 seconds), and configurable minimum distance (default 5 meters).

#### Scenario: Location updates while recording
- **WHEN** a recording is active and location permissions are granted
- **THEN** the LocationSource emits Location objects at approximately the configured interval

#### Scenario: Location permissions denied
- **WHEN** location permissions are not granted
- **THEN** the LocationSource does not emit and the UI shows an error prompting the user to grant permissions

### Requirement: Telephony Data Source
The system SHALL provide a TelephonySource that reads serving and neighbor cell info from TelephonyManager.allCellInfo and maps each CellInfo subclass (CellInfoLte, CellInfoNr, CellInfoWcdma, CellInfoGsm) to a unified CellMeasurement model.

#### Scenario: LTE cell info snapshot
- **WHEN** a telephony snapshot is taken and the device is on LTE
- **THEN** the snapshot includes a CellMeasurement with technology=LTE, rsrp, rsrq, pci, earfcn, and isServing=true for the registered cell

#### Scenario: Multiple cells visible
- **WHEN** a telephony snapshot is taken with 3 neighbor cells
- **THEN** all 4 cells (1 serving + 3 neighbors) are included in the snapshot

#### Scenario: Cell info unavailable
- **WHEN** TelephonyManager.allCellInfo returns null or empty
- **THEN** the measurement is recorded with an empty cells list and the HUD shows "No signal"

### Requirement: Foreground Recording Service
The system SHALL provide a RecordingService (foreground service, START_STICKY) that combines location and telephony snapshots into Measurement objects and batches them to the database. The Activity binds to the service for live UI updates; the service continues recording when unbound.

#### Scenario: Start recording
- **WHEN** the user taps Start
- **THEN** the service starts in foreground with a persistent notification showing duration, count, and signal level

#### Scenario: Background recording
- **WHEN** the user switches to another app or turns off the screen
- **THEN** the service continues recording measurements

#### Scenario: Stop recording
- **WHEN** the user taps Stop
- **THEN** the service flushes remaining buffered measurements to the database and stops

#### Scenario: Notification content
- **WHEN** the service is recording
- **THEN** the notification shows elapsed time, measurement count, and current signal level (updated every ~5 seconds)

#### Scenario: Service killed by OS
- **WHEN** the OS kills the service due to memory pressure
- **THEN** buffered measurements are flushed in onDestroy and the recording is marked as ended with endedAt set to the last measurement timestamp

#### Scenario: Navigate away while recording
- **WHEN** the user presses back or home while recording is active
- **THEN** recording continues in background via the foreground service and the notification remains visible

### Requirement: Runtime Permissions
The system SHALL request permissions in order: ACCESS_FINE_LOCATION, READ_PHONE_STATE, POST_NOTIFICATIONS (Android 13+). Recording SHALL NOT start until location and phone state permissions are granted.

#### Scenario: All permissions granted
- **WHEN** the user grants location and phone state permissions
- **THEN** the Start button becomes enabled

#### Scenario: Location permission denied
- **WHEN** the user denies location permission
- **THEN** the UI shows a rationale explaining why location is needed and the Start button remains disabled

#### Scenario: Permission permanently denied
- **WHEN** the user has denied a permission with "Don't ask again" (shouldShowRequestPermissionRationale returns false)
- **THEN** the UI shows a dialog with an "Open Settings" button that navigates to the app's system settings page
