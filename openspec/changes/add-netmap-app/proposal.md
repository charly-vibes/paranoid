# Change: Add NetMap mini-app

## Why
NetMap is the first mini-app for Paranoid. It maps cellular network connectivity along user-traveled routes by recording GPS coordinates synchronized with real-time radio measurements (signal strength, technology, cell identity) and visualizing the data on an interactive map.

## Non-Goals (v1)
- Cloud sync / multi-user backend
- Speed tests against external servers
- Multi-carrier scanning from single device
- Offline tile packs (download on demand is sufficient for v1)
- SettingsActivity (hardcode defaults for v1; settings screen deferred to follow-up change)

## What Changes
- Add Gradle dependencies: Room, MapLibre Native Android, Play Services Location, Coroutines
- Add manifest permissions: location, phone state, foreground service, notifications
- Create data layer: Room database with Recording + Measurement entities and DAOs
- Create data sources: TelephonySource (cell info snapshots), LocationSource (GPS flow)
- Create foreground RecordingService that collects location + telephony on a timer
- Create NetMapActivity with MapLibre map, recording controls, and signal HUD
- Create RecordingsActivity to list/manage saved recordings
- Create RecordingDetailActivity to replay a recording on a map
- Create export support: GeoJSON, CSV, KML, GPX via FileProvider + share sheet
- Register all activities in AndroidManifest.xml
- Prerequisite: convert hub from WebView to native RecyclerView (separate ticket)

## Impact
- Affected specs: netmap-data, netmap-recording, netmap-map-ui, netmap-export (all new)
- Prerequisite: PARANOID-a6z (convert hub from WebView to native RecyclerView)
- Cleanup: delete `netmap-android-spec.md` (outdated Compose/Hilt version superseded by `netmap/spec/functionality.md`)
- Affected code:
  - `android/app/build.gradle.kts` (new dependencies, Room schema export, KSP plugin)
  - `android/build.gradle.kts` (KSP plugin declaration)
  - `android/app/src/main/AndroidManifest.xml` (permissions + activities)
  - `android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/` (all new code)
  - `android/app/src/main/res/layout/` (XML layouts for NetMap screens)
