# Change: Configurable capture + live graph for sensor logger

## Why
The v1 sensor logger records every available sensor at `SENSOR_DELAY_NORMAL` with no way to opt out, no way to slow down, and no way to see what is actually being captured. This wastes battery and storage on sensors the user does not care about (e.g. magnetometer, proximity), forces a one-size-fits-all sampling rate, and gives no feedback during a session. Users want to (a) pick which sensors are recorded, (b) pick the sampling rate per sensor, and (c) watch a live line graph while recording so they can decide whether the data is meaningful before committing a long session to disk.

## What Changes
- **BREAKING (recording defaults):** `SensorRecordingService` no longer auto-registers every available sensor. It registers only the sensors enabled in the user's persisted `RecordingProfile`. Default profile on first launch enables accelerometer + gyroscope + linear acceleration at `SENSOR_DELAY_NORMAL`; all other sensors default to disabled.
- **ADDED:** Per-sensor capture configuration — each sensor has an independent enable flag and a sampling-rate selection (`SENSOR_DELAY_NORMAL` / `SENSOR_DELAY_UI` / `SENSOR_DELAY_GAME` / `SENSOR_DELAY_FASTEST`, plus an explicit "Off" that excludes the sensor from registration).
- **ADDED:** `RecordingProfile` persistence — DataStore Preferences-backed flattened key/value pairs (no Room schema change). Each sensor is persisted as three primitive keys (`<sensor>_enabled: Boolean`, `<sensor>_rate: String enum`, `<sensor>_visible: Boolean`) since DataStore Preferences only supports primitive types and `Set<String>`.
- **ADDED:** Session-frozen profile snapshot — at `startRecording()` the service reads the current profile once and exposes it as `sessionProfile: StateFlow<RecordingProfile?>` for the duration of the session. Both the write path and the live graph filter against this snapshot, not the current profile, so mid-session config edits cannot produce inconsistent behavior.
- **ADDED:** Live sample stream — `SensorRecordingService` publishes a coalesced `StateFlow<Map<SensorType, List<SensorSample>>>` (last N=600 samples per sensor; sample-rate-independent, drop-oldest) for UI subscribers. The stream is a side channel; it does NOT replace or alter the existing Room write path. `SensorSample` is defined as `(elapsedMs: Long, values: FloatArray)`.
- **ADDED:** `SensorCaptureConfigActivity` — list of sensors with two checkboxes per row ("Record" and "Show on graph") and a rate dropdown. Sensors that are not present on the device are listed but greyed out with an "Unavailable on this device" label. Changes apply on next session start; live edits during an active session are deferred with a "Applies to next recording" banner bound to a live observation of `SensorRecordingService.isRecording`.
- **ADDED:** `SensorLiveGraphActivity` — multi-channel line graph rendered with a `View`-based custom drawing surface (no third-party charting library). Each sensor present in the live stream AND marked `visibleOnGraph = true` in the session-frozen profile snapshot renders one row of channels (x/y/z or single value), auto-scaled to the visible window.
- **MODIFIED:** `SensorLoggerActivity` gains two entry points: "Configure capture" → `SensorCaptureConfigActivity` (always enabled), and "Live graph" → `SensorLiveGraphActivity` (enabled only while a recording session is active). The Start button is disabled (with a hint "No sensors enabled — open Configure capture") whenever every sensor in the profile is `OFF` or has `enabled = false` AND `visibleOnGraph = false`, so the service never has to handle an empty-profile start.
- **NON-GOAL:** Historical playback of completed sessions on the live graph. The graph is live-only in this change; replay is a separate future change.

## Phasing
The change is delivered in three strictly ordered phases inside a single openspec change:
- **Phase A — capture configuration (tickets 0–2, 4, 6).** `RecordingProfile`, DataStore persistence, service registration becoming config-driven, the config screen, the Start-button gate. Ships independently as a working improvement.
- **Phase B — live graph (tickets 3, 5).** Live sample stream side channel, `LiveGraphView`, `SensorLiveGraphActivity`. Builds on the live-stream additions in Phase A's service work.
- **Phase C — rate UX amendment (tickets 8–12, EXEC-004).** Post-`v0.10.0-rc.1` on-device feedback: the four hardware-relative levels (Normal/UI/Game/Fastest) are opaque to users and different sensors visibly deliver at different rates regardless of the chosen level. Replace `SensorRateLevel` with a `SamplingRate` sum type (`Off | Auto | Hz(Int)`), simplify the config row to a single Enable interaction (with separate "Show on graph" and a `Off / Auto / Custom Hz` rate selector that defaults to `Auto` on enable), surface the actual delivered rate per band on the live graph as a `~N Hz` annotation, and migrate the persisted `<NAME>_rate` strings (legacy values are accepted on read and rewritten in the new `AUTO` / `HZ:<n>` encoding on next save). Bookkeeping key bumps from `seen_capture_defaults_dialog_v2` to `_v3` so existing users get a one-time explainer.

## Impact
- Affected specs:
  - `sensor-logger-recording` — registration becomes config-driven; session-frozen profile snapshot; new live-stream requirement.
  - `sensor-logger-ui` — new config + live-graph screens; control screen modified.
  - `sensor-logger-data` — new `RecordingProfile` persistence (DataStore Preferences, not Room).
- Affected code:
  - `apps/sensorlogger/service/SensorRecordingService.kt` — read profile on start; freeze snapshot; conditional registration; coalesced live stream publisher.
  - `apps/sensorlogger/service/SensorEventBuffer.kt` — unchanged write path; gains ring-buffer sibling for live stream.
  - `apps/sensorlogger/config/RecordingProfile.kt` (new) — domain model.
  - `apps/sensorlogger/config/RecordingProfileStore.kt` (new) — DataStore Preferences wrapper with flattened-keys serialization.
  - `apps/sensorlogger/SensorCaptureConfigActivity.kt` + layout (new).
  - `apps/sensorlogger/SensorLiveGraphActivity.kt` + `LiveGraphView.kt` custom view (new).
  - `apps/sensorlogger/SensorLoggerActivity.kt` — add two navigation buttons + Start-button gate.
  - `android/app/build.gradle.kts` — add `androidx.datastore:datastore-preferences` dependency.
  - `AndroidManifest.xml` — register the two new activities.
- Dependency: this change applies on top of `add-sensor-logger-app` and is expected to be archived after it. Before implementation begins, the MODIFIED requirement headers in this change MUST be diffed against `openspec/specs/sensor-logger-*/spec.md` to ensure character-for-character match (see task 7.0).
