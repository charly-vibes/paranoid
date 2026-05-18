## Suggested implementation order
1 → 2A → 2B → 3A/3B (parallel) → 4 → 5 → 6 → 7

## 1. Ticket: Lock implementation assumptions
**Goal:** confirm remaining product boundaries before writing code.

- [x] 1.1 Verify the canonical sensor list for v1 (motion + orientation + environment as specified; no heart-rate or step-counter).
- [x] 1.2 Verify v1 UI scope: recording control, session list, and session detail only — no chart/graph view of raw event data.
- [x] 1.3 Verify `SensorRecordingService` notification ID: `RecordingService.NOTIFICATION_ID = 1001` confirmed at `RecordingService.kt:299`; sensor logger uses 1002.
- [x] 1.4 Verify `ParanoidDatabase` current version: `version = 4` confirmed at `ParanoidDatabase.kt:28`; migration 4→5 is correct.
- [x] 1.5 Verify combined-recording detection: `RecordingService.isRunning` static flag **does not exist**. Actual pattern is instance-level `isRecording: StateFlow<Boolean>` via bound `LocalBinder` (same as `NetMapActivity`). `design.md` §Decision 8 updated to reflect the real pattern.

**Acceptance criteria:**
- [x] 1.6 All five verification steps are confirmed with codebase evidence; discrepancy in Decision 8 resolved — design updated before Ticket 2.

---

## 2. Ticket: Domain model and pure logic
**Goal:** define the value objects and pure calculation functions; no Android dependencies, fully unit-testable.

### 2A. Domain model
- [x] 2.1 **Red:** write failing unit tests for `SensorSession` value object (start/end times, duration calculation, `isIncomplete` flag).
- [x] 2.2 **Green:** implement `SensorSession` to pass the tests.
- [x] 2.3 **Red:** write failing unit test for `SensorEvent` value object (elapsed_ms, sensor type, x/y/z/accuracy).
- [x] 2.4 **Green:** implement `SensorEvent` to pass the test.
- [x] 2.5 **Refactor:** tidy naming, sealed types for sensor category (Motion, Orientation, Environment).

### 2B. Session summary logic
- [x] 2.6 **Red:** write failing unit test for event-count-per-sensor aggregation (given a list of `SensorEvent`, return a `Map<SensorType, Int>`).
- [x] 2.7 **Green:** implement the smallest aggregation function to pass the test.
- [x] 2.8 **Red:** write failing unit test for session duration from `started_at` and `ended_at` (handles null ended_at).
- [x] 2.9 **Green:** implement duration helper.
- [x] 2.10 **Refactor:** consolidate summary types; ensure no Android imports leak into pure logic.

**Acceptance criteria:**
- [x] 2.11 All domain model tests pass with zero Android dependencies.

---

## 3. Ticket: Data persistence
**Goal:** add Room entities, DAOs, and DB migration; wire into `ParanoidDatabase`.

### 3A. Entities and DAOs
- [ ] 3.1 **Red:** write failing instrumentation test for inserting a `SensorSessionEntity` and reading it back via `SensorSessionDao`.
- [ ] 3.2 **Green:** implement `SensorSessionEntity`, `SensorSessionDao`.
- [ ] 3.3 **Red:** write failing instrumentation test for inserting `SensorEventEntity` rows and querying by `session_id` ordered by `elapsed_ms`.
- [ ] 3.4 **Green:** implement `SensorEventEntity`, `SensorEventDao` with composite index `(session_id, elapsed_ms)`.
- [ ] 3.5 **Red:** write failing test for CASCADE delete: deleting a session deletes its events.
- [ ] 3.6 **Green:** verify CASCADE is declared in the entity foreign key; test passes.
- [ ] 3.7 **Red:** write failing test for incomplete sessions query (`ended_at IS NULL`).
- [ ] 3.8 **Green:** add `queryIncompleteSessions()` to `SensorSessionDao`.
- [ ] 3.9 **Refactor:** simplify DAO interfaces; remove any queries not exercised by tests.

### 3B. Database migration
- [ ] 3.10 **Red:** write a migration test that opens a version-4 database with real data, runs `MIGRATION_4_5`, and asserts the new tables exist and old data is intact.
- [ ] 3.11 **Green:** implement `MIGRATION_4_5` in `ParanoidDatabase`; bump version to 5; add new entities and DAOs to the `@Database` annotation.
- [ ] 3.12 **Refactor:** review migration SQL for consistency with existing migration style (table names, index naming conventions).

**Acceptance criteria:**
- [ ] 3.13 Migration test passes on a real in-memory Room database seeded with version-4 schema.
- [ ] 3.14 `SensorSessionDao` and `SensorEventDao` are accessible via `ParanoidDatabase.getInstance()`.

---

## 4. Ticket: SensorRecordingService
**Goal:** implement the foreground service with sensor registration, FIFO batching, in-memory buffer, periodic flush, and flush-on-stop.

- [ ] 4.1 **Red:** write a unit test for the in-memory buffer: append events, flush returns all events and clears the buffer.
- [ ] 4.2 **Green:** implement `SensorEventBuffer` (pure class, no Android deps).
- [ ] 4.3 **Red:** write a unit test for flush-suppression when buffer is empty (no-op, no DB call).
- [ ] 4.4 **Green:** add empty-buffer guard to flush logic.
- [ ] 4.5 Implement `SensorRecordingService`: foreground service, `foregroundServiceType="dataSync"`, notification ID 1002.
- [ ] 4.6 Implement `registerAllSensors()`: iterate sensor list, skip absent sensors, register with `SENSOR_DELAY_NORMAL` + `maxReportLatencyUs = 5_000_000L`.
- [ ] 4.7 Implement `onSensorChanged()`: append to in-memory buffer only (no blocking I/O on callback thread).
- [ ] 4.8 Implement 30-second flush coroutine on `Dispatchers.IO`.
- [ ] 4.9 Implement `stopRecording()`: wake lock → `unregisterListener()` → await final callbacks via `CompletableDeferred` that resolves on first empty-batch signal from `onSensorChanged` or after 500 ms timeout (whichever comes first) → bulk insert → `ended_at` in a single `@Transaction` → release wake lock.
- [ ] 4.10 Implement `onDestroy()` safety call: `sensorManager.unregisterListener(this)`.
- [ ] 4.11 Implement `Binder` interface for Activity → Service communication (elapsed time, event count, registered sensor list).
- [ ] 4.12 **Refactor:** separate recording orchestration from Android service lifecycle boilerplate; verify no blocking calls on sensor callback thread.

**Acceptance criteria:**
- [ ] 4.13 `SensorEventBuffer` unit tests pass.
- [ ] 4.14 Manual smoke test: service runs in foreground, notification appears, sensors register, periodic flush writes to DB.

---

## 5. Ticket: Error handling and recovery
**Goal:** handle disk-full errors, implement incomplete-session recovery on launch.

- [ ] 5.1 **Red:** write a unit test for `SQLiteFullException` handling: flush method catches exception, returns an error result type (does not throw).
- [ ] 5.2 **Green:** wrap bulk insert in try/catch; propagate error to the service's stop-with-error path.
- [ ] 5.3 Implement stop-with-error path: cancel foreground notification, post error notification "Recording stopped — storage full", leave session with `ended_at = NULL`.
- [ ] 5.4 **Red:** write a unit test for incomplete-session detection at launch: given a DAO returning one session with `ended_at = NULL`, the ViewModel emits it as `RecoveryState.Incomplete`.
- [ ] 5.5 **Green:** implement launch-time incomplete-session query and `RecoveryState` sealed class.
- [ ] 5.6 **Refactor:** unify error notification creation with any existing notification helper in the codebase.

**Acceptance criteria:**
- [ ] 5.7 Disk-full unit test passes; service transitions to idle state without crashing.
- [ ] 5.8 Incomplete-session unit test passes; ViewModel correctly surfaces recovery state.

---

## 6. Ticket: UI Activities
**Goal:** build `SensorLoggerActivity`, `SensorSessionsActivity`, and `SensorSessionDetailActivity`.

- [ ] 6.1 Implement `SensorLoggerActivity`: bind to `SensorRecordingService`; Start/Stop button; live elapsed-time counter; idle-state summary; combined-recording notice logic.
- [ ] 6.2 Implement `SensorSessionsActivity`: `RecyclerView` list, reverse-chronological order, incomplete badge, tap → detail.
- [ ] 6.3 Implement `SensorSessionDetailActivity`: start/end times, duration, total events, per-sensor breakdown list, "Mark as closed" / "Delete" buttons for incomplete sessions.
- [ ] 6.4 Dark theme layouts (background `#121212`, light text, 48dp min tap targets).
- [ ] 6.5 **Refactor:** ensure no business logic lives in Activity classes; all data queries go through ViewModels.

**Acceptance criteria:**
- [ ] 6.6 UI smoke test: start recording, wait 60 s, stop; session appears in list with correct duration and event count.
- [ ] 6.7 Incomplete-session UI smoke test: kill app during recording; reopen; incomplete session shown with badge; mark-as-closed works.

---

## 7. Ticket: Manifest, hub registration, and validation
**Goal:** wire everything into the Android project and validate the change.

- [ ] 7.1 Add `SensorRecordingService` to `AndroidManifest.xml` with `android:foregroundServiceType="dataSync"`.
- [ ] 7.2 Add `uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"` (API 34+) to manifest.
- [ ] 7.3 Register `SensorLoggerActivity`, `SensorSessionsActivity`, `SensorSessionDetailActivity` in manifest.
- [ ] 7.4 Add `SensorLoggerActivity` to the hub app list.
- [ ] 7.5 Run `openspec validate add-sensor-logger-app --strict` and resolve any issues.
- [ ] 7.6 Run `just test` (Docker) and confirm all new unit and instrumentation tests pass.
- [ ] 7.7 Run `just build` (Docker) and confirm a debug APK builds cleanly.

**Acceptance criteria:**
- [ ] 7.8 `openspec validate add-sensor-logger-app --strict` passes with no errors.
- [ ] 7.9 `just test` passes.
- [ ] 7.10 `just build` produces a debug APK without errors or warnings related to this change.
