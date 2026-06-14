# Changelog

All notable changes to Paranoid are documented here.

## [v0.10.0-rc.7] — 2026-06-14 _(pre-release)_

### Sensor Logger — fix: app crashed on opening the sessions list

- **Fixed**: on devices with very long recordings (millions of events), the app
  crashed back to the measure screen whenever the database was opened. The
  shared debug log revealed the real cause: the `(sessionId, sensorType)` index
  added in rc.5 (DB migration 5→6) ran out of memory (`SQLITE_NOMEM`) while
  building the index inside the migration transaction, crashing the app on
  every launch.
- Removed that index from the schema. Migration 5→6 is now a no-op and a new
  migration 6→7 drops the index for devices that managed to create it. Building
  the index is no longer attempted, so the database always opens. The per-sensor
  breakdown still works using the existing `(sessionId, elapsedMs)` index.

## [v0.10.0-rc.6] — 2026-06-10 _(pre-release)_

### Sensor Logger — diagnostics for the sessions list

- The sessions list no longer crashes back to the measure screen if loading fails: it now catches the error, shows it on screen, and logs it.
- Added a **Share debug log** button on the Sensor Logger screen that exports the app's recent logs so issues can be diagnosed.
- Added timing/error logging around the DB migration and session loading.

## [v0.10.0-rc.5] — 2026-06-09 _(pre-release)_

### Sensor Logger — fix: long sessions would not open

- **Fixed**: opening the detail screen for very long sessions (100+ minutes / millions of events) left the screen blank and reported "nothing to export". The per-sensor breakdown count did a `GROUP BY` with no matching index, forcing a full scan of every event row. Added a `(sessionId, sensorType)` index (DB migration 5→6) so the count is a fast index scan.
- The detail screen now shows an explicit **Loading…** state and surfaces load errors instead of silently staying blank.

### Privacy invariant

- Unchanged. All data stays on-device unless you explicitly share an export.

## [v0.10.0-rc.4] — 2026-06-09 _(pre-release)_

### Sensor Logger — robust, configurable session export

- **Streaming export**: sessions are now exported by streaming events to the file in bounded pages (keyset pagination + buffered writer) instead of building the whole output in memory. A multi-hour session that would previously OOM the app now exports safely. JSON is compact and escaped; non-finite floats are written as `null`.
- **Progress & volume awareness**: the export picker shows the event count and an estimated file size; a cancellable progress dialog reports events processed / total while the export runs.
- **Resource safety**: writes to a temp file renamed on success, refuses exports that would exceed free space, cleans up partial/stale files, and supports cooperative cancellation. Incomplete sessions must be closed before export (snapshot consistency).
- **Sensor selection**: choose exactly which sensor types to include (default all).
- **Downsampling**: thin the data per sensor — *All samples*, *1 of N* (every Nth event), or *Every T* (one sample per interval). The JSON `event_count` and a live size estimate both reflect the chosen sampling.

### Privacy invariant

- Unchanged. Data only leaves the device when **you** explicitly share an export.

## [v0.10.0-rc.3] — 2026-06-06 _(pre-release)_

### Sensor Logger — session export & share

- **Export recorded sessions**: the session detail screen gains an **Export / Share** action. Pick **CSV** or **JSON** and the file is handed to the Android share sheet (reuses the same `ShareHelper`/FileProvider plumbing as Netmap).
  - **CSV**: header `elapsed_ms,sensor_type,x,y,z,accuracy` followed by one row per recorded event.
  - **JSON**: session metadata (`session_id`, `started_at`, optional `ended_at`, `event_count`) plus an `events` array.
- Serialization runs off the main thread; exported event ordering is now deterministic (`ORDER BY elapsedMs, id`).

### Privacy invariant

- Unchanged. Data only leaves the device when **you** explicitly share an export.

## [v0.10.0-rc.2] — 2026-06-05 _(pre-release)_

### Sensor Logger — rate UX redesign (amendment EXEC-004, builds on rc.1)

- **Rate model**: the four opaque hardware-relative levels (Normal / UI / Game / Fastest) are replaced by an explicit `SamplingRate` of **Off**, **Auto**, or a **custom integer Hz** target. `Auto` maps to `SENSOR_DELAY_NORMAL`; a custom `N` Hz registers at `1_000_000 / N` µs (Android best-effort).
- **Simpler capture rows**: each sensor now has a single **Enable** toggle (enabling defaults the rate to `Auto` in one tap), a separate **Show on graph** toggle, and an **Off / Auto / Custom Hz** selector. The custom field rejects non-positive / non-integer input inline and disables Save until corrected.
- **Live graph honesty**: each band label now shows the sensor name **and its actual delivered rate** as a rolling `~N Hz` average over the visible window (em-dash until ≥2 samples) — so it is obvious why light, proximity, pressure, gravity, and motion sensors visibly refresh at different cadences even when configured the same.
- **Seamless upgrade from rc.1**: profiles saved by rc.1 are read transparently — legacy rate strings decode to their equivalents (`NORMAL → Auto`, `UI → 16 Hz`, `GAME → 50 Hz`, `FASTEST → 200 Hz`) and are rewritten in the new encoding on next save. A one-time dialog explains the new selector (bookkeeping key bumped `_v2 → _v3`).

### Internal

- `SensorRateLevel` enum removed in favor of the `SamplingRate` sum type (`Off | Auto | Hz(Int)`); `RecordingPolicy.planRegistrations` now yields `samplingPeriodUs` directly. New pure-Kotlin helpers `computeRollingHz` / `formatRateLabel` and the `RateMode` / `RateDraft` config-state model, all unit-tested. Hardening: non-finite sensor readings are skipped before reaching `Canvas.drawLine`; custom-Hz periods are floored at 1 µs.

### Privacy invariant

- Unchanged. All sensor data stays fully on-device.

## [v0.10.0-rc.1] — 2026-05-29 _(pre-release)_

### Sensor Logger — per-sensor capture configuration + live graph

- **BREAKING**: recording no longer captures every available sensor. By default only accelerometer, gyroscope, and linear acceleration are enabled at `SENSOR_DELAY_NORMAL`. A one-time first-launch dialog explains the change and points at the new Configure capture screen.
- **Configure capture** screen (`SensorCaptureConfigActivity`) — per-sensor row for every known `SensorType` with a "Record" checkbox (`enabled`), a "Show on graph" checkbox (`visibleOnGraph`), and a rate dropdown (`Off` / `Normal` / `UI` / `Game` / `Fastest` — Android `SENSOR_DELAY_*` levels). Profile persists across process restarts via DataStore Preferences (flattened per-sensor keys, no Room migration). Sensors absent on the device render greyed out, non-interactive, with the suffix "— Unavailable on this device". A non-dismissable "Applies to next recording" banner appears while a session is in progress and disappears live when it ends.
- **Live graph** screen (`SensorLiveGraphActivity`) — custom-`View` line graph stacked top-to-bottom, one band per visible sensor. Multi-axis sensors render x/y/z channels in distinct colors (red/teal/yellow); single-value sensors render one. Per-band auto-scaled to the visible window's min/max; constant-channel windows collapse to the band midpoint instead of dividing by zero. On rotation the view re-seeds from `liveStream.value` synchronously so the snapshot renders without a blank gap.
- **"Visualize without record"** is now a first-class mode — a sensor with `enabled = false, visibleOnGraph = true, rateLevel != OFF` is registered with `SensorManager` so its samples populate the live graph, but its events are filtered out of the persisted write buffer.
- **Start gate** — `SensorLoggerActivity` disables the Start button (and shows "No sensors enabled — open Configure capture") whenever the current profile would register no sensors. New `Configure capture` and `Live graph` navigation buttons; the latter is enabled only while a session is active.

### Internal

- **Session-frozen profile snapshot** — `SensorRecordingService.sessionProfile: StateFlow<RecordingProfile?>` is captured at `startRecording()` from `RecordingProfileStore.flow.first()` and held for the entire session. Mid-session edits to the saved profile do not affect the in-flight registration set or the write-path / live-graph filters until the next session begins.
- **Coalesced live-sample side channel** — new `liveStream: StateFlow<Map<SensorType, List<SensorSample>>>` exposed through the binder, fed from a per-sensor `FixedSizeRingBuffer<SensorSample>(capacity = 600)`. A `LiveStreamCoalescer` on `Dispatchers.Default` uses an `AtomicBoolean` dirty flag + 50 ms ticker to emit at most 20 Hz — the sensor callback hot path only flips the bit, so slow or cancelled UI subscribers cannot apply back-pressure to `onSensorChanged` or the persisted write path.
- New pure-Kotlin modules with full unit-test coverage: `RecordingProfile` / `RecordingProfileStore` / `SensorRateLevel` (config), `RecordingPolicy` (`shouldRegister` / `shouldWrite` / `planRegistrations`), `FixedSizeRingBuffer` / `LiveStreamCoalescer`, `LiveGraphGeometry` (band layout + per-channel auto-scaled stroke segments), `StartGate` (`canStartRecording` / `isLiveGraphButtonEnabled` / `shouldShowDefaultsDialog`).
- DataStore Preferences dependency (`androidx.datastore:datastore-preferences`) added to the sensor logger module; the recording-profile file uses 3 primitive keys per sensor (`<NAME>_enabled`, `<NAME>_rate`, `<NAME>_visible`) plus the bookkeeping key `seen_capture_defaults_dialog_v2`. `IOException` on read falls back to `RecordingProfile.Default` without surfacing to the consumer.

### Privacy invariant

- Unchanged. All sensor data — both recorded sessions and the live-graph ring buffer — stays fully on-device. The live stream is an in-memory `StateFlow` that never leaves the process.

## [v0.9.1] — 2026-05-20

### Branding

- Refreshed logo and Android launcher icons: cream background swapped for a vivid yellow (`#FFD60A`). All `mipmap-*dpi` launcher icons (square + round), `apple-touch-icon.png`, and `favicon.png` regenerated to match.

### Site

- Added a GitHub repo link in the public landing-page footer.
- Sensor Logger now appears on the Pages homepage: added `sensorlogger/spec/functionality.md` so `build-metadata.sh` picks the mini-app up during the Deploy workflow.

### Internal

- Added a `lefthook.yml` workflow wiring up the existing `.beads/hooks/*` (pre-commit, pre-push, post-checkout, post-merge, prepare-commit-msg) so they run consistently via lefthook.

## [v0.9.0] — 2026-05-19

### Sensor Logger — new mini-app

- New **Sensor Logger** mini-app that records motion, orientation, and environment sensors (accelerometer, gyroscope, linear acceleration, gravity, rotation vector, magnetometer, pressure, light, proximity) to a local SQLite database via Room
- Foreground service (`SensorRecordingService`, `foregroundServiceType=dataSync`) batches events at `SENSOR_DELAY_NORMAL` with a 5 s hardware max-report latency, flushes to disk every 30 s, and performs a hardware `flush()`-plus-wake-lock sequence on stop so no in-memory events are lost
- Three Activities: control screen (Start/Stop, live elapsed time, in-flight event count, combined-recording notice when NetMap is also recording), session list (reverse-chronological with an "Incomplete" badge for sessions whose recording was interrupted), and session detail (start/end/duration, total events, per-sensor breakdown, plus Mark-as-closed / Delete actions for incomplete sessions)
- Disk-full handling: `SQLiteFullException` during flush is caught, the service stops cleanly, and an error notification ("Recording stopped — storage full") is posted
- Incomplete-session recovery: sessions left with `ended_at IS NULL` (process killed mid-recording) are detected on launch via a `RecoveryState` sealed class and surfaced in the UI for manual resolution

### Privacy invariant

- All sensor data stays **fully on-device** — no network upload, no telemetry. Recordings are stored in the local Room database under the app's private data directory

### Internal

- New Room tables `sensor_sessions` and `sensor_events` with a composite `(session_id, elapsed_ms)` index and `ON DELETE CASCADE`; additive DB migration v4→v5
- `SensorRecordingService` exposes `isRecording: StateFlow<Boolean>` via a `LocalBinder` (same pattern as NetMap's `RecordingService`), enabling the Activity to drive its UI from a single source of truth and detect cross-app combined-recording state
- Pure-Kotlin helpers in `sensorlogger.model` (`countEventsBySensor`, `aggregateSensorCounts`, `prettySensorName`) are fully unit-tested with zero Android deps
- Activity layer carries no business logic — DB access and state aggregation live in `SensorLoggerViewModel`, `SensorSessionsViewModel`, and `SensorSessionDetailViewModel`
- Idle-state summary on the control screen is driven by a continuously-collected `observeAll()` Flow, so the "Last: …" summary refreshes automatically when the service commits `ended_at` after a stop (no race window with the prior session)

## [v0.8.0] — 2026-05-12

### NetMap — Approximate Antenna Locations

- New **antenna layer** on the recording detail view: each unique cell observed during a recording is shown as a colored marker at its signal-weighted estimated location, with a translucent confidence circle (drawn at zoom ≥ 12) sized by the estimate's heuristic uncertainty radius
- Tap any marker to open a **bottom sheet** showing technology, MCC/MNC, cell ID, TAC, PCI, EARFCN, sample count, strongest signal, and estimated radius
- "Low confidence" badge displayed when fewer than 5 samples contributed, or when the estimate could only be derived from PCI/EARFCN (which may collide between distinct sectors)
- New **"Show antennas"** toggle in both screens' toolbars (📡 icon) — defaults **on** in recording detail, **off** on the live map. Persisted per-screen
- Live map shows in-memory estimates that recompute every 10 measurements while recording — no waiting for the recording to stop
- "Approximate — based on observed signal" disclosure label visible whenever the antenna layer is on
- **Low-confidence estimates hidden by default** (rc.2): PCI-only fallback estimates and cells with fewer than 3 samples are filtered out of the map, and confidence circles are only drawn for high-confidence estimates. Long-press the 📡 toggle to switch to "show all" if you want to see the noise too. Driven by rc.1 smoke testing where neighbor LTE cells visually overwhelmed serving cells along the recorded path.

### Privacy invariant

- All antenna estimation is **fully on-device** — no OpenCellID, Google Geolocation, or similar lookup is ever performed
- The estimator's source files carry a `// NO-NETWORK INVARIANT` banner enforced by a JVM unit test that scans for any networking import (`java.net.*`, `okhttp3.*`, `javax.net.ssl.*`, …) and any fully-qualified networking reference

### Internal

- New `AntennaEstimator` pure function: signal-weighted centroid + 5 m single-link clustering (dampens stationary bias) + heuristic radius `max(50 m, 0.5 × spread, mean GPS accuracy)`
- New `netmap_antenna_estimates` Room table (composite PK `(recordingId, cellKey)`, additive migration v3→v4, CASCADE on recording delete)
- Estimates computed and persisted by `RecordingService` on finalize; lazily backfilled on detail-view open for legacy recordings
- Live in-memory buffer bounded at 600 samples (~20 min at 2 s interval) to keep memory predictable on entry-level devices; clustering runs on `Dispatchers.Default` to avoid Main-thread ANR

### Build / CI

- Version code & name now derived from git tags ([PARANOID-1sh](https://github.com/charly-vibes/paranoid/issues))
- Gradle dependency caching via `setup-gradle@v4` in CI
- `build-info.sh` covered by the existing shellcheck job
- GitHub Pages deploy scoped to web assets only

### Web / docs

- Hardcoded colors extracted to `colors.xml` resource
- `loadJSON` deduplicated into shared `utils.js`
- `apps-metadata.json` formatting cleaned up in `build-metadata.sh`
- `ParanoidApp` moved to top-level package; ProGuard keep rules added for `usageaudit`

## [v0.7.0] — 2026-05-05

### UsageAudit — History & Drill-Down

- **History** now lists recent days within the platform-retained usage window with total foreground time per day
- New **Day Detail** screen: full ranked app list, overnight summary, and hourly foreground-usage distribution for any past day
- New **App Detail** screen: per-app drill-down for a chosen day showing observed foreground intervals and total time
- **Share / CSV export** now scoped to the selected day from Day Detail (existing v1 CSV schema preserved)

### Internal

- Unified Today and Day Detail rendering via `DailyUsageSummary.toAppRows()`
- Extracted `RecentDaysEnumerator.startOfLocalDay()`; added DST and past-day aggregator tests
- Added ProGuard keep rules for `usageaudit`
- Moved `ParanoidApp` to top-level package
- CI now runs unit tests in the `build-android` job
- Removed dead `DiagnosticsSchema.kt`

## [v0.6.0] — 2026-04-28

### UsageAudit — New Mini-App

- New **UsageAudit** mini-app: minimal daily phone usage and overnight battery audit
- Usage access gating with system permission prompt
- Usage and battery data layer (all data stays local)
- **Today** and **Last Night** screens with activity and battery drain summaries
- **History** screen with recent nights overview
- Export to CSV and share sheet integration
- Integrated into the Paranoid hub launcher

## [v0.5.0] — 2026-04-23

### NetDiag — Exchange & Polish

- Added **snapshot exchange** via Bluetooth, QR code, and file import/export
- Added **share sheet** to export snapshots to any app
- Transport warning when comparing snapshots captured over different network types
- Timeout handling for capture operations
- Empty states for snapshot list and comparison screens
- Site fix: spec overlay now opens on card click

## [v0.4.0] — 2026-04-22

### NetDiag — Capture, Comparison & Persistence

- Full capture UI with snapshot detail and comparison results screens
- Session history view
- Room persistence layer for snapshots
- Comparison engine with diff logic
- Snapshot collectors (WiFi, cellular, DNS, HTTP)
- Initial app setup: dependencies, permissions, activities, hub entry

## [v0.3.0] — 2026-04-16

### NetMap — Export & Launcher

- App logo added to Android launcher and webpage
- Export all cells per measurement with tower position estimates
- Export correctness fixes (from Ro5 review)
- ABI splits and R8 minification to reduce APK size

## [v0.2.1] and earlier

See git history.
