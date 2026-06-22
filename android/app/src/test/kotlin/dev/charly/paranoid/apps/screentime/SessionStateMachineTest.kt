package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.SYSTEM_UNATTRIBUTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {

    private val debounce = 30_000L

    @Test
    fun `screen-on starts a session with the event timestamp as start time`() {
        val machine = SessionStateMachine(debounce)

        machine.onScreenOn(1_000L)

        assertTrue(machine.hasActiveSession)
        assertEquals(1_000L, machine.currentSession()?.startMillis)
        assertNull(machine.currentSession()?.endMillis)
    }

    @Test
    fun `screen-off longer than debounce closes session at the screen-off timestamp`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)

        machine.onScreenOff(10_000L)
        val closed = machine.onScreenOffElapsed(10_000L + debounce)

        assertEquals(0L, closed?.startMillis)
        // End time is the screen-off event time, not the debounce-expiry time.
        assertEquals(10_000L, closed?.endMillis)
        assertFalse(machine.hasActiveSession)
        assertEquals(listOf(closed), machine.drainCompleted())
    }

    @Test
    fun `screen-off shorter than debounce does not close the session`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)

        machine.onScreenOff(10_000L)
        // Screen comes back 29s later (within the 30s window).
        machine.onScreenOn(39_000L)

        // Debounce timer fires afterwards but the pending close was cancelled.
        assertNull(machine.onScreenOffElapsed(40_000L))
        assertTrue(machine.hasActiveSession)
        assertEquals(0L, machine.currentSession()?.startMillis)
        assertTrue(machine.drainCompleted().isEmpty())
    }

    @Test
    fun `debounce expiry before the threshold does not close the session`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)
        machine.onScreenOff(10_000L)

        // Only 20s have passed since screen-off.
        assertNull(machine.onScreenOffElapsed(30_000L))
        assertTrue(machine.hasActiveSession)
    }

    @Test
    fun `single app foreground for N samples accumulates N times the sample interval`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)

        // Five 5s samples of the same app.
        for (n in 1..5) {
            machine.onForegroundApp("app.a", n * 5_000L)
        }
        val closed = machine.run {
            onScreenOff(25_000L)
            onScreenOffElapsed(25_000L + debounce)
        }

        val intervals = closed!!.appIntervals
        assertEquals(1, intervals.size)
        assertEquals("app.a", intervals[0].packageName)
        // First interval starts at session start (0) and ends at the close time (25s).
        assertEquals(25_000L, intervals[0].durationMillis)
    }

    @Test
    fun `app switch closes the previous interval and opens a new one, tiling the session`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)

        machine.onForegroundApp("app.a", 5_000L)
        machine.onForegroundApp("app.b", 10_000L) // switch at 10s
        machine.onForegroundApp("app.b", 15_000L)
        machine.onScreenOff(20_000L)
        val closed = machine.onScreenOffElapsed(20_000L + debounce)!!

        val intervals = closed.appIntervals
        assertEquals(2, intervals.size)
        assertEquals("app.a", intervals[0].packageName)
        assertEquals(0L, intervals[0].startMillis)
        assertEquals(10_000L, intervals[0].endMillis)
        assertEquals("app.b", intervals[1].packageName)
        assertEquals(10_000L, intervals[1].startMillis)
        assertEquals(20_000L, intervals[1].endMillis)
        // Intervals tile the whole session: sum equals session duration.
        assertEquals(closed.durationMillis, intervals.sumOf { it.durationMillis })
    }

    @Test
    fun `unresolved foreground app is attributed to the system-unattributed identifier`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)

        machine.onForegroundApp(SYSTEM_UNATTRIBUTED, 5_000L)
        val current = machine.currentSession()!!

        assertEquals(SYSTEM_UNATTRIBUTED, current.appIntervals.single().packageName)
    }

    @Test
    fun `screen events without an active session are ignored`() {
        val machine = SessionStateMachine(debounce)

        machine.onScreenOff(1_000L)
        assertNull(machine.onScreenOffElapsed(1_000L + debounce))
        machine.onForegroundApp("app.a", 1_000L)

        assertFalse(machine.hasActiveSession)
        assertNull(machine.currentSession())
    }

    @Test
    fun `completed sessions are drained only once`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)
        machine.onScreenOff(1_000L)
        machine.onScreenOffElapsed(1_000L + debounce)

        assertEquals(1, machine.drainCompleted().size)
        assertTrue(machine.drainCompleted().isEmpty())
    }
}
