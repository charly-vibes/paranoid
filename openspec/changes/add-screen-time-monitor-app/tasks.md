# Tasks: add-screen-time-monitor-app

## Suggested implementation order
1 → 2 → 3A/3B/3C → 4 → 5 → 6 → 7 → 8 → 9

---

## 1. Ticket: Approve design before writing code

**Goal:** read and sign off on design.md before implementation starts.

- [x] 1.1 Read design.md in full; verify the decisions for session debounce, checkpoint sequence,
  overlay position, WorkManager scheduling (OneTimeWorkRequest + re-enqueue), UTC timestamp
  storage, and graceful degradation are all acceptable.
- [x] 1.2 Confirm data retention policy: minimum 31 days in SQLite.

**Acceptance criteria:**
- [x] 1.3 All design decisions in design.md are approved; no open questions remain before ticket 2.

---

## 2. Ticket: Session domain model and persistence

**Goal:** pure data layer with no Android dependencies, fully unit-testable.

- [x] 2.1 **Red:** write failing unit test for session start/stop with correct timestamps.
- [x] 2.2 **Green:** implement `Session` and `AppInterval` domain models.
- [x] 2.3 **Red:** write failing unit test for the 30 s debounce: screen-off < 30 s does not close session.
- [x] 2.4 **Green:** implement debounce logic in the session state machine.
- [x] 2.5 **Red:** write failing unit test for per-app interval accumulation (app switch mid-session).
- [x] 2.6 **Green:** implement foreground interval accumulation.
- [x] 2.7 Define SQLite schema for `sessions` and `app_intervals` tables.
- [x] 2.8 **Red:** write failing unit test for query: total foreground time by app for a date range.
- [x] 2.9 **Green:** implement `SessionStore` with insert and query methods.
- [x] 2.10 **Refactor:** tidy domain model, store interface, and table schema.

---

## 3. Ticket: Foreground service and screen event wiring

**Goal:** the service stays alive while monitoring is active and reacts to screen events.

### 3A. Service skeleton
- [x] 3.1 **Red:** write instrumentation test confirming service starts and shows a persistent notification.
- [x] 3.2 **Green:** implement `ScreenTimeService` as a foreground service with start/stop commands.
- [x] 3.3 Register service in `AndroidManifest.xml` with required `foregroundServiceType`.

### 3B. Screen event receiver
- [x] 3.4 **Red:** write unit test for receiver: SCREEN_ON starts a session, SCREEN_OFF (with debounce)
  closes it.
- [x] 3.5 **Green:** implement `ScreenPowerReceiver` registered dynamically from the service.
- [x] 3.6 **Red:** write unit test for receiver: SCREEN_OFF shorter than 30 s does not close session.
- [x] 3.7 **Green:** implement debounce with `Handler.postDelayed` cancellation on SCREEN_ON.

### 3C. Boot receiver
- [x] 3.8 Implement `ScreenTimeBootReceiver` registered for `BOOT_COMPLETED`; on boot, if monitoring
  was previously enabled (persisted flag in SharedPreferences), restart `ScreenTimeService`.
- [x] 3.9 On service start, check for any open session (null `end_time`) and close it: use
  `last_sample_time` if available, otherwise `service_start_time`. If the open session is
  from a prior calendar day, close it at 23:59:59 of that day.
- [x] 3.10 Register `ScreenTimeBootReceiver` in `AndroidManifest.xml` with `BOOT_COMPLETED` intent filter.

---

## 4. Ticket: Foreground app sampling loop

**Goal:** the service polls UsageEvents and accumulates per-app intervals.

- [x] 4.1 **Red:** write unit test for sampling: single app in foreground for N samples → N×5 s attributed.
- [x] 4.2 **Green:** implement 5 s poll loop calling `UsageStatsManager.queryEvents()`.
- [x] 4.3 **Red:** write unit test for app switch: correct interval closed and new one opened.
- [x] 4.4 **Green:** implement event-driven interval transition in the sampling loop.
- [x] 4.5 **Red:** write unit test for graceful skip when usage access is revoked at runtime.
- [x] 4.6 **Green:** implement null-safe/permission-safe query with notification warning on failure.
- [x] 4.7 **Refactor:** extract sampling logic from service into a testable `ForegroundAppSampler`.

---

## 5. Ticket: Checkpoint notification scheduler

**Goal:** notifications fire at the correct elapsed times within a session.

- [x] 5.1 **Red:** write unit test for sequence: next delay after start = 7 min.
- [x] 5.2 **Red:** write unit test for sequence: next delay after 7 min checkpoint = 6 min (to reach 13).
- [x] 5.3 **Red:** write unit test for sequence: next delay after 13 min = 16 min (to reach 29).
- [x] 5.4 **Red:** write unit test for sequence: next delay after 29 min = 29 min.
- [x] 5.5 **Green:** implement `CheckpointScheduler` using `Handler.postDelayed`.
- [x] 5.6 **Red:** write unit test for cancel: pending callback is cancelled on session end.
- [x] 5.7 **Green:** implement scheduler cancel on `onSessionEnd`.
- [x] 5.8 **Red:** write unit test for resume: given a session `start_time` read from `SessionStore`,
  `elapsed = now - start_time`; assert correct next checkpoint and delay are derived.
- [x] 5.9 **Green:** implement resume logic: on service start with an active session, read `start_time`
  from `SessionStore`, compute elapsed, determine next checkpoint from the sequence
  [7, 13, 29, 58, 87, …] min, and schedule with the remaining delay.
- [x] 5.10 Wire scheduler into `ScreenTimeService` and post notifications via `NotificationManager`.

---

## 6. Ticket: System overlay bar

**Goal:** a thin progress bar floats over all apps during an active session.

- [x] 6.1 Add `SYSTEM_ALERT_WINDOW` to `AndroidManifest.xml`.
- [x] 6.2 **Red:** write unit test for progress calculation: correct fill fraction at elapsed time T
  given next checkpoint at time C.
- [x] 6.3 **Green:** implement `OverlayProgressView` (custom `View`) with fill fraction setter.
- [x] 6.4 Implement `OverlayManager` that adds/removes the view via `WindowManager` with
  `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`.
- [x] 6.5 Measure status bar height via `WindowInsets` at attach time; position bar below it.
- [x] 6.6 Wire `OverlayManager` into the service: add on session start, remove on session end,
  update fill fraction when `CheckpointScheduler` fires.
- [x] 6.7 Handle `SYSTEM_ALERT_WINDOW` revocation at runtime without crashing.

---

## 7. Ticket: Morning report WorkManager job

**Goal:** an 08:00 notification summarises yesterday, 7-day rolling, and month-to-date usage.

- [x] 7.1 **Red:** write unit test for yesterday's total and app ranking query.
- [x] 7.2 **Green:** implement `ReportAggregator.yesterday(date)` against `SessionStore`.
- [x] 7.3 **Red:** write unit test for 7-day rolling average: 7 full days, some with zero usage.
- [x] 7.4 **Green:** implement `ReportAggregator.sevenDayAverage(endDate)`.
- [x] 7.5 **Red:** write unit test for monthly cumulative: sums from 1st to yesterday.
- [x] 7.6 **Green:** implement `ReportAggregator.monthToDate(date)`.
- [x] 7.7 **Refactor:** tidy aggregator, extract shared date-range query.
- [x] 7.8 Implement `MorningReportWorker` that calls `ReportAggregator` and posts a rich notification.
- [x] 7.9 Schedule `MorningReportWorker` as a `OneTimeWorkRequest` with `initialDelay = (next 08:00 - now)`
  on first monitoring start; at the end of `doWork()`, re-enqueue for the following day's 08:00.
- [x] 7.10 Confirm WorkManager dependency is in `build.gradle`; no manifest entry is needed for Workers.

---

## 8. Ticket: ScreenTimeActivity — permission flow and start/stop UI

**Goal:** the entry point handles all three permissions and lets the user enable monitoring.

- [x] 8.1 Implement permission-check screen: shows status for `PACKAGE_USAGE_STATS`,
  `POST_NOTIFICATIONS`, and `SYSTEM_ALERT_WINDOW` with deep-link buttons for each.
- [x] 8.2 Implement start/stop toggle that starts/stops `ScreenTimeService`.
- [x] 8.3 Show today's sessions in a simple list (start time, duration, top app).
- [x] 8.4 Show inline warnings when a permission is revoked while monitoring is active.
- [x] 8.5 Add `ScreenTimeActivity` to `AndroidManifest.xml` and the hub mini-app list.

---

## 9. Ticket: Integration and device validation

**Goal:** confirm end-to-end behaviour on a real device before shipping.

- [x] 9.1 **Instrumentation test:** start monitoring, lock/unlock the screen, confirm session written.
- [x] 9.2 **Instrumentation test:** revoke `SYSTEM_ALERT_WINDOW` mid-session; confirm service survives.
- [x] 9.3 **Device test:** verify overlay is visible and non-interactive in a third-party app.
- [x] 9.4 **Device test:** trigger 7-min checkpoint and confirm notification fires.
- [x] 9.5 **Device test:** confirm morning report notification fires (use `adb shell am set-time` to
  simulate 08:00 with WorkManager in expedited mode during testing).
- [x] 9.6 Prune `SessionStore` records older than 31 days; confirm no data loss for ≤31 days.
