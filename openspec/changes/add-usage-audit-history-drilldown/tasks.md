## Suggested implementation order
1 → 2 → 3 → 4 → 5

## 1. Day-scoped domain queries
- [x] 1.1 Audit existing day-scoped methods in `UsageAuditDataProvider`/`UsageQueries` and reuse where possible; only extend if a target-day parameter is missing.
- [x] 1.2 **Red:** write a failing unit test for daily aggregation over an arbitrary past day (not only "today"), including a DST-affected day. <!-- Slice D follow-up: added two tests in DailyUsageAggregatorTest — one using RecentDaysEnumerator.pastDayWindows + distractor next-day slice, one for a 23-hour DST spring-forward day. -->
- [x] 1.3 **Green:** implement/extend the aggregator so totals reconcile on 23/24/25-hour days. <!-- Slice A: aggregator already accepts arbitrary windows; DST hourly buckets handled in Slice B. -->
- [x] 1.4 **Red:** write a failing unit test for an hourly foreground-time distribution that returns the actual hour count for the local day (DST-aware).
- [x] 1.5 **Green:** implement the DST-aware hourly bucketing logic.
- [x] 1.6 **Red:** write a failing unit test for a `UsageStatsManager.queryEvents`-based adapter producing per-app foreground intervals (start/end) for a given day.
- [x] 1.7 **Green:** implement the events-based per-app interval extractor.
- [x] 1.8 **Red:** write a failing unit test for resolving an uninstalled package (no resolvable label) and surfacing an explicit "uninstalled" marker.
- [x] 1.9 **Green:** implement the package-resolution fallback.
- [x] 1.10 **Refactor:** tidy shared day-window helpers and naming. <!-- Slice D follow-up: extracted RecentDaysEnumerator.startOfLocalDay() and reused it in pastDayWindows + AndroidUsageAuditDataProvider.loadToday, replacing the duplicated midnight-Calendar incantation. -->

## 2. History list
- [x] 2.1 **Red:** write a failing test enumerating recent past days with totals from the platform-retained usage window; the current day MUST be excluded.
- [x] 2.2 **Green:** implement the recent-days enumerator.
- [x] 2.3 **Red:** write a failing UI test for the History screen list, the today-excluded behavior, and the empty state. <!-- Slice A: presenter-level test, matching existing TodayScreenPresenterTest pattern. -->
- [x] 2.4 **Green:** implement the History screen list and empty state.

## 3. Day Detail screen
- [x] 3.1 **Red:** write a failing UI test for Day Detail showing total, full ranked apps, hourly bars, and the overnight summary whose window *starts within* the selected day (when available). <!-- Slice B: presenter-level tests in DayDetailPresenterTest cover hourly bars (normal + DST) + overnight in/out-of-day. -->
- [x] 3.2 **Green:** implement the Day Detail screen.
- [x] 3.3 **Red:** write a failing UI test for a zero-usage day (empty hourly bars, no app rows, zero total). <!-- Slice A: zero total + no app rows. Empty hourly bars covered in Slice B. -->
- [x] 3.4 **Green:** implement the zero-usage state.
- [x] 3.5 **Red:** write a failing test that Share/CSV export from Day Detail is scoped to the selected day and uses the existing v1 schema (no hourly or interval columns).
- [x] 3.6 **Green:** wire the existing exporters to the selected day without extending the schema.
- [x] 3.7 **Refactor:** unify Today and Day Detail rendering paths. <!-- Slice D follow-up: extracted DailyUsageSummary.toAppRows() so TodayScreenPresenter and DayDetailPresenter share the same label/duration/packageName mapping. -->

## 4. App Detail drill-down
- [x] 4.1 **Red:** write a failing UI test for App Detail showing total and observed intervals on a chosen day. <!-- Slice C: presenter-level test in AppDetailPresenterTest. -->
- [x] 4.2 **Green:** implement the App Detail screen.
- [x] 4.3 **Red:** write a failing UI test for the no-activity state.
- [x] 4.4 **Green:** implement the no-activity state.
- [x] 4.5 **Red:** write a failing UI test for the uninstalled-package state (raw package name + "uninstalled" indicator, intervals still shown).
- [x] 4.6 **Green:** implement the uninstalled-package presentation.

## 5. Navigation and integration
- [x] 5.1 Add navigation entries: Today/History → Day Detail → App Detail; ensure Back works on all paths. <!-- Slice C: Day Detail row click → App Detail; Back via finish() on btn_back. -->
<!-- 5.2 (functionality.md update) and 5.3 (verification + manual device check) deferred to Slice D (PARANOID-p5i.9). -->
- [x] 5.2 Update `usageaudit/spec/functionality.md` Screens table with new rows: `Day Detail` ("Per-day breakdown: ranked apps, hourly distribution, overnight summary") and `App Detail` ("Per-app foreground intervals for a selected day"), and amend the existing `History` row to reflect daily browsing. <!-- Slice D (PARANOID-p5i.9): functionality.md updated with Day Detail + App Detail rows and History description amended. -->
- [x] 5.3 Run `just test` and confirm green. <!-- Slice D (PARANOID-p5i.9): just test passed (BUILD SUCCESSFUL, all tasks UP-TO-DATE). -->

## Slice D — manual device verification (HITL)
The following acceptance items in PARANOID-p5i.9 require a physical device and are deferred to the human:
- D.3 History → past day → totals reconcile with notification-shade usage stats (within tolerance).
- D.4 Hourly distribution looks plausible and reconciles with the rendered total.
- D.5 App Detail intervals on a known-active app.
- D.6 Uninstalled-package state by uninstalling a recently-used app and drilling into it.
- D.7 Share / CSV from Day Detail produce per-day output (no hourly or interval columns).
