# Change: Add screen-time monitor mini-app

## Why
Users want lightweight, continuous awareness of how long their phone screen has been on,
with progressively spaced nudges that interrupt unconscious scrolling without being overbearing.
The existing UsageAudit mini-app is read-only and historical; nothing in the app provides
live session feedback or proactive interruption.

## What Changes
- Add a new `screentime` mini-app with a foreground service that tracks continuous screen-on
  sessions and monitors which app is in the foreground.
- Add a system overlay (drawn over all apps via `WindowManager TYPE_APPLICATION_OVERLAY`) that
  shows a thin progress bar filling toward the next checkpoint.
- Add checkpoint notifications at 7, 13, and 29 minutes from session start, then every 29 minutes
  thereafter, resetting when the screen turns off.
- Add a morning report delivered each day at 08:00 via WorkManager, covering yesterday's
  usage, a rolling 7-day average, and month-to-date cumulative totals — all broken down by app.
- Add a `ScreenTimeActivity` as the mini-app entry point for permission setup, start/stop
  control, and a view of today's sessions.

## Impact
- New specs: `screen-time-session`, `screen-time-overlay`, `screen-time-notifications`, `screen-time-reports`
- New code: `apps/screentime/` Activity, Service, BroadcastReceiver, WorkManager Worker,
  overlay View, local data store
- Manifest additions: `SYSTEM_ALERT_WINDOW` permission, foreground service declarations,
  WorkManager worker registration, power event receiver
- Minimal change to hub app list (`MainActivity`) to register the new mini-app entry point; no other existing code is modified
