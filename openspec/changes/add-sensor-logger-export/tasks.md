## 1. Exporters (pure, JVM-testable)
- [x] 1.1 Add `exportSensorCsv(session, events)` producing one row per event
- [x] 1.2 Add `exportSensorJson(session, events)` with session metadata + events array
- [x] 1.3 Add unit tests for both formats (header, row count, metadata)

## 2. ViewModel
- [x] 2.1 Add `requestExport(format)` that loads events and emits a share payload

## 3. UI
- [x] 3.1 Add Export button to `activity_sensor_session_detail.xml`
- [x] 3.2 Wire button to a format picker dialog and `ShareHelper.share(...)`

## 4. Verify
- [x] 4.1 `just test` green
- [x] 4.2 `just build` green
- [x] 4.3 `openspec validate add-sensor-logger-export --strict`
