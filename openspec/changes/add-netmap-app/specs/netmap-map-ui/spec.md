## ADDED Requirements

Depends on: netmap-data, netmap-recording

### Requirement: Live Map View
The system SHALL display a MapLibre map in NetMapActivity using Carto Dark Matter tiles. The map SHALL center on the user's current location and follow the user during recording.

#### Scenario: Map loads on open
- **WHEN** the user opens NetMap
- **THEN** the map loads with dark tiles centered on the device's last known location

#### Scenario: Live tracking during recording
- **WHEN** a recording is active
- **THEN** the map follows the user's current position and draws a colored polyline showing the recorded track

#### Scenario: Map tiles unavailable (offline)
- **WHEN** map tiles cannot be loaded due to no network and no cached tiles
- **THEN** the map shows a blank/placeholder background and recording still functions normally

### Requirement: Signal-Colored Track
The system SHALL render the recorded track as a polyline with segments colored by signal level: green (EXCELLENT), cyan (GOOD), amber (FAIR), red (POOR), gray (NONE).

#### Scenario: Track coloring
- **WHEN** the user is recording and signal transitions from GOOD to POOR
- **THEN** the polyline segment changes from cyan to red at the transition point

### Requirement: Signal HUD
The system SHALL display a heads-up overlay showing the current serving cell's signal level, RSRP value, network type (LTE/NR/etc), and carrier name.

#### Scenario: HUD updates during recording
- **WHEN** a new measurement is taken
- **THEN** the HUD updates to reflect the latest signal metrics

#### Scenario: No cell info available
- **WHEN** telephony data is unavailable (airplane mode, no permission)
- **THEN** the HUD shows "No signal" with a gray indicator

### Requirement: Recording Controls
The system SHALL display Start/Stop buttons at the bottom of the map screen. Start begins a new recording; Stop ends the current recording.

#### Scenario: Start recording
- **WHEN** the user taps Start
- **THEN** a new recording begins, the button changes to Stop, and the track starts drawing

#### Scenario: Stop recording
- **WHEN** the user taps Stop
- **THEN** the recording ends, the track remains visible, and the button returns to Start

### Requirement: Screen Navigation
NetMapActivity SHALL have a toolbar with an overflow menu containing "Recordings" and "About" items. RecordingsActivity and RecordingDetailActivity SHALL have a back arrow in the toolbar. RecordingDetailActivity SHALL have an export menu item.

#### Scenario: Navigate to recordings
- **WHEN** the user taps the overflow menu and selects "Recordings"
- **THEN** RecordingsActivity opens showing all saved recordings

#### Scenario: Navigate back from detail
- **WHEN** the user taps the back arrow in RecordingDetailActivity
- **THEN** the user returns to RecordingsActivity

### Requirement: Recordings List
The system SHALL provide a RecordingsActivity showing all saved recordings with name, date, duration, measurement count, and distance. Tapping a recording opens RecordingDetailActivity.

#### Scenario: View recordings
- **WHEN** the user navigates to recordings list
- **THEN** all saved recordings are shown sorted by date (newest first)

#### Scenario: Delete recording
- **WHEN** the user long-presses a recording and confirms deletion
- **THEN** the recording and all its measurements are deleted

#### Scenario: No recordings
- **WHEN** no recordings exist
- **THEN** the list shows an empty state message ("No recordings yet")

### Requirement: Recording Detail View
The system SHALL provide a RecordingDetailActivity that displays a saved recording's track on a map with signal-colored segments and summary statistics (duration, distance, measurement count, average signal).

#### Scenario: View recording detail
- **WHEN** the user taps a recording in the list
- **THEN** the map shows the full track with signal coloring and stats overlay
