package dev.charly.paranoid.apps.sensorlogger.config

import dev.charly.paranoid.apps.sensorlogger.model.SensorType

/**
 * Per-sensor capture settings: whether to record the sensor to the database,
 * the sampling rate to request from `SensorManager`, and whether the sensor's
 * samples should appear on the live graph.
 *
 * `enabled = false && visibleOnGraph = true` is the "visualize only" mode: the
 * service registers the listener and feeds samples to the live ring buffer but
 * does not write them to the session's `sensor_events` table.
 */
data class SensorCaptureSetting(
    val enabled: Boolean,
    val samplingRate: SamplingRate,
    val visibleOnGraph: Boolean,
)

/**
 * Full per-`SensorType` recording configuration. Missing entries fall back to
 * the [RecordingProfile.OffSetting] (fully-disabled) baseline on lookup so
 * callers never see `null` for a known [SensorType].
 */
data class RecordingProfile(
    val settings: Map<SensorType, SensorCaptureSetting>,
) {
    operator fun get(type: SensorType): SensorCaptureSetting =
        settings[type] ?: OffSetting

    companion object {
        /** Disabled-everywhere baseline used as a per-sensor fallback. */
        val OffSetting: SensorCaptureSetting = SensorCaptureSetting(
            enabled = false,
            samplingRate = SamplingRate.Off,
            visibleOnGraph = false,
        )

        /**
         * Out-of-the-box capture profile: accelerometer, gyroscope, and linear
         * acceleration enabled at [SamplingRate.Auto] with
         * `visibleOnGraph = true`. Every other sensor is fully off (matches
         * [OffSetting]).
         */
        val Default: RecordingProfile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.ACCELEROMETER,
                    SensorType.GYROSCOPE,
                    SensorType.LINEAR_ACCELERATION ->
                        SensorCaptureSetting(
                            enabled = true,
                            samplingRate = SamplingRate.Auto,
                            visibleOnGraph = true,
                        )
                    else -> OffSetting
                }
            }
        )
    }
}
