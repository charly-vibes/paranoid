# Changelog

All notable changes to Paranoid are documented here.

## [v0.6.0] — 2026-04-28

### UsageAudit — New Mini-App

- New **UsageAudit** mini-app: minimal daily phone usage and overnight battery audit
- Usage access gating with system permission prompt
- Usage and battery data layer (all data stays local)
- **Today** and **Last Night** screens with activity and battery drain summaries
- **History** screen with recent nights overview
- Export to CSV and share sheet integration
- Integrated into the Paranoid hub launcher

## [v0.5.0] — 2026-04-23

### NetDiag — Exchange & Polish

- Added **snapshot exchange** via Bluetooth, QR code, and file import/export
- Added **share sheet** to export snapshots to any app
- Transport warning when comparing snapshots captured over different network types
- Timeout handling for capture operations
- Empty states for snapshot list and comparison screens
- Site fix: spec overlay now opens on card click

## [v0.4.0] — 2026-04-22

### NetDiag — Capture, Comparison & Persistence

- Full capture UI with snapshot detail and comparison results screens
- Session history view
- Room persistence layer for snapshots
- Comparison engine with diff logic
- Snapshot collectors (WiFi, cellular, DNS, HTTP)
- Initial app setup: dependencies, permissions, activities, hub entry

## [v0.3.0] — 2026-04-16

### NetMap — Export & Launcher

- App logo added to Android launcher and webpage
- Export all cells per measurement with tower position estimates
- Export correctness fixes (from Ro5 review)
- ABI splits and R8 minification to reduce APK size

## [v0.2.1] and earlier

See git history.
