package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.SYSTEM_UNATTRIBUTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppSamplerTest {

    private val debounce = 30_000L

    /** A scripted source that returns a queued result per sample. */
    private class ScriptedSource(private val results: MutableList<ForegroundSample>) : ForegroundAppSource {
        override fun sample(nowMillis: Long): ForegroundSample =
            if (results.isEmpty()) ForegroundSample.Unresolved else results.removeAt(0)
    }

    private fun resolved(pkg: String, since: Long) = ForegroundSample.Resolved(ForegroundApp(pkg, since))

    @Test
    fun `single app over N samples attributes N times the sample interval`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)
        val source = ScriptedSource(MutableList(5) { resolved("app.a", since = 0L) })
        val sampler = ForegroundAppSampler(source, machine::onForegroundApp)

        for (n in 1..5) sampler.sample(n * 5_000L)
        machine.onScreenOff(25_000L)
        val closed = machine.onScreenOffElapsed(25_000L + debounce)!!

        assertEquals(1, closed.appIntervals.size)
        assertEquals("app.a", closed.appIntervals[0].packageName)
        assertEquals(25_000L, closed.appIntervals[0].durationMillis)
    }

    @Test
    fun `app switch closes the previous interval at the foreground event time`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)
        val source = ScriptedSource(
            mutableListOf(
                resolved("app.a", since = 0L),       // sample @ 5s
                resolved("app.a", since = 0L),       // sample @ 10s
                resolved("app.b", since = 12_000L),  // sample @ 15s, B came fg at 12s
                resolved("app.b", since = 12_000L),  // sample @ 20s
            ),
        )
        val sampler = ForegroundAppSampler(source, machine::onForegroundApp)

        sampler.sample(5_000L)
        sampler.sample(10_000L)
        sampler.sample(15_000L)
        sampler.sample(20_000L)
        machine.onScreenOff(25_000L)
        val closed = machine.onScreenOffElapsed(25_000L + debounce)!!

        assertEquals(2, closed.appIntervals.size)
        assertEquals("app.a", closed.appIntervals[0].packageName)
        assertEquals(0L, closed.appIntervals[0].startMillis)
        assertEquals(12_000L, closed.appIntervals[0].endMillis) // switch at the event time
        assertEquals("app.b", closed.appIntervals[1].packageName)
        assertEquals(12_000L, closed.appIntervals[1].startMillis)
        assertEquals(25_000L, closed.appIntervals[1].endMillis)
        assertEquals(closed.durationMillis, closed.appIntervals.sumOf { it.durationMillis })
    }

    @Test
    fun `unresolved foreground is attributed to system-unattributed`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)
        val source = ScriptedSource(mutableListOf(ForegroundSample.Unresolved))
        val sampler = ForegroundAppSampler(source, machine::onForegroundApp)

        sampler.sample(5_000L)

        assertEquals(SYSTEM_UNATTRIBUTED, machine.currentSession()!!.appIntervals.single().packageName)
    }

    @Test
    fun `access unavailable skips the sample and does not attribute time`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)
        var unavailableCount = 0
        val source = ScriptedSource(mutableListOf(ForegroundSample.AccessUnavailable))
        val sampler = ForegroundAppSampler(
            source = source,
            onForegroundApp = machine::onForegroundApp,
            onAccessUnavailable = { unavailableCount++ },
        )

        sampler.sample(5_000L)

        assertEquals(1, unavailableCount)
        assertTrue(machine.currentSession()!!.appIntervals.isEmpty())
    }

    @Test
    fun `access warning fires once and clears when access is restored`() {
        val machine = SessionStateMachine(debounce)
        machine.onScreenOn(0L)
        var unavailable = 0
        var restored = 0
        val source = ScriptedSource(
            mutableListOf(
                ForegroundSample.AccessUnavailable,
                ForegroundSample.AccessUnavailable, // still revoked → no duplicate warning
                resolved("app.a", since = 0L),      // restored
            ),
        )
        val sampler = ForegroundAppSampler(
            source = source,
            onForegroundApp = machine::onForegroundApp,
            onAccessUnavailable = { unavailable++ },
            onAccessRestored = { restored++ },
        )

        sampler.sample(5_000L)
        sampler.sample(10_000L)
        sampler.sample(15_000L)

        assertEquals(1, unavailable)
        assertEquals(1, restored)
    }
}
