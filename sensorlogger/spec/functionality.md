# SensorLogger — Functionality Specification

## Purpose

Records motion, orientation, and environment sensors to a local database for later inspection. Captures accelerometer, gyroscope, linear acceleration, gravity, rotation vector, magnetometer, pressure, light, and proximity events. All data stays on-device.

## Core Goals

- Foreground recording that survives screen-off and app-switching via a dedicated `dataSync` service
- Hardware-batched sensor delivery (`SENSOR_DELAY_NORMAL`, 5 s max-report latency) to minimise wakeups and battery cost
- Flush-on-stop with a hardware `SensorManager.flush()` + wake-lock sequence so no buffered events are dropped at session end
- Per-session storage in Room with cascading delete of events when a session is removed
- Disk-full graceful stop with a user-visible error notification ("Recording stopped — storage full")
- Incomplete-session recovery: sessions whose process was killed mid-recording are flagged in the UI for manual close or delete
- Combined-recording notice when NetMap is also actively recording, so the user knows battery cost is higher

## Non-Goals (v1)

- Cloud sync, telemetry, or any network upload (privacy invariant)
- Real-time charting or live waveform display of raw sensor values
- Heart-rate, step-counter, or other on-body health sensors
- Per-sensor sampling rate configuration (single profile only)
- Multi-device aggregation

## Permissions

Required at install/runtime:
1. `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` — required to keep `SensorRecordingService` alive in the background on API 34+
2. `WAKE_LOCK` — held briefly during the flush-on-stop sequence

No location, network, microphone, or storage permissions are requested.

## Screens

| Screen | Purpose |
|---|---|
| `SensorLoggerActivity` | Start/Stop control, live elapsed time, in-flight event count, idle summary of the most-recently-completed session, combined-recording notice |
| `SensorSessionsActivity` | Reverse-chronological list of all sessions with duration or "Incomplete" badge |
| `SensorSessionDetailActivity` | Start/end times, total duration, total event count, per-sensor breakdown, Mark-as-closed / Delete actions for incomplete sessions |

## Technical Approach

**Pure Kotlin + Android Views.** Same stack as NetMap (no Compose, no Hilt).

### Stack

| Concern | Approach |
|---|---|
| UI | Android Views (XML layouts) + Kotlin |
| State | `AndroidViewModel` per Activity; `StateFlow` for service-owned state |
| Database | Room (shared `ParanoidDatabase`, schema v5) |
| Service | Bound + started foreground service (`foregroundServiceType="dataSync"`) |
| Sensors | `SensorManager` with `SensorEventListener2` for batched `onFlushCompleted` callbacks |
| Async | Kotlin Coroutines + Flow |
| DI | Constructor injection (no framework) |

### Module Structure

```
android/app/src/main/kotlin/dev/charly/paranoid/apps/sensorlogger/
├── SensorLoggerActivity.kt          # Main entry point
├── SensorSessionsActivity.kt        # Session list
├── SensorSessionDetailActivity.kt   # Session detail
├── data/
│   ├── SensorEntities.kt            # Room entities (sensor_sessions, sensor_events)
│   ├── SensorDaos.kt                # DAOs + countsBySensorType GROUP BY query
│   └── SensorMappers.kt             # Domain ↔ entity mappers
├── model/                           # Pure domain (SensorSession, SensorEvent, SensorType, SessionSummary)
├── recovery/
│   └── RecoveryState.kt             # Sealed class for incomplete-session recovery
├── service/
│   ├── SensorRecordingService.kt    # Foreground service
│   ├── SensorEventBuffer.kt         # In-memory FIFO with empty-flush guard
│   └── FlushResult.kt               # Success / DiskFull
└── ui/
    ├── SensorLoggerViewModel.kt          # Idle-summary state, Flow-driven
    ├── SensorSessionsViewModel.kt        # Sessions list Flow
    └── SensorSessionDetailViewModel.kt   # Per-session detail + actions
```

## Database Schema

Additive migration v4 → v5 adds two tables to the shared `ParanoidDatabase`:

- `sensor_sessions(id, startedAt, endedAt?)`
- `sensor_events(id, sessionId, elapsedMs, sensorType, x, y, z, accuracy)` with composite index `(sessionId, elapsedMs)` and `ON DELETE CASCADE`

## Privacy Invariant

All sensor data stays on-device. The app makes no network calls, holds no networking permissions related to this mini-app, and writes only to the Room database in the app's private data directory.
