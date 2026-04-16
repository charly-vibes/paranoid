## Context
The app should remain small, offline-first, and honest about Android data limitations. The user wants to understand two kinds of consumption: time spent in apps and battery drain, especially overnight. They also want minimal UI and easy export/share.

## Goals / Non-Goals
- Goals:
  - Show daily phone usage and top apps with minimal friction.
  - Show overnight battery loss for a user-visible night window.
  - Correlate overnight battery loss with app activity observed in that same window.
  - Export/share summaries without requiring cloud sync.
- Non-Goals:
  - Exact per-app battery attribution comparable to system-level or privileged tools.
  - Battery optimization, app killing, or automated remediation.
  - Rich categorization, coaching, or notifications in v1.

## Feasibility Assessment

| Capability | Android approach | Feasibility | Notes |
|---|---|---|---|
| Daily app usage time | `UsageStatsManager` / usage access | High | Standard approach for foreground usage summaries; requires user-enabled usage access in Settings. |
| Top apps by time | Aggregate usage stats per package | High | Straightforward once usage access is granted. |
| Overnight battery drop | Persist battery level snapshots from `ACTION_BATTERY_CHANGED` plus app-open and power-state-change capture | Medium | Normal apps can read battery level, but they must collect their own history; Android does not expose a simple full historical battery timeline to third-party apps. v1 excludes periodic background workers. |
| Night window summary | User-configured default window with editable start/end | High | Simpler and more predictable than full auto-detection. |
| App activity during night window | Query usage events/stats within the selected window | High | Good for correlation, but not proof of causation. |
| Exact app battery drain percentages | Not reliably available to ordinary apps across devices/APIs | Low | Must not promise exact per-app battery use. Use “suspected contributors” or “active during drain window” wording. |
| CSV export | Local file generation + `FileProvider` share | High | Already aligned with existing project patterns. |
| Shareable text summary | Compose summary string + Android sharesheet | High | Very low complexity. |

## Decisions
- Decision: Build the first version around three screens: Today, Last Night, and History/Export.
  - Why: This matches the user’s stated needs and keeps the mini-app minimal.
  - Alternatives considered: Single dashboard screen was simpler, but it mixes present-day and overnight questions; a deeper analytics app was rejected as too heavy.

- Decision: Use a fixed default overnight window of 22:00–07:00, user-visible and user-editable, rather than full automatic sleep detection.
  - Why: It is predictable, easy to explain, works across cross-midnight summaries, and is feasible without extra sensors or heuristics.
  - Alternatives considered: Automatic detection from idle periods and charging transitions; rejected for v1 because it adds ambiguity and edge cases.

- Decision: Collect battery snapshots in v1 from Android battery broadcasts plus app-open and power-state-change capture only.
  - Why: This keeps the app minimal and avoids background-worker complexity while still producing useful overnight summaries.
  - Alternatives considered: Periodic background workers; rejected for v1 to keep resource use and implementation complexity low.

- Decision: Use `UsageStatsManager` as the Android source for both daily usage summaries and overnight app-activity correlation.
  - Why: It is the standard Android path for app usage access available to normal apps once the user enables the special Settings access.
  - Alternatives considered: Other system-private battery or usage sources; rejected because they are not reliably available to ordinary apps.

- Decision: Define an “active app” as an app with observed foreground usage overlapping the requested window.
  - Why: This is precise, testable, and honest about what the app can actually observe.
  - Alternatives considered: Counting launches only or any package presence; rejected because they are less useful and more ambiguous.

- Decision: Present overnight app results as observed activity and suspected contributors, not exact battery attribution.
  - Why: Android APIs available to ordinary apps do not reliably expose exact per-app battery drain across devices.
  - Alternatives considered: Showing inferred percentages; rejected because it would overstate confidence.

- Decision: Ship plain-text share plus CSV export in v1.
  - Why: Together they cover easy human sharing and later spreadsheet analysis with minimal implementation cost.
  - Alternatives considered: JSON export; useful later, but lower immediate value than CSV.

- Decision: Limit History/Export in v1 to recent locally available overnight reports generated from stored battery snapshots and usage queries.
  - Why: This preserves the minimal product shape and avoids storing derived reports unless implementation proves it necessary.
  - Alternatives considered: Persisting many derived report types up front; rejected as premature.

## Data Model

### BatterySnapshot
- timestamp
- batteryPercent
- chargingState
- batteryStatus
- batteryHealth (if available)

### AppUsageSlice
- packageName
- appLabel
- windowStart
- windowEnd
- foregroundDurationMillis
- launchCountEstimate (optional if derivable)

### Canonical test input model
Usage-domain tests should operate on normalized `AppUsageSlice` values rather than raw Android framework objects. Android-facing adapters may use `UsageStatsManager` and related usage APIs, but they must map results into slices where:
- each slice represents observed foreground usage for one app in one interval
- an app is considered active in a report window when any slice overlaps that window
- daily totals and rankings are computed from overlapping slice durations normalized to the requested window

### OvernightAudit
- startTime
- endTime
- batteryStartPercent
- batteryEndPercent
- batteryDeltaPercent
- totalForegroundMillis
- activeAppsCount
- notes / warnings
- hadChargingTransition
- hasIncompleteBatteryCoverage

## Risks / Trade-offs
- Battery history quality depends on how consistently the app records snapshots. If the app only samples opportunistically, overnight summaries may be coarse.
  - Mitigation: record on app open, power-state changes, boot, and optionally lightweight scheduled/background refresh where platform limits allow.

- Usage access is a special permission many users do not understand.
  - Mitigation: provide a clear empty state with one-tap navigation to the correct Settings screen and explain why the access is needed.

- Overnight battery drain may happen without visible foreground app activity.
  - Mitigation: make the UI explicit that the report shows observed app activity in the window, not full system internals.

## Export formats

### Plain-text summary
The shareable text summary should be compact and human-readable, suitable for chat or email. It should include:
- report type (`Today` or `Last Night`)
- report window
- total usage time or battery start/end/delta as applicable
- top apps with observed foreground duration
- warning lines for incomplete battery coverage, charging transitions, or no observed app activity

### CSV schema
v1 CSV export should include these columns:
- `report_date`
- `window_start`
- `window_end`
- `app_label`
- `package_name`
- `foreground_duration_millis`
- `battery_start_percent`
- `battery_end_percent`
- `battery_delta_percent`
- `warning_flags`

`warning_flags` is a delimited text field that may contain values such as `incomplete_battery_data`, `charging_transition`, or `no_observed_app_activity`.

## User-facing wording rules
- Use “observed activity” for app usage found inside a report window.
- Use “suspected contributors” only when correlating app activity with battery loss.
- Do not claim exact per-app battery percentages or exact causation.
- When battery drops with no observed app activity, explicitly say that no foreground app activity was observed during the window.
- When charging occurs inside the overnight window, explicitly say that the window included charging activity and that drain values may reflect both discharge and charging.

## Remaining Open Questions
- Should v1 also show screen-on count or unlock count if the data is available with acceptable complexity?
