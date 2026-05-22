package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRegistrationFromProfileTest {

    private class FakeProbe(private val present: Set<SensorType>) : SensorPresenceProbe {
        override fun hasSensor(type: SensorType): Boolean = type in present
    }

    @Test
    fun `profile enabling only accelerometer and gyroscope yields exactly two registrations`() {
        val profile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.ACCELEROMETER, SensorType.GYROSCOPE ->
                        SensorCaptureSetting(true, SensorRateLevel.NORMAL, true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val probe = FakeProbe(SensorType.values().toSet())

        val plan = planRegistrations(profile, probe)

        assertEquals(
            listOf(
                PlannedRegistration(SensorType.ACCELEROMETER, SensorRateLevel.DELAY_NORMAL),
                PlannedRegistration(SensorType.GYROSCOPE, SensorRateLevel.DELAY_NORMAL),
            ),
            plan,
        )
    }

    @Test
    fun `sensor with rateLevel OFF is never registered even if enabled or visibleOnGraph`() {
        val profile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.ACCELEROMETER ->
                        SensorCaptureSetting(true, SensorRateLevel.OFF, true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val probe = FakeProbe(SensorType.values().toSet())

        assertEquals(emptyList<PlannedRegistration>(), planRegistrations(profile, probe))
    }

    @Test
    fun `sensor absent on device is skipped even when profile says register`() {
        val profile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.PRESSURE ->
                        SensorCaptureSetting(true, SensorRateLevel.NORMAL, true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val probe = FakeProbe(emptySet())

        assertEquals(emptyList<PlannedRegistration>(), planRegistrations(profile, probe))
    }

    @Test
    fun `visualize-only sensor (enabled=false, visibleOnGraph=true) is registered`() {
        val profile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.MAGNETIC_FIELD ->
                        SensorCaptureSetting(false, SensorRateLevel.GAME, true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val probe = FakeProbe(SensorType.values().toSet())

        assertEquals(
            listOf(PlannedRegistration(SensorType.MAGNETIC_FIELD, SensorRateLevel.DELAY_GAME)),
            planRegistrations(profile, probe),
        )
    }
}
