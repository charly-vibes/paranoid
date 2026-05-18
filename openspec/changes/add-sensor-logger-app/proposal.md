# Change: Add sensor logger mini-app

## Why
Users want to record device sensor data (motion, orientation, and environment) for analysis, diagnostics, or logging — entirely offline and without cloud sync. No existing Paranoid mini-app covers sensor recording. The implementation must be resource-conscious: hardware FIFO batching, minimal CPU wake time, and no continuous wake lock during steady recording.

## What Changes
- Add a new `sensor-logger` mini-app with three capabilities: recording service, data persistence, and UI.
- `sensor-logger-recording`: `SensorRecordingService` (foreground service, `foregroundServiceType="dataSync"`) owns sensor registration, FIFO batching (5 s batch window), in-memory buffer flush to Room, and a crash-safe stop sequence that acquires a `PARTIAL_WAKE_LOCK` only during flush-on-stop.
- `sensor-logger-data`: Room schema (`sensor_sessions` + `sensor_events` tables) added to the shared `ParanoidDatabase` via migration 4→5; includes incomplete-session recovery on app launch.
- `sensor-logger-ui`: `SensorLoggerActivity` (recording control), `SensorSessionsActivity` (session list), `SensorSessionDetailActivity` (per-session view); all registered in the hub app list.
- No new permissions are required for standard motion and orientation sensors. `FOREGROUND_SERVICE_DATA_SYNC` is added for Android 14+.
- Concurrent recording with NetMap is supported without code changes; a combined-active status indicator is shown when both services run simultaneously.

## Impact
- Affected specs: `sensor-logger-recording` (new), `sensor-logger-data` (new), `sensor-logger-ui` (new)
- Affected code:
  - New mini-app: `android/app/src/main/kotlin/dev/charly/paranoid/apps/sensorlogger/`
  - `ParanoidDatabase`: add `SensorSessionEntity`, `SensorEventEntity`, `SensorSessionDao`, `SensorEventDao`; bump version 4→5 with migration
  - `AndroidManifest.xml`: new service declaration (`foregroundServiceType="dataSync"`), `FOREGROUND_SERVICE_DATA_SYNC` permission (API 34+)
  - Hub activity list: add `SensorLoggerActivity` entry
