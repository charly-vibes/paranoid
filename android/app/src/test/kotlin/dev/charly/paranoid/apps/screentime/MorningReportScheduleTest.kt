package dev.charly.paranoid.apps.screentime

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MorningReportScheduleTest {

    private val utc = ZoneId.of("UTC")
    private val hour = 3_600_000L

    private fun millis(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun `before 8am schedules for 8am the same day`() {
        val now = millis("2026-06-18T06:00:00Z")

        val delay = MorningReportSchedule.nextReportDelayMillis(now, utc)

        assertEquals(2 * hour, delay)
    }

    @Test
    fun `after 8am schedules for 8am the next day`() {
        val now = millis("2026-06-18T09:00:00Z")

        val delay = MorningReportSchedule.nextReportDelayMillis(now, utc)

        assertEquals(23 * hour, delay)
    }

    @Test
    fun `exactly 8am schedules for the next day`() {
        val now = millis("2026-06-18T08:00:00Z")

        val delay = MorningReportSchedule.nextReportDelayMillis(now, utc)

        assertEquals(24 * hour, delay)
    }
}
