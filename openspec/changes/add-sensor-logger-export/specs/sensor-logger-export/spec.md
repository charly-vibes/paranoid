## ADDED Requirements

### Requirement: Session CSV Export
The system SHALL export a recorded session's events as CSV, with a header row
followed by one row per event containing elapsed time, sensor type, x/y/z values,
and accuracy.

#### Scenario: Export session with events
- **WHEN** a session containing events is exported as CSV
- **THEN** the output begins with a header row `elapsed_ms,sensor_type,x,y,z,accuracy`
- **AND** the output contains one data row per recorded event

#### Scenario: Export empty session
- **WHEN** a session with no events is exported as CSV
- **THEN** the output contains only the header row

### Requirement: Session JSON Export
The system SHALL export a recorded session's events as JSON containing session
metadata (id, started/ended timestamps) and an array of event objects.

#### Scenario: Export session as JSON
- **WHEN** a session is exported as JSON
- **THEN** the output is a JSON object with the session id and start time
- **AND** the output contains an `events` array with one object per recorded event

### Requirement: Share Exported Session
The system SHALL let the user export the currently viewed session from the
session detail screen and share the result through the Android share sheet.

#### Scenario: User exports from detail screen
- **WHEN** the user taps Export on the session detail screen and picks a format
- **THEN** the app generates the file and launches the system share chooser
