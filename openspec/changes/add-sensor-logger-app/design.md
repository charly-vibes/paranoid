# Design: Sensor Logger Mini-App

## Decision 1 — Shared database vs. separate database

**Options:**
- A) Add sensor entities to the existing `ParanoidDatabase` (pattern established by netmap, netdiag, usageaudit).
- B) Create a separate `sensor_logger.db` scoped to the mini-app.

**Decision: A — shared database.**

All existing mini-apps share `ParanoidDatabase`. A separate DB would require a second Room singleton, a second WAL file, and separate migration tracking. The sensor schema is purely additive (two new tables, one new DAO pair) with no naming conflicts. Migration 4→5 follows the established pattern.

---

## Decision 2 — Service binding pattern

**Options:**
- A) Bound + started service: Activity binds via `ServiceConnection` for live status; service started independently so it survives Activity lifecycle (pattern used by `RecordingService` in netmap).
- B) Started service only: Activity polls via a `BroadcastReceiver` or `SharedPreferences` flag.

**Decision: A — bound + started.**

NetMap's `RecordingService` already uses this pattern. The Activity gets a live `Binder` reference for real-time UI updates (elapsed time, event count); the service keeps running when the Activity is destroyed. No polling or broadcast bus needed.

---

## Decision 3 — Sensor selection

**Registered sensors (all available on the device at `SENSOR_DELAY_NORMAL`, 5 s FIFO batch):**

| Category    | Sensor type constants                                                       |
|-------------|-----------------------------------------------------------------------------|
| Motion      | `TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE`, `TYPE_LINEAR_ACCELERATION`, `TYPE_GRAVITY` |
| Orientation | `TYPE_ROTATION_VECTOR`, `TYPE_MAGNETIC_FIELD`                               |
| Environment | `TYPE_PRESSURE`, `TYPE_LIGHT`, `TYPE_PROXIMITY`                             |

`TYPE_ORIENTATION` is deprecated — excluded. `TYPE_HEART_RATE` / `TYPE_STEP_COUNTER` require `BODY_SENSORS` — excluded from v1. Sensors absent on the device are silently skipped at registration time; the UI shows only sensors that successfully registered.

---

## Decision 4 — Wake lock scope

A foreground service prevents process kill but does **not** prevent the CPU from entering low-power states between FIFO batch deliveries. Two distinct modes:

| Mode | Wake lock | Rationale |
|------|-----------|-----------|
| Steady recording | None | Hardware FIFO delivers batches; CPU sleeps between deliveries — intended behaviour |
| Flush-on-stop sequence | `PARTIAL_WAKE_LOCK` — acquired before `unregisterListener()`, released after Room commit | `unregisterListener()` triggers an OS-level FIFO flush; without a wake lock the CPU can sleep before the final callbacks and Room write complete. Final callbacks are awaited via a `CompletableDeferred` that resolves on the first empty-batch signal from `onSensorChanged` or after a 500 ms timeout, whichever comes first. |

---

## Decision 5 — Buffer and flush parameters

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `samplingPeriodUs` | `SENSOR_DELAY_NORMAL` (200 ms) | Sufficient for motion/orientation tracking; lowest-drain standard constant |
| `maxReportLatencyUs` | 5 000 000 µs (5 s) | Batches 5 s of events in hardware FIFO; CPU wakes once per 5 s per active sensor |
| In-memory buffer flush | Every 30 s | ≤30 s data loss on crash; ~1% extra battery/hr overhead vs. longer intervals |
| Min events before DB insert | 20 | Bulk insert is 10–100× faster than per-row; 20 events at 200 ms ≈ 4 s of data |

---

## Decision 6 — Timestamp strategy

Per-event absolute timestamps are vulnerable to wall-clock adjustments (NTP slew, DST, user timezone change). Strategy:

- `sensor_sessions.started_at` — `System.currentTimeMillis()` at session start (wall-clock; needed for display and export).
- `sensor_events.elapsed_ms` — `SystemClock.elapsedRealtime() - sessionStartElapsed` (monotonic offset; immune to clock adjustments).

At read time: absolute timestamp = `started_at + elapsed_ms`.

---

## Decision 7 — Concurrent NetMap recording

Both `SensorRecordingService` and `RecordingService` (NetMap) can run simultaneously:
- `SensorManager` multiplexes multiple `SensorEventListener` registrations on the same sensor type — no OS conflict.
- Both services write to `ParanoidDatabase` via the Room singleton; WAL mode serializes concurrent writers — blocking duration is negligible (<5 ms for small batches).
- Each service declares its own foreground notification with a distinct notification ID (NetMap uses ID 1001; SensorLogger will use 1002).
- No code coordination needed between the two services for correctness. Combined battery drain ~3–5 %/hr at recommended settings; the UI shows a combined-active indicator when both are detected running.

---

## Decision 8 — Combined-recording detection strategy

**Options:**
- A) Read `RecordingService.isRunning` companion object flag (in-process, synchronous, no API deprecation risk).
- B) Query `ActivityManager.getRunningServices()` (cross-process but deprecated in API 26+; unreliable for foreground services after Android O).

**Decision: A — `RecordingService.isRunning` companion flag.**

Option B is deprecated and returns unreliable results on Android 8+. Option A is the same approach used within the Paranoid process boundary and requires no IPC. `SensorLoggerActivity` reads the flag on `onResume()` and on service bind callback to decide whether to show the combined-recording notice.
