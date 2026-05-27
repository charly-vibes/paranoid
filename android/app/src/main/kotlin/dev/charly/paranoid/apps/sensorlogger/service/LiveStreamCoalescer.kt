package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Periodic coalescing publisher for the live sample stream.
 *
 * Producers call [markDirty] every time a backing ring buffer is mutated. The
 * coroutine launched by [start] wakes every [intervalMs] ms and, when the
 * dirty bit is set, asks [snapshot] for the current per-sensor sample lists
 * and writes them to [flow] exactly once per window.
 *
 * The producer hot path only flips an `AtomicBoolean`, so slow or cancelled
 * subscribers cannot apply back-pressure to the sensor-event callback or the
 * persisted write path. See `update-sensor-logger-config-and-graph/design.md`
 * §"Live stream is a coalesced side channel".
 */
class LiveStreamCoalescer(
    private val intervalMs: Long,
    private val snapshot: () -> Map<SensorType, List<SensorSample>>,
    /**
     * Dispatcher the periodic publisher runs on. Defaults to
     * [Dispatchers.Default] so the snapshot work — small map iteration plus
     * each ring buffer's `Synchronized` snapshot copy — does not contend
     * with the service's Main-thread scope. See design.md §"Live stream is
     * a coalesced side channel".
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    init {
        require(intervalMs > 0) { "intervalMs must be > 0, was $intervalMs" }
    }

    private val _flow =
        MutableStateFlow<Map<SensorType, List<SensorSample>>>(emptyMap())
    val flow: StateFlow<Map<SensorType, List<SensorSample>>> = _flow.asStateFlow()

    private val dirty = AtomicBoolean(false)

    @Volatile
    private var job: Job? = null

    /** Mark the underlying ring buffers as dirty. Cheap; safe from any thread. */
    fun markDirty() {
        dirty.set(true)
    }

    /**
     * Launch the periodic publisher in [scope]. Idempotent — subsequent
     * calls while the previous job is active are no-ops.
     */
    @Synchronized
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(dispatcher) {
            while (true) {
                delay(intervalMs)
                if (dirty.compareAndSet(true, false)) {
                    _flow.value = snapshot()
                }
            }
        }
    }

    /** Cancel the publisher and reset the published value to an empty map. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        dirty.set(false)
        _flow.value = emptyMap()
    }
}
