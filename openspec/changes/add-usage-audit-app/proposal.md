# Change: Add usage audit mini-app

## Why
Users want a minimal Android-only tool that explains phone time usage and overnight battery drain without cloud sync or heavy analytics. The current app set has no mini-app that combines app usage summaries, night drain auditing, and easy export/share.

## What Changes
- Add a new `usage-audit` capability for a minimal mini-app focused on daily phone usage and overnight battery analysis.
- Specify a daily summary that shows total phone usage time and top apps by foreground time.
- Specify an overnight audit that correlates a configurable night window, battery level change, and app activity observed during that window.
- Specify export and share flows for compact text summaries and structured CSV exports.
- Define transparency requirements so the UI clearly distinguishes exact observations from estimated or inferred battery attribution.

## Impact
- Affected specs: `usage-audit`
- Affected code: new mini-app under `android/app/src/main/kotlin/dev/charly/paranoid/apps/usageaudit/`, manifest entries, layouts/resources, local persistence for snapshots/exports
