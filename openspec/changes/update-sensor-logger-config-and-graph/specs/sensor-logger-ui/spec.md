## MODIFIED Requirements

### Requirement: Recording control screen
`SensorLoggerActivity` SHALL display a Start / Stop button and a live status area. When recording is active the status area SHALL show: elapsed time (updated every second), count of registered sensors, and total events recorded in the current session. When idle the status area SHALL show the last completed session's summary or "No recordings yet" if none exist. The screen SHALL expose two navigation entry points: "Configure capture" (always enabled) opens `SensorCaptureConfigActivity`; "Live graph" (enabled only while recording is active, bound to a live observation of `SensorRecordingService.isRecording`) opens `SensorLiveGraphActivity`. The Start button SHALL be disabled — with a hint reading "No sensors enabled — open Configure capture" — whenever the current `RecordingProfile` contains no sensor satisfying `(setting.enabled || setting.visibleOnGraph) && setting.rateLevel != OFF`; the gate prevents the service from being asked to start an empty session. On the first launch after this change ships (detected via the bookkeeping key `seen_capture_defaults_dialog_v2` defaulting to `false`), the screen SHALL show a one-time dialog (not a toast) explaining that sensor recording now defaults to only accelerometer, gyroscope, and linear acceleration, with a button "Open Configure capture" that launches `SensorCaptureConfigActivity`; dismissing the dialog SHALL call `RecordingProfileStore.markDefaultsDialogSeen()`. If `RecordingProfileStore` reports a profile read failure (fallback to defaults), the screen SHALL display a non-blocking notice reading "Capture settings could not be loaded — using defaults".

#### Scenario: Idle state — no prior sessions
- **GIVEN** the app has never recorded
- **WHEN** the user opens `SensorLoggerActivity`
- **THEN** a Start button is shown (enabled, because `RecordingProfile.Default` has enabled sensors)
- **AND** the status area shows "No recordings yet"
- **AND** "Configure capture" is enabled and "Live graph" is disabled

#### Scenario: Idle state — prior session exists
- **GIVEN** at least one completed session exists
- **WHEN** the user opens `SensorLoggerActivity`
- **THEN** the status area shows the most recent session's duration and event count

#### Scenario: Active recording — live status updates
- **GIVEN** recording is active
- **WHEN** the Activity is visible
- **THEN** elapsed time increments each second
- **AND** event count reflects the total events flushed to the database plus the in-memory buffer size
- **AND** "Live graph" is enabled

#### Scenario: Stop button ends recording
- **GIVEN** recording is active
- **WHEN** the user taps Stop
- **THEN** the flush-on-stop sequence executes
- **AND** the UI transitions to idle and shows the completed session summary
- **AND** "Live graph" returns to disabled

#### Scenario: Start button is gated on a non-empty registration set
- **GIVEN** the user has saved a profile where every sensor has `rateLevel = OFF` (or `enabled = false && visibleOnGraph = false`)
- **WHEN** the user views `SensorLoggerActivity`
- **THEN** the Start button is disabled
- **AND** a hint reading "No sensors enabled — open Configure capture" is visible

#### Scenario: First-launch dialog explains new defaults
- **GIVEN** the bookkeeping key `seen_capture_defaults_dialog_v2` is `false`
- **WHEN** `SensorLoggerActivity` becomes visible
- **THEN** a dialog appears explaining the new default recording set
- **AND** the dialog offers a button that opens `SensorCaptureConfigActivity`
- **AND** dismissing the dialog calls `markDefaultsDialogSeen()`
- **AND** the dialog is not shown on subsequent launches

#### Scenario: Profile read failure shows fallback notice
- **GIVEN** `RecordingProfileStore` has fallen back to `RecordingProfile.Default` due to a read failure
- **WHEN** the user resumes `SensorLoggerActivity`
- **THEN** a non-blocking notice reads "Capture settings could not be loaded — using defaults"

## ADDED Requirements

### Requirement: Capture configuration screen
`SensorCaptureConfigActivity` SHALL present every known `SensorType` in a list, one row per sensor. Each row SHALL contain: the sensor's human-readable name, a "Record" checkbox bound to `enabled`, a "Show on graph" checkbox bound to `visibleOnGraph`, and a rate dropdown bound to `rateLevel` with options labelled `Off`, `Normal`, `UI`, `Game`, `Fastest` (hardware-relative labels, no specific Hz values, because Android sensor delays are best-effort and vary by device). Changes SHALL be persisted via `RecordingProfileStore.update(...)` and SHALL take effect on the next `startRecording()` call. Rows for sensors not present on the device SHALL be rendered greyed out (reduced alpha), their checkboxes and dropdown SHALL be non-interactive, and the row SHALL display the label "Unavailable on this device" next to the sensor name. While a recording session is currently active, the screen SHALL display a non-dismissable banner at the top reading "Applies to next recording"; the banner's visibility SHALL be bound to a live observation of `SensorRecordingService.isRecording` and SHALL appear or disappear without requiring the screen to be reopened.

#### Scenario: Toggle Record off persists and is honored next session
- **GIVEN** the user opens the config screen with accelerometer Record enabled
- **WHEN** the user unchecks Record for accelerometer and saves
- **AND** the user starts a new recording session
- **THEN** the new session contains zero accelerometer rows in `sensor_events`

#### Scenario: Visualize without record
- **GIVEN** the user has magnetometer with Record unchecked and Show on graph checked
- **WHEN** the user starts a recording session and opens the live graph
- **THEN** magnetometer is shown on the graph
- **AND** the resulting session detail screen does not list magnetometer as a contributing sensor

#### Scenario: Rate selection persists
- **GIVEN** the user sets gyroscope rate to `Game`
- **WHEN** the user saves and reopens the config screen
- **THEN** gyroscope rate shows `Game`

#### Scenario: Absent sensors are greyed out
- **GIVEN** the device has no barometer (`TYPE_PRESSURE` is absent)
- **WHEN** the user opens the config screen
- **THEN** the pressure row is rendered with reduced alpha
- **AND** its Record checkbox, Show-on-graph checkbox, and rate dropdown are disabled (non-interactive)
- **AND** the label "Unavailable on this device" is shown next to the sensor name

#### Scenario: Banner appears live without screen reopen
- **GIVEN** the user has the config screen open and no recording is active
- **WHEN** another component starts a recording session
- **THEN** within one observation cycle the "Applies to next recording" banner appears on the still-foregrounded config screen
- **AND** when the recording stops the banner disappears live

#### Scenario: Setting Off via dropdown excludes the sensor
- **GIVEN** the user selects rate `Off` for a sensor
- **WHEN** the change is saved
- **THEN** that sensor is excluded from registration regardless of the state of Record or Show on graph for the next session

---

### Requirement: Live graph screen
`SensorLiveGraphActivity` SHALL display a multi-channel line graph of the live sample stream from `SensorRecordingService.liveStream`. The set of sensors rendered SHALL be the intersection of (a) sensors present in the current `liveStream` snapshot and (b) sensors for which `service.sessionProfile.value?.get(type)?.visibleOnGraph == true`. The session-frozen `sessionProfile` snapshot (not the mutable `RecordingProfileStore.flow`) SHALL be used so that mid-session edits to `visibleOnGraph` do not desynchronize the graph from the actual registered set. Multi-axis sensors (accelerometer, gyroscope, linear acceleration, gravity, rotation vector, magnetometer) SHALL render x, y, z channels in distinct colors; single-value sensors (pressure, light, proximity) SHALL render a single channel. Each rendered sensor SHALL occupy one horizontal band auto-scaled to the visible window's per-channel min/max. The visible window SHALL show the last 600 samples per sensor. The view SHALL refresh on every emission of `liveStream` while the Activity is in the `STARTED` lifecycle state. On Activity recreate (e.g. configuration change such as rotation), the view SHALL immediately read `liveStream.value` once on attach so the current snapshot renders without waiting for the next emission. When recording is not active OR no sensors satisfy the rendering predicate, the screen SHALL display an explanatory empty state.

#### Scenario: Live data renders during recording
- **GIVEN** recording is active with accelerometer registered and `sessionProfile`'s accelerometer entry has `visibleOnGraph = true`
- **WHEN** the user opens the live graph
- **THEN** within 2 seconds a non-empty waveform for accelerometer's x, y, z channels is visible

#### Scenario: Only visible-on-graph sensors are rendered (frozen snapshot)
- **GIVEN** recording is active with accelerometer (`visibleOnGraph = true` in snapshot) and gyroscope (`visibleOnGraph = false` in snapshot) both registered
- **WHEN** the user opens the live graph
- **THEN** an accelerometer band is shown
- **AND** no gyroscope band is shown

#### Scenario: Mid-session `visibleOnGraph` edit does not desynchronize the graph
- **GIVEN** a session is active where the frozen snapshot has gyroscope `visibleOnGraph = false`
- **WHEN** the user opens the config screen mid-session, toggles gyroscope `visibleOnGraph = true`, and saves
- **AND** the user returns to the live graph
- **THEN** the gyroscope band is still hidden for the remainder of this session
- **AND** the next session (started after the current one ends) will show the gyroscope band

#### Scenario: Empty state when no sensors are visible
- **GIVEN** the session-frozen snapshot has every sensor with `visibleOnGraph = false`
- **WHEN** the user opens the live graph during an active recording
- **THEN** the screen shows "No sensors selected for visualization — open Configure capture"

#### Scenario: Empty state when not recording
- **GIVEN** no recording session is active (`sessionProfile.value == null`)
- **WHEN** the user opens the live graph
- **THEN** the screen shows "Start a recording to see live data"

#### Scenario: Empty / singleton window renders a placeholder line
- **GIVEN** a sensor was just registered and the live stream snapshot contains 0 or 1 samples for it
- **WHEN** the graph draws that band
- **THEN** a flat placeholder line is drawn at the band's vertical midpoint
- **AND** no NaN or crash occurs from auto-scaling

#### Scenario: Auto-scaling fits the visible window
- **GIVEN** an accelerometer band is rendered with the visible 600-sample window containing values in `[-2.0, 2.0]`
- **WHEN** the user picks up the device and produces a 9.8 m/s² spike
- **THEN** within at most one second of the spike entering the window the y-axis rescales so the spike is fully visible

#### Scenario: Activity recreate renders snapshot immediately
- **GIVEN** the user is viewing the live graph during an active recording with a non-empty snapshot
- **WHEN** the user rotates the device, causing Activity recreate
- **THEN** the recreated `LiveGraphView` reads `liveStream.value` on first attach and renders that snapshot
- **AND** the graph is not blank during the gap before the next coalesced emission

#### Scenario: Stop while live graph is open returns to empty state
- **GIVEN** the user is viewing the live graph during an active recording
- **WHEN** another component stops the recording (`sessionProfile.value` becomes `null`)
- **THEN** the live graph stops updating and transitions to "Start a recording to see live data"
