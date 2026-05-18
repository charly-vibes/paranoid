package dev.charly.paranoid.apps.sensorlogger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_sessions")
data class SensorSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
)

@Entity(
    tableName = "sensor_events",
    foreignKeys = [ForeignKey(
        entity = SensorSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["sessionId", "elapsedMs"])],
)
data class SensorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val elapsedMs: Long,
    val sensorType: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int,
)
