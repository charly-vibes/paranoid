package dev.charly.paranoid.apps.netmap.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val carrier: String? = null,
    val notes: String? = null
)

@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recordingId"), Index("timestamp")]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val speedKmh: Float? = null,
    val bearing: Float? = null,
    val altitude: Double? = null,
    val networkType: String,
    val dataState: String,
    val cellsJson: String
)

/**
 * Cached per-cell location estimate for one recording.
 *
 * Composite primary key (recordingId, cellKey). CASCADE on recordingId so
 * deleting a recording removes its estimates automatically.
 *
 * Computed by [dev.charly.paranoid.apps.netmap.estimate.AntennaEstimator]
 * — see openspec/changes/add-netmap-antenna-locations/specs/netmap-data/spec.md
 */
@Entity(
    tableName = "netmap_antenna_estimates",
    primaryKeys = ["recordingId", "cellKey"],
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recordingId")]
)
data class AntennaEstimateEntity(
    val recordingId: String,
    val cellKey: String,
    val technology: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Float,
    val sampleCount: Int,
    val strongestSignal: String,
    val isPciOnly: Boolean
)
