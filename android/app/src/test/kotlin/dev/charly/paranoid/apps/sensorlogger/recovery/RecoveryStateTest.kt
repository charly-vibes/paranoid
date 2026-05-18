package dev.charly.paranoid.apps.sensorlogger.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryStateTest {

    @Test
    fun `None state when no incomplete sessions`() {
        val sessions = emptyList<Long>()
        val state = RecoveryState.from(sessions)
        assertEquals(RecoveryState.None, state)
    }

    @Test
    fun `Incomplete state when sessions have null endedAt`() {
        val sessions = listOf(1L, 2L)
        val state = RecoveryState.from(sessions)
        assertTrue(state is RecoveryState.Incomplete)
        assertEquals(2, (state as RecoveryState.Incomplete).sessionIds.size)
    }

    @Test
    fun `Incomplete state contains the session ids`() {
        val state = RecoveryState.from(listOf(42L))
        assertEquals(listOf(42L), (state as RecoveryState.Incomplete).sessionIds)
    }
}
