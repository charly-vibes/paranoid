## ADDED Requirements

### Requirement: Foreground service recording
The system SHALL run sensor recording inside a foreground service (`SensorRecordingService`) declared with `android:foregroundServiceType="dataSync"`. The service SHALL display a persistent notification for the duration of recording. The `FOREGROUND_SERVICE_DATA_SYNC` permission SHALL be declared in the manifest for API 34+.

#### Scenario: Service starts and shows notification
- **GIVEN** the user taps Start Recording
- **WHEN** `SensorRecordingService` is started
- **THEN** a foreground notification appears with the app name and "Recording sensors…" label
- **AND** the service remains running if the user navigates away from the Activity

#### Scenario: Service survives Activity destruction
- **GIVEN** recording is active
- **WHEN** the user swipes the Activity from recents
- **THEN** `SensorRecordingService` continues running
- **AND** reopening the app reconnects the Activity to the running service and restores live status

---

### Requirement: Multi-sensor registration with FIFO batching
The system SHALL register all available standard sensors (`TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE`, `TYPE_LINEAR_ACCELERATION`, `TYPE_GRAVITY`, `TYPE_ROTATION_VECTOR`, `TYPE_MAGNETIC_FIELD`, `TYPE_PRESSURE`, `TYPE_LIGHT`, `TYPE_PROXIMITY`) at `SENSOR_DELAY_NORMAL` with a 5-second FIFO batch window (`maxReportLatencyUs = 5_000_000L`). Sensors not present on the device SHALL be silently skipped.

#### Scenario: Sensors registered on start
- **GIVEN** `SensorRecordingService` has started
- **WHEN** sensor registration completes
- **THEN** every sensor present on the device from the standard list is registered with `maxReportLatencyUs = 5_000_000L`
- **AND** sensors absent on the device are skipped without error

#### Scenario: Absent sensor skipped silently
- **GIVEN** the device has no barometer (`TYPE_PRESSURE`)
- **WHEN** recording starts
- **THEN** all other sensors register normally
- **AND** no error or warning is shown to the user for the absent sensor

---

### Requirement: In-memory buffer with periodic flush
The system SHALL accumulate sensor events in an in-memory buffer and flush to Room in bulk every 30 seconds. A flush SHALL also occur on recording stop. Flushed batches MUST contain ≥1 event; the system SHALL NOT issue an empty database write.

#### Scenario: Periodic flush during recording
- **GIVEN** recording has been active for 30 seconds with events arriving
- **WHEN** the 30-second flush timer fires
- **THEN** all buffered events are bulk-inserted into `sensor_events` in a single Room transaction
- **AND** the in-memory buffer is cleared

#### Scenario: No flush on empty buffer
- **GIVEN** the in-memory buffer is empty when the flush timer fires
- **WHEN** the timer fires
- **THEN** no database write is issued

---

### Requirement: Crash-safe flush-on-stop with wake lock
The system SHALL execute the following sequence on user-initiated stop: (1) acquire `PARTIAL_WAKE_LOCK`; (2) call `unregisterListener()` on all sensors, triggering a hardware FIFO flush; (3) await final sensor callbacks via a `CompletableDeferred` that resolves on the first empty-batch signal from `onSensorChanged` or after a 500 ms timeout, whichever comes first; (4) bulk-insert remaining buffer; (5) mark `sensor_sessions.ended_at` — steps 4 and 5 SHALL execute in a single `@Transaction`; (6) release wake lock. The wake lock SHALL NOT be held during steady-state recording.

#### Scenario: Clean stop flushes all data
- **GIVEN** recording is active with events in the hardware FIFO and in-memory buffer
- **WHEN** the user taps Stop Recording
- **THEN** a `PARTIAL_WAKE_LOCK` is acquired
- **AND** `unregisterListener()` is called, flushing the hardware FIFO into callbacks
- **AND** remaining in-memory events are bulk-inserted into `sensor_events`
- **AND** `sensor_sessions.ended_at` is set to the current wall-clock time
- **AND** the wake lock is released before the service stops

#### Scenario: No wake lock during steady recording
- **GIVEN** recording is active
- **WHEN** the system is operating normally (not in a stop sequence)
- **THEN** no `PARTIAL_WAKE_LOCK` is held by `SensorRecordingService`

---

### Requirement: Disk-full error handling
When a Room write fails due to insufficient storage (`SQLiteFullException`), the system SHALL stop recording, persist the partial session as-is (with NULL `ended_at`), dismiss the foreground notification, and show an error notification informing the user that recording stopped due to low storage. The system SHALL NOT silently discard buffered data without attempting a write.

#### Scenario: Disk-full during flush
- **GIVEN** recording is active
- **WHEN** a bulk insert fails with `SQLiteFullException`
- **THEN** recording stops immediately
- **AND** the foreground notification is replaced with an error notification: "Recording stopped — storage full"
- **AND** the session remains in the database with `ended_at = NULL` (recoverable as incomplete)

---

### Requirement: ANR prevention
All database writes and sensor callback processing that exceed simple in-memory operations SHALL execute on `Dispatchers.IO` or a background `Handler`. The sensor callback thread (`onSensorChanged`) SHALL only append to the in-memory buffer; no blocking I/O or synchronisation primitives on that thread.

#### Scenario: Sensor callback is non-blocking
- **GIVEN** recording is active
- **WHEN** `onSensorChanged` fires
- **THEN** the event is appended to the in-memory buffer only
- **AND** no database call, file I/O, or blocking wait occurs on the callback thread

---

### Requirement: Sensor listener unregistered on service destroy
The system SHALL call `unregisterListener()` for all registered sensors in `onDestroy()` of `SensorRecordingService`, regardless of whether a clean stop sequence was executed.

#### Scenario: Listener unregistered on destroy
- **GIVEN** `SensorRecordingService` is being destroyed (OOM, force-stop)
- **WHEN** `onDestroy()` is called
- **THEN** `sensorManager.unregisterListener(this)` is called
