package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SamplingRate
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stand-in for the Espresso ConfigAbsentSensorTest from task 4.3 — covers the
 * pure rendering decision the row binder makes for present vs absent sensors.
 * Espresso is intentionally avoided: see task 4.3 amendment and ticket 2's
 * decomposition rationale.
 */
class ConfigRowStateTest {

    private val on = SensorCaptureSetting(true, SamplingRate.Auto, true)

    @Test
    fun `present sensor row is fully enabled, opaque, with no suffix`() {
        val state = buildRowState(SensorType.ACCELEROMETER, on, deviceHas = true)
        assertTrue(state.enabledControls)
        assertEquals(1.0f, state.alpha, 0.0f)
        assertEquals("Accelerometer", state.label)
    }

    @Test
    fun `absent sensor row is greyed out, disabled, with the unavailability suffix`() {
        val state = buildRowState(SensorType.PRESSURE, RecordingProfile.OffSetting, deviceHas = false)
        assertFalse(state.enabledControls)
        assertEquals(0.4f, state.alpha, 0.0f)
        assertTrue(
            "label '${state.label}' must end with the unavailability suffix",
            state.label.endsWith(" — Unavailable on this device"),
        )
        assertTrue(state.label.startsWith("Pressure"))
    }

    @Test
    fun `every SensorType has a human-readable label`() {
        for (type in SensorType.values()) {
            val state = buildRowState(type, RecordingProfile.OffSetting, deviceHas = true)
            assertTrue("label for $type must not be blank", state.label.isNotBlank())
        }
    }
}
