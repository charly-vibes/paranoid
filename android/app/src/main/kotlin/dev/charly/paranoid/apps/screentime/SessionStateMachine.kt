package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.AppInterval
import dev.charly.paranoid.apps.screentime.model.Session

/** Screen must be off for at least this long before a session is considered ended. */
const val DEFAULT_DEBOUNCE_MILLIS: Long = 30_000L

/**
 * Pure, Android-free state machine for screen-on sessions and per-app foreground
 * interval accumulation.
 *
 * The machine has no timers of its own: the service layer feeds it screen events
 * ([onScreenOn]/[onScreenOff]), foreground-app samples ([onForegroundApp]), and drives
 * debounce expiry via [onScreenOffElapsed]. This keeps the logic fully unit-testable
 * with virtual time.
 *
 * Lifecycle (see screen-time-session spec):
 * - Screen-on with no active session starts a session at the event time.
 * - Screen-off marks a pending close; a screen-on within the debounce window cancels it
 *   and the session continues uninterrupted (checkpoint clock not reset).
 * - Once the screen has been off for at least [debounceMillis], the session closes at the
 *   *screen-off* timestamp (not the debounce-expiry time).
 */
class SessionStateMachine(
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private var open: OpenSession? = null
    private var pendingOffAtMillis: Long? = null
    private val completed = mutableListOf<Session>()

    val hasActiveSession: Boolean get() = open != null

    /** Starts a new session if none is active, or cancels a pending debounce close. */
    fun onScreenOn(nowMillis: Long) {
        if (open != null) {
            // Screen came back within (or after) a pending screen-off: cancel the close.
            pendingOffAtMillis = null
            return
        }
        open = OpenSession(startMillis = nowMillis)
        pendingOffAtMillis = null
    }

    /** Marks the screen as off, starting the debounce window. No-op if no active session. */
    fun onScreenOff(nowMillis: Long) {
        if (open == null) return
        pendingOffAtMillis = nowMillis
    }

    /**
     * Drives debounce expiry. If the screen has been off for at least [debounceMillis] as of
     * [nowMillis], closes the active session at the recorded screen-off timestamp and returns
     * it. Returns null if there is no pending close or the window has not elapsed.
     */
    fun onScreenOffElapsed(nowMillis: Long): Session? {
        val offAt = pendingOffAtMillis ?: return null
        val session = open ?: return null
        if (nowMillis - offAt < debounceMillis) return null
        val closed = session.close(offAt)
        completed += closed
        open = null
        pendingOffAtMillis = null
        return closed
    }

    /** Records the foreground package observed at [nowMillis]. No-op if no active session. */
    fun onForegroundApp(packageName: String, nowMillis: Long) {
        open?.observe(packageName, nowMillis)
    }

    /** Snapshot of the active session (intervals provisionally closed at the last sample). */
    fun currentSession(): Session? = open?.snapshot()

    /** Returns sessions closed since the last call and clears the buffer. */
    fun drainCompleted(): List<Session> {
        val out = completed.toList()
        completed.clear()
        return out
    }

    /**
     * Accumulates per-app intervals for one open session. The first observed app's interval
     * begins at the session start so intervals tile the whole session; an app switch closes
     * the current interval at the switch time and opens the next.
     */
    private class OpenSession(val startMillis: Long) {
        private val closedIntervals = mutableListOf<AppInterval>()
        private var currentPackage: String? = null
        private var currentStartMillis: Long = startMillis
        private var lastObservedMillis: Long = startMillis

        fun observe(packageName: String, nowMillis: Long) {
            val current = currentPackage
            when {
                current == null -> {
                    currentPackage = packageName
                    currentStartMillis = startMillis
                }
                current != packageName -> {
                    closedIntervals += AppInterval(current, currentStartMillis, nowMillis)
                    currentPackage = packageName
                    currentStartMillis = nowMillis
                }
            }
            lastObservedMillis = nowMillis
        }

        fun close(endMillis: Long): Session = Session(
            startMillis = startMillis,
            endMillis = endMillis,
            appIntervals = intervalsClosingAt(endMillis),
        )

        fun snapshot(): Session = Session(
            startMillis = startMillis,
            endMillis = null,
            appIntervals = intervalsClosingAt(lastObservedMillis),
        )

        private fun intervalsClosingAt(endMillis: Long): List<AppInterval> {
            val current = currentPackage ?: return closedIntervals.toList()
            return closedIntervals + AppInterval(current, currentStartMillis, endMillis)
        }
    }
}
