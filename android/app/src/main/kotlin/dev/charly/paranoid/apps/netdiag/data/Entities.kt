package dev.charly.paranoid.apps.netdiag.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "netdiag_sessions")
data class DiagnosticsSessionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val createdAtMs: Long,
    val notes: String?,
)

@Entity(
    tableName = "netdiag_snapshots",
    foreignKeys = [ForeignKey(
        entity = DiagnosticsSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class DiagnosticsSnapshotEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val capturedAtMs: Long,
    val deviceLabel: String,
    val deviceModel: String,
    val snapshotJson: String,
)

@Entity(
    tableName = "netdiag_comparisons",
    foreignKeys = [ForeignKey(
        entity = DiagnosticsSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class DiagnosticsComparisonEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val comparedAtMs: Long,
    val overallStatus: String,
    val comparisonJson: String,
)
