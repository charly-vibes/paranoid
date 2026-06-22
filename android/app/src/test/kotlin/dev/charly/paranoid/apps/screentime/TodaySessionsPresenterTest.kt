package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.AppInterval
import dev.charly.paranoid.apps.screentime.model.SYSTEM_UNATTRIBUTED
import dev.charly.paranoid.apps.screentime.model.Session
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySessionsPresenterTest {

    private val utc = ZoneId.of("UTC")
    private val minute = 60_000L

    private fun millis(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun `includes only sessions started today, newest first, with duration and top app`() {
        val now = millis("2026-06-18T12:00:00Z")
        val sessions = listOf(
            // Earlier today: app.a 20 min, app.b 5 min -> top app.a.
            Session(
                startMillis = millis("2026-06-18T08:00:00Z"),
                endMillis = millis("2026-06-18T08:25:00Z"),
                appIntervals = listOf(
                    AppInterval("app.a", millis("2026-06-18T08:00:00Z"), millis("2026-06-18T08:20:00Z")),
                    AppInterval("app.b", millis("2026-06-18T08:20:00Z"), millis("2026-06-18T08:25:00Z")),
                ),
            ),
            // Later today: app.b 10 min -> top app.b.
            Session(
                startMillis = millis("2026-06-18T10:00:00Z"),
                endMillis = millis("2026-06-18T10:10:00Z"),
                appIntervals = listOf(
                    AppInterval("app.b", millis("2026-06-18T10:00:00Z"), millis("2026-06-18T10:10:00Z")),
                ),
            ),
            // Yesterday: must be excluded.
            Session(
                startMillis = millis("2026-06-17T10:00:00Z"),
                endMillis = millis("2026-06-17T11:00:00Z"),
                appIntervals = listOf(
                    AppInterval("app.a", millis("2026-06-17T10:00:00Z"), millis("2026-06-17T11:00:00Z")),
                ),
            ),
        )

        val rows = TodaySessionsPresenter.present(sessions, now, utc)

        assertEquals(2, rows.size)
        // Newest first.
        assertEquals(millis("2026-06-18T10:00:00Z"), rows[0].startMillis)
        assertEquals(10 * minute, rows[0].durationMillis)
        assertEquals("app.b", rows[0].topAppPackage)
        assertEquals(25 * minute, rows[1].durationMillis)
        assertEquals("app.a", rows[1].topAppPackage)
    }

    @Test
    fun `open session runs to now and is marked open`() {
        val now = millis("2026-06-18T09:30:00Z")
        val sessions = listOf(
            Session(
                startMillis = millis("2026-06-18T09:00:00Z"),
                endMillis = null,
                appIntervals = listOf(
                    AppInterval("app.a", millis("2026-06-18T09:00:00Z"), millis("2026-06-18T09:15:00Z")),
                ),
            ),
        )

        val rows = TodaySessionsPresenter.present(sessions, now, utc)

        assertEquals(1, rows.size)
        assertTrue(rows[0].isOpen)
        assertEquals(30 * minute, rows[0].durationMillis)
    }

    @Test
    fun `top app is null when only unattributed time exists`() {
        val now = millis("2026-06-18T09:30:00Z")
        val sessions = listOf(
            Session(
                startMillis = millis("2026-06-18T09:00:00Z"),
                endMillis = millis("2026-06-18T09:05:00Z"),
                appIntervals = listOf(
                    AppInterval(SYSTEM_UNATTRIBUTED, millis("2026-06-18T09:00:00Z"), millis("2026-06-18T09:05:00Z")),
                ),
            ),
        )

        val rows = TodaySessionsPresenter.present(sessions, now, utc)

        assertEquals(1, rows.size)
        assertNull(rows[0].topAppPackage)
    }
}
