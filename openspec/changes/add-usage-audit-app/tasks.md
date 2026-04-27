## Suggested implementation order
1 → 2A/2B → 2C → 3A/3B/3C → 4 → 5 → 6 → 7

## 1. Ticket: Document and verify implementation assumptions
**Goal:** lock the Android API strategy and remaining product boundaries before writing code.

- [x] 1.1 Confirm the `UsageStatsManager` access flow for daily usage summaries and overnight app activity queries.
- [x] 1.2 Confirm the v1 battery snapshot strategy: battery broadcasts plus app-open and power-state-change capture; v1 excludes periodic background workers.
- [x] 1.3 Confirm the v1 overnight window rule: fixed default window, user-visible, user-editable, and safe across cross-midnight summaries.
- [x] 1.4 Confirm the v1 export formats: shareable plain-text summary plus CSV export.
- [x] 1.5 Confirm wording rules for exact observations vs inferred drain contributors.
- [x] 1.6 Define the canonical domain input model for tests: normalized app-activity intervals with observed foreground usage overlapping a requested window.
- [x] 1.7 Define the v1 CSV schema and History/Export scope.

**Acceptance criteria:**
- [x] 1.8 The change documents a single agreed v1 battery collection strategy with no periodic background work in v1.
- [x] 1.9 The change documents a single agreed v1 overnight-window rule and defines “active app” as observed foreground usage overlapping the window.
- [x] 1.10 The change documents exact export formats, CSV columns, history scope, and attribution wording.

## 2. Ticket: Build domain model and summary calculators
**Goal:** create the data model and pure logic for daily usage and overnight audits.

### 2A. Daily usage aggregation
- [x] 2.1 **Red:** write a failing unit test for total daily foreground usage time.
- [x] 2.2 **Green:** implement the smallest daily aggregation logic to make the total-usage test pass.
- [x] 2.3 **Red:** write a failing unit test for ranking apps by foreground duration.
- [x] 2.4 **Green:** implement the smallest ranking logic to make the test pass.
- [x] 2.5 **Refactor:** tidy naming, data shapes, and duplication in the daily usage aggregator.

### 2B. Overnight audit calculation
- [x] 2.6 **Red:** write a failing unit test for computing overnight battery start/end values and delta from snapshots.
- [x] 2.7 **Green:** implement the smallest overnight battery summary logic to make the delta test pass.
- [x] 2.8 **Red:** write a failing unit test for listing apps with observed foreground usage overlapping the overnight window.
- [x] 2.9 **Green:** implement the smallest app-activity correlation logic to make the test pass.
- [x] 2.10 **Red:** write a failing unit test for incomplete-window warnings when snapshots do not fully cover the requested window.
- [x] 2.11 **Green:** implement the smallest incomplete-data warning logic to make the test pass.
- [x] 2.12 **Red:** write a failing unit test for a night window with battery drop but no observed app activity.
- [x] 2.13 **Green:** implement the smallest honest empty-result behavior for no-observed-activity nights.
- [x] 2.14 **Red:** write a failing unit test for charging transitions inside the overnight window.
- [x] 2.15 **Green:** implement the smallest mixed charge/discharge summary behavior for that case.
- [x] 2.16 **Red:** write a failing unit test for cross-midnight and timezone-safe overnight window summaries.
- [x] 2.17 **Green:** implement the smallest time-safe summary behavior to make the test pass.
- [x] 2.18 **Refactor:** tidy the overnight audit calculator and shared summary types.

### 2C. Persistence model
- [x] 2.19 **Red:** write a failing unit/integration test for persisting and reading `BatterySnapshot` records.
- [x] 2.20 **Green:** implement the smallest Room entities/DAO needed to persist battery snapshots.
- [x] 2.21 **Red:** write a failing test only if v1 truly needs persisted derived reports for History/Export.
- [x] 2.22 **Green:** implement only the persistence actually required by the failing tests.
- [x] 2.23 **Refactor:** simplify persistence boundaries and remove unnecessary storage.

**Acceptance criteria:**
- [x] 2.24 Daily summaries produce total usage time and ranked apps by foreground duration.
- [x] 2.25 Overnight summaries produce start/end timestamps, battery start/end percentages, and total battery delta.
- [x] 2.26 Overnight summaries can return “incomplete data” warnings when snapshots do not fully cover the requested window.
- [x] 2.27 Persisted storage is limited to what v1 needs for local audit history and export.

## 3. Ticket: Add Android data collection and permission flow
**Goal:** connect Android system data sources to the domain layer with clear permission handling.

### 3A. Usage access gating
- [x] 3.1 **Red:** write a failing test for the permission-check abstraction used by usage-access-gated screens.
- [x] 3.2 **Green:** implement the smallest usage-access check to make the test pass.
- [x] 3.3 **Red:** write a failing UI/instrumentation test for the missing-usage-access empty state inside the app.
- [x] 3.4 **Green:** implement the smallest in-app empty state and Settings handoff UI to make the test pass.
- [x] 3.5 **Refactor:** simplify permission-gating wiring and copy.
- [ ] 3.6 **Manual:** verify the Settings handoff works on a device.

### 3B. Usage queries
- [x] 3.7 **Red:** write a failing test for mapping normalized Android usage results into the canonical daily domain input model.
- [x] 3.8 **Green:** implement the smallest `UsageStatsManager` adapter for Today summaries.
- [x] 3.9 **Red:** write a failing test for querying app activity inside the configured overnight window.
- [x] 3.10 **Green:** implement the smallest overnight usage query adapter to make the test pass.
- [x] 3.11 **Red:** write a failing test for package normalization, including missing labels and noisy/system packages.
- [x] 3.12 **Green:** implement the smallest normalization/filtering behavior required by the test.
- [x] 3.13 **Refactor:** tidy Android usage-query mapping and boundaries.

### 3C. Battery snapshot collection
- [x] 3.14 **Red:** write a failing unit test for a battery-signal mapper that converts Android battery signals into `BatterySnapshot` values.
- [x] 3.15 **Green:** implement the smallest mapper to make the test pass.
- [x] 3.16 **Red:** write a failing integration test for persisting mapped `BatterySnapshot` records through the collector seam.
- [x] 3.17 **Green:** implement the smallest battery receiver/collector that persists snapshots.
- [x] 3.18 **Red:** write a failing test for v1 reliability hooks: app-open and power-state-change capture.
- [x] 3.19 **Green:** implement only the hooks required to satisfy the tests.
- [x] 3.20 **Refactor:** reduce coupling between mappers, collectors, persistence, and app lifecycle hooks.
- [ ] 3.21 **Manual:** verify overnight snapshot behavior on a real device.

**Acceptance criteria:**
- [x] 3.22 The app shows a clear action when usage access is missing.
- [x] 3.23 The app can persist battery snapshots without network access.
- [x] 3.24 The app can query app activity for the configured overnight window.
- [x] 3.25 The automatable tests cover in-app gating and data mapping seams, while device-only flows are called out for manual verification.

## 4. Ticket: Implement export and sharing
**Goal:** make it easy to move audit results out of the app.

### 4A. Text summaries
- [ ] 4.1 **Red:** write a failing unit test for Today plain-text summary formatting.
- [ ] 4.2 **Green:** implement the smallest Today summary formatter to make the test pass.
- [ ] 4.3 **Red:** write a failing unit test for Last Night plain-text summary formatting.
- [ ] 4.4 **Green:** implement the smallest Last Night summary formatter to make the test pass.
- [ ] 4.5 **Refactor:** unify summary formatting patterns and wording.

### 4B. CSV export
- [ ] 4.6 **Red:** write a failing test for CSV rows/headers for the agreed v1 schema: report date/window, app label, package name, foreground duration, battery start percent, battery end percent, battery delta percent, and warning flags.
- [ ] 4.7 **Green:** implement the smallest CSV exporter to make the test pass.
- [ ] 4.8 **Refactor:** simplify CSV generation and escaping rules.

### 4C. Android sharing
- [ ] 4.9 **Red:** write a failing instrumentation/integration test for launching text/file sharing.
- [ ] 4.10 **Green:** implement the smallest `FileProvider`/sharesheet wiring to make the test pass.
- [ ] 4.11 **Refactor:** tidy export file lifecycle and share intent creation.

**Acceptance criteria:**
- [ ] 4.12 A user can share a compact human-readable summary from the visible report.
- [ ] 4.13 A user can export a CSV file and send it through Android sharing.
- [ ] 4.14 Export works offline using only local data.

## 5. Ticket: Build the minimal mini-app UI
**Goal:** ship the smallest useful UI with the three agreed screens.

### 5A. Today screen
- [x] 5.1 **Red:** write a failing UI test for Today screen empty state.
- [x] 5.2 **Green:** implement the smallest Today screen empty state to make the test pass.
- [x] 5.3 **Red:** write a failing UI test for Today screen populated summary and top apps.
- [x] 5.4 **Green:** implement the smallest Today screen populated state to make the test pass.
- [x] 5.5 **Refactor:** simplify Today screen layout and binding logic.

### 5B. Last Night screen
- [x] 5.6 **Red:** write a failing UI test for Last Night screen summary content.
- [x] 5.7 **Green:** implement the smallest Last Night summary UI to make the test pass.
- [x] 5.8 **Red:** write a failing UI test for incomplete-data warning, no-observed-activity wording, and attribution wording.
- [x] 5.9 **Green:** implement the smallest warning/wording UI to make the test pass.
- [x] 5.10 **Red:** write a failing UI test for nights that include charging transitions.
- [x] 5.11 **Green:** implement the smallest mixed charge/discharge presentation to make the test pass.
- [x] 5.12 **Refactor:** simplify Last Night presentation and labels.

### 5C. History/Export screen
- [ ] 5.13 **Red:** write a failing UI test for History/Export actions.
- [ ] 5.14 **Green:** implement the smallest History/Export screen needed to expose recent locally available overnight reports and export actions.
- [ ] 5.15 **Refactor:** simplify navigation and action wiring.

### 5D. Presentation polish
- [x] 5.16 **Red:** write a failing UI test for attribution-limit copy visibility where required.
- [x] 5.17 **Green:** implement the smallest copy/layout changes to make the test pass.
- [x] 5.18 **Refactor:** tidy layouts/resources for dark theme and OLED-friendly presentation.

**Acceptance criteria:**
- [ ] 5.19 The Today screen shows a useful empty state when no data is available.
- [ ] 5.20 The Last Night screen clearly distinguishes observed activity from inferred drain contribution.
- [ ] 5.21 The UI remains Views-based, minimal, and offline-first.

## 6. Ticket: Integrate the mini-app into paranoid
**Goal:** register the new mini-app and align project docs.

- [ ] 6.1 **Red:** write a failing integration/UI check for the direct in-app navigation entry that launches the mini-app from the hub/main navigation.
- [ ] 6.2 **Green:** add the new mini-app activity, resources, and manifest/navigation wiring to make the test pass.
- [ ] 6.3 **Red:** identify any missing export/file-provider wiring through a failing integration check.
- [ ] 6.4 **Green:** add only the required export/file-provider wiring to make the check pass.
- [ ] 6.5 **Refactor:** tidy manifest/resource/navigation duplication.
- [ ] 6.6 Add or update the mini-app functionality spec under `usageaudit/spec/functionality.md` to match the implemented behavior.

**Acceptance criteria:**
- [ ] 6.7 The mini-app is launchable from the main app via one direct navigation entry.
- [ ] 6.8 The implementation-facing mini-app spec matches the shipped behavior.

## 7. Ticket: Validate on device
**Goal:** verify correctness, honesty of presentation, and basic operational usefulness.

- [ ] 7.1 Run unit tests for aggregators, summaries, exporters, and time-window edge cases.
- [ ] 7.2 Run instrumentation/UI tests for in-app permission gating, empty states, and screen flows.
- [ ] 7.3 Manually verify daytime usage summaries on a device with known app activity.
- [ ] 7.4 Manually verify the Settings handoff for usage access on a device.
- [ ] 7.5 Manually verify overnight battery audit behavior across at least one real night window.
- [ ] 7.6 Manually verify wording for nights with no observed app activity and for nights with charging transitions.
- [ ] 7.7 Verify CSV and plain-text share flows with at least one external target app.
- [ ] 7.8 Perform a wording audit to ensure user-facing battery text never claims exact per-app drain.
- [ ] 7.9 Tidy documentation and mark all completed tasks as done.

**Acceptance criteria:**
- [ ] 7.10 Test runs are green.
- [ ] 7.11 Manual checks confirm the app reports battery loss honestly without claiming exact per-app drain.
- [ ] 7.12 Export/share flows produce usable outputs for real-world sharing.
