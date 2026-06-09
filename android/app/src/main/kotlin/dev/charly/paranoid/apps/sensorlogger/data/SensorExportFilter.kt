package dev.charly.paranoid.apps.sensorlogger.data

/**
 * Downsampling strategy applied independently per sensor type during export.
 */
sealed class ExportSampling {
    /** Keep every recorded event. */
    object All : ExportSampling()

    /** Keep one of every [n] events (n >= 1). */
    data class EveryNth(val n: Int) : ExportSampling()

    /** Keep at most one event per [intervalMs]-millisecond window. */
    data class Interval(val intervalMs: Long) : ExportSampling()

    /** Stable, human/JSON-friendly description. */
    fun describe(): String = when (this) {
        All -> "all"
        is EveryNth -> "1_of_$n"
        is Interval -> "every_${intervalMs}ms"
    }
}

/**
 * Stateful, single-pass event filter for export. [accept] must be called with
 * events in ascending elapsed-time order (as produced by the keyset queries).
 * State is tracked per sensor type so each sensor is sampled on its own cadence.
 *
 * Type filtering is normally done in SQL; passing an empty [includeTypes] here
 * means "accept any type" so the filter can be reused without re-checking.
 */
class SensorExportFilter(
    private val includeTypes: Set<String>,
    private val sampling: ExportSampling,
) {
    private val counters = HashMap<String, Int>()
    private val lastKeptMs = HashMap<String, Long>()

    fun accept(event: SensorEventEntity): Boolean {
        if (includeTypes.isNotEmpty() && event.sensorType !in includeTypes) return false
        return when (sampling) {
            ExportSampling.All -> true
            is ExportSampling.EveryNth -> {
                if (sampling.n <= 1) return true
                val seen = counters.getOrDefault(event.sensorType, 0)
                counters[event.sensorType] = seen + 1
                seen % sampling.n == 0
            }
            is ExportSampling.Interval -> {
                if (sampling.intervalMs <= 0) return true
                val last = lastKeptMs[event.sensorType]
                if (last == null || event.elapsedMs - last >= sampling.intervalMs) {
                    lastKeptMs[event.sensorType] = event.elapsedMs
                    true
                } else {
                    false
                }
            }
        }
    }
}
