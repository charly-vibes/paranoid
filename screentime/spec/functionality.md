# ScreenTime — Functionality Specification

## Purpose

On-device screen-time monitor that tracks each screen-on session, attributes per-app foreground time, nudges awareness with usage checkpoints and a top-of-screen overlay bar, and posts a daily morning report. All data stays local; the only new permission is the overlay.

## Core Goals

- Session tracking: record every screen-on session with start/end and total duration
- Per-app attribution: sample the foreground app so each session shows where time went
- Live awareness: an always-on-top overlay bar fills toward each checkpoint and shifts green→red past 70%
- Checkpoints: notifications at 7, 13, and 29 minutes, then every 29 minutes within a continuous session
- Morning report: an 08:00 summary of yesterday, the 7-day rolling average, and month-to-date totals
- Offline-first: all sessions stored locally in the shared on-device database

## Non-Goals (v1)

- Cloud sync or multi-device aggregation
- App blocking, limits, or enforcement (this is awareness-only)
- Accessibility-service integration (avoided to keep the permission surface small)
- Exact per-app energy accounting

## Permissions

Required:
1. `PACKAGE_USAGE_STATS` — read the current foreground app via `UsageStatsManager`
2. `POST_NOTIFICATIONS` — checkpoint and morning-report notifications (Android 13+)
3. `SYSTEM_ALERT_WINDOW` — draw the overlay progress bar over other apps (the only new permission)

Each permission is shown with its grant status and a deep-link button on the entry screen.

## Screens

| Screen | Purpose |
|---|---|
| ScreenTime (entry) | Permission status with deep links, start/stop toggle, today's sessions, and a warning when a permission is revoked while monitoring |

## How It Works

- A foreground service keeps monitoring alive and survives reboots (restarted via a boot receiver).
- Screen on/off is tracked with a 30-second screen-off debounce, so brief blips don't split a session.
- The foreground app is sampled every 5 seconds to build per-app intervals within each session.
- Sessions and their per-app intervals persist in the shared on-device Room database.

## Overlay Bar

- A thin, non-interactive bar pinned to the very top edge of the screen (over the status bar), so it never covers app content.
- Fills linearly from the previous checkpoint toward the next one, resetting at each checkpoint.
- Colour stays green up to 70% fill, then interpolates linearly toward red, reaching full red at 100%.

## Checkpoints

- Fixed checkpoints at 7 and 13 minutes from session start.
- Then a recurring checkpoint every 29 minutes (29, 58, 87, …) while the session continues.
- The cadence resets when the session ends.

## Morning Report

- A WorkManager job posts a notification at 08:00 local time and re-enqueues itself for the next day.
- Summarises yesterday's total and top apps, the rolling 7-day daily average, and month-to-date usage.
- Sessions older than 31 days are pruned during the daily run.

## Data Integrity

- Foreground time that cannot be attributed to a user app (launcher, lock screen, system UI) is counted toward total screen-on time but excluded from per-app breakdowns.
- Open sessions left behind by a crash or reboot are reconciled on the next start.
