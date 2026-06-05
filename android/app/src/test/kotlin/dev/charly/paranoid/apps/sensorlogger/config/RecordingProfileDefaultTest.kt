package dev.charly.paranoid.apps.sensorlogger.config

import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingProfileDefaultTest {

    private val recordedByDefault = setOf(
        SensorType.ACCELEROMETER,
        SensorType.GYROSCOPE,
        SensorType.LINEAR_ACCELERATION,
    )

    @Test
    fun `default profile records accelerometer + gyroscope + linear acceleration at Auto and visible`() {
        for (type in recordedByDefault) {
            val setting = RecordingProfile.Default[type]
            assertTrue("$type must be enabled", setting.enabled)
            assertTrue("$type must be visibleOnGraph", setting.visibleOnGraph)
            assertEquals("$type rate", SamplingRate.Auto, setting.samplingRate)
        }
    }

    @Test
    fun `default profile leaves all other sensors fully off`() {
        for (type in SensorType.values()) {
            if (type in recordedByDefault) continue
            val setting = RecordingProfile.Default[type]
            assertFalse("$type must be disabled", setting.enabled)
            assertFalse("$type must not be visibleOnGraph", setting.visibleOnGraph)
            assertEquals("$type rate", SamplingRate.Off, setting.samplingRate)
        }
    }

    @Test
    fun `default profile covers every SensorType`() {
        val expected = SensorType.values().toSet()
        assertEquals(expected, RecordingProfile.Default.settings.keys)
    }
}
