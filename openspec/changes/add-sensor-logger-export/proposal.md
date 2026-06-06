# Change: Sensor Logger session export & share

## Why
Recorded SensorLogger sessions can be observed in the detail screen, but there is
no way to get the raw data out of the device. Users need to export a session's
events (CSV/JSON) and share them via the Android share sheet for offline
analysis, exactly as Netmap recordings already allow.

## What Changes
- Add pure exporter functions that serialize a session + its events to CSV and JSON.
- Add an "Export" action to the session detail screen that loads the session's
  events and shares the chosen format through the system share sheet.
- Reuse the existing `ShareHelper`/FileProvider plumbing used by Netmap.

## Impact
- Affected specs: sensor-logger-export (new capability)
- Affected code:
  - `apps/sensorlogger/data/SensorExporters.kt` (new)
  - `apps/sensorlogger/SensorSessionDetailActivity.kt`
  - `apps/sensorlogger/ui/SensorSessionDetailViewModel.kt`
  - `res/layout/activity_sensor_session_detail.xml`
