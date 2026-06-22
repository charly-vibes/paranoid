# Design: Screen Time Monitor

## Context
A lightweight nudge tool: the user wants to notice how long the screen has been on and which
apps they are using, without requiring root or system-level battery attribution. The design
must stay within the constraints of a normal Android app with minimal dependencies.

## Goals / Non-Goals
- Goals:
  - Track continuous screen-on sessions (start/stop times).
  - Sample the foreground app at low frequency to produce per-app time within each session.
  - Show a live overlay progress bar visible over all apps.
  - Deliver checkpoint notifications that cycle at 7 → 13 → 29 → 29 → 29 … minutes.
  - Produce a morning summary report with day, 7-day, and monthly breakdowns.
- Non-Goals:
  - Exact per-app battery attribution.
  - Accessibility service integration (avoided to minimise permission surface).
  - Cloud sync or cross-device history.
  - App blocking or forced breaks.
  - Notification interaction (dismiss, snooze) beyond the default Android behaviour.

## Feasibility Assessment

| Capability | Android approach | Feasibility | Notes |
|---|---|---|---|
| Screen-on/off detection | `BroadcastReceiver` for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` | High | Standard; must be registered dynamically from a running service. |
| Foreground app sampling | `UsageStatsManager.queryEvents()` polled on a Handler loop | High | `PACKAGE_USAGE_STATS` already in manifest. ~5s poll interval is sufficient and battery-light. |
| System overlay bar | `WindowManager` `TYPE_APPLICATION_OVERLAY` | High | Requires `SYSTEM_ALERT_WINDOW` — the only new permission. User must grant via special-access Settings screen. |
| Checkpoint notifications | `Handler.postDelayed` inside the foreground service | High | Simpler than `AlarmManager`; the service is already running. Recalculate remaining delay on resume to survive screen-off/on cycles. |
| Morning report scheduling | `WorkManager` `OneTimeWorkRequest` with `initialDelay` to next 08:00, re-enqueued in `doWork()` | High | `PeriodicWorkRequest` cannot target a specific clock time and drifts 15-30+ min/day. One-time + re-enqueue stays aligned with 08:00 and self-corrects after time zone changes. |
| Per-app usage aggregation | Accumulate foreground intervals from UsageEvents | High | Already proven by `UsageAudit`. The new app stores its own lightweight session records. |

## Decisions

- Decision: Use `UsageStatsManager.queryEvents()` polled on a ~5 s Handler loop rather than an
  AccessibilityService for foreground app detection.
  - Why: No extra user-visible permission; `PACKAGE_USAGE_STATS` is already granted for
    `UsageAudit`. Five-second granularity is sufficient for session-level per-app breakdowns.
  - Alternatives considered: AccessibilityService gives real-time events but requires a
    permission that is both scary to users and difficult to explain for this purpose.

- Decision: Schedule checkpoint notifications with `Handler.postDelayed` inside the foreground
  service, not `AlarmManager`.
  - Why: The service is already running for the overlay; adding scheduler logic there avoids
    a second wakeup mechanism and keeps the code cohesive.
  - Alternatives considered: `AlarmManager` is needed when the app must wake from death; not
    required here because the service stays alive while monitoring is active.

- Decision: Checkpoint sequence is 7 → 13 → 29 minutes from session start, then every 29 minutes
  thereafter. The clock resets when the screen turns off.
  - Why: Three distinct nudges (short, medium, extended) then a steady cadence. Resetting on
    screen-off aligns with the user's intuition of a "session".
  - Alternatives considered: Continuing across screen-off was rejected because a pocket unlock
    or brief notification check should not count as a continuous session.

- Decision: Add a 30-second debounce for screen-off before ending a session.
  - Why: Very short screen-off events (pocket unlock, glance at notification) should not reset
    the checkpoint clock. 30 s is short enough to feel immediate but avoids spurious resets.
  - Alternatives considered: No debounce (resets too aggressively), 2 min (too long — masks
    genuine breaks).

- Decision: The overlay bar is display-only (no tap, no dismiss).
  - Why: Interaction requires an `AccessibilityService` or `OnTouchListener` with `FLAG_NOT_TOUCHABLE`
    removed, which would intercept touches from underlying apps. Keeping it inert avoids
    this complication and prevents accidental dismissal.

- Decision: Use a `OneTimeWorkRequest` with `initialDelay = (next 08:00 - now)`, re-enqueued
  at the end of `doWork()` for the next day's 08:00, rather than a `PeriodicWorkRequest`.
  - Why: `PeriodicWorkRequest` drifts 15-30+ minutes relative to wall clock per day and
    cannot target a specific clock time. The one-time re-enqueue pattern stays aligned with
    08:00 and naturally self-corrects after time zone changes or DST transitions.
  - Alternatives considered: `PeriodicWorkRequest` with `setInitialDelay`; rejected because
    it drifts irrecoverably over days.

- Decision: The morning report is a single rich notification with three expandable sections
  (yesterday, 7-day rolling, month-to-date). No separate screen in v1.
  - Why: Keeps the feature minimal. The user can open `UsageAudit` for deeper historical drill-down.
  - Alternatives considered: A dedicated report Activity; deferred to a future change.

- Decision: Session data is stored in screentime's own Room tables (`screentime_sessions`,
  `screentime_app_intervals`) inside the shared `ParanoidDatabase`, not shared with `UsageAudit`.
  - Why: Room is already the project-wide persistence layer (used by netmap, netdiag,
    usageaudit, sensorlogger), and every mini-app already adds its own tables to the single
    `paranoid.db` Room database. Screentime follows that convention: dedicated tables (one-to-many
    sessions→intervals, CASCADE delete) keep its data isolated from other mini-apps while reusing
    the existing migration/DAO infrastructure. Domain models and the session state machine stay
    pure Kotlin so the lifecycle logic remains unit-testable without Android.
  - Alternatives considered: A separate raw `SQLiteOpenHelper` database; rejected — it would
    introduce a second persistence pattern inconsistent with the rest of the codebase for no
    benefit. (An earlier draft of this design incorrectly claimed Room was "not yet used"; in
    fact it is the established convention.)

- Decision: Store all session timestamps as UTC epoch milliseconds.
  - Why: Local timestamps are ambiguous across time zone changes and DST transitions. Reports
    group sessions into calendar days using the device's current local timezone at query time
    only, so stored UTC values are converted at the last moment.
  - Alternatives considered: Storing local time strings; rejected because they are
    irrecoverably incorrect if the user travels or DST changes after storage.

## Definitions
- **Monitoring**: the `ScreenTimeService` is running. Enabled by the user via `ScreenTimeActivity`; persists across reboots.
- **Session**: a continuous screen-on period within an active monitoring context. Starts on `ACTION_SCREEN_ON`, ends when the screen has been off for more than 30 seconds.
- **Checkpoint**: a time threshold within a session at which the system posts a notification (7 min, 13 min, 29 min, then every 29 min thereafter).

## Overlay Positioning
Android does not permit drawing above the system status bar. The overlay bar is positioned
immediately below the status bar (y = status bar height), full screen width, 4 dp tall.
Height is measured at draw time via `WindowInsets`. On API 29 and below, `WindowInsets` on
`TYPE_APPLICATION_OVERLAY` windows may not reliably report system bar heights; fall back to
`resources.getIdentifier("status_bar_height", "dimen", "android")` with a 24 dp constant
as a final fallback.
The bar uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` so it never intercepts input.
