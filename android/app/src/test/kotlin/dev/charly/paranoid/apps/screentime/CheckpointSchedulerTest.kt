package dev.charly.paranoid.apps.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointSchedulerTest {

    private val minute = 60_000L

    /** Records scheduled delays and lets the test fire the pending callback on demand. */
    private class FakeTimer : CheckpointScheduler.Timer {
        val scheduledDelays = mutableListOf<Long>()
        var cancelCount = 0
        private var pending: (() -> Unit)? = null

        override fun schedule(delayMillis: Long, action: () -> Unit) {
            scheduledDelays += delayMillis
            pending = action
        }

        override fun cancel() {
            cancelCount++
            pending = null
        }

        fun fire() {
            val action = pending ?: error("no pending callback")
            pending = null
            action()
        }

        val hasPending: Boolean get() = pending != null
    }

    @Test
    fun `first checkpoint after start is at 7 minutes`() {
        val timer = FakeTimer()
        val scheduler = CheckpointScheduler(timer) {}

        scheduler.start(startMillis = 0L, nowMillis = 0L)

        assertEquals(7 * minute, timer.scheduledDelays.last())
    }

    @Test
    fun `checkpoint cadence is 7 then 6 then 16 then 29 minute delays`() {
        val timer = FakeTimer()
        val fired = mutableListOf<Long>()
        val scheduler = CheckpointScheduler(timer) { fired += it }

        scheduler.start(startMillis = 0L, nowMillis = 0L)
        timer.fire() // reaches 7 min
        timer.fire() // reaches 13 min
        timer.fire() // reaches 29 min
        timer.fire() // reaches 58 min

        // Delays scheduled before each fire: 7, then 6 (→13), then 16 (→29), then 29 (→58), then 29 (→87).
        assertEquals(listOf(7L, 6L, 16L, 29L, 29L).map { it * minute }, timer.scheduledDelays)
        // Checkpoints fired at the expected elapsed markers.
        assertEquals(listOf(7L, 13L, 29L, 58L).map { it * minute }, fired)
    }

    @Test
    fun `recurring cadence stays at 29 minutes after the first hour`() {
        val timer = FakeTimer()
        val fired = mutableListOf<Long>()
        val scheduler = CheckpointScheduler(timer) { fired += it }

        scheduler.start(0L, 0L)
        repeat(6) { timer.fire() } // 7, 13, 29, 58, 87, 116

        assertEquals(listOf(7L, 13L, 29L, 58L, 87L, 116L).map { it * minute }, fired)
    }

    @Test
    fun `stop cancels the pending checkpoint`() {
        val timer = FakeTimer()
        val scheduler = CheckpointScheduler(timer) {}

        scheduler.start(0L, 0L)
        assertTrue(timer.hasPending)
        scheduler.stop()

        assertTrue(!timer.hasPending)
        assertTrue(timer.cancelCount >= 1)
    }

    @Test
    fun `resume mid-session schedules only the remaining delay to the next checkpoint`() {
        val timer = FakeTimer()
        val scheduler = CheckpointScheduler(timer) {}

        // Session started 20 minutes ago; next checkpoint is 29 min, so 9 min remain.
        scheduler.start(startMillis = 0L, nowMillis = 20 * minute)

        assertEquals(9 * minute, timer.scheduledDelays.last())
    }

    @Test
    fun `resume just after the 13 minute checkpoint targets the 29 minute checkpoint`() {
        val timer = FakeTimer()
        val scheduler = CheckpointScheduler(timer) {}

        scheduler.start(startMillis = 0L, nowMillis = 15 * minute)

        // Next checkpoint is 29 min → 14 min remaining.
        assertEquals(14 * minute, timer.scheduledDelays.last())
    }
}
