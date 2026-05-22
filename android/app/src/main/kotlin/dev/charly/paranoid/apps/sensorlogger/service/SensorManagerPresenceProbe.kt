package dev.charly.paranoid.apps.sensorlogger.service

import android.hardware.SensorManager
import dev.charly.paranoid.apps.sensorlogger.model.SensorType

/**
 * Real-device implementation of [SensorPresenceProbe] backed by
 * `SensorManager.getDefaultSensor(int)`. Returns `true` when the device
 * exposes a default sensor of the requested type.
 */
internal class SensorManagerPresenceProbe(
    private val sensorManager: SensorManager,
) : SensorPresenceProbe {
    override fun hasSensor(type: SensorType): Boolean {
        val androidType = SensorRecordingService.ANDROID_SENSOR_TYPE_OF[type] ?: return false
        return sensorManager.getDefaultSensor(androidType) != null
    }
}
