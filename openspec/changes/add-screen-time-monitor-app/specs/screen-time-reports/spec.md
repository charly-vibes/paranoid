# screen-time-reports Specification

## Purpose
Deliver a morning summary each day covering the previous day's phone usage, a rolling
7-day average, and cumulative month-to-date totals — all broken down by app.

## ADDED Requirements

### Requirement: Daily morning report
The system SHALL schedule a daily report at 08:00 local time using WorkManager. The report
covers three time horizons: yesterday (full day), the rolling 7 days ending yesterday, and
the current calendar month to date. Each horizon includes total screen time and a ranked
list of apps with their observed foreground time.

#### Scenario: Report fires at 08:00 with data
- **GIVEN** monitoring has been active for at least one day
- **WHEN** 08:00 local time is reached
- **THEN** the system posts a notification summarising yesterday's total screen time
- **AND** the notification expandable section shows the top apps by foreground time for yesterday
- **AND** the notification expandable section shows the rolling 7-day average total
- **AND** the notification expandable section shows the month-to-date cumulative total

#### Scenario: Report on first morning after install
- **GIVEN** monitoring was first enabled today (no full previous day of data)
- **WHEN** the 08:00 report fires
- **THEN** the system shows "No data for yesterday" rather than fabricating figures
- **AND** the 7-day and monthly sections show whatever partial data is available

#### Scenario: Report with no session data for yesterday
- **GIVEN** monitoring was active but the phone was not used at all yesterday
- **WHEN** the 08:00 report fires
- **THEN** the system shows 0 minutes total for yesterday
- **AND** the app breakdown is empty with an explicit "no usage recorded" message

#### Scenario: Report persists data from previous days
- **GIVEN** session data for multiple past days is stored
- **WHEN** the 08:00 report fires
- **THEN** the 7-day average is computed from the 7 calendar days ending yesterday
- **AND** the monthly cumulative is computed from the 1st of the current calendar month through yesterday

### Requirement: Per-app breakdown in report
Each time horizon in the morning report SHALL include a ranked list of apps with their
observed foreground time for that horizon, derived from the accumulated foreground intervals
in the session store. The notification SHALL display at most 5 apps per time horizon using
Android's `InboxStyle`; if more than 5 apps were active, the remainder SHALL be summarised
as "N more apps". `system.unattributed` intervals are excluded from all per-app breakdowns.

#### Scenario: Apps ranked by descending foreground time
- **GIVEN** multiple apps were used yesterday
- **WHEN** the report notification is rendered
- **THEN** apps are listed in descending order of yesterday's foreground time
- **AND** each entry shows the app label (or package name if label is unavailable) and duration
- **AND** at most 5 apps are shown; if more were active, the 6th line reads "N more apps"

#### Scenario: 7-day average per app
- **GIVEN** session data covers multiple days in the rolling 7-day window
- **WHEN** the 7-day section is computed
- **THEN** each app's average daily foreground time is derived from the 7-day total divided by 7
- **AND** apps with zero usage on some days are not excluded from the average

#### Scenario: Monthly total per app
- **GIVEN** session data covers multiple days in the current calendar month
- **WHEN** the monthly section is computed
- **THEN** each app's cumulative foreground time is the sum across all days from the 1st to yesterday
- **AND** the system does not include today's partial data in the monthly cumulative

### Requirement: Cross-midnight session attribution
The system SHALL split session intervals at the calendar day boundary (00:00:00 local time)
when computing per-day totals. A session that starts before midnight and ends after midnight
contributes time proportionally to each calendar day.

#### Scenario: Session crossing midnight attributed to both days
- **GIVEN** a session starts at 23:50 on Day 1 and ends at 00:10 on Day 2
- **WHEN** the morning report computes Day 1's total
- **THEN** the system attributes 10 minutes to Day 1 and 10 minutes to Day 2
- **AND** per-app intervals within the split are attributed proportionally to each day

#### Scenario: Cross-midnight session does not inflate daily total
- **GIVEN** the only usage on a given day was the pre-midnight portion of a session ending after midnight
- **WHEN** that day's total is computed
- **THEN** the system shows only the minutes before midnight, not the full session duration

### Requirement: Session data persistence
The system SHALL persist session records (start time, end time, per-app foreground intervals)
to a local SQLite store so they remain available for the morning report WorkManager job,
which runs in a separate process context.

#### Scenario: Session written on close
- **GIVEN** a session ends (screen off beyond debounce)
- **WHEN** the session record is finalised
- **THEN** the session and its per-app intervals are written to the local store
- **AND** the data is immediately available to report queries

#### Scenario: Data retention
- **GIVEN** the local store contains session data
- **WHEN** data is older than 31 days
- **THEN** the system MAY prune records older than 31 days to bound storage growth
- **AND** the system SHALL retain at least 31 days to support full monthly reports
