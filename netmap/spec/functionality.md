# NetMap — Functionality Specification

## Purpose

Maps cellular network connectivity along user-traveled routes. Records GPS coordinates synchronized with real-time radio measurements (signal strength, technology, cell identity) and visualizes the data on an interactive map.

## Core Goals

- Continuous GPS tracking with low battery impact
- Real radio measurements from `TelephonyManager` (RSRP, RSRQ, RSSI, SINR)
- Offline-first: works without internet, syncs map tiles on demand
- Background recording survives screen-off and app-switching
- Export to standard formats (GeoJSON, CSV, KML, GPX)

## Non-Goals (v1)

- Cloud sync / multi-user backend
- Speed test against external servers
- Multi-carrier scanning from single device

## Technical Approach

**Pure Kotlin + Android Views.** No Compose, no Hilt, no heavy frameworks.

### Stack

| Concern | Approach |
|---|---|
| UI | Android Views (XML layouts) + Kotlin |
| Maps | MapLibre Native Android (free, no API key) |
| Database | Room (lightweight, fits well) |
| Location | FusedLocationProvider (Play Services) |
| Telephony | `TelephonyManager.allCellInfo` + `TelephonyCallback` |
| Async | Kotlin Coroutines + Flow |
| DI | Constructor injection (no framework) |
| Serialization | `org.json` (stdlib) or kotlinx.serialization |
| Logging | `android.util.Log` (no Timber) |

### Module Structure

```
android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/
├── NetMapActivity.kt          # Main entry point
├── MapFragment.kt             # Map view + recording controls
├── RecordingsActivity.kt      # List of saved recordings
├── RecordingDetailActivity.kt # One recording: map + charts
├── SettingsActivity.kt        # Preferences
├── data/
│   ├── db/                    # Room entities, DAOs, database
│   ├── TelephonySource.kt    # Cell info snapshots
│   ├── LocationSource.kt     # GPS flow wrapper
│   └── export/               # GeoJSON, CSV, KML, GPX exporters
├── model/                     # Domain models (Measurement, Recording, CellInfo)
└── service/
    └── RecordingService.kt    # Foreground service
```

## Permissions

Required at runtime:
1. `ACCESS_FINE_LOCATION` — map signal to coordinates
2. `READ_PHONE_STATE` — read cell info and signal metrics
3. `POST_NOTIFICATIONS` (Android 13+) — foreground service notification
4. `ACCESS_BACKGROUND_LOCATION` — only when background recording enabled

## Data Model

### Recording
- id (UUID), name, startedAt, endedAt, measurementCount, distanceMeters, carrier, notes

### Measurement
- id, recordingId, timestamp, lat/lng, gpsAccuracy, speed, bearing, altitude
- networkType (GSM/UMTS/LTE/NR/NONE), dataState
- cells: list of CellMeasurement (serving + neighbors)

### CellMeasurement
- isServing, technology (GSM/WCDMA/LTE/NR)
- mcc, mnc, cellId, tac, pci, earfcn, band
- rssi, rsrp, rsrq, rssnr, sinr, cqi, asuLevel, signalLevel

### Signal Level Thresholds (LTE RSRP)

| Level | dBm |
|---|---|
| EXCELLENT | >= -85 |
| GOOD | -85 to -95 |
| FAIR | -95 to -105 |
| POOR | -105 to -115 |
| NONE | < -115 |

## Recording Service

Foreground service with persistent notification:
- Collects location + telephony snapshots every ~2 seconds
- Batches DB writes (20 measurements or 30 seconds)
- Notification shows: duration, count, current signal level
- Uses `Flow.sample()` to throttle GPS updates
- `START_STICKY` for resilience

## UI Screens

| Screen | Purpose |
|---|---|
| NetMapActivity | Live map, recording controls, signal HUD |
| RecordingsActivity | List of saved recordings with stats |
| RecordingDetailActivity | Replay one recording on map, charts |
| SettingsActivity | Sample rate, distance threshold, map style |

### Map Rendering

- MapLibre Native with OpenStreetMap or Carto Dark tiles
- Track rendered as line with data-driven color by signal level
- Offline tile pack for the visible region

## Export Formats

| Format | Use case |
|---|---|
| GeoJSON | GIS tools (QGIS, Mapbox Studio) |
| CSV | Spreadsheet analysis |
| KML | Google Earth |
| GPX | Route tools (Strava, OsmAnd) |

Share via `FileProvider` + `ACTION_SEND`.

## Settings (SharedPreferences)

| Key | Default | Description |
|---|---|---|
| sample_interval_ms | 2000 | Min time between measurements |
| min_distance_m | 5.0 | Min distance between measurements |
| auto_pause | true | Pause when stationary |
| keep_screen_on | false | During active recording |
| map_style | dark | dark / light / satellite |

## Performance Targets

| Metric | Target |
|---|---|
| Cold start | < 1 s |
| Idle RAM | < 50 MB |
| Recording RAM | < 100 MB |
| Battery / hour | < 5% |
| APK contribution | < 8 MB |

## Open Questions

1. Map tiles: offline-first with bundled region, or download on demand?
2. Auto-resume recording after reboot?
3. Auto-delete old recordings?
4. Encrypt recordings at rest?
