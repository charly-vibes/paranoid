Slice A (PARANOID-p5i.6) — usage-audit history & drill-down

Decisions:
- Reused DailyUsageAggregator.summarize as-is (already accepts arbitrary windows). No target-day param needed.
- Added RecentDaysEnumerator (pure, TimeZone-aware) producing midnight-to-midnight DayWindow lists; today always excluded; chronological oldest-first.
- Added DailyHistoryPresenter and DayDetailPresenter mirroring the existing TodayScreenPresenter unit-test seam (no Robolectric).
- formatDayDuration treats 0 as '0m' instead of '<1m' so zero-usage days render meaningfully.
- Day Detail = new activity (UsageAuditDayDetailActivity); reads day-start from Intent extra and re-loads via the same data provider, finding the matching DailyUsageSummary in recentDays.
- Slice B will add hourly distribution + overnight summary inside Day Detail and scope existing share/CSV to the selected day. The legacy HistoryScreenPresenter (overnight rows) is left in-place but no longer wired to UI; can be deleted in Slice B refactor (3.7).
