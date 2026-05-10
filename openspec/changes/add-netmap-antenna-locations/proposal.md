# Change: Show approximate antenna locations on the NetMap map

## Why
NetMap recordings collect rich serving + neighbor cell data along the recorded
track, but the map only shows the user's own path. Operators, RF-curious users,
and field testers have no way to see *where the antennas they are talking to
actually are*. Adding a privacy-preserving, fully-offline estimate of each
unique cell's location turns the existing dataset into a coverage map without
introducing any external lookup or third-party telemetry — which is critical
for an app named "paranoid".

## What Changes
- **Domain & estimator:** Add a local antenna location estimator that, for any
  set of measurements belonging to a single unique cell (keyed by
  `(mcc, mnc, tac, cellId)`, falling back to `(tech, pci, earfcn)` with a
  "low-confidence: PCI-only" flag because PCI has only 504 LTE values and can
  collide), computes a signal-weighted centroid and an estimated uncertainty
  radius derived from the spread of observations and per-sample GPS accuracy.
- **Persistence:** Persist a derived `AntennaEstimate` per recording, computed
  on recording finalize and (for legacy recordings) lazily on first detail-view
  open.
- **Rendering:** Render estimated antennas on `RecordingDetailActivity` as
  markers with a translucent uncertainty circle, color-coded by strongest
  observed `SignalLevel`.
- **Interaction:** Tapping a marker opens a Material bottom-sheet showing
  technology, MCC/MNC, cell ID, TAC, PCI, EARFCN, sample count, strongest
  signal, and estimated radius, plus a "low confidence" badge when sample
  count < 5 or the estimate uses the PCI-only fallback key.
- **Live map:** `NetMapActivity` shows incrementally-updated in-memory
  estimates (recomputed every 10 measurements) when the toggle is enabled.
- **Toggle:** A "Show antennas" toolbar toggle (default off on live map,
  default on in detail) persisted per-screen in `SharedPreferences`.
- **Disclosure:** A static "Approximate — based on observed signal" string
  shown in the toolbar overflow whenever the layer is visible.
- **Privacy invariant:** No OpenCellID / OpenCelliD-like / Google Geolocation
  lookup. Enforced by a `detekt` rule that fails CI if the estimator package
  imports any networking class, plus a runtime test that asserts no
  `Socket`/`HttpURLConnection` is created during estimation.
- **Export:** Antenna estimates are explicitly **out of scope for this change**
  for the existing GeoJSON/CSV/KML/GPX exporters; a follow-up change can add
  them. The detail screen export menu retains its current track-only behavior.

## Impact
- Affected specs:
  - `netmap-data` — new `AntennaEstimate` domain model and persistence
  - `netmap-map-ui` — new map layer, toggle, and detail bottom-sheet
- Affected code:
  - [Models.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/model/Models.kt)
  - [Entities.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/data/Entities.kt)
  - [Daos.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/data/Daos.kt)
  - [ParanoidDatabase.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/data/ParanoidDatabase.kt) (additive Room migration)
  - [MapHelper.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/MapHelper.kt)
  - [NetMapActivity.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/NetMapActivity.kt)
  - [RecordingDetailActivity.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/RecordingDetailActivity.kt)
  - [RecordingService.kt](file:///var/home/sasha/para/areas/dev/gh/charly/paranoid/android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/service/RecordingService.kt) (finalize hook)
- New module: `…/netmap/estimate/AntennaEstimator.kt` (pure function, unit-tested)
