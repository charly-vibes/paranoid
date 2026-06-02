## ADDED Requirements

### Requirement: Recording profile persistence
The system SHALL persist a `RecordingProfile` — a map from `SensorType` to `{enabled: Boolean, samplingRate: SamplingRate, visibleOnGraph: Boolean}` — using `androidx.datastore.preferences` in a single preferences file named `sensor_logger_profile.preferences_pb`. Because DataStore Preferences only supports primitive types and `Set<String>`, each `SensorType` SHALL be flattened to exactly three primitive keys: `<sensorTypeName>_enabled: Boolean`, `<sensorTypeName>_rate: String` (encoded as `"OFF"`, `"AUTO"`, or `"HZ:<n>"` where `<n>` is a positive integer), and `<sensorTypeName>_visible: Boolean`. Reads SHALL reconstruct the `RecordingProfile` by reading the three keys per known `SensorType` and applying `RecordingProfile.Default` for any sensor whose keys are absent. Writes SHALL set all three keys for all known sensor types in a single `edit { }` transaction, always emitting the new encoding. The profile SHALL survive process death and app reinstall-from-backup. No Room schema migration is required.

#### Scenario: Default profile on first launch
- **GIVEN** the app has never been launched and no DataStore file exists
- **WHEN** any consumer first reads `RecordingProfileStore.flow`
- **THEN** the emitted profile enables exactly accelerometer, gyroscope, and linear acceleration at `samplingRate = Auto` with `visibleOnGraph = true`
- **AND** all other sensors have `enabled = false, visibleOnGraph = false, samplingRate = Off`

#### Scenario: Profile update persists across process death
- **GIVEN** the user has saved a profile that enables magnetometer at `samplingRate = Hz(50)`
- **WHEN** the app process is killed and relaunched
- **THEN** the next read of `RecordingProfileStore.flow` emits a profile with magnetometer `enabled = true, samplingRate = Hz(50)`

#### Scenario: Flattened keys are written atomically
- **GIVEN** a consumer calls `RecordingProfileStore.update(newProfile)`
- **WHEN** the write completes
- **THEN** all `<sensor>_enabled`, `<sensor>_rate`, `<sensor>_visible` keys are written within a single DataStore `edit { }` block
- **AND** no observer can read a partial state where some sensors reflect the new profile and others reflect the previous one

#### Scenario: Missing keys for a sensor yield that sensor's default
- **GIVEN** the DataStore file contains valid keys for accelerometer only
- **WHEN** `RecordingProfileStore.flow` emits the reconstructed profile
- **THEN** accelerometer reflects the stored values
- **AND** every other `SensorType` reflects `RecordingProfile.Default`'s value for that sensor

#### Scenario: Profile is independent of recorded data
- **GIVEN** the user has recorded several sessions
- **WHEN** the user changes the profile
- **THEN** the rows in `sensor_sessions` and `sensor_events` are unchanged
- **AND** no Room migration runs as a result of the profile change

#### Scenario: Profile read is reactive
- **GIVEN** a UI screen is observing `RecordingProfileStore.flow`
- **WHEN** another component calls `RecordingProfileStore.update(...)` with a different profile
- **THEN** the observing screen receives a new emission with the updated profile

#### Scenario: Legacy rate-string encoding loads as the equivalent SamplingRate
- **GIVEN** the DataStore file contains a legacy `<NAME>_rate` value (`"OFF"`, `"NORMAL"`, `"UI"`, `"GAME"`, or `"FASTEST"`) written by `v0.10.0-rc.1`
- **WHEN** `RecordingProfileStore.flow` reads the file
- **THEN** the corresponding `samplingRate` is `Off`, `Auto`, `Hz(16)`, `Hz(50)`, and `Hz(200)` respectively
- **AND** the next `update(...)` call rewrites the key in the new encoding (`"OFF"` / `"AUTO"` / `"HZ:<n>"`)

#### Scenario: Unknown rate-string value falls back to per-sensor default
- **GIVEN** the DataStore file contains a malformed `<NAME>_rate` value (e.g. `"HZ:0"`, `"HZ:-5"`, `"HZ:abc"`, or `"NONSENSE"`)
- **WHEN** `RecordingProfileStore.flow` reads the file
- **THEN** that sensor's `samplingRate` resolves to `RecordingProfile.Default`'s value for the sensor
- **AND** no exception is raised to the consumer

---

### Requirement: Profile read fallback on IO failure
`RecordingProfileStore.flow` SHALL catch upstream `IOException` (per the `androidx.datastore` recommendation for handling read failures) and emit `RecordingProfile.Default` to downstream consumers without propagating the exception. The store SHALL log a warning describing the failure. Subsequent `update(...)` calls SHALL overwrite the corrupt file's contents and restore normal operation.

#### Scenario: Corrupt DataStore yields default profile to consumers
- **GIVEN** the DataStore preferences file is corrupt and the underlying `Flow<Preferences>` would throw `IOException`
- **WHEN** a consumer collects `RecordingProfileStore.flow`
- **THEN** the consumer receives a `RecordingProfile.Default` emission
- **AND** no exception propagates to the consumer
- **AND** a warning is logged

#### Scenario: Update after corruption recovers the store
- **GIVEN** the consumer has just received a `RecordingProfile.Default` due to a corrupt store
- **WHEN** the consumer calls `RecordingProfileStore.update(newProfile)`
- **THEN** the write succeeds and overwrites the corrupt file
- **AND** subsequent reads emit `newProfile`

---

### Requirement: First-launch dialog bookkeeping key
The system SHALL persist a single boolean key `seen_capture_defaults_dialog_v3` in the same DataStore preferences file. The key SHALL default to `false` (absent) and SHALL be set to `true` once the user dismisses the migration dialog described in the `sensor-logger-ui` spec. The store SHALL expose `hasSeenDefaultsDialog(): Flow<Boolean>` and `suspend fun markDefaultsDialogSeen()`. The key name is versioned (`_v3` after the rate-UX amendment, EXEC-004) so users who already acknowledged the previous `_v2` defaults dialog see the new selector explainer once. The legacy `_v2` key is left in place but ignored.

#### Scenario: Key absent on first launch
- **GIVEN** the DataStore file has just been created and no dialog has been dismissed
- **WHEN** `hasSeenDefaultsDialog()` is collected
- **THEN** the first emission is `false`

#### Scenario: Dismiss persists key
- **GIVEN** the user has dismissed the first-launch dialog
- **WHEN** `markDefaultsDialogSeen()` returns
- **THEN** subsequent emissions of `hasSeenDefaultsDialog()` are `true`
- **AND** the value survives process restart

#### Scenario: Existing _v2 acknowledgement does not suppress the new dialog
- **GIVEN** the DataStore file contains `seen_capture_defaults_dialog_v2 = true` (set under `v0.10.0-rc.1`)
- **WHEN** the user first launches a build that uses the `_v3` key
- **THEN** `hasSeenDefaultsDialog()` emits `false`
- **AND** the new explainer dialog is shown once
