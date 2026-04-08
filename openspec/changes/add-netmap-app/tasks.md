## 0. Prerequisite: Native Hub (PARANOID-a6z)
- [x] 0.1 Convert MainActivity from WebView to native RecyclerView hub
- [x] 0.2 Remove webkit dependency and WebView-related workarounds

## 1. Project Setup
Depends on: 0 (native hub).
- [x] 1.1 Add KSP plugin to root and app build.gradle.kts
- [x] 1.2 Add dependencies: Room (runtime + KSP compiler), MapLibre Native Android, Play Services Location, Coroutines, kotlinx.serialization
- [x] 1.3 Add permissions to AndroidManifest.xml (location, phone state, foreground service, notifications, internet)
- [x] 1.4 Register NetMapActivity, RecordingsActivity, RecordingDetailActivity, and RecordingService in AndroidManifest.xml
- [x] 1.5 Verify project compiles with new dependencies
- [x] 1.6 Delete `netmap-android-spec.md` (outdated Compose/Hilt spec superseded by `netmap/spec/functionality.md`)

## 2. Data Layer (netmap-data)
Depends on: 1.
- [x] 2.1 Create domain models: Recording, Measurement, CellMeasurement, GeoPoint, enums (CellTech, NetworkType, DataState, SignalLevel)
- [x] 2.2 Create signal level computation function with LTE RSRP thresholds + unit tests
- [x] 2.3 Create Room entities: RecordingEntity, MeasurementEntity (with cellsJson TypeConverter)
- [x] 2.4 Create DAOs: RecordingDao (CRUD + empty-recording cleanup), MeasurementDao (batch insert, query by recording)
- [x] 2.5 Create ParanoidDatabase (Room database class)
- [x] 2.6 Write unit tests for signal level computation and cell JSON converter

## 3. Data Sources (netmap-recording)
Depends on: 1, 2.
- [x] 3.1 Create LocationSource: Flow wrapper around FusedLocationProviderClient (configurable interval/distance, defaults 2s/5m)
- [x] 3.2 Create TelephonySource: snapshot function mapping CellInfo subclasses to CellMeasurement + unit tests for cell mapping
- [x] 3.3 Handle null/empty allCellInfo gracefully (empty cells list, no crash)

## 4. Recording Service (netmap-recording)
Depends on: 2, 3.
- [x] 4.1 Create notification channel setup
- [x] 4.2 Create RecordingService: foreground service (START_STICKY) combining location + telephony
- [x] 4.3 Implement measurement buffering and batch DB writes (20 items or 30s)
- [x] 4.4 Implement notification updates (duration, count, signal level every ~5s)
- [x] 4.5 Implement onDestroy flush: save buffered measurements and mark recording ended on service kill
- [x] 4.6 Implement service binding so Activity receives live measurement Flow

## 5. Map UI (netmap-map-ui)
Depends on: 2, 3, 4.
- [x] 5.1 Create NetMapActivity layout (XML): MapLibre MapView, signal HUD overlay, Start/Stop button, toolbar with overflow menu
- [x] 5.2 Implement NetMapActivity: map initialization with Carto Dark Matter tiles, location centering
- [x] 5.3 Implement runtime permission flow: request in order, rationale dialogs, "Open Settings" for permanently denied
- [x] 5.4 Wire Start/Stop buttons to RecordingService (bind/unbind lifecycle)
- [x] 5.5 Implement signal-colored polyline rendering on the map
- [x] 5.6 Implement signal HUD (RSRP, network type, carrier, "No signal" fallback)
- [x] 5.7 Implement toolbar navigation: overflow menu with "Recordings" item
- [x] 5.8 Create RecordingsActivity layout and implementation (RecyclerView list, long-press delete, empty state)
- [x] 5.9 Create RecordingDetailActivity layout and implementation (map + stats overlay, toolbar with back + export)

## 6. Export (netmap-export)
Depends on: 2. Can run in parallel with 5.
- [x] 6.1 Implement GeoJSON exporter + unit test
- [x] 6.2 Implement CSV exporter + unit test
- [x] 6.3 Implement KML exporter + unit test
- [x] 6.4 Implement GPX exporter + unit test
- [x] 6.5 Configure FileProvider and share intent helper
- [x] 6.6 Add export menu to RecordingDetailActivity (format picker dialog -> share sheet)

## 7. Polish
Depends on: all above.
- [x] 7.1 Dark theme styling for all NetMap screens (matching Paranoid dark theme: #121212 background, light text)
- [x] 7.2 Empty states (no recordings, no signal, no map tiles)
- [x] 7.3 Verify build and release workflow produces working APK with NetMap
