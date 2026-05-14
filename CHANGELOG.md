# Changelog

All notable changes to Paranoid are documented here.

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
