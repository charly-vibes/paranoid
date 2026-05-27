package dev.charly.paranoid.apps.sensorlogger.service

/**
 * One per-sensor sample on the in-memory live stream.
 *
 * `elapsedMs` is `SystemClock.elapsedRealtime() - sessionStartElapsedMs`,
 * matching the same monotonic axis used by [dev.charly.paranoid.apps.sensorlogger.model.SensorEvent].
 * `values` is the raw `SensorEvent.values` payload (1-3 floats depending on the sensor).
 *
 * Custom `equals` / `hashCode` are required because `FloatArray` uses
 * referential equality by default, which would break `StateFlow` dedup checks
 * and snapshot-comparison tests.
 */
data class SensorSample(
    val elapsedMs: Long,
    val values: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorSample) return false
        return elapsedMs == other.elapsedMs && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = elapsedMs.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}
