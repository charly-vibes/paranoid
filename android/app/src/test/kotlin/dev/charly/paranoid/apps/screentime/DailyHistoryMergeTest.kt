package dev.charly.paranoid.apps.screentime

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyHistoryMergeTest {

    private val day = 24L * 60L * 60L * 1_000L

    private fun usage(start: Long, total: Long): DayUsage =
        DayUsage(startMillis = start, endMillis = start + day, totalForegroundMillis = total, appsByForeground = emptyList())

    @Test
    fun `merges distinct days newest first`() {
        val persisted = listOf(usage(0L, 10L), usage(day, 20L))
        val live = listOf(usage(2 * day, 30L))
        val merged = DailyHistoryMerge.merge(persisted, live)
        assertEquals(listOf(2 * day, day, 0L), merged.map { it.startMillis })
    }

    @Test
    fun `live value wins for an overlapping day`() {
        val persisted = listOf(usage(day, 20L))
        val live = listOf(usage(day, 99L))
        val merged = DailyHistoryMerge.merge(persisted, live)
        assertEquals(1, merged.size)
        assertEquals(99L, merged[0].totalForegroundMillis)
    }

    @Test
    fun `drops zero-activity days`() {
        val persisted = listOf(usage(0L, 0L))
        val live = listOf(usage(day, 5L))
        val merged = DailyHistoryMerge.merge(persisted, live)
        assertEquals(listOf(day), merged.map { it.startMillis })
    }

    @Test
    fun `persisted days beyond the live window are preserved`() {
        // An old persisted day (100 days ago) not present in live must survive.
        val persisted = listOf(usage(0L, 42L))
        val live = listOf(usage(100 * day, 5L))
        val merged = DailyHistoryMerge.merge(persisted, live)
        assertEquals(2, merged.size)
        assertEquals(42L, merged.first { it.startMillis == 0L }.totalForegroundMillis)
    }
}
