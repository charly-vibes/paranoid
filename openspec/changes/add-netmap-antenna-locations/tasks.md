## 1. Domain & Estimator
- [ ] 1.1 Add `AntennaEstimate`, `CellKey`, and `isPciOnly` flag to
       `model/Models.kt`
- [ ] 1.2 Create `estimate/AntennaEstimator.kt` exposing
       `fun estimate(recordingId: UUID, measurements: List<Measurement>): List<AntennaEstimate>`
- [ ] 1.3 Implement signal-weighted centroid + uncertainty radius per design
       D1, D3 (`max(50, 0.5×spread, mean-gps-accuracy)`)
- [ ] 1.4 Implement single-link 5 m clustering (D2) — replace each cluster
       with a representative whose weight is the **sum** of cluster weights
- [ ] 1.5 Drop samples with null GPS fix before clustering
- [ ] 1.6 Set `isPciOnly = true` when only the PCI/EARFCN fallback key is usable
- [ ] 1.7 Unit tests:
       - single sample → 50 m floor radius
       - mean GPS accuracy of 75 m dominates a tight cluster
       - five collinear samples → centroid lies on the line
       - 200 stationary + 1 distant sample → centroid pulled toward distant
       - mixed serving + neighbor cells → one estimate per unique CellKey
       - cells with no usable identifiers are skipped
       - PCI-only fallback sets `isPciOnly = true`
       - measurements with null lat/lng contribute nothing
       - weighting prefers stronger RSRP over weaker

## 2. Persistence
- [ ] 2.1 Add `AntennaEstimateEntity` to `data/Entities.kt` with composite PK
       `(recordingId, cellKey)` and FK CASCADE on `recordingId`
- [ ] 2.2 Add DAO methods: `upsertAll`, `flowForRecording(id)`,
       `deleteForRecording(id)`
- [ ] 2.3 Bump Room schema version and add an **additive-only** migration
- [ ] 2.4 Confirm `schemas/<N+1>.json` differs from `<N>.json` only by
       addition of the new table — verify in CI
- [ ] 2.5 Compute + persist estimates when `RecordingService` finalizes a
       recording (foreground service stop hook)
- [ ] 2.6 Lazy compute on `RecordingDetailActivity` open if estimates row
       missing for that recording
- [ ] 2.7 Migration test: opening DB at old version succeeds and new table
       is empty

## 3. Map UI
- [ ] 3.1 Extend `MapHelper` with `drawAntennaLayer(estimates, zoomLevel)`
       rendering markers always and uncertainty circles only when zoom ≥ 12,
       colored by strongest SignalLevel
- [ ] 3.2 Tap handler opens a Material bottom-sheet with cell identifiers,
       sample count, strongest signal, and estimated radius
- [ ] 3.3 Bottom-sheet shows "low confidence" badge when `sampleCount < 5`
       OR `isPciOnly = true` (with appropriate copy for the PCI case)
- [ ] 3.4 Add "Show antennas" toggle to `NetMapActivity` toolbar (default off)
- [ ] 3.5 Add "Show antennas" toggle to `RecordingDetailActivity` toolbar
       (default on)
- [ ] 3.6 Persist toggle state per-screen in `SharedPreferences`
- [ ] 3.7 Add static "Approximate — based on observed signal" item to the
       toolbar overflow on both activities, visible only when the layer is on
- [ ] 3.8 Implement live in-memory estimator pass on `NetMapActivity`,
       triggered every 10 measurements while toggle is enabled

## 4. Privacy guardrails
- [ ] 4.1 Add a project-level `detekt` rule that fails CI when any file under
       `…/netmap/estimate/` (or its caller in `RecordingService`) imports a
       networking class (`java.net.*`, `okhttp3.*`, `java.nio.channels.*`)
- [ ] 4.2 Add a runtime JUnit test that installs a `SecurityManager`-based or
       `OkHttp` interceptor sentinel and fails when the estimator opens any
       `Socket` or `HttpURLConnection`
- [ ] 4.3 Add a `// NO-NETWORK INVARIANT` banner header to
       `AntennaEstimator.kt` and the new DAO file documenting the invariant

## 5. Verification
- [ ] 5.1 `just test` passes (all new + existing unit tests green)
- [ ] 5.2 `just build` produces a debug APK
- [ ] 5.3 Manual smoke test on a recorded trip: estimates appear, tapping
       opens the sheet, toggle works, no network requests in `adb logcat`
- [ ] 5.4 Manual smoke test on `NetMapActivity` while recording: enabling
       toggle adds markers within ~20 s and they update as new cells appear
- [ ] 5.5 `openspec validate add-netmap-antenna-locations --strict` passes
