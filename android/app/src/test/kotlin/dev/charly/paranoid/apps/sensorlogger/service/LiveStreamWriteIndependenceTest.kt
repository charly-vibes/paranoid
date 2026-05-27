package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.model.SensorEvent
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ticket 3.3 (RED) for `update-sensor-logger-config-and-graph`:
 *
 * With a slow or cancelled live-stream collector attached to the coalesced
 * `liveStream`, the per-second write-buffer row count for a fixed burst must
 * equal the row count when no collector is attached at all.
 *
 * The contract under test: the live stream is a side channel; back-pressure
 * from a slow UI collector never flows back to the write path.
 */
class LiveStreamWriteIndependenceTest {

    private fun event(elapsedMs: Long) = SensorEvent(
        sessionId = 1L,
        elapsedMs = elapsedMs,
        sensorType = SensorType.ACCELEROMETER,
        x = 0f, y = 0f, z = 0f, accuracy = 3,
    )

    private fun sample(elapsedMs: Long) =
        SensorSample(elapsedMs, floatArrayOf(0f, 0f, 0f))

    private suspend fun runBurst(
        writeBuffer: SensorEventBuffer,
        ringBuffer: FixedSizeRingBuffer<SensorSample>,
        coalescer: LiveStreamCoalescer,
        durationMs: Long,
    ): Int {
        val deadline = System.currentTimeMillis() + durationMs
        var t = 0L
        var produced = 0
        // Model `onSensorChanged` doing the two appends + markDirty per event.
        while (System.currentTimeMillis() < deadline) {
            ringBuffer.append(sample(t))
            writeBuffer.append(event(t))
            coalescer.markDirty()
            produced++
            t++
        }
        return produced
    }

    @Test
    fun `slow collector does not reduce write-buffer event count vs no collector`() = runBlocking {
        val withoutCollector = run {
            val scope = CoroutineScope(Dispatchers.Default)
            val writeBuffer = SensorEventBuffer()
            val ringBuffer = FixedSizeRingBuffer<SensorSample>(600)
            val coalescer = LiveStreamCoalescer(
                intervalMs = 50L,
                snapshot = { mapOf(SensorType.ACCELEROMETER to ringBuffer.snapshot()) },
            )
            coalescer.start(scope)
            val produced = runBurst(writeBuffer, ringBuffer, coalescer, durationMs = 500L)
            val flushed = writeBuffer.flush().size
            scope.cancel()
            produced to flushed
        }

        val withSlowCollector = run {
            val scope = CoroutineScope(Dispatchers.Default)
            val writeBuffer = SensorEventBuffer()
            val ringBuffer = FixedSizeRingBuffer<SensorSample>(600)
            val coalescer = LiveStreamCoalescer(
                intervalMs = 50L,
                snapshot = { mapOf(SensorType.ACCELEROMETER to ringBuffer.snapshot()) },
            )
            coalescer.start(scope)
            // A pathologically slow consumer: 250 ms per emission.
            val collectJob = scope.launch {
                coalescer.flow.collect { delay(250L) }
            }
            val produced = runBurst(writeBuffer, ringBuffer, coalescer, durationMs = 500L)
            val flushed = writeBuffer.flush().size
            collectJob.cancel()
            scope.cancel()
            produced to flushed
        }

        // The write-buffer flushed count must equal whatever the producer
        // appended (no drops, no awaits on the consumer).
        assertEquals(withoutCollector.first, withoutCollector.second)
        assertEquals(withSlowCollector.first, withSlowCollector.second)
    }

    @Test
    fun `cancelled collector does not affect subsequent write-buffer appends`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val writeBuffer = SensorEventBuffer()
        val ringBuffer = FixedSizeRingBuffer<SensorSample>(600)
        val coalescer = LiveStreamCoalescer(
            intervalMs = 50L,
            snapshot = { mapOf(SensorType.ACCELEROMETER to ringBuffer.snapshot()) },
        )
        coalescer.start(scope)

        val collectJob = scope.launch { coalescer.flow.collect { delay(250L) } }
        delay(100L)
        collectJob.cancel()

        val produced = runBurst(writeBuffer, ringBuffer, coalescer, durationMs = 200L)
        val flushed = writeBuffer.flush().size
        assertEquals(produced, flushed)

        scope.cancel()
    }
}
