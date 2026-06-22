# screen-time-session Specification

## Purpose
Track continuous screen-on sessions and the foreground app active within each session.

## ADDED Requirements

### Requirement: Screen session lifecycle
The system SHALL define a session as a continuous screen-on period. A session starts when
the screen turns on and ends when the screen has been off for more than 30 seconds (debounce).
Each session records a start timestamp, an end timestamp (null while active), and a list of
per-app foreground intervals observed during that session.

#### Scenario: Session starts on screen-on
- **GIVEN** monitoring is active
- **WHEN** the screen turns on
- **THEN** the system creates a new session record with the current timestamp as start time
- **AND** begins sampling the foreground app

#### Scenario: Screen-off shorter than debounce threshold does not end session
- **GIVEN** a session is active
- **WHEN** the screen turns off and back on within 30 seconds
- **THEN** the session continues without interruption
- **AND** the checkpoint clock is not reset

#### Scenario: Screen-off longer than debounce threshold ends session
- **GIVEN** a session is active
- **WHEN** the screen remains off for more than 30 seconds
- **THEN** the system closes the session recording the screen-off event timestamp as end time (the time `ACTION_SCREEN_OFF` was received, not the debounce expiry time)
- **AND** the checkpoint clock resets

#### Scenario: Session persists across process restarts
- **GIVEN** a session is active
- **WHEN** the monitoring service is killed and restarted (e.g., by the OS)
- **THEN** the system recovers the open session from the data store
- **AND** resumes checkpoint scheduling from the elapsed time already recorded

### Requirement: Foreground app sampling
The system SHALL sample the foreground app at approximately 5-second intervals during an
active session using `UsageStatsManager.queryEvents()` and accumulate contiguous foreground
intervals per package within the session. App attribution is subject to platform-level
event delivery lag (typically less than 2 minutes on stock Android, longer on some OEMs);
per-app times are approximate and the system does not compensate for this lag.

#### Scenario: Single app in foreground for an interval
- **GIVEN** a session is active and app A is in the foreground
- **WHEN** 5 seconds elapse and a sample is taken
- **THEN** the system records 5 seconds of foreground time for app A in the current session

#### Scenario: App switch recorded between samples
- **GIVEN** app A was in the foreground at the previous sample
- **WHEN** the next sample shows app B moved to foreground
- **THEN** the system closes app A's interval at the switch event timestamp
- **AND** opens a new interval for app B
- **AND** the sum of all intervals equals the elapsed session time

#### Scenario: System UI or launcher foreground
- **GIVEN** the foreground app cannot be resolved to a user-installed or system-user package
  (e.g., the launcher, lock screen, or system UI)
- **WHEN** the sample is taken
- **THEN** the system records the interval under the identifier `system.unattributed`
- **AND** does not attribute it to any user app
- **AND** `system.unattributed` intervals are excluded from per-app breakdowns in reports

#### Scenario: Usage access not granted during monitoring
- **GIVEN** the user has revoked usage access after enabling monitoring
- **WHEN** a foreground app sample is attempted
- **THEN** the system skips the sample without crashing
- **AND** shows a persistent warning in the monitoring notification that usage access is required
