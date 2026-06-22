package dev.charly.paranoid.apps.screentime.data

import dev.charly.paranoid.apps.screentime.model.AppInterval
import dev.charly.paranoid.apps.screentime.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStoreMappersTest {

    @Test
    fun `session entity plus interval rows map back to the domain session`() {
        val entity = SessionEntity(id = 42L, startMillis = 1_000L, endMillis = 5_000L)
        val intervals = listOf(
            AppIntervalEntity(id = 1L, sessionId = 42L, packageName = "app.a", startMillis = 1_000L, endMillis = 3_000L),
            AppIntervalEntity(id = 2L, sessionId = 42L, packageName = "app.b", startMillis = 3_000L, endMillis = 5_000L),
        )

        val session = entity.toDomain(intervals)

        assertEquals(42L, session.id)
        assertEquals(1_000L, session.startMillis)
        assertEquals(5_000L, session.endMillis)
        assertEquals(
            listOf(
                AppInterval("app.a", 1_000L, 3_000L),
                AppInterval("app.b", 3_000L, 5_000L),
            ),
            session.appIntervals,
        )
    }

    @Test
    fun `open session maps with a null end time`() {
        val entity = SessionEntity(id = 7L, startMillis = 2_000L, endMillis = null)

        val session = entity.toDomain(emptyList())

        assertEquals(null, session.endMillis)
        assertEquals(true, session.isOpen)
    }

    @Test
    fun `domain session round-trips through entities`() {
        val session = Session(
            id = 9L,
            startMillis = 100L,
            endMillis = 900L,
            appIntervals = listOf(
                AppInterval("app.a", 100L, 400L),
                AppInterval("app.b", 400L, 900L),
            ),
        )

        val sessionEntity = SessionEntity.fromDomain(session)
        val intervalEntities = session.appIntervals.toEntities(sessionId = sessionEntity.id)
        val restored = sessionEntity.toDomain(intervalEntities)

        assertEquals(session, restored)
    }

    @Test
    fun `interval entities carry the persisted session id`() {
        val intervals = listOf(AppInterval("app.a", 0L, 1_000L))

        val entities = intervals.toEntities(sessionId = 55L)

        assertEquals(55L, entities.single().sessionId)
        assertEquals("app.a", entities.single().packageName)
    }
}
