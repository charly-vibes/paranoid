package dev.charly.paranoid.apps.screentime

/*
 * INSTRUMENTATION TESTS — NOT RUN IN CI (left as documentation).
 *
 * These cover the Android-framework wiring of ticket 3 (ScreenTimeService, the dynamic
 * screen on/off receiver, and ScreenTimeBootReceiver). They require an emulator/device:
 * `./gradlew connectedDebugAndroidTest`. The project's CI (.github/workflows/ci.yml) only
 * runs `assembleDebug` + `test` (JVM unit tests) on ubuntu-latest with no emulator job, so
 * there is no way to execute them automatically yet. Enabling them would require adding an
 * emulator runner (e.g. reactivecircus/android-emulator-runner) to CI.
 *
 * The pure session/debounce/interval logic these would exercise is already covered by JVM
 * unit tests: SessionStateMachineTest and StaleSessionResolverTest. The cases below verify
 * only the parts that need a real Android runtime.
 *
 * Intended cases:
 *
 * 1. service_starts_in_foreground_with_persistent_notification (task 3.1)
 *    - GIVEN the app context
 *    - WHEN ScreenTimeService is started with ACTION_START
 *    - THEN the service is running AND a notification with channel "screentime" (id 1004) is
 *      posted AND ScreenTimeMonitoringPrefs.isEnabled(context) is true.
 *    Sketch:
 *        val ctx = ApplicationProvider.getApplicationContext<Context>()
 *        ctx.startForegroundService(ScreenTimeService.startIntent(ctx))
 *        // poll NotificationManager.activeNotifications for id 1004
 *        assertTrue(ScreenTimeMonitoringPrefs.isEnabled(ctx))
 *
 * 2. screen_on_starts_session_screen_off_beyond_debounce_closes_it (task 3.4)
 *    - GIVEN monitoring is active
 *    - WHEN ACTION_SCREEN_ON is broadcast, then ACTION_SCREEN_OFF, and >30 s elapse
 *    - THEN exactly one closed session (non-null endMillis) is written to ScreenTimeDao
 *      with endMillis ≈ the SCREEN_OFF timestamp.
 *    Note: drive time with an injectable clock or use UiAutomator screen on/off; the debounce
 *    Handler delay (30 s) makes this a slow test — prefer a shortened debounce build hook.
 *
 * 3. screen_off_shorter_than_debounce_keeps_session_open (task 3.6)
 *    - GIVEN a session is active
 *    - WHEN ACTION_SCREEN_OFF then ACTION_SCREEN_ON occur within 30 s
 *    - THEN no closed session is written AND the open session row (null endMillis) persists.
 *
 * 4. boot_completed_restarts_service_only_when_enabled (tasks 3.8)
 *    - GIVEN ScreenTimeMonitoringPrefs.setEnabled(context, true)
 *    - WHEN ScreenTimeBootReceiver receives ACTION_BOOT_COMPLETED
 *    - THEN ScreenTimeService is (re)started; AND when disabled, it is NOT started.
 *
 * 5. stale_open_session_is_closed_on_start (task 3.9)
 *    - GIVEN a session row with null endMillis left in ScreenTimeDao
 *    - WHEN the service starts
 *    - THEN the row is closed (endMillis set) per StaleSessionResolver, clamped to the start
 *      day if recovery crosses midnight.
 */
