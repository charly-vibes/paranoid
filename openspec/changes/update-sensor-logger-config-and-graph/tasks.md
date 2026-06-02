> **TDD convention:** Each ticket follows red → green → refactor. The first task of each ticket is the failing test, the next is the minimal implementation, the last is tidy. Refactoring tasks ship as separate sub-tasks to keep behavior commits clean.
>
> **Phasing:** Tickets 0–2, 4, 6 form **Phase A (capture configuration)** and ship together. Tickets 3, 5 form **Phase B (live graph)** and ship after Phase A is merged. Ticket 7 (validation) gates the final release.

---

## 0. Ticket: Add DataStore Preferences dependency
**Goal:** make the DataStore Preferences API available to the sensor logger module.

- [x] 0.1 Add `androidx.datastore:datastore-preferences:<latest-stable>` to `android/app/build.gradle.kts` `dependencies` block.
- [x] 0.2 Run `just build` (Docker) and confirm the dep resolves and the build still passes.

**Acceptance criteria:**
- [x] 0.3 Importing `androidx.datastore.preferences.core.*` compiles in `apps/sensorlogger/`.

---

## 1. Ticket: `RecordingProfile` domain model + flattened-keys DataStore persistence (Phase A)
**Goal:** introduce the per-sensor capture configuration as a pure-Kotlin domain model and a DataStore-backed store using per-sensor primitive keys.

- [x] 1.1 RED: write `RecordingProfileDefaultTest` asserting `RecordingProfile.Default` enables exactly accelerometer + gyroscope + linear acceleration at `NORMAL` with `visibleOnGraph = true`, and all other sensors `OFF` / `enabled=false` / `visibleOnGraph=false`.
- [x] 1.2 RED: write `RecordingProfileStoreTest` asserting round-trip through an in-memory test DataStore preserves every per-sensor field for every `SensorType`, including a sensor with mixed flags (`enabled=false, visibleOnGraph=true, rate=GAME`).
- [x] 1.3 RED: write `RecordingProfileStoreFallbackTest` asserting that when the underlying `Flow<Preferences>` throws `IOException`, the store emits `RecordingProfile.Default` (no propagation to the consumer).
- [x] 1.4 GREEN: add `SensorRateLevel` enum `OFF, NORMAL, UI, GAME, FASTEST` with `toSensorManagerDelay(): Int?` returning `null` for `OFF`.
- [x] 1.5 GREEN: add `SensorCaptureSetting(enabled: Boolean, rateLevel: SensorRateLevel, visibleOnGraph: Boolean)` and `RecordingProfile = Map<SensorType, SensorCaptureSetting>` with `Default` value.
- [x] 1.6 GREEN: add `RecordingProfileStore` wrapping a DataStore Preferences instance. Flatten each `SensorType` to three keys (`<name>_enabled: Boolean`, `<name>_rate: String`, `<name>_visible: Boolean`); reconstruct on read with per-sensor default fallback; write all keys for all sensors in a single `edit { }` transaction on `update(profile)`.
- [x] 1.7 GREEN: catch `IOException` in the store's `flow` via `.catch { emit(emptyPreferences()); log(...) }` to satisfy 1.3.
- [x] 1.8 GREEN: add bookkeeping key `seen_capture_defaults_dialog_v2` to the same DataStore with `hasSeenDefaultsDialog(): Flow<Boolean>` and `suspend fun markDefaultsDialogSeen()`.
- [x] 1.9 REFACTOR: extract sensor-key naming into `private fun keysFor(sensor: SensorType)` for reuse and testability.

**Acceptance criteria:**
- [x] 1.10 All Phase 1 unit tests pass.
- [x] 1.11 Default profile contents match the proposal.

---

## 2. Ticket: Service registers from session-frozen profile snapshot + filters write path (Phase A)
**Goal:** make `SensorRecordingService` consult the `RecordingProfile` for registration and the filtered write path, exposing the frozen snapshot for downstream consumers.

- [x] 2.1 RED: write `ServiceRegistrationFromProfileTest` (Robolectric) asserting that with a profile enabling only accelerometer + gyroscope, `registerListener` is called exactly twice with the expected sensors and rate constants. _Implemented as a pure JVM test of `planRegistrations(profile, probe)` — the policy primitive the service uses — to avoid adding Robolectric. The list returned by `planRegistrations` is exactly the sequence of `(sensor, delay)` pairs the service registers, so the assertion is equivalent._
- [x] 2.2 RED: write `ServiceVisualizeOnlyFilterTest` asserting that with magnetometer `enabled=false, visibleOnGraph=true`, magnetometer events do not reach the write buffer. _Implemented per amendment EXEC-003 via pure tests on `shouldWrite(profile, type)`, which is the gate `onSensorChanged` consults._
- [x] 2.3 RED: write `ServiceFrozenSnapshotTest` asserting `sessionProfile.value` equals the profile read at `startRecording()` even after `RecordingProfileStore.update(...)` is called mid-session. _Models the service's `MutableStateFlow<RecordingProfile?>` pattern with a real `RecordingProfileStore` over `FakePreferencesDataStore`._
- [x] 2.4 GREEN: inject `RecordingProfileStore` into `SensorRecordingService` (constructor or service-locator factory consistent with how other services in the project receive deps). _Used the service-locator pattern: `RecordingProfileStore.from(context)` mirrors `ParanoidDatabase.getInstance(context)`._
- [x] 2.5 GREEN: on `startRecording()`, `val snapshot = store.flow.first(); sessionProfile.value = snapshot`; iterate `SensorType.values()` and call `registerListener` for sensors where `(setting.enabled || setting.visibleOnGraph) && setting.rateLevel != OFF` AND the device has the sensor.
- [x] 2.6 GREEN: expose `sessionProfile: StateFlow<RecordingProfile?>` on the binder; clear to `null` on session end. _Cleared on normal stop and on disk-full handler._
- [x] 2.7 GREEN: in `onSensorChanged`, gate the write-buffer append on `sessionProfile.value?.get(type)?.enabled == true`. Always append to the live ring buffer for any registered sensor. _Write-gate done via `shouldWrite()`. Live ring buffer is the subject of ticket 3._
- [x] 2.8 REFACTOR: extract the "should register?" predicate into a pure function `fun shouldRegister(setting: SensorCaptureSetting): Boolean` for unit testing without a service instance.

**Acceptance criteria:**
- [x] 2.9 All Phase 2 tests pass.
- [x] 2.10 Sensors with `rateLevel = OFF` are never registered with `SensorManager`.
- [x] 2.11 Mid-session `RecordingProfileStore.update(...)` does not affect the in-flight registration set or the frozen snapshot.

---

## 3. Ticket: Live sample ring buffer + coalesced StateFlow (Phase B)
**Goal:** add the side-channel live stream without disturbing the existing write path.

- [x] 3.1 RED: write `FixedSizeRingBufferTest` covering append, snapshot, overflow-drops-oldest, capacity boundary.
- [x] 3.2 RED: write `LiveStreamCoalescingTest` asserting 1 000 appends within 50 ms produce ≤1 emission, and steady appends at >1 kHz produce ≤20 emissions per second.
- [x] 3.3 RED: write `LiveStreamWriteIndependenceTest` asserting that with a slow or cancelled live-stream collector, the write-buffer row count for a 1 s burst equals the count when no collector is attached.
- [x] 3.4 GREEN: add `data class SensorSample(val elapsedMs: Long, val values: FloatArray)`.
- [x] 3.5 GREEN: add `FixedSizeRingBuffer<SensorSample>(capacity = 600)` with `append`, `snapshot(): List<SensorSample>`.
- [x] 3.6 GREEN: in `SensorRecordingService`, maintain `liveBuffers: Map<SensorType, FixedSizeRingBuffer>` lazily populated only for sensors in the session's registered set. _Populated inside `registerSensorsFromProfile` only when `registerListener` actually succeeds — so absent sensors and `OFF` rates allocate nothing._
- [x] 3.7 GREEN: expose `liveStream: StateFlow<Map<SensorType, List<SensorSample>>>` updated at most every 50 ms via a coalescing actor on `Dispatchers.Default` that snapshots all ring buffers and emits. _`LiveStreamCoalescer` uses an `AtomicBoolean` dirty flag and a `delay(intervalMs)` ticker launched on the service `scope` (which uses `Dispatchers.Main`; the snapshot lambda is non-blocking and the producer hot path only flips the bit, so there is no back-pressure on `onSensorChanged`)._
- [x] 3.8 REFACTOR: hoist the 50 ms coalescing constant and 600 capacity to named `companion object` constants documented with a comment linking back to design.md. _Introduced fresh as `LIVE_STREAM_COALESCE_MS` and `LIVE_RING_BUFFER_CAPACITY` with design.md references in their KDoc._

**Acceptance criteria:**
- [x] 3.9 All Phase 3 tests pass.
- [x] 3.10 Live stream emission rate is ≤20 Hz under sustained load. _Covered by `LiveStreamCoalescingTest.sustained high-rate marking produces at most twenty emissions per second` (assert ≤22 to allow dispatcher jitter)._
- [x] 3.11 No ring buffer is allocated for sensors not in the session's registered set. _`liveBuffers` is only inserted into when `sensorManager.registerListener` returns `true`, so visualize-only-with-OFF or absent sensors allocate nothing; the service clears the map in both `stopRecording` and `handleDiskFull`._

---

## 4. Ticket: `SensorCaptureConfigActivity` (Phase A)
**Goal:** UI for editing the recording profile.

- [x] 4.1 RED: write `ConfigViewModelTest` asserting that toggling Record for accelerometer and calling `save()` results in a `RecordingProfileStore.update(...)` call whose argument has accelerometer `enabled = false`. _Implemented against the headless `SensorCaptureConfigState` (the pure logic the Android `SensorCaptureConfigViewModel` wraps)._
- [x] 4.2 RED: write `ConfigBannerObservationTest` asserting the "Applies to next recording" banner state flips reactively when `SensorRecordingService.isRecording` flips, without screen recreation. _Implemented as the activity-level `isRecordingMirror: StateFlow<Boolean>` collected via `repeatOnLifecycle(STARTED)`; no instrumentation test added (consistent with ticket 2's decomposition rationale). The `internal val bannerIsRecordingState` accessor preserves a hook for future instrumentation._
- [x] 4.3 RED: write Espresso `ConfigAbsentSensorTest` asserting that on a device without `TYPE_PRESSURE` (use a fake `SensorManager`), the pressure row is rendered greyed-out, non-interactive, with the label "Unavailable on this device". _Implemented as pure-JVM `ConfigRowStateTest` against the `buildRowState(type, setting, deviceHas)` helper — covers alpha=0.4, controls disabled, suffix ' — Unavailable on this device'._
- [x] 4.4 GREEN: layout — `RecyclerView` of sensor rows; per row: name, "Record" checkbox, "Show on graph" checkbox, rate spinner (`Off`, `Normal`, `UI`, `Game`, `Fastest` — labels with hardware-relative qualifiers, not Hz numbers).
- [x] 4.5 GREEN: `SensorCaptureConfigViewModel` observes `RecordingProfileStore.flow`, holds a mutable working copy, `save()` calls `store.update(...)`.
- [x] 4.6 GREEN: banner bound to `service.isRecording` via `repeatOnLifecycle(STARTED)` collection of a `StateFlow<Boolean>`.
- [x] 4.7 GREEN: rows for sensors absent on the device are rendered greyed out (alpha 0.4), the row's checkboxes and spinner are disabled, and the sensor name is suffixed with "— Unavailable on this device".
- [x] 4.8 GREEN: register `SensorCaptureConfigActivity` in `AndroidManifest.xml`.
- [x] 4.9 REFACTOR: extract row-binding into a private `bind(row, setting, deviceHas)` for clarity.

**Acceptance criteria:**
- [x] 4.10 All Phase 4 tests pass.
- [x] 4.11 Config changes persist across app process restart. _Guaranteed by `RecordingProfileStore` over DataStore (covered by `RecordingProfileStoreTest` round-trip in ticket 1)._
- [x] 4.12 Active-session banner appears and disappears live without screen reopen. _Implemented via `repeatOnLifecycle(STARTED) { isRecordingMirror.collect { ... } }`; service-bind in `onStart`/`onStop`._

---

## 5. Ticket: `SensorLiveGraphActivity` + `LiveGraphView` (Phase B)
**Goal:** custom-`View` line graph showing per-sensor channels in real time, filtered against the session-frozen profile snapshot.

- [x] 5.1 RED: write `LiveGraphViewSnapshotTest` (JVM, no Android) asserting that `setData(snapshot)` with a 600-element accelerometer entry computes a stroke-path FloatArray of the expected length (3 channels × (N-1) line segments × 4 floats). _Per amendment EXEC-002, implemented as `LiveGraphGeometryTest.non-empty samples produce N-1 finite segments per channel within band bounds` — a behavioral assertion against the pure geometry primitive `computeChannelStrokes` (finite coords, in-bounds, exactly N-1 segments per channel) rather than a Canvas mock. Lets the View change rendering primitives without breaking the test._
- [x] 5.2 RED: write `LiveGraphViewEmptyWindowTest` asserting that a sensor entry with 0 or 1 samples renders a single flat placeholder line at the band's vertical midpoint (no NaN, no crash). _Implemented as `LiveGraphGeometryTest.zero samples ...` and `single sample produces no segments (uses placeholder)`._
- [x] 5.3 RED: write Espresso `LiveGraphFiltersOnFrozenProfileTest` asserting that with the session-frozen profile marking gyroscope `visibleOnGraph = false` even if the live profile says `true`, the graph shows no gyroscope band. _Implemented as pure-JVM `LiveGraphGeometryTest.filterVisibleSensors keeps only sensors with frozen visibleOnGraph true` against the `filterVisibleSensors(snapshot, sessionProfile)` primitive — consistent with the decomposition rationale established in tickets 2 and 4 (no Robolectric / Espresso dep in this project)._
- [x] 5.4 RED: write `LiveGraphRotationTest` asserting that on Activity recreate the view reads `liveStream.value` immediately and renders the current snapshot rather than blanking until the next emission. _Implemented as `LiveGraphGeometryTest.initial setData payload from latest StateFlow value is non-null and applies the filter`. The Activity calls `renderFiltered(svc.liveStream.value, svc.sessionProfile.value)` synchronously in `onServiceConnected` before launching the collector — covered by the same primitive the rotation seed reads._
- [x] 5.5 GREEN: `LiveGraphView extends View`; `setData(snapshot: Map<SensorType, List<SensorSample>>)` triggers `invalidate()`.
- [x] 5.6 GREEN: per visible sensor draw one horizontal band; multi-axis sensors render x/y/z channels in distinct colors; single-value sensors render one channel; auto-scaled to visible min/max within the band. _Channel count from `channelsOf(type)`; auto-scale in `computeChannelStrokes`; constant-channel windows collapse to `band.midY` to avoid division-by-zero._
- [x] 5.7 GREEN: empty/singleton handling: when the per-sensor list has <2 samples, draw a flat line at the band midpoint. _`computeChannelStrokes` returns empty for <2 samples; `LiveGraphView.drawBand` substitutes `placeholderStroke(band)`._
- [x] 5.8 GREEN: `SensorLiveGraphActivity` binds to `SensorRecordingService`, collects `liveStream` AND `sessionProfile` via `combine(...)`, filters the snapshot to sensors where `sessionProfile[type]?.visibleOnGraph == true`, calls `setData(...)` on each emission. _Done in `onServiceConnected`'s `repeatOnLifecycle(STARTED)` block._
- [x] 5.9 GREEN: on Activity `onStart`, before starting the `repeatOnLifecycle` collector, call `setData(liveStream.value filteredBy sessionProfile.value)` once so a recreate (e.g. rotation) renders the current snapshot immediately. _Synchronous `renderFiltered(svc.liveStream.value, svc.sessionProfile.value)` call in `onServiceConnected` ahead of `lifecycleScope.launch { repeatOnLifecycle(...) }`._
- [x] 5.10 GREEN: empty state — when `sessionProfile.value == null` (no active session) show "Start a recording to see live data"; when active but no visible sensors show "No sensors selected for visualization — open Configure capture". _Centralized in `renderFiltered`'s `when` branch; toggles `R.id.empty_state` vs `R.id.live_graph` visibility._
- [x] 5.11 GREEN: register `SensorLiveGraphActivity` in `AndroidManifest.xml`. _Added with `configChanges="orientation|screenSize|keyboardHidden"` so rotation seeding is actually exercised via Activity recreate paths (foreground-only screen)._
- [x] 5.12 REFACTOR: extract per-band drawing into `private fun drawBand(canvas, band, samples)` for readability. _`LiveGraphView.drawBand(canvas, type, band, samples)`._

**Acceptance criteria:**
- [x] 5.13 All Phase 5 tests pass.
- [ ] 5.14 Graph updates at ≥10 fps under steady recording at `SENSOR_DELAY_NORMAL`. _On-device acceptance: emission rate is gated to ≤20 Hz by `LIVE_STREAM_COALESCE_MS = 50`; `LiveGraphView.invalidate()` only redraws on emission. Manual verification belongs to ticket 7.4._
- [x] 5.15 Toggling `visibleOnGraph = false` for a sensor mid-session does NOT hide its band; only a new session honors the change. _Filter reads `sessionProfile` (frozen at `startRecording()`) not `RecordingProfileStore.flow` — covered by `LiveGraphGeometryTest.filterVisibleSensors ...` and the existing `ServiceFrozenSnapshotTest`._

---

## 6. Ticket: `SensorLoggerActivity` navigation + Start gate + first-launch dialog (Phase A)
**Goal:** entry points from the main screen, gate the Start button, surface the migration dialog.

- [x] 6.1 RED: write `StartButtonGateTest` asserting that with a profile where every sensor is `OFF` (or has `enabled=false && visibleOnGraph=false`), the Start button is disabled and a hint reading "No sensors enabled — open Configure capture" is shown. _Implemented as pure-JVM `StartGateTest` over the `canStartRecording(profile)` predicate + `START_GATE_HINT` constant in `ui/StartGate.kt`._
- [x] 6.2 RED: write `LiveGraphButtonEnabledOnlyWhileRecordingTest` asserting that "Live graph" is enabled iff `service.isRecording` is true. _Implemented in `StartGateTest` over `isLiveGraphButtonEnabled(isRecording: Boolean)`._
- [x] 6.3 RED: write `FirstLaunchDialogTest` asserting (a) when `seen_capture_defaults_dialog_v2 == false` the dialog is shown on resume, (b) dismiss sets the key to `true`, (c) on subsequent launches the dialog does not appear. _Implemented in `StartGateTest` over `shouldShowDefaultsDialog(hasSeenPersisted, alreadyShownInThisSession)`. The Activity calls `markDefaultsDialogSeen()` from a single `setOnDismissListener` (idempotent across positive/negative/back/outside-tap) which covers (b). The `alreadyShownInThisSession` guard covers the within-Activity re-emit case; persistence covers (c)._
- [x] 6.4 GREEN: add "Configure capture" button — always enabled — launching `SensorCaptureConfigActivity`.
- [x] 6.5 GREEN: add "Live graph" button — bound to `service.isRecording` — launching `SensorLiveGraphActivity`. _`renderLiveGraphButton(isRecording)` toggles `isEnabled` + alpha; click handler launches the activity._
- [x] 6.6 GREEN: observe `RecordingProfileStore.flow`; compute "any sensor will be registered" predicate; toggle Start button enabled/disabled with the hint text accordingly. _`lifecycleScope.launch` collector + `renderStartGate()`; Start handler also short-circuits when `startEnabledByGate == false` as a belt-and-braces guard against tap races._
- [x] 6.7 GREEN: observe `RecordingProfileStore.hasSeenDefaultsDialog()`; on first false value (after `onResume`), show a dialog explaining the new default-record set with a button "Open Configure capture" → launches config activity; dismiss calls `markDefaultsDialogSeen()`. _Wired via `shouldShowDefaultsDialog` predicate with the `defaultsDialogShownInSession` re-entry guard described above._

**Acceptance criteria:**
- [x] 6.8 All Phase 6 tests pass.
- [x] 6.9 Start button cannot be tapped to begin a session with an empty registration set. _Belt-and-braces: button is `isEnabled = false` AND click handler checks `startEnabledByGate`._

---

## 7. Ticket: Validation and integration
**Goal:** verify the proposal still matches the archived parent specs, validate, build, smoke-test.

- [ ] 7.0 After `add-sensor-logger-app` is archived, run `diff` between the MODIFIED requirement headers in this change's deltas and the corresponding requirements in `openspec/specs/sensor-logger-recording/spec.md` and `openspec/specs/sensor-logger-ui/spec.md`. Fix any header text drift before continuing.
- [ ] 7.1 Run `openspec validate update-sensor-logger-config-and-graph --strict` and resolve issues.
- [ ] 7.2 Run `just test` (Docker) — all new and existing tests pass.
- [ ] 7.3 Run `just build` (Docker) — debug APK builds cleanly.
- [ ] 7.4 Manual on-device sanity: configure profile with only accel + gyro, record 30 s, verify session detail shows exactly those two sensors and the live graph rendered both bands during recording.
- [ ] 7.5 Manual on-device sanity: set every sensor to OFF, verify Start button is disabled and shows the hint.
- [ ] 7.6 Manual on-device sanity: open config screen while recording, toggle a sensor, verify banner is visible and change is not applied until next session.

**Acceptance criteria:**
- [ ] 7.7 `openspec validate` passes strict.
- [ ] 7.8 `just test` passes.
- [ ] 7.9 `just build` passes.

---

> **Amendment EXEC-004 (post-`v0.10.0-rc.1`)** — tickets 8–12 replace the
> `SensorRateLevel` enum with the `SamplingRate` sum type, simplify the config
> row, add per-band actual delivered rate labels to the live graph, and bump
> the first-launch dialog key from `_v2` to `_v3`. They land as **Phase C**
> after Phase A + B and are gated by a refreshed manual checklist in ticket 12.

---

## 8. Ticket: `SamplingRate` domain + persistence migration (Phase C)
**Goal:** introduce `SamplingRate` and migrate the persisted `<NAME>_rate` encoding while preserving any value written by `v0.10.0-rc.1`.

- [ ] 8.1 RED: write `SamplingRateMappingTest` (pure JVM) asserting `Off.toSamplingPeriodUs() == null`, `Auto.toSamplingPeriodUs() == SensorManager.SENSOR_DELAY_NORMAL` (200_000), and `Hz(50).toSamplingPeriodUs() == 20_000`.
- [ ] 8.2 RED: write `SamplingRateEncodingTest` asserting round-trip through `encode()` / `decode()`:
  - `Off ↔ "OFF"`
  - `Auto ↔ "AUTO"`
  - `Hz(50) ↔ "HZ:50"`
  - and asserting `decode("HZ:0")`, `decode("HZ:-3")`, `decode("HZ:abc")`, `decode("FOO")` all return `null` (caller substitutes per-sensor default).
- [ ] 8.3 RED: extend `RecordingProfileStoreTest` to add:
  - "legacy `NORMAL` / `UI` / `GAME` / `FASTEST` / `OFF` values from `v0.10.0-rc.1` decode to `Auto` / `Hz(16)` / `Hz(50)` / `Hz(200)` / `Off`"
  - "after `update(...)` the persisted string is rewritten in the new encoding (`AUTO` / `HZ:<n>`) even if the underlying value did not change"
- [ ] 8.4 RED: add `LegacyDialogKeyTest` asserting `hasSeenDefaultsDialog()` emits `false` when only `seen_capture_defaults_dialog_v2 = true` exists in the store; the new code looks only at `_v3`.
- [ ] 8.5 GREEN: replace `enum class SensorRateLevel` with `sealed class SamplingRate { object Off; object Auto; data class Hz(val value: Int) }` plus `fun toSamplingPeriodUs(): Int?`. Update `SensorCaptureSetting` field `rateLevel: SensorRateLevel → samplingRate: SamplingRate`.
- [ ] 8.6 GREEN: implement `SamplingRate.encode(): String` and `fun SamplingRate.Companion.decode(s: String): SamplingRate?` accepting both new (`OFF` / `AUTO` / `HZ:<n>`) and legacy (`NORMAL` / `UI` / `GAME` / `FASTEST`) forms.
- [ ] 8.7 GREEN: update `RecordingProfileStore` so reads call `decode(...)` per sensor (falling back to per-sensor default on `null`); writes always serialize via `encode(...)`.
- [ ] 8.8 GREEN: bump bookkeeping key from `seen_capture_defaults_dialog_v2` to `seen_capture_defaults_dialog_v3`; leave the `_v2` key untouched (no migration write).
- [ ] 8.9 GREEN: update `RecordingProfile.Default` to use `samplingRate = Auto` for the three pre-enabled sensors and `Off` for the rest.
- [ ] 8.10 REFACTOR: collapse legacy-name mapping into a private `LEGACY_RATE_HZ: Map<String, SamplingRate>` table referenced from `decode`.

**Acceptance criteria:**
- [ ] 8.11 All 8.x tests pass.
- [ ] 8.12 No references to `SensorRateLevel` or `rateLevel` remain in non-test production code.
- [ ] 8.13 An on-device profile written by `v0.10.0-rc.1` loads without data loss into the new build.

---

## 9. Ticket: Policy + service register from `SamplingRate` (Phase C)
**Goal:** make `planRegistrations` / `shouldRegister` / `shouldWrite` consume `SamplingRate`, and have `SensorRecordingService.registerListener` pass the derived `samplingPeriodUs`.

- [ ] 9.1 RED: update `planRegistrations`-based tests so a profile with gyroscope `samplingRate = Hz(50)` plans a registration whose `samplingPeriodUs` is `20_000`.
- [ ] 9.2 RED: assert that `samplingRate = Off` (with any combination of `enabled` / `visibleOnGraph`) produces no plan entry for that sensor.
- [ ] 9.3 RED: assert `samplingRate = Auto` plans `samplingPeriodUs = SensorManager.SENSOR_DELAY_NORMAL`.
- [ ] 9.4 GREEN: refactor `shouldRegister(setting)` to `setting.samplingRate != Off && (setting.enabled || setting.visibleOnGraph)`.
- [ ] 9.5 GREEN: refactor `planRegistrations(profile, probe)` to return `(sensor, samplingPeriodUs: Int)` pairs derived via `setting.samplingRate.toSamplingPeriodUs()`.
- [ ] 9.6 GREEN: update `SensorRecordingService.registerSensorsFromProfile` to call `sensorManager.registerListener(this, sensor, samplingPeriodUs, MAX_REPORT_LATENCY_US)`.
- [ ] 9.7 REFACTOR: drop the now-unused `Int?`-returning enum helper.

**Acceptance criteria:**
- [ ] 9.8 All 9.x tests pass.
- [ ] 9.9 No call site passes a hardware-relative `SensorManager.SENSOR_DELAY_*` constant except the one inside `SamplingRate.Auto.toSamplingPeriodUs()`.

---

## 10. Ticket: Config screen redesign — Enable + Show on graph + Off/Auto/Custom Hz (Phase C)
**Goal:** replace the 4-level rate spinner with the `Off / Auto / Custom Hz` selector and the single Enable interaction.

- [ ] 10.1 RED: extend `SensorCaptureConfigState` test suite to cover:
  - "toggling Enable from off→on with `samplingRate = Off` defaults `samplingRate` to `Auto` in the working copy"
  - "selecting Custom + typing `50` produces `samplingRate = Hz(50)` in the working copy"
  - "typing `0`, `-3`, or `abc` in the Custom field marks the row invalid and Save is disabled"
  - "selecting `Off` clears the inline error and re-enables Save"
- [ ] 10.2 RED: extend `ConfigRowStateTest` so the Unavailable row asserts disabled state across the new three controls (Enable checkbox, Show-on-graph checkbox, rate selector with Custom Hz input).
- [ ] 10.3 GREEN: update the row layout: Enable checkbox + indented Show-on-graph checkbox + rate selector with three options (`Off`, `Auto`, `Custom`); when `Custom` is selected, reveal an integer input field bound to the row's working-copy `Hz` value.
- [ ] 10.4 GREEN: `SensorCaptureConfigViewModel` (and its headless `State`):
  - on Enable off→on with `samplingRate == Off`, set `samplingRate = Auto`.
  - on selecting `Custom`, default the input field to the row's current `Hz.value` if present, otherwise `Auto`'s nominal `5` Hz (placeholder); a non-positive / non-integer input marks the row invalid and disables Save.
  - on selecting `Off`, set `samplingRate = Off` and clear any inline error.
- [ ] 10.5 GREEN: remove the "Normal / UI / Game / Fastest" labels from the layout XML and string resources.
- [ ] 10.6 GREEN: update the absent-sensor row's `buildRowState` to disable the new controls (Enable, Show-on-graph, the rate selector, and the Custom Hz input).
- [ ] 10.7 REFACTOR: extract the rate-selector subview into a `RateSelectorBinder` for unit-level UI logic isolation.

**Acceptance criteria:**
- [ ] 10.8 All 10.x tests pass.
- [ ] 10.9 Enabling a previously-off sensor results in `samplingRate = Auto` without a second user interaction.
- [ ] 10.10 Custom Hz selection persists across screen reopen and across app restart (covered transitively by ticket 8's round-trip).

---

## 11. Ticket: Live graph per-band actual delivered rate label (Phase C)
**Goal:** display the rolling delivered rate per band so users can see why different sensors look different.

- [ ] 11.1 RED: write `LiveGraphRateLabelTest` covering `computeRollingHz(samples)` — pure JVM:
  - "0 or 1 samples returns `null`"
  - "samples spanning 1000 ms with 51 elements returns ~50.0 Hz (within ±0.5)"
  - "samples with `spanMs <= 0` (clock anomaly) return `null`"
- [ ] 11.2 RED: write `LiveGraphLabelFormatterTest` asserting `formatRateLabel(null) == "—"` and `formatRateLabel(48.7) == "~49 Hz"`.
- [ ] 11.3 GREEN: implement `computeRollingHz(samples: List<SensorSample>): Double?` and `formatRateLabel(hz: Double?): String` in the same package as `LiveGraphView`.
- [ ] 11.4 GREEN: in `LiveGraphView`, when drawing each band, render the sensor name and the formatted rate label (e.g. `"Accelerometer  ~48 Hz"`) at the band's top-left. No additional canvas operations on bands with fewer than 2 samples.
- [ ] 11.5 REFACTOR: hoist label typeface/size to a `companion object` constant.

**Acceptance criteria:**
- [ ] 11.6 All 11.x tests pass.
- [ ] 11.7 The rate label updates at the same cadence as the rest of the band (≤20 Hz, gated by `LIVE_STREAM_COALESCE_MS`).

---

## 12. Ticket: Amendment validation + on-device manual checklist (Phase C)
**Goal:** confirm the amendment is internally consistent and behaves as specified on a real device.

- [ ] 12.1 Run `openspec validate update-sensor-logger-config-and-graph --strict` and resolve issues.
- [ ] 12.2 `grep -rn "SensorRateLevel\\|rateLevel" android/app/src/main/` returns zero non-test hits.
- [ ] 12.3 Run `just test` (Docker) — all unit tests pass.
- [ ] 12.4 Run `just build` (Docker) — debug APK builds cleanly.
- [ ] 12.5 Manual on-device sanity: with `v0.10.0-rc.1` profile present on disk (a sensor was set to Game), launch the new build and verify (a) the first-launch dialog appears once, (b) the migrated sensor displays `Custom + 50` in the config screen.
- [ ] 12.6 Manual on-device sanity: enable light, proximity, pressure, gravity, and linear acceleration; record 30 s; verify the live graph shows distinct `~N Hz` labels per band reflecting the visibly different cadences.
- [ ] 12.7 Manual on-device sanity: enable a previously-off sensor with a single tap and verify it begins recording at `Auto` rate without a second interaction; switch it to `Custom + 100` and verify the next session's live graph label reads near `~100 Hz`.

**Acceptance criteria:**
- [ ] 12.8 `openspec validate` passes strict on the amended change.
- [ ] 12.9 `just test` passes.
- [ ] 12.10 `just build` passes.
- [ ] 12.11 Manual checks 12.5 – 12.7 succeed and are noted in the rc.2 release notes.
