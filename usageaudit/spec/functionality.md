# UsageAudit — Functionality Specification

## Purpose

Minimal daily phone usage and overnight battery audit. Shows today's total observed app activity, last night's battery drain with suspected contributors, and a recent nights history. All data stays local with share and CSV export.

## Core Goals

- Daily usage summary: total phone usage time and top foreground apps for the current day
- Overnight battery audit: battery level change and app activity across a configurable night window
- Transparent attribution: distinguish observed activity from inferred or correlated drain
- Export and share in human-readable text and CSV formats
- Offline-first: all data stored and viewable locally without network

## Non-Goals (v1)

- Continuous background monitoring or persistent tracking service
- Cloud sync or multi-device aggregation
- Exact per-app battery drain percentages (platform does not expose this reliably)
- Root or Shizuku integration

## Permissions

Required at runtime:
1. `PACKAGE_USAGE_STATS` — read app usage statistics via `UsageStatsManager`

## Screens

| Screen | Purpose |
|---|---|
| Today | Daily usage summary with ranked app list |
| Last Night | Overnight battery audit with activity breakdown |
| History | Recent nights with battery drop summaries |

## Overnight Window

- Default: 22:00–07:00
- User-configurable start and end times
- Validation: start ≠ end, window ≤ 16 hours

## Export

| Format | Use case |
|---|---|
| Plain text | Human-readable share via Android sharesheet |
| CSV | Spreadsheet-friendly export via Android sharing |

## Data Integrity

- Battery attribution labels overnight apps as "observed activity" or "suspected contributors"
- No claim of exact per-app battery percentages unless the platform source is explicitly available and trustworthy
- Incomplete overnight data triggers a user-visible warning
