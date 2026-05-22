package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per amendment EXEC-003 on PARANOID-4vr: the write-path filter is the policy
 * primitive `shouldWrite`. With magnetometer in the session-frozen profile as
 * `enabled=false, visibleOnGraph=true, rateLevel=NORMAL`, magnetometer samples
 * must NOT reach the write buffer.
 */
class ServiceVisualizeOnlyFilterTest {

    private val visualizeOnlyProfile = RecordingProfile(
        SensorType.values().associateWith { type ->
            when (type) {
                SensorType.MAGNETIC_FIELD ->
                    SensorCaptureSetting(
                        enabled = false,
                        rateLevel = SensorRateLevel.NORMAL,
                        visibleOnGraph = true,
                    )
                SensorType.ACCELEROMETER ->
                    SensorCaptureSetting(true, SensorRateLevel.NORMAL, true)
                else -> RecordingProfile.OffSetting
            }
        }
    )

    @Test
    fun `visualize-only magnetometer is not written`() {
        assertFalse(shouldWrite(visualizeOnlyProfile, SensorType.MAGNETIC_FIELD))
    }

    @Test
    fun `recorded accelerometer is written`() {
        assertTrue(shouldWrite(visualizeOnlyProfile, SensorType.ACCELEROMETER))
    }

    @Test
    fun `simulating 10 magnetometer events with write-gating buffers zero of them`() {
        // Models the service's onSensorChanged inner gate: only append to the
        // write buffer when shouldWrite returns true. The buffer is a stand-in
        // for SensorEventBuffer.
        val writes = mutableListOf<SensorType>()
        repeat(10) {
            val type = SensorType.MAGNETIC_FIELD
            if (shouldWrite(visualizeOnlyProfile, type)) writes += type
        }
        assertEquals(emptyList<SensorType>(), writes)
    }

    @Test
    fun `null session profile means no writes (no active session)`() {
        assertFalse(shouldWrite(null, SensorType.ACCELEROMETER))
    }
}
