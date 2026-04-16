## ADDED Requirements

### Requirement: Daily usage summary
The system SHALL provide a minimal daily summary showing total phone usage time for the current day and the apps with the highest observed foreground usage during that day.

#### Scenario: Daily summary with usage access granted
- **GIVEN** the user has granted usage access
- **WHEN** the user opens the Today screen
- **THEN** the system shows the current day total usage time
- **AND** the system shows a ranked list of apps by observed foreground usage time

#### Scenario: Daily summary without usage access
- **GIVEN** the user has not granted usage access
- **WHEN** the user opens the Today screen
- **THEN** the system explains that usage access is required
- **AND** the system provides a path to enable the required access in Android Settings

### Requirement: Overnight battery audit
The system SHALL provide an overnight audit for a user-visible night window, including battery level change across the window and app activity observed during that same window.

#### Scenario: Overnight audit with sufficient snapshots
- **GIVEN** the system has battery snapshots spanning the configured night window
- **AND** the system can read app usage activity for that same window
- **WHEN** the user opens the Last Night screen
- **THEN** the system shows the window start and end times
- **AND** the system shows battery start percentage, end percentage, and total percentage drop
- **AND** the system shows the apps with observed activity in that window

#### Scenario: Overnight audit with incomplete battery data
- **GIVEN** the system lacks enough battery snapshots to fully cover the configured night window
- **WHEN** the user opens the Last Night screen
- **THEN** the system shows the best available partial summary
- **AND** the system warns that the overnight battery data is incomplete

### Requirement: Transparent battery attribution limits
The system SHALL distinguish exact observations from inferred or correlated battery-drain information.

#### Scenario: Overnight app contribution labeling
- **GIVEN** the system displays apps active during an overnight drain window
- **WHEN** the app presents those results to the user
- **THEN** it labels them as observed activity or suspected contributors
- **AND** it does not claim exact per-app battery percentages unless the platform source is explicitly available and trustworthy

### Requirement: Export and share audit data
The system SHALL let the user export and share audit information in both human-readable and spreadsheet-friendly formats.

#### Scenario: Share human-readable summary
- **GIVEN** the user is viewing a daily or overnight summary
- **WHEN** the user selects Share
- **THEN** the system opens the Android sharesheet with a compact text summary of the visible report

#### Scenario: Export spreadsheet-friendly data
- **GIVEN** the user is viewing the export flow
- **WHEN** the user selects CSV export
- **THEN** the system generates a CSV file containing the relevant audit records
- **AND** the system makes that file available through Android sharing mechanisms

### Requirement: Minimal offline operation
The system SHALL operate offline for collection, viewing, and export of locally available audit data.

#### Scenario: Offline usage review
- **GIVEN** the device has no network connectivity
- **WHEN** the user opens the mini-app to inspect daily or overnight data already collected on the device
- **THEN** the system shows the locally available summaries
- **AND** the system allows local share/export actions that do not require cloud services
