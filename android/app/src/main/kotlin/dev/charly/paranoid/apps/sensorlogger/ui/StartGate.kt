package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.service.shouldRegister

/**
 * Hint shown beneath the Start button when [canStartRecording] returns false.
 */
const val START_GATE_HINT: String = "No sensors enabled — open Configure capture"

/**
 * Predicate: with this [profile], will the service register at least one
 * sensor on start? Mirrors the registration policy exactly — a sensor with
 * `rateLevel = OFF` or both flags `false` is not registered, so a profile in
 * which EVERY sensor satisfies that condition produces an empty session.
 *
 * The activity uses this to disable the Start button rather than letting the
 * user begin a no-op session.
 */
fun canStartRecording(profile: RecordingProfile): Boolean =
    SensorType.values().any { shouldRegister(profile[it]) }

/**
 * Predicate: should the "Live graph" entry-point button be enabled?
 *
 * Bound to a live observation of `SensorRecordingService.isRecording` so that
 * the button enables/disables without screen reopen, matching the
 * sensor-logger-ui spec.
 */
fun isLiveGraphButtonEnabled(isRecording: Boolean): Boolean = isRecording

/**
 * Predicate: should the first-launch defaults dialog be shown to the user
 * right now?
 *
 * The dialog migrates users from the v1 record-everything behavior to the
 * new per-sensor default set. Only shown when:
 *  - the bookkeeping key reads `false` (or absent — DataStore default), AND
 *  - the dialog has not already been shown in the current process lifecycle
 *    (the [alreadyShownInThisSession] guard prevents the dialog from
 *    re-appearing after a transient onResume).
 *
 * Returns `false` once any of those conditions fail.
 */
fun shouldShowDefaultsDialog(
    hasSeenPersisted: Boolean,
    alreadyShownInThisSession: Boolean,
): Boolean = !hasSeenPersisted && !alreadyShownInThisSession
