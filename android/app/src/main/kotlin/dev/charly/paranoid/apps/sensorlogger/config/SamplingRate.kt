package dev.charly.paranoid.apps.sensorlogger.config

/**
 * Per-sensor sampling rate (amendment EXEC-004, see
 * `openspec/changes/update-sensor-logger-config-and-graph/design.md`).
 *
 * Replaces the older `SensorRateLevel` enum with a sum type that admits a
 * user-chosen integer Hz target in addition to the off / auto states:
 *
 * - [Off]   — do not register the sensor.
 * - [Auto]  — register at `SensorManager.SENSOR_DELAY_NORMAL`; let the OS
 *             pick a rate.
 * - [Hz]    — register at `1_000_000 / value` µs (best-effort; the actual
 *             delivered rate is surfaced on the live graph).
 *
 * This file keeps zero Android imports — the small `SENSOR_DELAY_NORMAL`
 * mirror (value `3`) is verified by `SamplingRateTest` against the platform
 * constant.
 */
sealed class SamplingRate {

    /** Returns the `samplingPeriodUs` for `SensorManager.registerListener`, or `null` if not registered. */
    abstract fun toSamplingPeriodUs(): Int?

    /** Persistent encoding (see [decode] for the inverse + legacy fallback). */
    abstract fun encode(): String

    object Off : SamplingRate() {
        override fun toSamplingPeriodUs(): Int? = null
        override fun encode(): String = "OFF"
        override fun toString(): String = "Off"
    }

    object Auto : SamplingRate() {
        // Android's `SensorManager.registerListener` treats values 0..3 as the
        // SENSOR_DELAY_* constants (3 == NORMAL ≈ 200 ms). Larger values are
        // interpreted as a `samplingPeriodUs` in microseconds.
        override fun toSamplingPeriodUs(): Int = SENSOR_DELAY_NORMAL
        override fun encode(): String = "AUTO"
        override fun toString(): String = "Auto"
    }

    data class Hz(val value: Int) : SamplingRate() {
        init { require(value > 0) { "Hz value must be positive, got $value" } }
        // Floor at 1 µs: a value above 1_000_000 Hz would otherwise yield a
        // period of 0, which Android reinterprets as SENSOR_DELAY_FASTEST.
        override fun toSamplingPeriodUs(): Int = (1_000_000 / value).coerceAtLeast(1)
        override fun encode(): String = "HZ:$value"
        override fun toString(): String = "Hz($value)"
    }

    companion object {
        /** Mirrors `android.hardware.SensorManager.SENSOR_DELAY_NORMAL` (= 3). */
        const val SENSOR_DELAY_NORMAL: Int = 3

        /**
         * Decode a persisted `<NAME>_rate` string.
         *
         * Accepts the current encoding (`"OFF"`, `"AUTO"`, `"HZ:<n>"` with
         * `n > 0`) and the legacy encoding written by `v0.10.0-rc.1`
         * (`"NORMAL"`, `"UI"`, `"GAME"`, `"FASTEST"`). Returns `null` for any
         * unparseable value so the caller substitutes the per-sensor default.
         */
        fun decode(s: String): SamplingRate? = when {
            s == "OFF" -> Off
            s == "AUTO" -> Auto
            s in LEGACY_RATE_BY_NAME -> LEGACY_RATE_BY_NAME.getValue(s)
            s.startsWith("HZ:") -> {
                val n = s.removePrefix("HZ:").toIntOrNull()
                if (n == null || n <= 0) null else Hz(n)
            }
            else -> null
        }

        // Hz targets chosen to match each legacy SENSOR_DELAY_* level's
        // nominal Android Hz. See design.md "SamplingRate sum type" decision.
        private val LEGACY_RATE_BY_NAME: Map<String, SamplingRate> = mapOf(
            "NORMAL"  to Auto,
            "UI"      to Hz(16),
            "GAME"    to Hz(50),
            "FASTEST" to Hz(200),
        )
    }
}
