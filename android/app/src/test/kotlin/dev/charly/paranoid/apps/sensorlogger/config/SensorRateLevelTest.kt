package dev.charly.paranoid.apps.sensorlogger.config

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorRateLevelTest {

    @Test
    fun `OFF maps to null delay`() {
        assertNull(SensorRateLevel.OFF.toSensorManagerDelay())
    }

    @Test
    fun `non-OFF rates mirror SensorManager SENSOR_DELAY constants`() {
        assertEquals(SensorManager.SENSOR_DELAY_NORMAL, SensorRateLevel.NORMAL.toSensorManagerDelay())
        assertEquals(SensorManager.SENSOR_DELAY_UI, SensorRateLevel.UI.toSensorManagerDelay())
        assertEquals(SensorManager.SENSOR_DELAY_GAME, SensorRateLevel.GAME.toSensorManagerDelay())
        assertEquals(SensorManager.SENSOR_DELAY_FASTEST, SensorRateLevel.FASTEST.toSensorManagerDelay())
    }
}
