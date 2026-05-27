package dev.charly.paranoid.apps.sensorlogger.service

/**
 * Bounded, thread-safe FIFO ring buffer. When full, [append] overwrites the
 * oldest element. [snapshot] returns a defensive copy in insertion order,
 * decoupling readers from concurrent writers.
 *
 * Used by [LiveStreamCoalescer] to hold the most recent samples per sensor
 * without unbounded growth (capacity 600 in the live-graph use case — see
 * `SensorRecordingService.LIVE_RING_BUFFER_CAPACITY`).
 */
class FixedSizeRingBuffer<T : Any>(val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val items: Array<Any?> = arrayOfNulls(capacity)
    private var head: Int = 0   // next write position
    private var count: Int = 0  // number of valid entries (≤ capacity)

    @Synchronized
    fun append(item: T) {
        items[head] = item
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun snapshot(): List<T> {
        if (count == 0) return emptyList()
        val out = ArrayList<T>(count)
        // When the buffer is not yet full, items[0..count) hold the data in order.
        // When full, the oldest element sits at index `head`.
        val start = if (count < capacity) 0 else head
        for (i in 0 until count) {
            out.add(items[(start + i) % capacity] as T)
        }
        return out
    }

    @Synchronized
    fun size(): Int = count
}
