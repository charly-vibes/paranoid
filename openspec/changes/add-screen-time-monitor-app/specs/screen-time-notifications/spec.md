# screen-time-notifications Specification

## Purpose
Deliver checkpoint notifications that nudge the user at progressively spaced intervals
during a screen-on session, then continue at a steady cadence.

## ADDED Requirements

### Requirement: Checkpoint notification sequence
The system SHALL post a notification at 7 minutes, 13 minutes, and 29 minutes from the
start of a session, then every 29 minutes thereafter, for as long as the session is active.
The clock resets when a session ends (screen off beyond the debounce threshold).

#### Scenario: First checkpoint at 7 minutes
- **GIVEN** a session starts at T=0
- **WHEN** T=7 minutes elapses
- **THEN** the system posts a notification indicating 7 minutes of continuous screen-on time

#### Scenario: Second checkpoint at 13 minutes
- **GIVEN** the 7-minute notification has fired in the current session
- **WHEN** T=13 minutes elapses
- **THEN** the system posts a notification indicating 13 minutes of continuous screen-on time

#### Scenario: Third checkpoint at 29 minutes
- **GIVEN** the 13-minute notification has fired in the current session
- **WHEN** T=29 minutes elapses
- **THEN** the system posts a notification indicating 29 minutes of continuous screen-on time

#### Scenario: Continued cycling every 29 minutes
- **GIVEN** the 29-minute checkpoint has fired
- **WHEN** each subsequent 29-minute interval elapses
- **THEN** the system posts a notification indicating the cumulative session time
- **AND** this continues indefinitely while the session remains active

#### Scenario: Session ends, clock resets
- **GIVEN** one or more checkpoints have fired in the current session
- **WHEN** the session ends (screen off longer than 30 seconds)
- **THEN** all pending checkpoint callbacks are cancelled
- **AND** the next session begins a fresh sequence from 7 minutes

#### Scenario: Screen-off shorter than debounce does not reset checkpoint clock
- **GIVEN** the session is at T=10 minutes (past the 7-minute checkpoint)
- **WHEN** the screen turns off and on within 30 seconds
- **THEN** the checkpoint schedule is not reset
- **AND** the 13-minute notification fires at the correct absolute time

### Requirement: Foreground service persistent notification
The monitoring service SHALL post a persistent notification as required by Android to run as
a foreground service. This notification is distinct from checkpoint notifications. It SHALL
display the current monitoring state and open `ScreenTimeActivity` when tapped.

#### Scenario: Service notification shows monitoring is active
- **GIVEN** monitoring is active
- **WHEN** the service is running
- **THEN** a persistent notification titled "Screen time monitoring active" is visible in the notification shade
- **AND** tapping the notification opens `ScreenTimeActivity`

#### Scenario: Service notification reflects usage access warning
- **GIVEN** monitoring is active but usage access has been revoked
- **WHEN** a foreground app sample fails due to missing permission
- **THEN** the service notification subtitle updates to "Usage access required"
- **AND** tapping the notification opens `ScreenTimeActivity` to the permission screen

### Requirement: Notification content
Each checkpoint notification SHALL state the total elapsed session time. Checkpoints 1–3
(at 7, 13, and 29 minutes) SHALL indicate the milestone in the title. Checkpoints 4 and
beyond SHALL show only the cumulative elapsed time; no cycle number is displayed. Checkpoint
notifications SHALL not include action buttons in v1.

#### Scenario: Notification text for 7-minute checkpoint (checkpoint 1)
- **GIVEN** the 7-minute checkpoint fires
- **WHEN** the notification is posted
- **THEN** the notification title reads "7 min screen on" (or equivalent milestone phrasing)
- **AND** the body does not make claims about app usage or battery in v1

#### Scenario: Notification text for 13-minute checkpoint (checkpoint 2)
- **GIVEN** the 13-minute checkpoint fires
- **WHEN** the notification is posted
- **THEN** the notification title reads "13 min screen on"

#### Scenario: Notification text for 29-minute checkpoint (checkpoint 3)
- **GIVEN** the 29-minute checkpoint fires
- **WHEN** the notification is posted
- **THEN** the notification title reads "29 min screen on"

#### Scenario: Notification text for cycling checkpoints beyond 29 minutes
- **GIVEN** the fourth or later checkpoint fires
- **WHEN** the notification is posted
- **THEN** the notification title shows only the total elapsed session time (e.g., "58 min screen on", "87 min screen on")
- **AND** no cycle or checkpoint number is included

### Requirement: Notification permission gate
The system SHALL request `POST_NOTIFICATIONS` (Android 13+) before enabling monitoring
and SHALL gracefully degrade if the permission is denied: monitoring and the overlay
continue, but checkpoint notifications are silently skipped.

#### Scenario: Permission denied — monitoring still works
- **GIVEN** the user denies the notification permission
- **WHEN** monitoring is started
- **THEN** session tracking and the overlay bar function normally
- **AND** no checkpoint notification is posted
- **AND** the activity shows a note that notifications are disabled
