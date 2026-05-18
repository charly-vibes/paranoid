## Android Sensor Recording Research

### Android Sensor API Core

**Entry Points:**
- `SensorManager` via `getSystemService(Context.SENSOR_SERVICE)`
- Register: `registerListener(listener, sensor, samplingPeriodUs, maxReportLatencyUs)`
- Unregister: `unregisterListener(listener)` — MANDATORY to prevent drain

**Sensor Types by Category:**
- Motion: TYPE_ACCELEROMETER, TYPE_GYROSCOPE, TYPE_LINEAR_ACCELERATION, TYPE_GRAVITY
- Orientation: TYPE_MAGNETIC_FIELD, TYPE_ROTATION_VECTOR (TYPE_ORIENTATION deprecated)
- Environment: TYPE_PRESSURE, TYPE_LIGHT, TYPE_PROXIMITY, TYPE_HUMIDITY, TYPE_TEMPERATURE

> Standard motion and orientation sensors (accelerometer, gyroscope, rotation vector,
> magnetic field, pressure, light) require **no permission declaration**. BODY_SENSORS
> is only needed for TYPE_HEART_RATE and TYPE_STEP_COUNTER on wearables.

**Sampling Rate Constants:**
- SENSOR_DELAY_FASTEST = 0µs
- SENSOR_DELAY_GAME = 20ms (minimum for gesture capture)
- SENSOR_DELAY_UI = 60ms
- SENSOR_DELAY_NORMAL = 200ms (adequate for motion tracking)

---

### Efficiency Patterns

**FIFO Batching (Android 4.4+):**
- `registerListener(..., maxReportLatencyUs: 5_000_000L)` batches 5s of events in hardware FIFO
- CPU wakes once per batch instead of per event — major power saving
- Recommended: 5s batch window as a balance between latency and drain

**Service vs Activity:**
- Activity: natural cleanup in `onPause()`; fine for short bursts only
- Long-duration recording: Foreground Service is mandatory
- Android 14+: declare `android:foregroundServiceType="dataSync"` in manifest; no special
  Google permission required for motion/orientation sensors (use `"health"` only for
  wearable heart-rate / TYPE_BODY_SENSORS_HIGH which needs Google Health permission)
- Unregister listener in `onDestroy()` always

**CPU/Battery Rules:**
- Process on a `Handler` or coroutine — never on the sensor callback thread
- Buffer ≥20 events before SQLite insert (bulk insert is 10–100× faster than per-row)
- Use `SystemClock.elapsedRealtime()` for per-event timestamps (monotonic, immune to clock adjustments)
- Prefer `callbackFlow` to wrap `SensorEventListener` for Kotlin coroutine integration

**Wake Lock Policy:**
- No continuous `PARTIAL_WAKE_LOCK` needed during normal recording with foreground service +
  hardware FIFO batching — the FIFO delivers data without keeping the CPU awake
- **PARTIAL_WAKE_LOCK is required during flush-on-stop**: a foreground service prevents process
  kill but does not prevent the CPU from sleeping mid-flush. Acquire the lock before starting
  the final write and release after commit completes.

---

### Data Storage Options

| Option | Pros | Cons |
|--------|------|------|
| SQLite/Room | Indexed queries, batch insert, Paranoid precedent | Setup overhead |
| CSV (append) | Zero overhead, human-readable | No indexing, full scan to query |
| Binary (Protobuf) | ~30% smaller than JSON | Requires deserialization to inspect |

**Timestamp Strategy:** Hybrid — store session start as wall-clock (`System.currentTimeMillis()`)
in the `sessions` table; per-event uses a monotonic offset (`SystemClock.elapsedRealtime() - sessionStartElapsed`)
stored as `elapsed_ms` (Long). This avoids clock-adjustment artifacts and is unambiguous at read time.

**Schema:**
```
sessions(id INTEGER PK, started_at INTEGER wall-clock ms, ended_at INTEGER nullable)
events(id INTEGER PK, session_id INTEGER FK, elapsed_ms INTEGER, sensor_type INTEGER,
       x REAL, y REAL, z REAL, accuracy INTEGER)
INDEX ON events(session_id, elapsed_ms)
```

---

### Recording Lifecycle & Stop Hierarchy

Three distinct termination paths, each with different I/O time budget:

| Termination Path | Available Time | Required Action |
|-----------------|---------------|-----------------|
| User taps "Stop" | Unlimited | Flush buffer, close session, release wake lock |
| Activity swiped from recents | ~5s (foreground service survives) | Service keeps recording; Activity restart reconnects |
| OOM kill / System low-memory | 0–few ms | Write-ahead WAL provides last committed batch; in-memory buffer since last flush is lost |

**Flush-on-Stop Sequence (user stop):**
1. Acquire `PARTIAL_WAKE_LOCK` before stopping sensor listener
2. Call `unregisterListener()` — triggers FIFO flush delivering remaining hardware-buffered events
3. Wait for final sensor callbacks (use a CountDownLatch or suspendCoroutine)
4. Bulk-insert remaining in-memory buffer
5. Mark session `ended_at` in the `sessions` table (atomic; if absent, session is "incomplete")
6. Release wake lock

**OOM / Process Death Recovery:**
- Sessions with NULL `ended_at` are "incomplete" — show warning badge in UI
- Events table is never partially-written mid-transaction (Room transactions are atomic)
- Maximum data loss on crash = events buffered since last flush (up to 30s at recommended settings)
- On next app launch: scan for incomplete sessions and offer to mark them closed or delete

---

### Error Handling & Data Safety

**Sources of data loss:**

| Risk | Mitigation |
|------|-----------|
| Process death mid-buffer | Flush every 30s (configurable); crash = ≤30s loss |
| SQLite disk-full | Catch `SQLiteFullException`; stop recording + notify user; never silently drop data |
| Sensor unregistered by OS | `SensorEventListener.onAccuracyChanged()` accuracy=0 is not unregistration; set up a watchdog in the service that checks `isRegistered` state |
| ANR from blocking callback | All DB writes off the sensor callback thread (use `Dispatchers.IO`) |
| Incomplete flush on stop | Covered by wake lock sequence above |

**WAL mode:** Enable SQLite WAL (`journal_mode=WAL`) for non-blocking reads during writes.
Room enables this by default via `enableMultiInstanceInvalidation` — confirm it's set.

**Flush interval trade-off:**
- 10s: ~3% extra battery, ≤10s data loss on crash
- 30s: ~1% extra battery, ≤30s data loss on crash (recommended default)
- 60s: minimal extra battery, ≤60s data loss on crash

---

### Concurrent Recording with NetMap

Both apps share the `paranoid` process and can run simultaneously. No OS-level conflict
exists for `SensorManager` — multiple `SensorEventListener` registrations on the same
sensor type are multiplexed by Android.

**Shared resources to coordinate:**

| Resource | Conflict? | Resolution |
|----------|-----------|-----------|
| SensorManager listeners | None — Android multiplexes | Register independently |
| paranoid.db (Room) | Possible write contention | Single Room database instance (singleton); WAL mode handles concurrent reads |
| Foreground Service notification slot | One notification per service | Each app uses its own service class and notification ID |
| CPU/battery | Additive drain | Document combined overhead; expose sampling-rate config so user can reduce |

**Combined overhead estimate:**
- SensorLogger at SENSOR_DELAY_NORMAL: ~2–3% battery/hr
- NetMap antenna scanning: ~1–2% battery/hr (per prior research)
- Combined: ~3–5% battery/hr — acceptable for deliberate concurrent use

**Practical recommendation:** SensorLogger and NetMap can record simultaneously with no
code changes needed for correctness. The main concern is user-visible battery drain; add
a status indicator when both are active.

---

### Known Open-Source Patterns

- **Phyphox**: Service handles I/O, Activity handles UI via local Binder. Best-practice
  pattern for sensor apps. Uses separate flush-on-stop with wake lock.
- **AndroSensor / CSVLogger**: Per-event CSV writes — battery-intensive at high frequency, avoid.
- **Seismic**: SENSOR_DELAY_GAME, shared preferences for quick access — good for single-sensor.
- **Google Sensor Playback samples**: In-memory buffer, flush on Activity stop.

---

### Architecture Recommendation for Paranoid Codebase

1. **Foreground Service** (`SensorRecordingService`) owns `SensorManager` registration;
   Activity connects via local Binder or bound service
2. `SENSOR_DELAY_NORMAL` (200ms) + 5s FIFO batch window (`maxReportLatencyUs = 5_000_000L`)
3. In-memory buffer ≥20 events; flush to Room every 30s and on stop (with wake lock on stop)
4. Schema: `sessions` table (wall-clock start/end) + `events` table (`elapsed_ms` offset, not absolute timestamp)
5. `PARTIAL_WAKE_LOCK` only during flush-on-stop sequence, not during steady recording
6. Manifest: no extra permissions for motion/orientation; `android:foregroundServiceType="dataSync"` for Android 14+
7. WAL mode enabled (Room default); recover incomplete sessions on launch
8. Concurrent NetMap use: no code changes needed; add combined-recording status indicator

**Estimated overhead:** ~2–3% battery/hr for multi-sensor recording at 200ms rate.
Combined with NetMap: ~3–5% battery/hr.
