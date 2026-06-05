package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SamplingRate
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRegistrationFromProfileTest {

    private class FakeProbe(private val present: Set<SensorType>) : SensorPresenceProbe {
        override fun hasSensor(type: SensorType): Boolean = type in present
    }

    @Test
    fun `profile enabling only accelerometer and gyroscope at Auto yields two SENSOR_DELAY_NORMAL registrations`() {
        val profile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.ACCELEROMETER, SensorType.GYROSCOPE ->
                        SensorCaptureSetting(true, SamplingRate.Auto, true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val probe = FakeProbe(SensorType.values().toSet())

        val plan = planRegistrations(profile, probe)

        assertEquals(
            listOf(
                PlannedRegistration(SensorType.ACCELEROMETER, SamplingRate.SENSOR_DELAY_NORMAL),
                PlannedRegistration(SensorType.GYROSCOPE, SamplingRate.SENSOR_DELAY_NORMAL),
            ),
            plan,
        )
    }

    @Test
    fun `Hz custom rate plans the inverse-frequency samplingPeriodUs`() {
        val profile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.GYROSCOPE ->
                        SensorCaptureSetting(true, SamplingRate.Hz(50), true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val probe = FakeProbe(SensorType.values().toSet())

        assertEquals(
            listOf(PlannedRegistration(SensorType.GYROSCOPE, 20_000)),
            planRegistrations(profile, probe),
        )
    }

    @Test
    fun `samplingRate Off is never registered even if enabled or visibleOnGraph`() {
        val profile = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.ACCELEROMETER ->
                        SensorCaptureSetting(true, SamplingRate.Off, true)
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
                        SensorCaptureSetting(true, SamplingRate.Auto, true)
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
                        SensorCaptureSetting(false, SamplingRate.Hz(50), true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val probe = FakeProbe(SensorType.values().toSet())

        assertEquals(
            listOf(PlannedRegistration(SensorType.MAGNETIC_FIELD, 20_000)),
            planRegistrations(profile, probe),
        )
    }
}
