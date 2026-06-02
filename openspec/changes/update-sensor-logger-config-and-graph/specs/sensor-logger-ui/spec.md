## MODIFIED Requirements

### Requirement: Recording control screen
`SensorLoggerActivity` SHALL display a Start / Stop button and a live status area. When recording is active the status area SHALL show: elapsed time (updated every second), count of registered sensors, and total events recorded in the current session. When idle the status area SHALL show the last completed session's summary or "No recordings yet" if none exist. The screen SHALL expose two navigation entry points: "Configure capture" (always enabled) opens `SensorCaptureConfigActivity`; "Live graph" (enabled only while recording is active, bound to a live observation of `SensorRecordingService.isRecording`) opens `SensorLiveGraphActivity`. The Start button SHALL be disabled — with a hint reading "No sensors enabled — open Configure capture" — whenever the current `RecordingProfile` contains no sensor satisfying `(setting.enabled || setting.visibleOnGraph) && setting.samplingRate != Off`; the gate prevents the service from being asked to start an empty session. On the first launch of a build that includes the rate-UX amendment (EXEC-004 — detected via the bookkeeping key `seen_capture_defaults_dialog_v3` defaulting to `false`), the screen SHALL show a one-time dialog (not a toast) explaining the new rate selector ("Enable a sensor and it records at Auto rate. Tap the Hz field to set a custom target."), with a button "Open Configure capture" that launches `SensorCaptureConfigActivity`; dismissing the dialog SHALL call `RecordingProfileStore.markDefaultsDialogSeen()`. If `RecordingProfileStore` reports a profile read failure (fallback to defaults), the screen SHALL display a non-blocking notice reading "Capture settings could not be loaded — using defaults".

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
- **GIVEN** the user has saved a profile where every sensor has `samplingRate = Off` (or `enabled = false && visibleOnGraph = false`)
- **WHEN** the user views `SensorLoggerActivity`
- **THEN** the Start button is disabled
- **AND** a hint reading "No sensors enabled — open Configure capture" is visible

#### Scenario: First-launch dialog explains the new rate selector
- **GIVEN** the bookkeeping key `seen_capture_defaults_dialog_v3` is `false`
- **WHEN** `SensorLoggerActivity` becomes visible
- **THEN** a dialog appears explaining the new Enable + Auto / custom Hz selector
- **AND** the dialog offers a button that opens `SensorCaptureConfigActivity`
- **AND** dismissing the dialog calls `markDefaultsDialogSeen()`
- **AND** the dialog is not shown on subsequent launches (whether or not the legacy `_v2` key is `true`)

#### Scenario: Profile read failure shows fallback notice
- **GIVEN** `RecordingProfileStore` has fallen back to `RecordingProfile.Default` due to a read failure
- **WHEN** the user resumes `SensorLoggerActivity`
- **THEN** a non-blocking notice reads "Capture settings could not be loaded — using defaults"

## ADDED Requirements

### Requirement: Capture configuration screen
`SensorCaptureConfigActivity` SHALL present every known `SensorType` in a list, one row per sensor. Each row SHALL contain: the sensor's human-readable name, an "Enable" checkbox bound to `enabled`, an indented "Show on graph" checkbox bound to `visibleOnGraph`, and a rate selector bound to `samplingRate` exposing three mutually-exclusive states — `Off`, `Auto`, and a "Custom" mode with an integer Hz input field (`> 0`). Toggling Enable from off→on with no prior rate set SHALL default the row's `samplingRate` to `Auto` (single-interaction enablement). Choosing Custom and entering an integer N SHALL set `samplingRate = Hz(N)`; a non-positive or non-integer entry SHALL be rejected with an inline error and SHALL NOT be persisted. Disabling Enable SHALL leave the row's `samplingRate` field intact in the working copy but persisted as the user's last explicit selection (so re-enabling restores it). Changes SHALL be persisted via `RecordingProfileStore.update(...)` and SHALL take effect on the next `startRecording()` call. Rows for sensors not present on the device SHALL be rendered greyed out (reduced alpha), their checkboxes and rate controls SHALL be non-interactive, and the row SHALL display the label "Unavailable on this device" next to the sensor name. While a recording session is currently active, the screen SHALL display a non-dismissable banner at the top reading "Applies to next recording"; the banner's visibility SHALL be bound to a live observation of `SensorRecordingService.isRecording` and SHALL appear or disappear without requiring the screen to be reopened.

#### Scenario: Toggle Enable off persists and is honored next session
- **GIVEN** the user opens the config screen with accelerometer Enable on
- **WHEN** the user unchecks Enable for accelerometer and saves
- **AND** the user starts a new recording session
- **THEN** the new session contains zero accelerometer rows in `sensor_events`

#### Scenario: Enabling a previously-off sensor defaults to Auto
- **GIVEN** the user opens the config screen with magnetometer Enable off and `samplingRate = Off`
- **WHEN** the user checks Enable for magnetometer
- **THEN** the row's `samplingRate` displays as `Auto`
- **AND** saving persists `samplingRate = Auto`

#### Scenario: Custom Hz selection persists end-to-end
- **GIVEN** the user selects Custom for gyroscope and types `50`
- **WHEN** the user saves
- **THEN** `RecordingProfileStore.update(...)` is called with gyroscope `samplingRate = Hz(50)`
- **AND** the next `startRecording()` registers gyroscope with `samplingPeriodUs = 20_000` (1_000_000 / 50)
- **AND** reopening the config screen shows Custom + `50` for gyroscope

#### Scenario: Custom Hz input rejects non-positive integers
- **GIVEN** the user selects Custom for accelerometer
- **WHEN** the user types `0` or `-5` or `abc`
- **THEN** the row shows an inline error reading "Enter a positive whole number"
- **AND** Save remains disabled until a valid value is entered or the user switches back to Auto / Off

#### Scenario: Visualize without record
- **GIVEN** the user has magnetometer with Enable unchecked and Show on graph checked, and `samplingRate = Auto`
- **WHEN** the user starts a recording session and opens the live graph
- **THEN** magnetometer is shown on the graph
- **AND** the resulting session detail screen does not list magnetometer as a contributing sensor

#### Scenario: Absent sensors are greyed out
- **GIVEN** the device has no barometer (`TYPE_PRESSURE` is absent)
- **WHEN** the user opens the config screen
- **THEN** the pressure row is rendered with reduced alpha
- **AND** its Enable checkbox, Show-on-graph checkbox, and rate selector are disabled (non-interactive)
- **AND** the label "Unavailable on this device" is shown next to the sensor name

#### Scenario: Banner appears live without screen reopen
- **GIVEN** the user has the config screen open and no recording is active
- **WHEN** another component starts a recording session
- **THEN** within one observation cycle the "Applies to next recording" banner appears on the still-foregrounded config screen
- **AND** when the recording stops the banner disappears live

#### Scenario: Setting Off explicitly excludes the sensor
- **GIVEN** the user selects `Off` in the rate selector for a sensor
- **WHEN** the change is saved
- **THEN** that sensor is excluded from registration regardless of the state of Enable or Show on graph for the next session

#### Scenario: Legacy profile loads with equivalent rates
- **GIVEN** the user is upgrading from `v0.10.0-rc.1` where gyroscope was set to `rateLevel = GAME`
- **WHEN** they open the config screen after the update
- **THEN** gyroscope shows Custom + `50` (the Hz equivalent of `GAME`)
- **AND** saving rewrites the persisted value in the new `HZ:50` encoding

---

### Requirement: Live graph screen
`SensorLiveGraphActivity` SHALL display a multi-channel line graph of the live sample stream from `SensorRecordingService.liveStream`. The set of sensors rendered SHALL be the intersection of (a) sensors present in the current `liveStream` snapshot and (b) sensors for which `service.sessionProfile.value?.get(type)?.visibleOnGraph == true`. The session-frozen `sessionProfile` snapshot (not the mutable `RecordingProfileStore.flow`) SHALL be used so that mid-session edits to `visibleOnGraph` do not desynchronize the graph from the actual registered set. Multi-axis sensors (accelerometer, gyroscope, linear acceleration, gravity, rotation vector, magnetometer) SHALL render x, y, z channels in distinct colors; single-value sensors (pressure, light, proximity) SHALL render a single channel. Each rendered sensor SHALL occupy one horizontal band auto-scaled to the visible window's per-channel min/max. Each band's label SHALL include the sensor name AND the actual delivered rate computed as a rolling average over the visible window — formatted as `~N Hz` (integer rounding) when at least two samples are present, or `—` otherwise. The visible window SHALL show the last 600 samples per sensor. The view SHALL refresh on every emission of `liveStream` while the Activity is in the `STARTED` lifecycle state. On Activity recreate (e.g. configuration change such as rotation), the view SHALL immediately read `liveStream.value` once on attach so the current snapshot renders without waiting for the next emission. When recording is not active OR no sensors satisfy the rendering predicate, the screen SHALL display an explanatory empty state.

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
