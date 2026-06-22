package dev.charly.paranoid.apps.screentime

/*
 * INTEGRATION / DEVICE VALIDATION — NOT RUN IN CI (left as documentation).
 *
 * These cover ticket 9: end-to-end behaviour that can only be confirmed on a real device or
 * emulator (`./gradlew connectedDebugAndroidTest`). The project's CI (.github/workflows/ci.yml)
 * runs only `assembleDebug` + `test` (JVM unit tests) on ubuntu-latest with no emulator job, so
 * these cannot be executed automatically. Enabling them would require adding an emulator runner
 * (e.g. reactivecircus/android-emulator-runner) plus a granted SYSTEM_ALERT_WINDOW / usage-access
 * fixture, which is out of scope for this change.
 *
 * The pure logic underneath each case is already covered by JVM unit tests:
 *   - sessions / debounce: SessionStateMachineTest, StaleSessionResolverTest
 *   - checkpoints: CheckpointSequenceTest, CheckpointSchedulerTest
 *   - overlay geometry: OverlayProgressTest
 *   - report aggregation + scheduling: ReportAggregatorTest, MorningReportScheduleTest
 *   - retention/pruning cutoff: RetentionPolicyTest
 *   - today list: TodaySessionsPresenterTest
 *
 * Intended cases:
 *
 * 1. start_monitoring_lock_unlock_writes_session (task 9.1)
 *    - GIVEN ScreenTimeService started with ACTION_START and all permissions granted
 *    - WHEN the screen is turned off then on (UiAutomator / `adb shell input keyevent`), waiting
 *      past the 30 s debounce
 *    - THEN exactly one closed session is persisted to ScreenTimeDao with endMillis ≈ screen-off.
 *    Tip: build a debug hook to shorten DEFAULT_DEBOUNCE_MILLIS so the test is not 30 s+ slow.
 *
 * 2. overlay_permission_revoked_mid_session_service_survives (task 9.2)
 *    - GIVEN monitoring active with an open session and overlay shown
 *    - WHEN SYSTEM_ALERT_WINDOW is revoked (`adb shell appops set <pkg> SYSTEM_ALERT_WINDOW deny`)
 *    - THEN ScreenTimeService keeps running (no crash), the session stays open, and OverlayManager
 *      degrades gracefully (no overlay) rather than throwing.
 *
 * 3. overlay_visible_and_non_interactive_in_third_party_app (task 9.3)
 *    - GIVEN monitoring active and a checkpoint reached
 *    - WHEN a third-party app is foregrounded
 *    - THEN the overlay bar is visible AND touches pass through to the app beneath
 *      (FLAG_NOT_TOUCHABLE / FLAG_NOT_FOCUSABLE verified via UiAutomator tap-through).
 *
 * 4. seven_minute_checkpoint_fires_notification (task 9.4)
 *    - GIVEN a continuous session
 *    - WHEN cumulative screen-on time crosses the first checkpoint (7 min)
 *    - THEN a notification on channel "screentime_checkpoints" (id 1005) is posted.
 *    Tip: inject a faster CheckpointSequence for the test build to avoid a 7-min wait.
 *
 * 5. morning_report_notification_fires_at_eight (task 9.5)
 *    - GIVEN stored sessions for yesterday/this month
 *    - WHEN the device clock is moved to 08:00 (`adb shell am set-time`) and MorningReportWorker
 *      runs (enqueue as expedited for the test)
 *    - THEN a report notification on channel "screentime_reports" (id 1006) is posted with
 *      yesterday/7-day/month-to-date text, the worker re-enqueues the next run, AND sessions older
 *      than RetentionPolicy.RETENTION_DAYS (31) are pruned while ≤31-day data is retained (task 9.6).
 *      The pruning cutoff math itself is verified by RetentionPolicyTest.
 */
