package dev.charly.paranoid.apps.sensorlogger.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSessionTest {

    @Test
    fun `session with null endedAt is incomplete`() {
        val session = SensorSession(id = 1, startedAt = 1000L, endedAt = null)
        assertTrue(session.isIncomplete)
    }

    @Test
    fun `session with endedAt is not incomplete`() {
        val session = SensorSession(id = 1, startedAt = 1000L, endedAt = 5000L)
        assertFalse(session.isIncomplete)
    }

    @Test
    fun `durationMs returns difference when endedAt is set`() {
        val session = SensorSession(id = 1, startedAt = 1000L, endedAt = 6000L)
        assertEquals(5000L, session.durationMs)
    }

    @Test
    fun `durationMs returns null for incomplete session`() {
        val session = SensorSession(id = 1, startedAt = 1000L, endedAt = null)
        assertNull(session.durationMs)
    }

    @Test
    fun `zero-duration session is valid`() {
        val session = SensorSession(id = 1, startedAt = 5000L, endedAt = 5000L)
        assertEquals(0L, session.durationMs)
        assertFalse(session.isIncomplete)
    }
}
