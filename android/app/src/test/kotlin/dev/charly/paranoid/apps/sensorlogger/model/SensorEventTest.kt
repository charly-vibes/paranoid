package dev.charly.paranoid.apps.sensorlogger.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorEventTest {

    @Test
    fun `SensorEvent holds all fields`() {
        val event = SensorEvent(
            id = 1,
            sessionId = 42,
            elapsedMs = 500L,
            sensorType = SensorType.ACCELEROMETER,
            x = 1.0f,
            y = 2.0f,
            z = 3.0f,
            accuracy = 3,
        )
        assertEquals(1L, event.id)
        assertEquals(42L, event.sessionId)
        assertEquals(500L, event.elapsedMs)
        assertEquals(SensorType.ACCELEROMETER, event.sensorType)
        assertEquals(1.0f, event.x)
        assertEquals(2.0f, event.y)
        assertEquals(3.0f, event.z)
        assertEquals(3, event.accuracy)
    }

    @Test
    fun `ACCELEROMETER belongs to Motion category`() {
        assertEquals(SensorCategory.Motion, SensorType.ACCELEROMETER.category)
    }

    @Test
    fun `GYROSCOPE belongs to Motion category`() {
        assertEquals(SensorCategory.Motion, SensorType.GYROSCOPE.category)
    }

    @Test
    fun `LINEAR_ACCELERATION belongs to Motion category`() {
        assertEquals(SensorCategory.Motion, SensorType.LINEAR_ACCELERATION.category)
    }

    @Test
    fun `GRAVITY belongs to Motion category`() {
        assertEquals(SensorCategory.Motion, SensorType.GRAVITY.category)
    }

    @Test
    fun `ROTATION_VECTOR belongs to Orientation category`() {
        assertEquals(SensorCategory.Orientation, SensorType.ROTATION_VECTOR.category)
    }

    @Test
    fun `MAGNETIC_FIELD belongs to Orientation category`() {
        assertEquals(SensorCategory.Orientation, SensorType.MAGNETIC_FIELD.category)
    }

    @Test
    fun `PRESSURE belongs to Environment category`() {
        assertEquals(SensorCategory.Environment, SensorType.PRESSURE.category)
    }

    @Test
    fun `LIGHT belongs to Environment category`() {
        assertEquals(SensorCategory.Environment, SensorType.LIGHT.category)
    }

    @Test
    fun `PROXIMITY belongs to Environment category`() {
        assertEquals(SensorCategory.Environment, SensorType.PROXIMITY.category)
    }
}
