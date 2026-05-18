## ADDED Requirements

### Requirement: Session schema
The system SHALL persist recording sessions in a `sensor_sessions` table with columns: `id INTEGER PRIMARY KEY AUTOINCREMENT`, `started_at INTEGER NOT NULL` (wall-clock milliseconds via `System.currentTimeMillis()`), `ended_at INTEGER` (nullable; NULL indicates an incomplete session). A session is considered complete only when `ended_at` is not NULL.

#### Scenario: Session created on recording start
- **GIVEN** the user taps Start Recording
- **WHEN** `SensorRecordingService` begins registration
- **THEN** a new row is inserted into `sensor_sessions` with `started_at` set to the current wall-clock time and `ended_at = NULL`

#### Scenario: Session closed on clean stop
- **GIVEN** recording is active with a session row where `ended_at IS NULL`
- **WHEN** the flush-on-stop sequence completes
- **THEN** `ended_at` is updated and the final event batch is inserted atomically in a single `@Transaction`-annotated DAO method

---

### Requirement: Event schema
The system SHALL persist sensor events in a `sensor_events` table with columns: `id INTEGER PRIMARY KEY AUTOINCREMENT`, `session_id INTEGER NOT NULL REFERENCES sensor_sessions(id) ON DELETE CASCADE`, `elapsed_ms INTEGER NOT NULL` (monotonic offset: `SystemClock.elapsedRealtime() - sessionStartElapsed`), `sensor_type INTEGER NOT NULL`, `x REAL NOT NULL`, `y REAL NOT NULL`, `z REAL NOT NULL`, `accuracy INTEGER NOT NULL`. A composite index on `(session_id, elapsed_ms)` SHALL exist.

#### Scenario: Events stored with monotonic offset
- **GIVEN** a recording session started at elapsed time T₀
- **WHEN** a sensor event arrives at elapsed time T₁
- **THEN** the event is stored with `elapsed_ms = T₁ − T₀`
- **AND** the absolute timestamp can be recovered as `sessions.started_at + events.elapsed_ms`

#### Scenario: Events deleted with session
- **GIVEN** a session row exists with associated event rows
- **WHEN** the session row is deleted
- **THEN** all associated event rows are also deleted via CASCADE

---

### Requirement: Database migration 4→5
The system SHALL add the `sensor_sessions` and `sensor_events` tables to `ParanoidDatabase` via a new `MIGRATION_4_5` added to the existing migration chain. All existing data SHALL be preserved.

#### Scenario: Migration executes without data loss
- **GIVEN** a device running `ParanoidDatabase` at version 4 with existing netmap, netdiag, and usageaudit data
- **WHEN** the app upgrades and Room runs `MIGRATION_4_5`
- **THEN** the two new tables are created
- **AND** all pre-existing rows in other tables are intact

---

### Requirement: Incomplete session recovery
On app launch, the system SHALL query for sessions where `ended_at IS NULL`. For each such session the UI SHALL show a warning badge. The user SHALL be able to mark the session closed (sets `ended_at` to the launch time) or delete it.

#### Scenario: Incomplete session shown with warning
- **GIVEN** a previous recording was interrupted (process killed mid-session)
- **WHEN** the user opens the session list
- **THEN** the interrupted session is shown with a "Incomplete" warning badge
- **AND** the session's event count reflects the events that were flushed before the crash

#### Scenario: User closes incomplete session
- **GIVEN** an incomplete session is displayed
- **WHEN** the user taps "Mark as closed"
- **THEN** `ended_at` is set to the current time
- **AND** the warning badge is removed

#### Scenario: User deletes incomplete session
- **GIVEN** an incomplete session is displayed
- **WHEN** the user taps "Delete"
- **THEN** the session row and all its event rows are deleted via CASCADE

---

### Requirement: WAL mode and Room singleton
`ParanoidDatabase` SHALL be opened in WAL journal mode (Room default via `RoomDatabase.Builder`). A single `ParanoidDatabase` instance SHALL be used across all mini-apps via the existing `getInstance(context)` singleton. No separate database file is created for the sensor logger.

#### Scenario: Concurrent write from sensor logger and NetMap
- **GIVEN** both `SensorRecordingService` and NetMap's `RecordingService` are active simultaneously
- **WHEN** both services flush data to `ParanoidDatabase` concurrently
- **THEN** both writes complete successfully (WAL serializes concurrent writers; blocking duration is negligible for small batches)
- **AND** no data is lost or corrupted
