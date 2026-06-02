## MODIFIED Requirements

### Requirement: Multi-sensor registration with FIFO batching
The system SHALL register only the sensors enabled in the user's persisted `RecordingProfile`. A sensor is considered "to register" if `(setting.enabled || setting.visibleOnGraph) && setting.samplingRate != Off`. Each registered sensor SHALL be registered with `SensorManager` at the `samplingPeriodUs` derived from its `SamplingRate` — `Auto` SHALL map to `SensorManager.SENSOR_DELAY_NORMAL` (200_000 µs) and `Hz(N)` SHALL map to `1_000_000 / N` µs (Android best-effort; the actual delivered rate may differ and is surfaced on the live graph per §Live graph screen) — with a 5-second FIFO batch window (`maxReportLatencyUs = 5_000_000L`). Sensors not present on the device SHALL be silently skipped even if enabled in the profile. The profile is read once at `startRecording()` and frozen as a session-scoped snapshot (see "Session-frozen profile snapshot"). It SHALL NOT change for the duration of an in-flight session, even if the user updates the profile in DataStore mid-session. The service SHALL assume the UI layer has gated the Start action so that `startRecording()` is never called with an empty registered set; if called with an empty set anyway the service SHALL stop itself immediately without creating a session row.

#### Scenario: Only profile-enabled sensors register
- **GIVEN** the user's profile enables accelerometer and gyroscope and disables all other sensors
- **WHEN** `SensorRecordingService.startRecording()` runs
- **THEN** `registerListener` is called exactly for accelerometer and gyroscope
- **AND** no other sensor is registered with `SensorManager`

#### Scenario: Visualize-only sensor is registered but excluded from writes
- **GIVEN** the user's profile sets magnetometer to `enabled = false, visibleOnGraph = true, samplingRate = Auto`
- **WHEN** recording starts
- **THEN** magnetometer is registered with `SensorManager`
- **AND** magnetometer events do not appear in the in-memory write buffer
- **AND** magnetometer events do appear in the live sample stream

#### Scenario: Per-sensor Auto rate maps to SENSOR_DELAY_NORMAL
- **GIVEN** the profile enables accelerometer at `samplingRate = Auto`
- **WHEN** `registerListener` is called for accelerometer
- **THEN** the `samplingPeriodUs` argument equals `SensorManager.SENSOR_DELAY_NORMAL`

#### Scenario: Per-sensor Hz rate maps to the inverse-frequency period
- **GIVEN** the profile enables gyroscope at `samplingRate = Hz(50)`
- **WHEN** `registerListener` is called for gyroscope
- **THEN** the `samplingPeriodUs` argument equals `1_000_000 / 50` (i.e. 20_000 µs)
- **AND** the actual delivered rate may differ — Android's `samplingPeriodUs` is best-effort and the live graph surfaces the observed rate

#### Scenario: Absent sensor skipped silently
- **GIVEN** the device has no barometer (`TYPE_PRESSURE`) and the profile enables pressure
- **WHEN** recording starts
- **THEN** all other enabled sensors register normally
- **AND** no error or warning is shown for the absent sensor

#### Scenario: Mid-session profile edits are deferred via the frozen snapshot
- **GIVEN** recording is active and the session-frozen snapshot registered accelerometer only
- **WHEN** the user opens the config screen and enables gyroscope and saves to `RecordingProfileStore`
- **THEN** the active session continues to register and record accelerometer only
- **AND** the service's `sessionProfile.value` is unchanged
- **AND** the next `startRecording()` call reads a fresh snapshot and registers both accelerometer and gyroscope

#### Scenario: Empty registered set is rejected by the service
- **GIVEN** the UI gate has been bypassed and `startRecording()` is called with a profile where no sensor qualifies for registration
- **WHEN** the service evaluates the registration set
- **THEN** no `registerListener` call is made
- **AND** no row is inserted into `sensor_sessions`
- **AND** the service stops itself

## ADDED Requirements

### Requirement: Session-frozen profile snapshot
On `startRecording()` the service SHALL read the current `RecordingProfile` from `RecordingProfileStore.flow.first()`, store it in a `MutableStateFlow<RecordingProfile?>` named `sessionProfile`, and expose `sessionProfile: StateFlow<RecordingProfile?>` on the service binder. The snapshot SHALL be the sole source of truth for write-path filtering and live-graph filtering for the duration of the session. When the session ends (clean stop or destroy) the service SHALL set `sessionProfile.value = null`. DataStore reads occurring after session start (including `RecordingProfileStore.update(...)` calls from the config screen) SHALL NOT modify the snapshot.

#### Scenario: Snapshot is exposed via binder
- **GIVEN** recording is active and a bound activity is observing `service.sessionProfile`
- **WHEN** the observer reads `sessionProfile.value`
- **THEN** it equals the profile that was current at `startRecording()`

#### Scenario: Snapshot is immutable for the session
- **GIVEN** the session-frozen snapshot has accelerometer `enabled = true`
- **WHEN** the user saves a profile to `RecordingProfileStore` with accelerometer `enabled = false`
- **THEN** `sessionProfile.value` still reports accelerometer `enabled = true` until the session ends

#### Scenario: Snapshot is cleared on session end
- **GIVEN** a session is active and `sessionProfile.value != null`
- **WHEN** the session ends via clean stop, disk-full stop, or service destroy
- **THEN** `sessionProfile.value` becomes `null` before the binder is released

---

### Requirement: Profile read failure falls back to defaults
If reading `RecordingProfileStore.flow.first()` throws or the underlying DataStore file is corrupt, the system SHALL log a warning and proceed using `RecordingProfile.Default` as the session-frozen snapshot. The user SHALL be informed via a non-blocking notice on `SensorLoggerActivity` reading "Capture settings could not be loaded — using defaults".

#### Scenario: Corrupt DataStore falls back to default profile
- **GIVEN** the DataStore file `sensor_logger_profile.preferences_pb` is corrupt and reads throw `IOException`
- **WHEN** `startRecording()` runs
- **THEN** the session-frozen snapshot equals `RecordingProfile.Default`
- **AND** a warning is logged
- **AND** `SensorLoggerActivity` displays the fallback notice on next resume

---

### Requirement: Live sample stream side channel
`SensorRecordingService` SHALL maintain an in-memory ring buffer of the most recent 600 `SensorSample` values per registered sensor, where `SensorSample` is `(elapsedMs: Long, values: FloatArray)`. It SHALL expose `liveStream: StateFlow<Map<SensorType, List<SensorSample>>>` updated at most once every 50 ms (≤20 Hz emission rate) via a coalescing actor on `Dispatchers.Default`. The live stream is a side channel: it SHALL NOT block, replace, alter, or back-pressure the Room write path, and dropped graph samples SHALL NOT cause dropped recorded samples. Subscribers receive the latest `StateFlow.value`; intermediate emissions may be skipped under back-pressure (this is intentional — only the latest snapshot is meaningful for a live view). The stream SHALL be exposed via the same in-process bound-service binder used by `sessionProfile`; remote/AIDL exposure is out of scope for this requirement.

#### Scenario: Live stream emits while recording
- **GIVEN** recording is active with accelerometer registered and a UI subscriber is collecting `liveStream`
- **WHEN** 1 second of accelerometer events has been delivered
- **THEN** the subscriber has received at least one emission containing accelerometer samples
- **AND** the per-sensor list size is ≤600

#### Scenario: Ring buffer drops oldest under load
- **GIVEN** a sensor delivers 1 000 samples while only 600 fit in the ring buffer
- **WHEN** the live stream snapshot is taken
- **THEN** the snapshot contains the 600 most recent samples
- **AND** the 400 oldest samples are not present in the snapshot

#### Scenario: Live stream coalesces under high event rate
- **GIVEN** a registered sensor delivering events at `SENSOR_DELAY_FASTEST`
- **WHEN** the live stream is observed for 1 second
- **THEN** the number of emissions is ≤20

#### Scenario: Live stream does not affect Room writes
- **GIVEN** a UI subscriber is collecting `liveStream` and recording is active
- **WHEN** the subscriber's collection is artificially slowed or cancelled
- **THEN** the count of rows inserted into `sensor_events` for that period matches the count produced when no UI subscriber is collecting

#### Scenario: No live stream churn when no sensors are registered
- **GIVEN** the profile has every sensor `OFF` and recording is not active
- **WHEN** the service is idle
- **THEN** no ring buffer holds samples and the coalescing actor performs no work
