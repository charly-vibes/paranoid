package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.model.SensorType

/**
 * Predicate: should `SensorRecordingService` request `SensorManager` to deliver
 * events for a sensor configured by [setting]?
 *
 * A sensor needs a listener whenever it is either recorded to the DB or shown
 * on the live graph, AND its rate is not [SensorRateLevel.OFF][dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel.OFF].
 */
fun shouldRegister(setting: SensorCaptureSetting): Boolean =
    (setting.enabled || setting.visibleOnGraph) &&
        setting.rateLevel.toSensorManagerDelay() != null

/**
 * Predicate: should an incoming sample for [type] be appended to the persisted
 * write buffer for the currently-active session?
 *
 * Returns `false` when no session is active (null [profile]) or when the
 * session-frozen profile marks the sensor as visualize-only (`enabled=false`).
 */
fun shouldWrite(profile: RecordingProfile?, type: SensorType): Boolean =
    profile?.get(type)?.enabled == true

/** Probe abstraction over `SensorManager.getDefaultSensor` for testability. */
interface SensorPresenceProbe {
    fun hasSensor(type: SensorType): Boolean
}

/** One planned listener registration: which sensor, at which `SensorManager.SENSOR_DELAY_*`. */
data class PlannedRegistration(val type: SensorType, val delay: Int)

/**
 * Compute the full set of listener registrations the service should request
 * for [profile] on a device whose sensor presence is reported by [probe].
 *
 * The returned list iterates [SensorType.values] in declaration order, which
 * is the same order the service should call `SensorManager.registerListener`.
 */
fun planRegistrations(
    profile: RecordingProfile,
    probe: SensorPresenceProbe,
): List<PlannedRegistration> =
    SensorType.values().mapNotNull { type ->
        val setting = profile[type]
        if (!shouldRegister(setting)) return@mapNotNull null
        if (!probe.hasSensor(type)) return@mapNotNull null
        val delay = setting.rateLevel.toSensorManagerDelay() ?: return@mapNotNull null
        PlannedRegistration(type, delay)
    }
