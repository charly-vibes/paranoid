## 1. Data layer
- [x] 1.1 Add keyset-paged query `getBySessionAfter(sessionId, lastElapsedMs, lastId, limit)`
- [x] 1.2 Add `countForSessionLong(sessionId)` returning Long

## 2. Streaming exporters (pure, JVM-testable)
- [x] 2.1 CSV: `writeSensorCsvHeader(out)` + `writeSensorCsvEvents(events, out)`
- [x] 2.2 JSON: `writeSensorJsonStart/Event/End` writing compact, escaped output
- [x] 2.3 Non-finite floats serialized as null (JSON) / passed through (CSV)
- [x] 2.4 `estimateExportBytes(format, eventCount)` cheap size estimate
- [x] 2.5 Unit tests: header/rows, JSON envelope+escaping, estimate monotonic

## 3. ViewModel export state machine
- [x] 3.1 `ExportState` sealed type: Idle / Running(written,total,bytes) / Ready(file) / Failed
- [x] 3.2 Page + stream-write to temp file on Dispatchers.IO, rename on success
- [x] 3.3 Progress updates per page; cooperative cancellation; cleanup on cancel/fail
- [x] 3.4 Free-space guard before export

## 4. ShareHelper
- [x] 4.1 Add `shareFile(context, file, mimeType)` with ClipData grant

## 5. UI
- [x] 5.1 Export picker shows count + estimated size per format
- [x] 5.2 Cancellable progress dialog bound to ExportState
- [x] 5.3 Launch share sheet on Ready; reset state after

## 6. Verify
- [x] 6.1 `just test` green
- [x] 6.2 `just build` green
- [x] 6.3 `openspec validate update-sensor-logger-export-streaming --strict`

## 7. Review follow-ups (Ro5)
- [x] 7.1 Lifecycle-aware export collection (repeatOnLifecycle STARTED)
- [x] 7.2 Identity-guarded terminal-state consumption
- [x] 7.3 Block export of incomplete sessions (snapshot consistency)
- [x] 7.4 Cancellation checkpoints after page write and before rename
- [x] 7.5 Stale cleanup by age only (don't delete active .tmp)
