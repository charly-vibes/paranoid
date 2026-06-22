package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.AppInterval
import dev.charly.paranoid.apps.screentime.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAggregatorTest {

    private fun session(vararg intervals: AppInterval): Session = Session(
        startMillis = intervals.firstOrNull()?.startMillis ?: 0L,
        endMillis = intervals.lastOrNull()?.endMillis,
        appIntervals = intervals.toList(),
    )

    @Test
    fun `totals foreground time per app across multiple sessions`() {
        val sessions = listOf(
            session(
                AppInterval("app.a", 0L, 4_000L),
                AppInterval("app.b", 4_000L, 6_000L),
            ),
            session(
                AppInterval("app.a", 10_000L, 13_000L),
            ),
        )

        val totals = SessionAggregator.foregroundByApp(sessions, 0L, 20_000L)

        assertEquals(2, totals.size)
        // app.a: 4_000 + 3_000 = 7_000; app.b: 2_000 → sorted by duration desc.
        assertEquals("app.a", totals[0].packageName)
        assertEquals(7_000L, totals[0].foregroundMillis)
        assertEquals("app.b", totals[1].packageName)
        assertEquals(2_000L, totals[1].foregroundMillis)
    }

    @Test
    fun `counts only the portion of an interval overlapping the query window`() {
        val sessions = listOf(
            session(AppInterval("app.a", 0L, 10_000L)),
        )

        val totals = SessionAggregator.foregroundByApp(sessions, 3_000L, 8_000L)

        assertEquals(1, totals.size)
        assertEquals(5_000L, totals[0].foregroundMillis)
    }

    @Test
    fun `excludes intervals entirely outside the window`() {
        val sessions = listOf(
            session(AppInterval("app.a", 0L, 1_000L)),
            session(AppInterval("app.b", 5_000L, 9_000L)),
        )

        val totals = SessionAggregator.foregroundByApp(sessions, 2_000L, 4_000L)

        assertTrue(totals.isEmpty())
    }

    @Test
    fun `empty or inverted window yields no totals`() {
        val sessions = listOf(session(AppInterval("app.a", 0L, 10_000L)))

        assertTrue(SessionAggregator.foregroundByApp(sessions, 5_000L, 5_000L).isEmpty())
        assertTrue(SessionAggregator.foregroundByApp(sessions, 8_000L, 4_000L).isEmpty())
    }

    @Test
    fun `equal durations are ordered by package name for stable output`() {
        val sessions = listOf(
            session(
                AppInterval("app.z", 0L, 1_000L),
                AppInterval("app.a", 1_000L, 2_000L),
            ),
        )

        val totals = SessionAggregator.foregroundByApp(sessions, 0L, 10_000L)

        assertEquals(listOf("app.a", "app.z"), totals.map { it.packageName })
    }
}
