package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.model.SensorType

/**
 * Pure rendering decision for one row of `SensorCaptureConfigActivity`'s
 * RecyclerView. Computed without touching any Android View so it can be
 * unit-tested directly.
 */
data class ConfigRowState(
    val label: String,
    val enabledControls: Boolean,
    val alpha: Float,
)

private const val UNAVAILABLE_ALPHA = 0.4f
private const val PRESENT_ALPHA = 1.0f
private const val UNAVAILABLE_SUFFIX = " — Unavailable on this device"

/**
 * Compute the row display state for [type], given the current [setting] and
 * whether the device actually exposes a default sensor of that type
 * ([deviceHas]).
 *
 * Absent sensors render at 40% alpha with their controls disabled and a
 * suffix appended to the label.
 */
fun buildRowState(
    type: SensorType,
    @Suppress("UNUSED_PARAMETER") setting: SensorCaptureSetting,
    deviceHas: Boolean,
): ConfigRowState {
    val baseLabel = sensorTypeLabel(type)
    return if (deviceHas) {
        ConfigRowState(
            label = baseLabel,
            enabledControls = true,
            alpha = PRESENT_ALPHA,
        )
    } else {
        ConfigRowState(
            label = baseLabel + UNAVAILABLE_SUFFIX,
            enabledControls = false,
            alpha = UNAVAILABLE_ALPHA,
        )
    }
}

/** Human-readable label for [type]. Single source of truth used by the row UI. */
fun sensorTypeLabel(type: SensorType): String = when (type) {
    SensorType.ACCELEROMETER -> "Accelerometer"
    SensorType.GYROSCOPE -> "Gyroscope"
    SensorType.LINEAR_ACCELERATION -> "Linear acceleration"
    SensorType.GRAVITY -> "Gravity"
    SensorType.ROTATION_VECTOR -> "Rotation vector"
    SensorType.MAGNETIC_FIELD -> "Magnetic field"
    SensorType.PRESSURE -> "Pressure"
    SensorType.LIGHT -> "Light"
    SensorType.PROXIMITY -> "Proximity"
}
