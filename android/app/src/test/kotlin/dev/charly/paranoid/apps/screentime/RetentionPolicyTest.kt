package dev.charly.paranoid.apps.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {

    private val day = 24L * 60L * 60L * 1_000L

    @Test
    fun `cutoff is exactly 31 days before now`() {
        val now = 1_000_000_000_000L
        assertEquals(now - 31 * day, RetentionPolicy.cutoffMillis(now))
    }

    @Test
    fun `sessions within 31 days are kept, older ones are prunable`() {
        val now = 1_000_000_000_000L
        val cutoff = RetentionPolicy.cutoffMillis(now)

        val keptEnd = now - 30 * day // 30 days old -> kept
        val prunableEnd = now - 32 * day // 32 days old -> prunable

        // pruneSessionsEndedBefore deletes rows with endMillis < cutoff.
        assertTrue(keptEnd >= cutoff)
        assertTrue(prunableEnd < cutoff)
    }
}
