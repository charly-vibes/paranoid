## ADDED Requirements

### Requirement: Daily history browsing
The system SHALL let the user browse usage summaries for past days within the platform-retained usage window (the lookback period exposed by Android's `UsageStatsManager`), not only the current day. The History screen SHALL exclude the current day, which remains reachable via the Today screen.

#### Scenario: List recent past days
- **GIVEN** the user has granted usage access
- **AND** the platform retains usage data for several past days
- **WHEN** the user opens the History screen
- **THEN** the system shows a chronological list of past days with each day's total foreground usage time
- **AND** the current day is not in the list
- **AND** the list is scoped to the platform-retained usage window

#### Scenario: Open a past day
- **GIVEN** the user is viewing the daily history list
- **WHEN** the user selects a past day
- **THEN** the system opens the Day Detail screen for that day

#### Scenario: No history available
- **GIVEN** the platform exposes no past usage data
- **WHEN** the user opens the History screen
- **THEN** the system shows an empty state explaining the limitation

### Requirement: Day detail drill-down
The system SHALL provide a Day Detail screen that shows the full ranked app list, an hourly distribution of foreground usage, and the overnight summary whose window starts within the selected day, when available.

#### Scenario: Past day detail
- **GIVEN** the user has selected a specific day from the History list
- **WHEN** the Day Detail screen renders
- **THEN** the system shows the day's total foreground usage time
- **AND** the system shows the full ranked list of apps with observed foreground time
- **AND** the system shows a per-hour breakdown of foreground usage for that day
- **AND** the system shows the overnight summary whose window starts within that day, when battery snapshots are available

#### Scenario: Day with no recorded foreground usage
- **GIVEN** the platform reports zero foreground usage for the selected day
- **WHEN** the Day Detail screen renders
- **THEN** the system shows zero total time, an empty hourly breakdown, and no app rows
- **AND** the system does not invent or estimate activity

#### Scenario: Day affected by daylight saving time
- **GIVEN** the selected day crosses a DST transition (23-hour or 25-hour day)
- **WHEN** the hourly breakdown renders
- **THEN** the system shows the actual number of hours in that local day
- **AND** the sum of hourly buckets equals the day's total foreground time

#### Scenario: Share or export the selected day
- **GIVEN** the user is viewing the Day Detail screen
- **WHEN** the user selects Share or CSV export
- **THEN** the system produces output scoped to the selected day rather than the current day
- **AND** the output uses the existing v1 CSV/text schema; hourly distribution and per-app intervals are not added to the export

### Requirement: App detail drill-down
The system SHALL let the user drill into a single app for a selected day to see its observed foreground intervals and total time.

#### Scenario: Open app detail from day view
- **GIVEN** the user is on the Day Detail screen
- **WHEN** the user selects an app row
- **THEN** the system opens an App Detail view for that app on that day
- **AND** the system shows the app's total observed foreground time for the day
- **AND** the system shows the list of observed foreground intervals with start and end times

#### Scenario: App with no observed activity
- **GIVEN** the user opened App Detail for an app with no observed foreground activity on the selected day
- **WHEN** the App Detail view renders
- **THEN** the system shows zero total time and an explicit no-activity state without inventing intervals

#### Scenario: App uninstalled since the selected day
- **GIVEN** the user opens App Detail for a package that is no longer installed
- **WHEN** the system cannot resolve a human-readable label
- **THEN** the system shows the raw package name with an explicit "uninstalled" indicator
- **AND** the system still shows total time and intervals from the platform-retained data
