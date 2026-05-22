package dev.charly.paranoid.apps.sensorlogger.config

/**
 * Per-sensor capture rate, expressed in hardware-relative terms rather than Hz.
 *
 * Values map 1:1 onto `android.hardware.SensorManager.SENSOR_DELAY_*` constants
 * (kept as raw ints here so this file stays free of any Android imports — see
 * spec `update-sensor-logger-config-and-graph` design note "pure-Kotlin domain").
 */
enum class SensorRateLevel {
    OFF,
    NORMAL,
    UI,
    GAME,
    FASTEST;

    /**
     * Returns the matching `SensorManager.SENSOR_DELAY_*` value, or `null` for [OFF]
     * (meaning: do not register).
     */
    fun toSensorManagerDelay(): Int? = when (this) {
        OFF -> null
        NORMAL -> DELAY_NORMAL
        UI -> DELAY_UI
        GAME -> DELAY_GAME
        FASTEST -> DELAY_FASTEST
    }

    companion object {
        // Mirrors of `android.hardware.SensorManager.SENSOR_DELAY_*` so this
        // file keeps zero Android imports. Verified by `SensorRateLevelTest`
        // against the platform constants.
        const val DELAY_NORMAL: Int = 3
        const val DELAY_UI: Int = 2
        const val DELAY_GAME: Int = 1
        const val DELAY_FASTEST: Int = 0
    }
}
