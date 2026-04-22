package dev.charly.paranoid.apps.netdiag.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: DiagnosticsSessionEntity)

    @Query("SELECT * FROM netdiag_sessions WHERE id = :id")
    suspend fun getById(id: String): DiagnosticsSessionEntity?

    @Query("SELECT * FROM netdiag_sessions ORDER BY createdAtMs DESC")
    fun listByDateDesc(): Flow<List<DiagnosticsSessionEntity>>

    @Query("DELETE FROM netdiag_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SnapshotDao {
    @Insert
    suspend fun insert(snapshot: DiagnosticsSnapshotEntity)

    @Insert
    suspend fun insertAll(snapshots: List<DiagnosticsSnapshotEntity>)

    @Query("SELECT * FROM netdiag_snapshots WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): List<DiagnosticsSnapshotEntity>

    @Query("SELECT * FROM netdiag_snapshots WHERE id = :id")
    suspend fun getById(id: String): DiagnosticsSnapshotEntity?

    @Query("SELECT * FROM netdiag_snapshots ORDER BY capturedAtMs DESC")
    suspend fun getAll(): List<DiagnosticsSnapshotEntity>
}

@Dao
interface ComparisonDao {
    @Insert
    suspend fun insert(comparison: DiagnosticsComparisonEntity)

    @Query("SELECT * FROM netdiag_comparisons WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): List<DiagnosticsComparisonEntity>

    @Query("SELECT * FROM netdiag_comparisons WHERE id = :id")
    suspend fun getById(id: String): DiagnosticsComparisonEntity?
}
