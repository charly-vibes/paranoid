package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySummaryFormatterTest {

    @Test
    fun `formats today summary with total usage and top apps`() {
        val summary = DailyUsageSummary(
            windowStartMillis = 0L,
            windowEndMillis = 86_400_000L,
            totalForegroundDurationMillis = 5_400_000L,
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.chat", "Chat", 3_600_000L),
                AppUsageSummary("com.example.reader", "Reader", 1_800_000L),
            ),
        )
        val text = TodaySummaryFormatter.format(summary)

        assertTrue(text.contains("Today"))
        assertTrue(text.contains("1h 30m"))
        assertTrue(text.contains("Chat"))
        assertTrue(text.contains("1h 0m"))
        assertTrue(text.contains("Reader"))
        assertTrue(text.contains("30m"))
    }

    @Test
    fun `formats empty today summary`() {
        val text = TodaySummaryFormatter.format(null)

        assertTrue(text.contains("No usage data"))
    }
}

class LastNightSummaryFormatterTest {

    @Test
    fun `formats last night summary with battery and apps`() {
        val audit = OvernightAudit(
            windowStartMillis = 100L,
            windowEndMillis = 400L,
            batteryStartPercent = 82,
            batteryEndPercent = 74,
            batteryDeltaPercent = -8,
            totalForegroundDurationMillis = 1_200_000L,
            activeApps = listOf(
                AppUsageSummary("com.example.chat", "Chat", 1_200_000L),
            ),
            activeAppsCount = 1,
            hadChargingTransition = false,
            hasIncompleteBatteryCoverage = false,
            warningFlags = emptySet(),
        )
        val text = LastNightSummaryFormatter.format(audit)

        assertTrue(text.contains("Last Night"))
        assertTrue(text.contains("82%"))
        assertTrue(text.contains("74%"))
        assertTrue(text.contains("−8%"))
        assertTrue(text.contains("Chat"))
    }

    @Test
    fun `includes warning lines for incomplete data`() {
        val audit = OvernightAudit(
            windowStartMillis = 100L,
            windowEndMillis = 400L,
            batteryStartPercent = 80,
            batteryEndPercent = 72,
            batteryDeltaPercent = -8,
            totalForegroundDurationMillis = 0L,
            activeApps = emptyList(),
            activeAppsCount = 0,
            hadChargingTransition = false,
            hasIncompleteBatteryCoverage = true,
            warningFlags = setOf(WarningFlag.INCOMPLETE_BATTERY_DATA, WarningFlag.NO_OBSERVED_APP_ACTIVITY),
        )
        val text = LastNightSummaryFormatter.format(audit)

        assertTrue(text.contains("Battery data may be incomplete"))
        assertTrue(text.contains("No foreground app activity was observed"))
    }

    @Test
    fun `includes charging transition warning`() {
        val audit = OvernightAudit(
            windowStartMillis = 100L,
            windowEndMillis = 400L,
            batteryStartPercent = 60,
            batteryEndPercent = 65,
            batteryDeltaPercent = 5,
            totalForegroundDurationMillis = 0L,
            activeApps = emptyList(),
            activeAppsCount = 0,
            hadChargingTransition = true,
            hasIncompleteBatteryCoverage = false,
            warningFlags = setOf(WarningFlag.CHARGING_TRANSITION),
        )
        val text = LastNightSummaryFormatter.format(audit)

        assertTrue(text.contains("charging activity"))
    }

    @Test
    fun `formats empty last night summary`() {
        val text = LastNightSummaryFormatter.format(null)

        assertTrue(text.contains("No overnight data"))
    }
}

class CsvExporterTest {

    @Test
    fun `generates CSV with headers and rows for today summary`() {
        val summary = DailyUsageSummary(
            windowStartMillis = 1_713_700_000_000L,
            windowEndMillis = 1_713_786_400_000L,
            totalForegroundDurationMillis = 5_400_000L,
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.chat", "Chat", 3_600_000L),
                AppUsageSummary("com.example.reader", "Reader", 1_800_000L),
            ),
        )
        val csv = CsvExporter.exportToday(summary)
        val lines = csv.lines().filter { it.isNotBlank() }

        assertEquals(
            "report_date,window_start,window_end,app_label,package_name,foreground_duration_millis,battery_start_percent,battery_end_percent,battery_delta_percent,warning_flags",
            lines[0],
        )
        assertTrue(lines[1].contains("Chat"))
        assertTrue(lines[1].contains("com.example.chat"))
        assertTrue(lines[1].contains("3600000"))
        assertTrue(lines[2].contains("Reader"))
        assertEquals(3, lines.size)
    }

    @Test
    fun `generates CSV with headers and rows for overnight audit`() {
        val audit = OvernightAudit(
            windowStartMillis = 1_713_736_800_000L,
            windowEndMillis = 1_713_769_200_000L,
            batteryStartPercent = 82,
            batteryEndPercent = 74,
            batteryDeltaPercent = -8,
            totalForegroundDurationMillis = 1_200_000L,
            activeApps = listOf(
                AppUsageSummary("com.example.chat", "Chat", 1_200_000L),
            ),
            activeAppsCount = 1,
            hadChargingTransition = false,
            hasIncompleteBatteryCoverage = true,
            warningFlags = setOf(WarningFlag.INCOMPLETE_BATTERY_DATA),
        )
        val csv = CsvExporter.exportLastNight(audit)
        val lines = csv.lines().filter { it.isNotBlank() }

        assertEquals(3, lines.size) // header + 1 app row + 1 summary row
        assertTrue(lines[1].contains("Chat"))
        assertTrue(lines[1].contains("82"))
        assertTrue(lines[1].contains("74"))
        assertTrue(lines[1].contains("-8"))
        assertTrue(lines[1].contains("incomplete_battery_data"))
    }

    @Test
    fun `escapes commas in app labels`() {
        val summary = DailyUsageSummary(
            windowStartMillis = 0L,
            windowEndMillis = 86_400_000L,
            totalForegroundDurationMillis = 1_000_000L,
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.app", "My App, Pro", 1_000_000L),
            ),
        )
        val csv = CsvExporter.exportToday(summary)
        val dataLine = csv.lines().filter { it.isNotBlank() }[1]

        assertTrue(dataLine.contains("\"My App, Pro\""))
    }

    @Test
    fun `empty today summary produces header only`() {
        val csv = CsvExporter.exportToday(null)
        val lines = csv.lines().filter { it.isNotBlank() }

        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("report_date"))
    }

    @Test
    fun `day-detail share text is scoped to the selected day's summary`() {
        // Slice B: Share Summary on Day Detail must reuse the existing
        // TodaySummaryFormatter on the *selected* day's summary, with no extra
        // hourly or interval data appended.
        val pastDayStart = 1_713_700_000_000L
        val summary = DailyUsageSummary(
            windowStartMillis = pastDayStart,
            windowEndMillis = pastDayStart + 86_400_000L,
            totalForegroundDurationMillis = 7_200_000L,
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.reader", "Reader", 7_200_000L),
            ),
        )

        val text = TodaySummaryFormatter.format(summary)

        assertTrue("expected per-day total in share text", text.contains("2h 0m"))
        assertTrue("expected app label in share text", text.contains("Reader"))
        // Schema guard: no hourly distribution leaks into the plain-text share.
        assertTrue("share text must not include hourly markers", !text.contains("hour"))
        assertTrue("share text must not include interval markers", !text.contains("interval"))
    }

    @Test
    fun `day-detail CSV uses the existing v1 schema with no hourly or interval columns`() {
        // Slice B explicitly preserves the v1 schema (10 columns) when sharing
        // a past day. New hourly/interval data is *not* part of the export.
        val pastDayStart = 1_713_700_000_000L
        val summary = DailyUsageSummary(
            windowStartMillis = pastDayStart,
            windowEndMillis = pastDayStart + 86_400_000L,
            totalForegroundDurationMillis = 3_600_000L,
            appsByForegroundDuration = listOf(
                AppUsageSummary("com.example.reader", "Reader", 3_600_000L),
            ),
        )

        val csv = CsvExporter.exportToday(summary)
        val lines = csv.lines().filter { it.isNotBlank() }
        val header = lines[0]
        val v1Header = "report_date,window_start,window_end,app_label,package_name," +
            "foreground_duration_millis,battery_start_percent,battery_end_percent," +
            "battery_delta_percent,warning_flags"

        assertEquals(v1Header, header)
        // No hourly bucket or interval columns.
        assertTrue(!header.contains("hour"))
        assertTrue(!header.contains("interval"))
        // Day-scoped: window_start in the data row matches the selected day.
        assertTrue("expected window_start to scope to selected day", lines[1].contains(pastDayStart.toString()))
    }
}
