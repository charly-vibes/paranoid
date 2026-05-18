package dev.charly.paranoid.apps.sensorlogger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.sensorlogger.data.SensorEventEntity
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensorDaoTest {

    private lateinit var db: ParanoidDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ParanoidDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertsAndReadsSession() = runBlocking {
        val entity = SensorSessionEntity(startedAt = 1_000L, endedAt = null)
        val id = db.sensorSessionDao().insert(entity)
        val stored = db.sensorSessionDao().getById(id)
        assertNotNull(stored)
        assertEquals(1_000L, stored?.startedAt)
        assertNull(stored?.endedAt)
    }

    @Test
    fun insertsAndReadsBatchEvents() = runBlocking {
        val sessionId = db.sensorSessionDao().insert(SensorSessionEntity(startedAt = 0L))
        val events = listOf(
            SensorEventEntity(sessionId = sessionId, elapsedMs = 100L, sensorType = "ACCELEROMETER", x = 1f, y = 2f, z = 3f, accuracy = 3),
            SensorEventEntity(sessionId = sessionId, elapsedMs = 200L, sensorType = "GYROSCOPE", x = 0f, y = 0f, z = 0f, accuracy = 3),
        )
        db.sensorEventDao().insertBatch(events)
        val stored = db.sensorEventDao().getBySession(sessionId)
        assertEquals(2, stored.size)
        assertEquals(100L, stored[0].elapsedMs)
        assertEquals(200L, stored[1].elapsedMs)
    }

    @Test
    fun eventsOrderedByElapsedMs() = runBlocking {
        val sessionId = db.sensorSessionDao().insert(SensorSessionEntity(startedAt = 0L))
        db.sensorEventDao().insertBatch(listOf(
            SensorEventEntity(sessionId = sessionId, elapsedMs = 300L, sensorType = "LIGHT", x = 0f, y = 0f, z = 0f, accuracy = 3),
            SensorEventEntity(sessionId = sessionId, elapsedMs = 100L, sensorType = "LIGHT", x = 0f, y = 0f, z = 0f, accuracy = 3),
            SensorEventEntity(sessionId = sessionId, elapsedMs = 200L, sensorType = "LIGHT", x = 0f, y = 0f, z = 0f, accuracy = 3),
        ))
        val stored = db.sensorEventDao().getBySession(sessionId)
        assertEquals(listOf(100L, 200L, 300L), stored.map { it.elapsedMs })
    }

    @Test
    fun cascadeDeleteRemovesEvents() = runBlocking {
        val sessionId = db.sensorSessionDao().insert(SensorSessionEntity(startedAt = 0L))
        db.sensorEventDao().insertBatch(listOf(
            SensorEventEntity(sessionId = sessionId, elapsedMs = 50L, sensorType = "PRESSURE", x = 1f, y = 0f, z = 0f, accuracy = 2),
        ))
        db.sensorSessionDao().deleteById(sessionId)
        val events = db.sensorEventDao().getBySession(sessionId)
        assertTrue(events.isEmpty())
    }

    @Test
    fun queryIncompleteSessionsReturnsOnlyNullEndedAt() = runBlocking {
        val id1 = db.sensorSessionDao().insert(SensorSessionEntity(startedAt = 1_000L, endedAt = null))
        db.sensorSessionDao().insert(SensorSessionEntity(startedAt = 2_000L, endedAt = 5_000L))
        val incomplete = db.sensorSessionDao().queryIncompleteSessions()
        assertEquals(1, incomplete.size)
        assertEquals(id1, incomplete[0].id)
    }
}
