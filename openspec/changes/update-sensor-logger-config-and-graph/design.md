## Context
The v1 sensor logger is a write-only pipeline: hardware FIFO → callback → in-memory buffer → 30 s flush → Room. Every available sensor is registered at `SENSOR_DELAY_NORMAL`. There is no user control over what is captured and no feedback on what the data looks like. This change introduces two user-facing surfaces (capture config + live graph) and one new internal pathway (a coalesced live-sample side channel) without disturbing the existing write path.

## Goals / Non-Goals

**Goals**
- Per-sensor enable + per-sensor sampling rate, persisted across sessions.
- Independent "record" vs. "visualize" selection per sensor — visualize-without-record is a supported mode (sensor is registered for the live stream only, no Room writes for it).
- Live multi-channel line graph that reflects real sensor output during a session.
- Zero added cost when the user has not opened the live graph (no UI subscriber → no ring-buffer churn beyond the cheap append).
- No Room schema migration.

**Non-Goals**
- Replay of historical sessions on the graph (deferred).
- Cross-session profile presets / import-export.
- Adaptive rate (rate auto-tuned by available battery).

> **AMENDMENT EXEC-004** (post-`v0.10.0-rc.1` on-device feedback):
> "Custom sampling rates" was previously a Non-Goal. On-device testing
> showed that (a) the named hardware-relative levels are opaque to users,
> (b) different sensors visibly deliver at different rates even when set
> to the same level, and (c) users want a single "Enable" interaction
> per sensor rather than a 3-control row. Amendment removes the
> Non-Goal, replaces the enum with a `SamplingRate` sum type, simplifies
> the config row, and adds a per-band actual-rate label to the live
> graph. See Decisions §"SamplingRate sum type with backward-compat"
> and §"Per-band actual delivered rate label". Implemented by tickets
> 8–12 in `tasks.md`.

## Decisions

### Decision: DataStore Preferences with flattened per-sensor keys (not Proto, not Room)
**What:** Persist the profile in `androidx.datastore.preferences` under the file `sensor_logger_profile.preferences_pb`. Because Preferences only supports primitive types and `Set<String>`, the `Map<SensorType, SensorCaptureSetting>` is flattened to three primitive keys per sensor:

```
booleanPreferencesKey("<sensorTypeName>_enabled")  // e.g. "ACCELEROMETER_enabled"
stringPreferencesKey ("<sensorTypeName>_rate")     // SamplingRate encoding: "OFF"|"AUTO"|"HZ:<n>" (legacy "NORMAL"|"UI"|"GAME"|"FASTEST" accepted on read, rewritten on next save)
booleanPreferencesKey("<sensorTypeName>_visible")
```

Plus one bookkeeping key:
```
booleanPreferencesKey("seen_capture_defaults_dialog_v3")
```

`RecordingProfileStore.flow` reconstructs the `RecordingProfile` by reading the three keys per known `SensorType`, applying `RecordingProfile.Default` for any sensor whose keys are absent. `update(profile)` writes all three keys per sensor in a single `edit { ... }` transaction.

**Why:**
- Settings volume is tiny (≤9 sensors × 3 keys = 27 keys). Flattening is straightforward.
- Avoids a Room migration (v5→v6) for what is fundamentally a settings blob.
- Avoids the proto-schema overhead of Proto DataStore.
- Native `Flow<Preferences>` gives reactive observation for free.

**Alternatives considered:**
- **Room table** — rejected: needs migration; conflates "settings" with "recorded data".
- **Proto DataStore** — rejected: schema file + codegen + new build plugin. Overkill for 27 primitive keys.
- **`SharedPreferences`** — rejected: legacy, no Flow API, no async safety.
- **Single `Set<String>` of serialized JSON entries** — rejected: brittle, harder to migrate.

### Decision: Session-frozen profile snapshot exposed alongside the live stream
**What:** When `startRecording()` is called, the service:
1. Reads the current profile from `RecordingProfileStore.flow.first()`.
2. Computes the registered-sensors set and the visualize-on-graph set from that snapshot.
3. Stores the snapshot in `sessionProfile: MutableStateFlow<RecordingProfile?>`, exposed read-only on the binder.
4. Keeps the snapshot for the entire session; clears to `null` when the session ends.

The write path filters events against `sessionProfile.value`'s `enabled` flags. The live graph filters its render set against `sessionProfile.value`'s `visibleOnGraph` flags. Neither path consults `RecordingProfileStore.flow` after session start.

**Why:** Mid-session re-registration would (a) require atomic flush-and-rebind, (b) create gaps where some sensors stop and others start at different elapsed times — bad data for analysis, (c) complicate the recovery-on-crash logic that assumes a stable sensor set per session. Without a frozen snapshot exposed to UI, the live graph would observe the current (mutable) profile and could show/hide bands that do not match what the service is actually registering — a confusing UX bug.

**Alternatives considered:**
- Apply live — rejected for the reasons above.
- Freeze in service only, let UI observe the current profile — rejected because it creates the inconsistency described above.

### Decision: Live stream is a coalesced side channel, not a replacement for the write path
**What:** `SensorRecordingService` keeps the existing FIFO-batched write path unchanged. In parallel, it owns a `Map<SensorType, FixedSizeRingBuffer<SensorSample>>` (size 600) and updates it on every `onSensorChanged` call. UI subscribers observe via `liveStream: StateFlow<Map<SensorType, List<SensorSample>>>` that emits at most every 50 ms (coalesced on a `Dispatchers.Default` actor).

**Same-process binder assumption:** This design relies on `SensorRecordingService` being a same-process bound service (it is today). `StateFlow` cannot cross AIDL/binder process boundaries. If a future change moves the service to a remote/AIDL process, the live stream must switch to an `IBinder` callback interface and the per-sensor sample lists must be parcelable.

**Back-pressure:** subscribers receive the latest `StateFlow.value`; intermediate emissions may be skipped if the subscriber is slow. This is intentional — only the latest snapshot is meaningful for a live view. Future maintainers MUST NOT switch to `SharedFlow` for this stream as that would reintroduce the back-pressure cost this design avoids.

**Why:** The Room write path is correctness-critical (lossless, batched, ANR-safe). The live graph is a glanceable UI aid. Decoupling them means (a) the graph cannot cause data loss, (b) the graph cannot inflate battery cost when nobody is watching it, and (c) we can drop graph samples freely under load. The 600-sample window covers ~12 s at high rates and is sufficient for visual judgment.

**Alternatives considered:**
- Render the graph from Room rows — rejected: forces a flush before the graph updates and adds DB read pressure on the main UI cycle.
- One `StateFlow` per sensor — rejected: more boilerplate; a single map is sufficient and atomic.

### Decision: "Visualize without record" is a first-class mode
**What:** A sensor with `enabled = false, visibleOnGraph = true, samplingRate != Off` is still registered with `SensorManager` (so the live stream fills), but its events are filtered out of the in-memory write buffer (via the session-frozen `enabled` check).

**Why:** The user explicitly asked for independent visualize / record selection. Tying them together would force users to commit a sensor to disk just to glance at it.

**Trade-off:** Registering a sensor for visualization costs battery even when not recording. This is acceptable because the live graph activity is foreground-only and the user has opted in explicitly.

### Decision: Empty-profile Start is rejected at the UI layer
**What:** `SensorLoggerActivity` observes the current profile; if no sensor has `(enabled || visibleOnGraph) && samplingRate != Off`, the Start button is disabled and shows a hint pointing to "Configure capture". The service is never called with an empty registration set, so it does not need an empty-profile guard.

**Why:** UI-layer gating is simpler and gives the user immediate, actionable feedback. The alternative (let the service start, show a transient notification, self-stop) creates an empty session row, a foreground notification flicker, and a contradictory state with the existing v1 "service starts on tap" guarantee.

### Decision: Custom `View` for the line graph, no charting library
**What:** `LiveGraphView` extends `View`, overrides `onDraw`, uses `Canvas.drawLines` with `FloatArray` segment buffers. Redraws on each `liveStream` emission (≤20 Hz). On Activity recreate (e.g. rotation), the view reads `liveStream.value` immediately on first attach rather than waiting for the next emission.

**Why:** Consistent with the project's "pure Kotlin + Android Views, no Compose, no extra deps" stack. A library like MPAndroidChart would add ~1 MB and a non-trivial transitive dep tree for what is a single scrolling line plot per channel. Manual drawing is ~200 lines of well-bounded code.

**Empty / singleton window:** when a sensor's ring buffer holds 0 or 1 samples, the band draws a flat placeholder line at the band's vertical midpoint until ≥2 samples are present (no division-by-zero in auto-scale).

**Trade-off:** No built-in pinch-to-zoom or pan. Not needed for a live-only view that auto-scrolls the visible window.

### Decision: `SamplingRate` sum type with backward-compat (amendment EXEC-004)
**What:** Replace `enum SensorRateLevel { OFF, NORMAL, UI, GAME, FASTEST }` with a sealed type:

```kotlin
sealed class SamplingRate {
    object Off : SamplingRate()
    object Auto : SamplingRate()                       // = SENSOR_DELAY_NORMAL
    data class Hz(val value: Int) : SamplingRate()     // value > 0
}

fun SamplingRate.toSamplingPeriodUs(): Int? = when (this) {
    Off       -> null
    Auto      -> SensorManager.SENSOR_DELAY_NORMAL     // 200_000 µs
    is Hz     -> 1_000_000 / value                     // Android best-effort
}
```

The persisted `<NAME>_rate: String` key stays. Encoding extends to: `"OFF"`, `"AUTO"`, `"HZ:<n>"`. Legacy values are accepted on read and rewritten in the new form on the next save: `OFF→Off`, `NORMAL→Auto`, `UI→Hz(16)`, `GAME→Hz(50)`, `FASTEST→Hz(200)` (numbers chosen to match each level's nominal Android Hz target). The bookkeeping key is bumped to `seen_capture_defaults_dialog_v3` so existing `_v2`-acknowledged users get a one-time explainer for the new selector.

**Why:** A single integer Hz target is what users actually want to express; the four hardware-relative levels are a leaky abstraction that varied wildly between sensors. `Auto` preserves the "just turn it on and don't think about it" affordance — the most common path stays one toggle wide. `Hz(N)` enables power users without adding a parallel adaptive-rate concept.

**Alternatives considered:**
- Add only a 5th "Custom" enum entry with an associated number — rejected: forces the enum to grow over time and bakes the old hardware-relative names into the data model forever.
- Keep the four levels in the UI; expose Hz only via a hidden developer toggle — rejected: still requires per-sensor decision-making in the common case; the named levels remain opaque.

**Trade-off:** `Hz(N)` is a *target*; Android's `samplingPeriodUs` is best-effort. The live-graph actual-rate label (next decision) makes this observable rather than mysterious.

### Decision: Per-band actual delivered rate label (amendment EXEC-004)
**What:** `LiveGraphView` adds a small `~N Hz` annotation next to each band's sensor-name label. The value is a rolling average computed from the visible window:

```kotlin
fun computeRollingHz(samples: List<SensorSample>): Double? =
    if (samples.size < 2) null
    else {
        val spanMs = samples.last().elapsedMs - samples.first().elapsedMs
        if (spanMs <= 0) null else (samples.size - 1).toDouble() * 1000.0 / spanMs
    }
```

Updated on every `liveStream` emission (≤20 Hz alongside the existing redraw — no extra render cost).

**Why:** Honesty over control. Light, proximity, pressure, and motion sensors all deliver at physically different cadences regardless of what the user requests; without surfacing the truth users assume the app is broken or the selector is ignored. A 4-character annotation is the smallest possible disclosure that resolves the confusion.

**Alternatives considered:**
- Show *requested* Hz next to the *actual* Hz — rejected for v1: doubles the visual noise and the requested value is already on the config screen.
- Histogram of inter-sample gaps — rejected: too much UI for too little insight.

## Risks / Trade-offs

- **Risk:** Users select `SENSOR_DELAY_FASTEST` on many sensors and exceed the Room write budget → disk-full mid-session.
  - **Mitigation:** Existing disk-full handling already stops cleanly with an error notification (sensor-logger-recording §Disk-full error handling). No pre-emptive rate warning in the config UI for v1; revisit if users hit this in practice.
- **Risk:** The live stream actor is a new shared-mutable-state path that could leak the service if the activity holds a strong reference.
  - **Mitigation:** Activity collects via `repeatOnLifecycle(STARTED)` and the service exposes the StateFlow through the binder; binder unbind on activity stop releases the reference automatically.
- **Risk:** DataStore file becomes corrupt or IO fails on read.
  - **Mitigation:** `RecordingProfileStore.flow` catches `IOException` upstream of the consumer and emits `RecordingProfile.Default`, logging a warning. `SensorLoggerActivity` shows a non-blocking notice "Capture settings could not be loaded — using defaults" so the user can re-save their preferences.
- **Risk:** Sensor present in the profile but absent on the device (e.g. user moved their saved profile to a different device).
  - **Mitigation:** Config screen lists every known sensor; absent ones are greyed out and non-interactive. The service silently skips absent sensors at registration time (existing v1 behavior).
- **Trade-off:** DataStore is async (`Flow`) — the service must read the profile suspending on `startRecording()`. We accept a one-time ~5 ms suspend at session start.
- **Trade-off:** Session-frozen profile snapshots are NOT persisted across crashes. After process death, an incomplete session's "registered sensors" is reconstructed from the `sensor_events.sensor_type` rows that were flushed. Acceptable because recorded data is self-describing.

## Testing strategy

- **Unit tests (JVM, no Android dependencies):**
  - `RecordingProfile.Default` contents.
  - `SamplingRate.toSamplingPeriodUs()` mapping (`Off → null`, `Auto → SENSOR_DELAY_NORMAL`, `Hz(n) → 1_000_000 / n`).
  - Legacy `<NAME>_rate` string decode: `OFF→Off`, `NORMAL→Auto`, `UI→Hz(16)`, `GAME→Hz(50)`, `FASTEST→Hz(200)`; round-trip rewrite to new `AUTO`/`HZ:<n>` form on next save.
  - `RecordingProfileStore` flattening / unflattening round-trip with an in-memory `TestDataStore`.
  - `FixedSizeRingBuffer` append / overflow / snapshot.
  - Coalescing actor: 1 000 appends in <50 ms → ≤1 emission.
  - Service write-path filter: visualize-only sensor produces no buffer entries.
- **Robolectric / instrumentation tests:**
  - `SensorRecordingService` with a fake `SensorManager`: only profile-enabled sensors registered, with correct rate levels.
  - DataStore IO failure → fallback to `RecordingProfile.Default`.
- **UI smoke tests (Espresso, on-emulator):**
  - Config screen: toggle Record off for accelerometer → next recorded session contains no accelerometer rows.
  - Live graph: start recording with accelerometer visible → non-empty waveform within 2 s.
  - Start button disabled when every sensor is OFF.
  - "Live graph" button disabled when not recording.
  - Banner appears live when recording starts while config screen is open.

## Migration Plan

This change ships after `add-sensor-logger-app` is archived. On first launch with the new code:
1. `RecordingProfileStore` reads the DataStore file. If any required key is absent for a given sensor, the default for that sensor is used (effectively yielding `RecordingProfile.Default` on first run).
2. No Room migration is needed. Existing recorded sessions are untouched.
3. The bookkeeping key `seen_capture_defaults_dialog_v3` is checked. If `false` or absent, `SensorLoggerActivity` shows a one-time **dialog** (not toast) explaining the new `Enable + Auto / custom Hz` selector (amendment EXEC-004) with a button that opens `SensorCaptureConfigActivity`. The key is set to `true` when the dialog is dismissed. The legacy `_v2` key is left alone and ignored.
4. Old behavior (record everything) is gone — this is the BREAKING change.

Rollback: revert the APK. The DataStore file is left behind but ignored by the v1 service.

## Open Questions

- Live graph window size — fixed 600 samples or fixed time window (e.g. last 10 s)? Current decision: fixed sample count for simpler ring-buffer math; revisit if user feedback says the visible time span feels inconsistent across sensors at different rates.
