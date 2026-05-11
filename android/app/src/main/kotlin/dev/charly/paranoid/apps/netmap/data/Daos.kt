// NO-NETWORK INVARIANT (antenna estimate persistence)
// The antenna estimates DAO is purely a local Room cache. It MUST NOT
// reach the network. See AntennaEstimator.kt for the full invariant.

package dev.charly.paranoid.apps.netmap.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity)

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM recordings WHERE id = :id AND (SELECT COUNT(*) FROM measurements WHERE recordingId = :id) = 0")
    suspend fun deleteIfEmpty(id: String): Int
}

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insertBatch(measurements: List<MeasurementEntity>)

    @Query("SELECT * FROM measurements WHERE recordingId = :recordingId ORDER BY timestamp ASC")
    fun observeByRecording(recordingId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE recordingId = :recordingId ORDER BY timestamp ASC")
    suspend fun getByRecording(recordingId: String): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurements WHERE recordingId = :recordingId")
    suspend fun countForRecording(recordingId: String): Int

    @Query("SELECT MAX(timestamp) FROM measurements WHERE recordingId = :recordingId")
    suspend fun lastTimestamp(recordingId: String): Long?
}

@Dao
interface AntennaEstimateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(estimates: List<AntennaEstimateEntity>)

    @Query("SELECT * FROM netmap_antenna_estimates WHERE recordingId = :recordingId")
    fun flowForRecording(recordingId: String): Flow<List<AntennaEstimateEntity>>

    @Query("SELECT * FROM netmap_antenna_estimates WHERE recordingId = :recordingId")
    suspend fun getForRecording(recordingId: String): List<AntennaEstimateEntity>

    @Query("SELECT COUNT(*) FROM netmap_antenna_estimates WHERE recordingId = :recordingId")
    suspend fun countForRecording(recordingId: String): Int

    @Query("DELETE FROM netmap_antenna_estimates WHERE recordingId = :recordingId")
    suspend fun deleteForRecording(recordingId: String)
}
