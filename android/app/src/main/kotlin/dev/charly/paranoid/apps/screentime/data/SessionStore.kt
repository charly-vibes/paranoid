package dev.charly.paranoid.apps.screentime.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import dev.charly.paranoid.apps.screentime.model.AppInterval
import dev.charly.paranoid.apps.screentime.model.Session

@Entity(tableName = "screentime_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMillis: Long,
    // Null while the session is active.
    val endMillis: Long?,
) {
    companion object {
        fun fromDomain(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            startMillis = session.startMillis,
            endMillis = session.endMillis,
        )
    }
}

@Entity(
    tableName = "screentime_app_intervals",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class AppIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Persistence for screen-time sessions and their per-app foreground intervals. Sessions and
 * intervals live in separate tables (one-to-many, CASCADE delete) within the shared
 * [dev.charly.paranoid.apps.netmap.data.ParanoidDatabase]; screen-time owns its own tables
 * and shares nothing with other mini-apps.
 */
@Dao
interface ScreenTimeDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertIntervals(intervals: List<AppIntervalEntity>)

    @Update
    suspend fun updateSession(session: SessionEntity)

    /** The most recent still-open session (null end), if any — used to recover after restart. */
    @Query("SELECT * FROM screentime_sessions WHERE endMillis IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun openSession(): SessionEntity?

    /** Sessions that overlap the half-open window [startMillis, endMillis), oldest first. */
    @Query(
        "SELECT * FROM screentime_sessions " +
            "WHERE startMillis < :endMillis AND (endMillis IS NULL OR endMillis > :startMillis) " +
            "ORDER BY startMillis ASC",
    )
    suspend fun sessionsOverlapping(startMillis: Long, endMillis: Long): List<SessionEntity>

    @Query("SELECT * FROM screentime_app_intervals WHERE sessionId = :sessionId ORDER BY startMillis ASC")
    suspend fun intervalsForSession(sessionId: Long): List<AppIntervalEntity>

    /** Deletes closed sessions that ended before [cutoffMillis]; CASCADE removes their intervals. */
    @Query("DELETE FROM screentime_sessions WHERE endMillis IS NOT NULL AND endMillis < :cutoffMillis")
    suspend fun pruneSessionsEndedBefore(cutoffMillis: Long): Int
}

/** Maps a stored session row plus its interval rows into a domain [Session]. */
fun SessionEntity.toDomain(intervals: List<AppIntervalEntity>): Session = Session(
    id = id,
    startMillis = startMillis,
    endMillis = endMillis,
    appIntervals = intervals.map { it.toDomain() },
)

fun AppIntervalEntity.toDomain(): AppInterval = AppInterval(
    packageName = packageName,
    startMillis = startMillis,
    endMillis = endMillis,
)

/** Maps a domain session's intervals into rows for the given persisted [sessionId]. */
fun List<AppInterval>.toEntities(sessionId: Long): List<AppIntervalEntity> = map { interval ->
    AppIntervalEntity(
        sessionId = sessionId,
        packageName = interval.packageName,
        startMillis = interval.startMillis,
        endMillis = interval.endMillis,
    )
}
