package dev.charly.paranoid.apps.screentime

/**
 * Schedules checkpoint callbacks within a session following [CheckpointSequence]. The actual
 * timing mechanism is injected via [Timer] so the scheduler is unit-testable without an Android
 * `Handler`; production uses a `Handler.postDelayed`-backed implementation.
 *
 * On each fire it invokes [onCheckpoint] with the checkpoint's elapsed-millis marker, then
 * schedules the following one. [start] supports resume: passing the real `now` for a session that
 * began earlier schedules only the remaining delay to the next checkpoint.
 */
class CheckpointScheduler(
    private val timer: Timer,
    private val onCheckpoint: (checkpointElapsedMillis: Long) -> Unit,
) {
    /** Abstracts a cancellable single-shot delayed callback. */
    interface Timer {
        fun schedule(delayMillis: Long, action: () -> Unit)
        fun cancel()
    }

    private var sessionStartMillis: Long? = null

    /**
     * (Re)starts checkpoint scheduling for a session that began at [startMillis], as of
     * [nowMillis]. For a fresh session pass `nowMillis == startMillis`; for resume pass the
     * current time so elapsed time is honoured.
     */
    fun start(startMillis: Long, nowMillis: Long) {
        timer.cancel()
        sessionStartMillis = startMillis
        scheduleNext(nowMillis)
    }

    /** Cancels any pending checkpoint and clears session state (call on session end). */
    fun stop() {
        sessionStartMillis = null
        timer.cancel()
    }

    private fun scheduleNext(nowMillis: Long) {
        val start = sessionStartMillis ?: return
        val elapsed = (nowMillis - start).coerceAtLeast(0L)
        val nextCheckpoint = CheckpointSequence.nextCheckpointAfterMillis(elapsed)
        val delay = (nextCheckpoint - elapsed).coerceAtLeast(0L)
        timer.schedule(delay) {
            onCheckpoint(nextCheckpoint)
            // Reschedule relative to this checkpoint's moment so the cadence stays aligned.
            scheduleNext(start + nextCheckpoint)
        }
    }
}
