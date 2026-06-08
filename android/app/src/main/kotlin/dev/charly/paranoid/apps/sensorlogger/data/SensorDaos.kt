package dev.charly.paranoid.apps.sensorlogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorSessionDao {
    @Insert
    suspend fun insert(session: SensorSessionEntity): Long

    @Update
    suspend fun update(session: SensorSessionEntity)

    @Query("SELECT * FROM sensor_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SensorSessionEntity>>

    @Query("SELECT * FROM sensor_sessions WHERE id = :id")
    suspend fun getById(id: Long): SensorSessionEntity?

    @Query("DELETE FROM sensor_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM sensor_sessions WHERE endedAt IS NULL")
    suspend fun queryIncompleteSessions(): List<SensorSessionEntity>
}

data class SensorTypeCount(val sensorType: String, val count: Int)

@Dao
interface SensorEventDao {
    @Insert
    suspend fun insertBatch(events: List<SensorEventEntity>)

    @Query("SELECT * FROM sensor_events WHERE sessionId = :sessionId ORDER BY elapsedMs ASC, id ASC")
    suspend fun getBySession(sessionId: Long): List<SensorEventEntity>

    /**
     * Keyset-paginated fetch ordered by (elapsedMs, id). Pass
     * `lastElapsedMs = Long.MIN_VALUE, lastId = Long.MIN_VALUE` for the first
     * page. Avoids the growing-OFFSET scan cost on large sessions.
     */
    @Query(
        "SELECT * FROM sensor_events WHERE sessionId = :sessionId " +
            "AND (elapsedMs > :lastElapsedMs OR (elapsedMs = :lastElapsedMs AND id > :lastId)) " +
            "ORDER BY elapsedMs ASC, id ASC LIMIT :limit"
    )
    suspend fun getBySessionAfter(
        sessionId: Long,
        lastElapsedMs: Long,
        lastId: Long,
        limit: Int,
    ): List<SensorEventEntity>

    @Query("SELECT COUNT(*) FROM sensor_events WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM sensor_events WHERE sessionId = :sessionId")
    suspend fun countForSessionLong(sessionId: Long): Long

    @Query(
        "SELECT sensorType, COUNT(*) AS count FROM sensor_events " +
            "WHERE sessionId = :sessionId GROUP BY sensorType"
    )
    suspend fun countsBySensorType(sessionId: Long): List<SensorTypeCount>
}
