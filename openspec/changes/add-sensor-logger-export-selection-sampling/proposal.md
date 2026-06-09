# Change: Selective & downsampled session export

## Why
A session can mix nine sensors at very different rates. Users often want only a
subset of sensors and/or a thinned-out version of the data (e.g. one sample per
second, or 1 of every 5) instead of every raw event. Exporting everything is
both larger than needed and harder to analyze.

## What Changes
- **Sensor selection**: the export flow lets the user choose which sensor types
  to include (default: all present). Filtering is pushed into SQL so progress and
  counts reflect only the selected sensors.
- **Downsampling**: the user can pick a sampling strategy applied per sensor type:
  - *All samples* (no thinning),
  - *1 of N* (keep every Nth event),
  - *Every T* (keep at most one sample per interval).
- The JSON envelope records the actual exported `event_count` (written after the
  events stream, so sampling counts are accurate) plus the chosen sampling.
- The export config dialog shows a live estimated output size that reacts to the
  selection and sampling choice.

## Impact
- Affected specs: sensor-logger-export (new requirements)
- Affected code:
  - `apps/sensorlogger/data/SensorExporters.kt` (sampling model, count-at-end JSON, estimate)
  - `apps/sensorlogger/data/SensorExportFilter.kt` (new pure per-sensor filter)
  - `apps/sensorlogger/data/SensorDaos.kt` (type-filtered paged query + count)
  - `apps/sensorlogger/ui/SensorSessionDetailViewModel.kt` (export params)
  - `apps/sensorlogger/SensorSessionDetailActivity.kt` (config dialog)
  - `res/layout/dialog_export_config.xml` (new)
