package dev.charly.paranoid.apps.screentime.model

/**
 * Identifier used when the foreground app cannot be resolved to a user-installed or
 * system-user package (launcher, lock screen, system UI). Reports exclude these
 * intervals from per-app breakdowns. See
 * openspec/changes/add-screen-time-monitor-app/specs/screen-time-session/spec.md.
 */
const val SYSTEM_UNATTRIBUTED: String = "system.unattributed"

/**
 * A contiguous period during which a single package was in the foreground within a
 * session. Timestamps are UTC epoch millis; [endMillis] is exclusive. [durationMillis]
 * is clamped to be non-negative.
 */
data class AppInterval(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
}

/**
 * A continuous screen-on session. Timestamps are UTC epoch millis. [endMillis] is null
 * while the session is active. When closed, [appIntervals] tile [startMillis, endMillis]
 * contiguously so their durations sum to the session duration.
 */
data class Session(
    val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long? = null,
    val appIntervals: List<AppInterval> = emptyList(),
) {
    val isOpen: Boolean get() = endMillis == null

    val durationMillis: Long? get() = endMillis?.let { (it - startMillis).coerceAtLeast(0L) }
}
