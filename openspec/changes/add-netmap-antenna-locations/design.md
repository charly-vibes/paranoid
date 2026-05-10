## Context
NetMap already records, for every measurement, the device's GPS position plus
all visible serving and neighbor cells (`CellMeasurement` list, with
`mcc/mnc/cellId/tac/pci/earfcn` and signal metrics). That dataset is enough to
locally estimate the position of each antenna we observed during a recording,
without ever sending cell identifiers off-device. Because *paranoid* is a
privacy-first project, no external geolocation API may be consulted.

## Goals / Non-Goals
- **Goals**
  - Show a per-cell estimated location and rough accuracy circle on the map.
  - Compute the estimate fully on-device from the recording's own measurements.
  - Cache the estimate so the detail screen opens instantly the second time.
  - Keep the estimator a pure function so it is trivially unit-testable.
- **Non-Goals**
  - No external geolocation lookup (OpenCellID, Google Geolocation API, or
    similar; legacy Mozilla Location Services was decommissioned in 2024 and
    is mentioned only as a historical example of what we will not call).
  - No cross-recording aggregation (each recording's estimates stand alone).
  - No "true" trilateration with path-loss modelling — a weighted centroid is
    explicitly accepted as "approximate".
  - No editing/correcting estimates manually.
  - Antenna estimates are NOT included in any GeoJSON/CSV/KML/GPX export in
    this change; a follow-up proposal can extend the exporters.

## Decisions

### D1 — Estimator: signal-weighted centroid
- Weight per sample = `max(0, rsrp + 140)` for LTE/NR, `max(0, rscp + 130)`
  for WCDMA, `max(0, rssi + 110)` for GSM. Unknown technology → uniform
  weight 1.
- Position = weighted mean of (lat, lng) using equirectangular projection
  around the samples' bounding-box centroid. Valid up to ±60° latitude;
  beyond that fall back to Haversine.

### D2 — Sample de-duplication (single-link clustering)
- Group input samples whose GPS positions are within 5 m of each other using
  a single-link clusterer.
- Replace each cluster with a representative whose position is the cluster
  mean and whose weight is the **sum** of cluster member weights. This
  dampens stationary bias (e.g., user idle at a red light) without
  discarding signal evidence.

### D3 — Estimated uncertainty radius (heuristic)
- `radiusM = max(50.0, 0.5 × max-pairwise-distance-m, mean-gps-accuracy-m)`.
- This is a heuristic uncertainty radius, **not** a 1-σ statistical bound.

### D4 — Cell key
- Primary: `(mcc, mnc, tac, cellId)` when all four are present.
- Fallback: `(tech, pci, earfcn)`. Estimates produced from the fallback key
  carry an `isPciOnly = true` flag because PCI alone has only 504 LTE values
  and may collide between distinct physical sectors observed in the same
  recording.
- Cells with neither tuple available are dropped.

### D5 — "Strongest signal" definition
- The estimate's `strongestSignal` is the maximum `SignalLevel` enum value
  observed across all contributing samples, ordered
  `NONE < POOR < FAIR < GOOD < EXCELLENT`.

### D6 — Persistence & lifecycle
- New Room entity `antenna_estimates` keyed by `(recordingId, cellKey)`.
- Computed and upserted on recording finalize (foreground service stop hook).
- Lazily computed on `RecordingDetailActivity` open if estimates are missing
  (covers recordings created before this change).
- Recomputed whenever a recording's measurement set is mutated (currently
  no such code path exists, but this rule pre-empts a future import feature).

### D7 — Live map: incremental in-memory estimator
- `NetMapActivity` does **not** rely on persisted estimates while recording.
- When the antenna-layer toggle is enabled, the activity recomputes estimates
  from the in-memory measurement buffer every 10 new measurements and renders
  them transiently. The post-finalize pass writes the canonical version.

### D8 — Estimator function signature
- `fun estimate(recordingId: UUID, measurements: List<Measurement>): List<AntennaEstimate>`
- Pure: no I/O, no system calls, no `Context`. The `recordingId` is plumbed
  through so the returned values are immediately persistable.

### D9 — UI
- Marker icon: small filled circle, color matches strongest `SignalLevel`
  seen (same palette as the track polyline).
- Translucent fill circle: uncertainty radius, same color at 15 % alpha.
- At zoom levels below 12, render markers without uncertainty circles to
  avoid an unreadable overlap soup at country zoom.
- Tap → MaterialBottomSheet with the cell's identifiers + stats.
- Live `NetMapActivity` toggle defaults **off**; `RecordingDetailActivity`
  toggle defaults **on**. Both persist per-screen in `SharedPreferences`.
- A static "Approximate — based on observed signal" label is shown in the
  toolbar overflow as a non-interactive item whenever the layer is visible.
  (No new on-map legend is introduced.)
- Bottom sheet shows a "low confidence" badge when `sampleCount < 5` OR the
  estimate's cell key is the PCI-only fallback.

### D10 — Privacy enforcement
- The `…/netmap/estimate/` package and its callers are forbidden from
  importing any networking class.
- Enforced via a project-level `detekt` rule **and** a runtime JUnit test
  that fails on `Socket`/`HttpURLConnection` open during estimator
  execution. A `// NO-NETWORK INVARIANT` banner header sits at the top of
  the file.

## Risks / Trade-offs
- **Path bias** — centroids sit on the road. Mitigated by the "Approximate"
  label and the prominent uncertainty circle.
- **Stationary bias** — user idle pulls the centroid. Mitigated by D2.
- **PCI collisions** — addressed by D4's `isPciOnly` flag and the
  bottom-sheet badge.
- **Database migration** — additive table only; verified by inspecting
  `schemas/` JSON diff in CI.
- **Live-map cost** — every 10 measurements is O(n × cells); trivial for a
  1-hour drive (~1800 samples × ~15 cells) but worth measuring.

## Migration Plan
1. Add `antenna_estimates` table via additive Room migration `N → N+1`.
2. Confirm `schemas/` JSON diff shows only the new table.
3. No backfill needed — estimates are lazy on first detail-view open.
4. Rollback path: dropping the table is safe.

## Open Questions
- None. Earlier drafts' "position-density weighting" and "toggle persistence"
  questions are resolved by D2 and D9 respectively.
