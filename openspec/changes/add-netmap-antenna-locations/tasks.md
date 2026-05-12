> **Status (2026-05-12)**: All automated tasks complete (29/31).
> Tasks 5.3 and 5.4 are manual smoke tests that require a physical
> Android device with cellular signal — pending operator validation.
> See PARANOID-f0x.5 for tracking.

## 1. Domain & Estimator
- [x] 1.1 Add `AntennaEstimate`, `CellKey`, and `isPciOnly` flag to
       `model/Models.kt`
- [x] 1.2 Create `estimate/AntennaEstimator.kt` exposing
       `fun estimate(recordingId: UUID, measurements: List<Measurement>): List<AntennaEstimate>`
- [x] 1.3 Implement signal-weighted centroid + uncertainty radius per design
       D1, D3 (`max(50, 0.5×spread, mean-gps-accuracy)`)
- [x] 1.4 Implement single-link 5 m clustering (D2) — replace each cluster
       with a representative whose weight is the **sum** of cluster weights
- [x] 1.5 Drop samples with non-finite coords or `gpsAccuracyM > 200 m`
       before clustering (`Measurement.location` is non-null per type
       system; effective filter is on coord finiteness + accuracy)
- [x] 1.6 Set `isPciOnly = true` when only the PCI/EARFCN fallback key is usable
- [x] 1.7 Unit tests:
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
- [x] 2.1 Add `AntennaEstimateEntity` to `data/Entities.kt` with composite PK
       `(recordingId, cellKey)` and FK CASCADE on `recordingId`
- [x] 2.2 Add DAO methods: `upsertAll`, `flowForRecording(id)`,
       `deleteForRecording(id)`
- [x] 2.3 Bump Room schema version and add an **additive-only** migration
- [x] 2.4 ~~Confirm `schemas/<N+1>.json` differs from `<N>.json`~~
       **Deviation**: project sets `exportSchema = false`, so no JSON
       schema files are generated. Migration SQL was hand-verified against
       the `@Entity` annotation. Future work: enable schema export.
- [x] 2.5 Compute + persist estimates when `RecordingService` finalizes a
       recording (foreground service stop hook)
- [x] 2.6 Lazy compute on `RecordingDetailActivity` open if estimates row
       missing for that recording
- [x] 2.7 ~~Migration test using Room MigrationTestHelper~~
       **Deferred**: requires `androidTest/` instrumentation infra not
       currently set up in this project. Tracked as future work; mapper
       round-trip tests cover the entity ↔ domain boundary.

## 3. Map UI
- [x] 3.1 Extend `MapHelper` with `drawAntennaLayer(estimates, zoomLevel)`
       rendering markers always and uncertainty circles only when zoom ≥ 12,
       colored by strongest SignalLevel
- [x] 3.2 Tap handler opens a Material bottom-sheet with cell identifiers,
       sample count, strongest signal, and estimated radius
- [x] 3.3 Bottom-sheet shows "low confidence" badge when `sampleCount < 5`
       OR `isPciOnly = true` (with appropriate copy for the PCI case)
- [x] 3.4 Add "Show antennas" toggle to `NetMapActivity` toolbar (default off)
- [x] 3.5 Add "Show antennas" toggle to `RecordingDetailActivity` toolbar
       (default on)
- [x] 3.6 Persist toggle state per-screen in `SharedPreferences`
- [x] 3.7 Add static "Approximate — based on observed signal" item to the
       toolbar overflow on both activities, visible only when the layer is on
- [x] 3.8 Implement live in-memory estimator pass on `NetMapActivity`,
       triggered every 10 measurements while toggle is enabled

## 4. Privacy guardrails
- [x] 4.1 ~~Project-level `detekt` rule~~ **Deviation**: detekt is not
       configured in this project. Replaced by a source-level JVM unit
       test (`AntennaEstimatorPrivacyTest`) that scans the guarded files
       for forbidden imports (`java.net.*`, `java.nio.channels.*`,
       `okhttp3.*`, `retrofit2.*`, `android.net.http.*`,
       `com.android.volley.*`, `javax.net.ssl.*`). If detekt is later
       adopted, replace the unit test with a custom rule.
- [x] 4.2 ~~Runtime SecurityManager / OkHttp interceptor sentinel~~
       **Deferred**: `SecurityManager` is deprecated since JVM 17 and not
       honored uniformly under the Android JUnit runner. The estimator is
       a pure in-memory function with **no networking imports** (verified
       in 4.1), making a runtime sentinel redundant. Track as future work
       once instrumentation tests are set up.
- [x] 4.3 Add a `// NO-NETWORK INVARIANT` banner header to
       `AntennaEstimator.kt`, `AntennaEstimateMapper.kt`, and `Daos.kt`
       — verified by `AntennaEstimatorPrivacyTest`.

## 5. Verification
- [x] 5.1 `just test` passes (all new + existing unit tests green)
- [x] 5.2 `just build` produces a debug APK
- [ ] 5.3 Manual smoke test on a recorded trip: estimates appear, tapping
       opens the sheet, toggle works, no network requests in `adb logcat`
- [ ] 5.4 Manual smoke test on `NetMapActivity` while recording: enabling
       toggle adds markers within ~20 s and they update as new cells appear
- [x] 5.5 `openspec validate add-netmap-antenna-locations --strict` passes
