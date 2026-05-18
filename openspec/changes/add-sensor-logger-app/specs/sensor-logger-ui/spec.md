## ADDED Requirements

### Requirement: Hub registration
`SensorLoggerActivity` SHALL be registered in the hub app list and in `AndroidManifest.xml`. It SHALL appear alongside other mini-apps in the Paranoid hub navigation.

#### Scenario: Sensor logger visible in hub
- **GIVEN** the user opens the Paranoid hub
- **WHEN** the app list is rendered
- **THEN** a "Sensor Logger" entry is visible and tappable
- **AND** tapping it launches `SensorLoggerActivity`

---

### Requirement: Recording control screen
`SensorLoggerActivity` SHALL display a Start / Stop button and a live status area. When recording is active the status area SHALL show: elapsed time (updated every second), count of registered sensors, and total events recorded in the current session. When idle the status area SHALL show the last completed session's summary or "No recordings yet" if none exist.

#### Scenario: Idle state — no prior sessions
- **GIVEN** the app has never recorded
- **WHEN** the user opens `SensorLoggerActivity`
- **THEN** a Start button is shown
- **AND** the status area shows "No recordings yet"

#### Scenario: Idle state — prior session exists
- **GIVEN** at least one completed session exists
- **WHEN** the user opens `SensorLoggerActivity`
- **THEN** the status area shows the most recent session's duration and event count

#### Scenario: Active recording — live status updates
- **GIVEN** recording is active
- **WHEN** the Activity is visible
- **THEN** elapsed time increments each second
- **AND** event count reflects the total events flushed to the database plus the in-memory buffer size

#### Scenario: Stop button ends recording
- **GIVEN** recording is active
- **WHEN** the user taps Stop
- **THEN** the flush-on-stop sequence executes
- **AND** the UI transitions to idle and shows the completed session summary

---

### Requirement: Session list screen
`SensorSessionsActivity` SHALL display all recording sessions in reverse-chronological order. Each row SHALL show: start date/time, duration (or "Incomplete" for sessions with `ended_at IS NULL`), and event count. Incomplete sessions SHALL show a warning badge. Tapping a row opens `SensorSessionDetailActivity`.

#### Scenario: Session list shows complete session
- **GIVEN** one complete session exists (duration 2 min, 1 200 events)
- **WHEN** the user opens `SensorSessionsActivity`
- **THEN** the session row shows the start time, "2:00", and "1200 events"

#### Scenario: Session list shows incomplete session with badge
- **GIVEN** one incomplete session exists (`ended_at IS NULL`)
- **WHEN** the user opens `SensorSessionsActivity`
- **THEN** the session row shows an "Incomplete" badge instead of a duration

---

### Requirement: Session detail screen
`SensorSessionDetailActivity` SHALL display: session start and end times (or "Incomplete"), total duration, total event count, list of sensor types that contributed events (with per-sensor event counts), and — for incomplete sessions — action buttons to "Mark as closed" or "Delete".

#### Scenario: Detail for complete session
- **GIVEN** a complete session with accelerometer (600 events) and gyroscope (600 events)
- **WHEN** the user opens the session detail
- **THEN** total duration and total event count (1 200) are shown
- **AND** a per-sensor breakdown lists Accelerometer (600) and Gyroscope (600)
- **AND** no action buttons for incomplete-session recovery are shown

#### Scenario: Detail for incomplete session — mark as closed
- **GIVEN** an incomplete session is displayed in detail
- **WHEN** the user taps "Mark as closed"
- **THEN** `ended_at` is set to the current time
- **AND** the UI refreshes to show the session as complete without a warning badge

#### Scenario: Detail for incomplete session — delete
- **GIVEN** an incomplete session is displayed in detail
- **WHEN** the user taps "Delete" and confirms
- **THEN** the session and all its events are deleted
- **AND** the user is returned to the session list

---

### Requirement: Combined recording status indicator
When `SensorRecordingService` is active at the same time as NetMap's `RecordingService`, `SensorLoggerActivity` SHALL show a notice indicating that NetMap is also recording and that combined battery usage is higher than either service alone. Detection SHALL use the `RecordingService.isRunning` companion object flag, checked on `onResume()` and on service bind callback.

#### Scenario: Combined recording notice shown
- **GIVEN** sensor recording is active
- **AND** NetMap's `RecordingService` is also running
- **WHEN** the user views `SensorLoggerActivity`
- **THEN** a notice reads "NetMap is also recording — combined battery usage is higher"

#### Scenario: No notice when only sensor logger is active
- **GIVEN** sensor recording is active
- **AND** NetMap's `RecordingService` is not running
- **WHEN** the user views `SensorLoggerActivity`
- **THEN** no combined-recording notice is shown
