package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Ticket 3.2 (RED) for `update-sensor-logger-config-and-graph`:
 *
 * - 1000 appends in <50 ms produce ≤1 emission past the initial empty state.
 * - Steady appends at >1 kHz produce ≤20 emissions per second of real time.
 *
 * Both tests use real coroutines and real time on `Dispatchers.Default`
 * because the production coalescer is built on the same primitives (no
 * virtual time substitution to keep the test surface honest).
 */
class LiveStreamCoalescingTest {

    private fun distinctSnapshot(tick: AtomicLong): () -> Map<SensorType, List<SensorSample>> = {
        // Always return a structurally-distinct payload so StateFlow's
        // equality-based dedup does NOT collapse legitimate emissions.
        mapOf(
            SensorType.ACCELEROMETER to listOf(
                SensorSample(
                    elapsedMs = tick.incrementAndGet(),
                    values = floatArrayOf(0f, 0f, 0f),
                )
            )
        )
    }

    @Test
    fun `1000 appends within one window produce at most one emission`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val tick = AtomicLong(0)
        val emissions = AtomicInteger(0)
        val coalescer = LiveStreamCoalescer(
            intervalMs = 50L,
            snapshot = distinctSnapshot(tick),
        )

        val collectJob: Job = coalescer.flow
            .drop(1) // skip the StateFlow's initial empty-map seed
            .onEach { emissions.incrementAndGet() }
            .launchIn(scope)

        coalescer.start(scope)
        repeat(1000) { coalescer.markDirty() }

        // Wait long enough to be sure the ticker has fired at least once but
        // not enough that a second emission could legitimately occur (since
        // we stop marking dirty after the burst).
        delay(150L)

        val count = emissions.get()
        assertTrue("expected ≤1 emission, got $count", count <= 1)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `sustained high-rate marking produces at most twenty emissions per second`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val tick = AtomicLong(0)
        val emissions = AtomicInteger(0)
        val coalescer = LiveStreamCoalescer(
            intervalMs = 50L,
            snapshot = distinctSnapshot(tick),
        )

        val collectJob = coalescer.flow
            .drop(1)
            .onEach { emissions.incrementAndGet() }
            .launchIn(scope)

        coalescer.start(scope)

        // Producer: mark dirty as fast as we can for 1 second.
        val producerJob = scope.launch(Dispatchers.Default) {
            val deadline = System.currentTimeMillis() + 1000L
            while (System.currentTimeMillis() < deadline) {
                coalescer.markDirty()
            }
        }

        producerJob.join()
        // Give the ticker a small grace window to flush the final dirty bit
        // it observed before the producer stopped.
        delay(80L)

        val count = emissions.get()
        // 1 s / 50 ms interval = 20 expected ticks. Allow a small +slack for
        // dispatcher jitter without losing the ≤20 Hz design guarantee.
        assertTrue("expected ≤22 emissions/sec, got $count", count <= 22)

        collectJob.cancel()
        scope.cancel()
    }
}
