package dev.charly.paranoid.apps.screentime

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class StaleSessionResolverTest {

    private val utc = ZoneId.of("UTC")

    private fun millis(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun `uses last sample time when present and same day`() {
        val start = millis("2026-06-17T08:00:00Z")
        val lastSample = millis("2026-06-17T09:30:00Z")
        val serviceStart = millis("2026-06-17T10:00:00Z")

        val end = StaleSessionResolver.resolveEnd(start, lastSample, serviceStart, utc)

        assertEquals(lastSample, end)
    }

    @Test
    fun `falls back to service start time when no sample recorded`() {
        val start = millis("2026-06-17T08:00:00Z")
        val serviceStart = millis("2026-06-17T08:00:20Z")

        val end = StaleSessionResolver.resolveEnd(start, null, serviceStart, utc)

        assertEquals(serviceStart, end)
    }

    @Test
    fun `clamps to end of start day when recovered time is on a later day`() {
        val start = millis("2026-06-17T22:00:00Z")
        // Service restarted the next morning.
        val serviceStart = millis("2026-06-18T07:00:00Z")

        val end = StaleSessionResolver.resolveEnd(start, null, serviceStart, utc)

        assertEquals(millis("2026-06-17T23:59:59.999Z"), end)
        assertEquals(
            "2026-06-17",
            Instant.ofEpochMilli(end).atZone(utc).toLocalDate().toString(),
        )
    }

    @Test
    fun `last sample on a later day is also clamped to the start day`() {
        val start = millis("2026-06-17T23:50:00Z")
        val lastSample = millis("2026-06-18T00:10:00Z")
        val serviceStart = millis("2026-06-18T00:15:00Z")

        val end = StaleSessionResolver.resolveEnd(start, lastSample, serviceStart, utc)

        assertEquals(millis("2026-06-17T23:59:59.999Z"), end)
    }

    @Test
    fun `never returns a time earlier than the session start`() {
        val start = millis("2026-06-17T08:00:00Z")
        // Clock skew: service start appears before the session start.
        val serviceStart = millis("2026-06-17T07:59:00Z")

        val end = StaleSessionResolver.resolveEnd(start, null, serviceStart, utc)

        assertEquals(start, end)
    }
}
