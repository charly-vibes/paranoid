## 1. Sampling model & filter (pure, JVM-testable)
- [x] 1.1 `ExportSampling` sum type: All / EveryNth(n) / Interval(ms)
- [x] 1.2 `SensorExportFilter` — per-sensor-type stateful accept(event)
- [x] 1.3 `estimateSampledCount(sampling, selectedCounts, durationMs)`
- [x] 1.4 Unit tests: EveryNth keeps 1/N per sensor, Interval throttles per sensor, type filter

## 2. JSON count-at-end
- [x] 2.1 `writeSensorJsonStart` no longer needs total; `writeSensorJsonEnd(count)` writes accurate event_count + sampling
- [x] 2.2 Update convenience builders + tests

## 3. Data layer
- [x] 3.1 `getBySessionAfterTypes(sessionId, types, lastElapsedMs, lastId, limit)`
- [x] 3.2 `countForSessionTypesLong(sessionId, types)`

## 4. ViewModel
- [x] 4.1 `requestExport(format, includeTypes, sampling)` applies filter while streaming
- [x] 4.2 Reject empty selection

## 5. UI
- [x] 5.1 `dialog_export_config.xml` — sensor checkboxes, sampling spinner, format spinner, live estimate
- [x] 5.2 Wire dialog → requestExport; disable Export when no sensor selected

## 6. Verify
- [x] 6.1 `just test` green
- [x] 6.2 `just build` green
- [x] 6.3 `openspec validate add-sensor-logger-export-selection-sampling --strict`
