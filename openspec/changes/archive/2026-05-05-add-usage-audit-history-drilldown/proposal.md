# Change: Add daily history browsing and drill-down to usage audit

## Why
The mini-app already has a History screen, but it only lists recent overnight reports. There is no way to browse a specific past day's usage or drill into a single app's observed activity. This change extends History with daily browsing and adds two drill-down levels (Day Detail, App Detail) so users can investigate any past day for which the platform retains usage data.

## What Changes
- Extend the existing History screen with a list of recent days within the platform-retained usage window, each showing total foreground usage time.
- Add a Day Detail screen that shows the full ranked app list and the overnight summary attached to the selected day.
- Add an hourly foreground-usage distribution to the Day Detail screen.
- Add an App Detail screen that drills into a single app for a chosen day, showing observed foreground intervals and total time.
- Reuse the existing share / CSV export from the selected day; do not extend the v1 CSV schema.

## Impact
- Affected specs: `usage-audit`
- Affected code: `android/app/src/main/kotlin/dev/charly/paranoid/apps/usageaudit/`
  - `UsageQueries.kt` — new adapter using `UsageStatsManager.queryEvents` for per-app foreground intervals; audit existing day-scoped methods before extending.
  - `UsageAuditScreens.kt` and new layouts under `android/app/src/main/res/layout/` for History list rows, Day Detail, App Detail.
  - `UsageAuditActivity.kt` — navigation graph: History → Day Detail → App Detail.
- Terminology: "platform-retained usage window" = the lookback period for which Android's `UsageStatsManager` exposes usage data (typically several days; not a fixed number). Older days simply do not appear.
