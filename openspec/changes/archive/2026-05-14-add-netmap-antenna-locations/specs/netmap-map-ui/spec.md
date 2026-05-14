## ADDED Requirements

Depends on: netmap-data (Antenna Estimate domain model and persistence)

### Requirement: Antenna Layer Rendering
The system SHALL render an "antennas" map layer on both `NetMapActivity` and
`RecordingDetailActivity`. The layer SHALL display, for each `AntennaEstimate`,
a marker at the estimated `(lat, lng)`. When the map's zoom level is at least
12, the layer SHALL also render a translucent filled circle of `radiusM`
meters around each marker; below zoom 12 the uncertainty circles SHALL be
hidden so the layer remains readable. Marker and circle color SHALL match the
estimate's `strongestSignal` using the existing track palette
(EXCELLENT=green, GOOD=cyan, FAIR=amber, POOR=red, NONE=gray).

#### Scenario: Detail view renders persisted estimates
- **WHEN** a recording with three persisted antenna estimates is opened in detail view and the antenna layer is enabled
- **THEN** three colored markers (and three uncertainty circles when zoom ≥ 12) are drawn at the estimated locations

#### Scenario: Country-zoom hides circles
- **WHEN** the user zooms out to zoom level 8 with the antenna layer enabled
- **THEN** markers remain visible but uncertainty circles are not drawn

### Requirement: Live Map Uses In-Memory Estimates
On `NetMapActivity` while a recording is active, the system SHALL recompute
antenna estimates from the in-memory measurement buffer every 10 new
measurements when the antenna layer toggle is enabled. These transient
estimates SHALL NOT be persisted; the canonical estimates are computed and
upserted by the recording-finalize hook (see netmap-data spec).

#### Scenario: Live layer updates incrementally
- **WHEN** the antenna layer is enabled on `NetMapActivity` and the recording crosses a multiple of 10 measurements
- **THEN** the layer is re-rendered from a freshly computed in-memory estimate set, and any new cell that became identifiable in the last batch appears as a marker

#### Scenario: Live estimates are not persisted
- **WHEN** the user stops a recording mid-render of in-memory estimates
- **THEN** only the post-finalize canonical estimates appear in `antenna_estimates`, and the in-memory transient set is discarded

#### Scenario: Toggle enabled mid-recording renders immediately
- **WHEN** the user enables "Show antennas" mid-recording with a non-empty in-memory measurement buffer
- **THEN** an estimate pass runs immediately on the current buffer (without waiting for the next 10-measurement boundary) and the layer renders

### Requirement: Antenna Layer Toggle
The system SHALL provide a "Show antennas" toggle in the toolbar overflow of
both `NetMapActivity` and `RecordingDetailActivity`. The toggle's state SHALL
be persisted per-screen in `SharedPreferences`. Defaults SHALL be **off** for
`NetMapActivity` and **on** for `RecordingDetailActivity`.

#### Scenario: Toggle hides the layer
- **WHEN** the user disables "Show antennas"
- **THEN** all antenna markers and uncertainty circles are removed from the map and the polyline track remains visible

#### Scenario: Toggle persists across launches
- **WHEN** the user enables the toggle on the live map and reopens the app
- **THEN** the toggle is still enabled

### Requirement: Antenna Detail Bottom Sheet
The system SHALL show a Material bottom-sheet when the user taps an antenna
marker. The sheet SHALL display: technology, MCC/MNC (or "—" when unknown),
cell ID, TAC, PCI, EARFCN, sample count, strongest observed signal, and the
estimated radius. The sheet SHALL display a "low confidence" badge when
`sampleCount < 5` OR the estimate's `isPciOnly` flag is true.

#### Scenario: Tap opens sheet
- **WHEN** the user taps an antenna marker
- **THEN** the bottom-sheet opens populated with that cell's identifiers and stats

#### Scenario: Low confidence badge by sample count
- **WHEN** an estimate has `sampleCount = 2`
- **THEN** the bottom-sheet shows a "low confidence" badge

#### Scenario: Low confidence badge by PCI-only key
- **WHEN** an estimate has `isPciOnly = true` and `sampleCount = 50`
- **THEN** the bottom-sheet still shows a "low confidence" badge with explanatory copy ("identified by PCI only — may collide with another sector")

### Requirement: Approximate Location Disclosure
The system SHALL display the static, non-interactive label
"Approximate — based on observed signal" in the toolbar overflow menu of
both `NetMapActivity` and `RecordingDetailActivity` whenever the antenna
layer is visible, so users do not mistake the markers for true antenna
locations. No new on-map legend element is introduced.

#### Scenario: Disclosure appears with the layer
- **WHEN** the antenna layer is enabled
- **THEN** the disclosure label appears as a non-interactive item in the toolbar overflow menu

#### Scenario: Disclosure hidden with the layer
- **WHEN** the antenna layer is disabled
- **THEN** the disclosure label is not present in the toolbar overflow menu
