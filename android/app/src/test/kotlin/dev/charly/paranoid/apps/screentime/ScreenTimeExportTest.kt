package dev.charly.paranoid.apps.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeExportTest {

    private val minute = 60_000L

    private fun day(start: Long, total: Long, vararg apps: Pair<String, Long>): DayUsage = DayUsage(
        startMillis = start,
        endMillis = start + 24 * 60 * minute,
        totalForegroundMillis = total,
        appsByForeground = apps.map { AppUsageTotal(it.first, it.second) },
    )

    @Test
    fun `summary lists each day with total and top apps`() {
        val history = listOf(
            day(0L, 25 * minute, "app.a" to 20 * minute, "app.b" to 5 * minute),
        )
        val text = ScreenTimeExport.summaryText(history) { pkg -> pkg.uppercase() }
        assertTrue(text.startsWith("ScreenTime activity"))
        assertTrue(text.contains("0h 25m") || text.contains("25m"))
        // Label resolver applied.
        assertTrue(text.contains("APP.A"))
    }

    @Test
    fun `summary handles empty history`() {
        assertTrue(ScreenTimeExport.summaryText(emptyList()).contains("No activity"))
    }

    @Test
    fun `csv has a header and one row per app per day`() {
        val history = listOf(
            day(0L, 25 * minute, "app.a" to 20 * minute, "app.b" to 5 * minute),
        )
        val csv = ScreenTimeExport.csv(history)
        val lines = csv.split("\n")
        assertEquals(3, lines.size) // header + 2 app rows
        assertTrue(lines[0].startsWith("date,window_start_millis"))
        assertTrue(lines[1].contains("app.a"))
        assertTrue(lines[2].contains("app.b"))
    }

    @Test
    fun `csv emits a total-only row for days with no app activity`() {
        val csv = ScreenTimeExport.csv(listOf(day(0L, 0L)))
        val lines = csv.split("\n")
        assertEquals(2, lines.size) // header + 1 total-only row
        // Trailing empty app fields.
        assertTrue(lines[1].endsWith(",,,"))
    }

    @Test
    fun `csv escapes labels containing commas`() {
        val history = listOf(day(0L, 10 * minute, "app.a" to 10 * minute))
        val csv = ScreenTimeExport.csv(history) { "Comma, Inc." }
        assertTrue(csv.contains("\"Comma, Inc.\""))
    }
}
