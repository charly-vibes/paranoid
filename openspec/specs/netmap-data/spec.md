# netmap-data Specification

## Purpose
TBD - created by archiving change add-netmap-antenna-locations. Update Purpose after archive.
## Requirements
### Requirement: Antenna Estimate Domain Model
The system SHALL define an `AntennaEstimate` domain model with fields:
`recordingId`, `cellKey` (string identifier derived from MCC/MNC/TAC/cellId or
PCI/EARFCN fallback), `tech` (CellTech), `lat`, `lng`, `radiusM` (Float, the
estimated heuristic uncertainty radius in meters — NOT a 1-σ statistical
bound), `sampleCount` (Int), `strongestSignal` (the maximum `SignalLevel`
enum value observed across all contributing samples, ordered
`NONE < POOR < FAIR < GOOD < EXCELLENT`), and `isPciOnly` (Boolean, true
when the estimate was derived from the PCI/EARFCN fallback key).

#### Scenario: Estimate fields populated
- **WHEN** the estimator produces a result for a cell with 12 samples and a strongest LTE RSRP of −78 dBm
- **THEN** the resulting `AntennaEstimate` carries `sampleCount = 12`, `strongestSignal = EXCELLENT`, `tech = LTE`, a non-null `cellKey`, and `isPciOnly = false`

### Requirement: Antenna Location Estimator
The system SHALL provide a pure function
`fun estimate(recordingId: UUID, measurements: List<Measurement>): List<AntennaEstimate>`
that returns one `AntennaEstimate` per unique cell.

Before computing each estimate, the function SHALL:
1. Drop any input sample whose parent `Measurement` has a non-finite
   coordinate (NaN/±∞) or `gpsAccuracyM > 200.0f`. (`Measurement.location`
   is type-system-non-null, so an explicit null check is not possible.)
2. Group the remaining samples for that cell using single-link clustering
   with a 5-meter threshold, and replace each cluster with a representative
   sample whose position is the cluster mean and whose weight is the **sum**
   of cluster member weights.

The estimated position SHALL be the signal-weighted mean of the surviving
sample positions. Per-sample weight SHALL be `max(0, rsrp + 140)` for LTE/NR
and `max(0, rssi + 110)` for GSM/WCDMA/CDMA/UNKNOWN. (The original recording
spec describes WCDMA as "RSCP-based", but `CellMeasurement` does not expose
an `rscp` field; RSSI is used as a pragmatic substitute. If RSCP is added to
the model later, it SHALL be preferred for WCDMA weights.)

The estimated uncertainty radius SHALL be
`max(50.0, 0.5 × max-pairwise-distance-m, mean-gps-accuracy-m)`.

Cells with neither a `(mcc, mnc, tac, cellId)` tuple nor a `(tech, pci, earfcn)`
tuple SHALL be skipped. The primary-key check SHALL reject sentinel "unknown"
values: `mcc <= 0`, `mnc < 0`, `tac < 0`, `cellId < 0`, or
`cellId == Long.MAX_VALUE`. The fallback-key check SHALL reject `pci < 0` and
`earfcn < 0`. Estimates produced from the fallback key SHALL have
`isPciOnly = true`.

#### Scenario: Single-sample cell yields floor radius
- **WHEN** a cell is observed at exactly one GPS position with reported accuracy 8 m
- **THEN** the resulting estimate's `radiusM` is 50.0

#### Scenario: GPS accuracy floor dominates
- **WHEN** all samples for a cell are within 1 m of each other but their mean GPS accuracy is 75 m
- **THEN** the resulting estimate's `radiusM` is 75.0

#### Scenario: Stronger signal pulls centroid
- **WHEN** the same cell is observed at point A with RSRP −110 dBm and at point B with RSRP −70 dBm
- **THEN** the estimated location is closer to B than to A

#### Scenario: Stationary samples are dampened
- **WHEN** a cell is observed 200 times within a 5 m radius cluster and once 500 m away with similar signal
- **THEN** the dedup-and-merge step collapses the 200 samples into a single weighted representative, so the centroid is pulled meaningfully toward the distant sample rather than pinned to the dense cluster

#### Scenario: Sample with poor GPS accuracy is dropped
- **WHEN** a `Measurement` has `gpsAccuracyM = 350.0f` (e.g., a network-derived fallback fix)
- **THEN** none of its `CellMeasurement` entries contribute to any estimate

#### Scenario: Sample with non-finite coordinates is dropped
- **WHEN** a `Measurement.location` carries `Double.NaN` or `Double.POSITIVE_INFINITY` for `lat` or `lng`
- **THEN** none of its `CellMeasurement` entries contribute to any estimate

#### Scenario: Unidentifiable cell skipped
- **WHEN** a CellMeasurement has no `cellId`, no `pci`, and no `earfcn`
- **THEN** no `AntennaEstimate` is produced for it

#### Scenario: PCI-only fallback flagged
- **WHEN** a cell has `pci` and `earfcn` but no `mcc/mnc/tac/cellId`
- **THEN** the resulting `AntennaEstimate` has `isPciOnly = true`

#### Scenario: Sentinel "unknown" identifiers fall through to fallback
- **WHEN** a cell carries `mcc = -1`, `mnc = -1`, `tac = -1`, `cellId = -1` (or `cellId = Long.MAX_VALUE`) but has valid `pci` and `earfcn`
- **THEN** the resulting `AntennaEstimate` uses the PCI/EARFCN fallback key and has `isPciOnly = true`

### Requirement: Antenna Estimate Persistence
The system SHALL persist `AntennaEstimate` rows in a Room table keyed by
`(recordingId, cellKey)`, with `ON DELETE CASCADE` on `recordingId`. The
schema migration SHALL be additive (no changes to existing tables).
Estimates SHALL be (re)computed and upserted when a recording is finalized,
lazily when a recording's detail screen is opened and no estimates exist for
it, and (forward-looking) whenever a recording's measurement set is mutated
by any future code path. No current code path mutates measurements after
finalize, so this third trigger is a forward-looking guarantee for an import
feature; this change adds no mutation site.

#### Scenario: Estimates persist across app restarts
- **WHEN** a recording is finalized and the app is restarted
- **THEN** opening the recording's detail view loads estimates from the database without recomputation

#### Scenario: Legacy recording lazily estimated
- **WHEN** a recording created before this change is opened in detail view
- **THEN** estimates are computed on first open and persisted, and subsequent opens use the cached rows

#### Scenario: Estimates deleted with recording
- **WHEN** a recording is deleted
- **THEN** all rows in `antenna_estimates` for that recording are also deleted

### Requirement: Offline-Only Antenna Estimation
The antenna estimator and its persistence layer SHALL NOT perform any network
I/O. No external geolocation service (OpenCellID, Google Geolocation API, or
similar) SHALL be queried with cell identifiers. This invariant SHALL be
enforced by both a static `detekt` rule that fails CI when the estimator
package imports any networking class, and a runtime JUnit test that fails
when a `Socket` or `HttpURLConnection` is opened during estimator execution.

#### Scenario: No network calls during estimation
- **WHEN** estimates are computed for a recording
- **THEN** no HTTP, DNS, or socket activity is initiated by the estimator or its persistence code path

#### Scenario: CI rejects networking import in estimator package
- **WHEN** a developer adds an `import java.net.HttpURLConnection` (or any networking class) to a file under `…/netmap/estimate/`
- **THEN** the `detekt` rule fails the CI build

### Requirement: Antenna Estimates Out of Scope for Export
The existing GeoJSON, CSV, KML, and GPX exporters SHALL NOT include antenna
estimates in this change. Estimates remain a view-only feature pending a
follow-up proposal that explicitly extends the export schema.

#### Scenario: Export omits antennas
- **WHEN** the user exports a recording that has persisted antenna estimates
- **THEN** the exported GeoJSON/CSV/KML/GPX contains only the existing track and measurement features, with no antenna entries
