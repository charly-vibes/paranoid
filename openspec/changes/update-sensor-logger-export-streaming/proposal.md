# Change: Streaming session export with progress & volume awareness

## Why
The first export implementation loads every event into a `List` and builds the
entire output as one in-memory `String` (pretty-printed for JSON). A 26-second
session already produces ~31k events / 6 MB JSON; a multi-hour session would be
millions of events / hundreds of MB and would OOM the app. The user also gets no
feedback during a long export and no warning about how much data they are about
to produce.

## What Changes
- **Stream to file**: page through events with keyset pagination and write
  directly to the export file via a buffered writer, never holding the whole
  dataset or output string in memory. JSON is written compactly (no pretty-print).
- **Volume awareness**: the export picker shows the event count and an estimated
  file size per format before the user commits.
- **Progress feedback**: a cancellable progress dialog shows events written /
  total while the export runs.
- **Resource safety**: refuse to export when estimated size exceeds free space
  (with margin); write to a temp file and rename on success; clean up partial and
  stale export files.

## Impact
- Affected specs: sensor-logger-export (MODIFIED requirements + new ones)
- Affected code:
  - `apps/sensorlogger/data/SensorExporters.kt` (streaming writers + size estimate)
  - `apps/sensorlogger/data/SensorDaos.kt` (keyset paging query, Long count)
  - `apps/sensorlogger/ui/SensorSessionDetailViewModel.kt` (export state machine)
  - `apps/sensorlogger/SensorSessionDetailActivity.kt` (size dialog + progress UI)
  - `apps/netmap/data/export/ShareHelper.kt` (shareFile overload)
