package dev.charly.paranoid.apps.usageaudit

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "usageaudit_battery_snapshots")
data class BatterySnapshotEntity(
    @PrimaryKey val timestampMillis: Long,
    val batteryPercent: Int,
    val chargingState: String,
    val batteryStatus: String?,
    val batteryHealth: String?,
) {
    fun toDomain(): BatterySnapshot = BatterySnapshot(
        timestampMillis = timestampMillis,
        batteryPercent = batteryPercent,
        chargingState = chargingState.toChargingState(),
        batteryStatus = batteryStatus,
        batteryHealth = batteryHealth,
    )

    companion object {
        fun fromDomain(snapshot: BatterySnapshot): BatterySnapshotEntity = BatterySnapshotEntity(
            timestampMillis = snapshot.timestampMillis,
            batteryPercent = snapshot.batteryPercent,
            chargingState = snapshot.chargingState.name,
            batteryStatus = snapshot.batteryStatus,
            batteryHealth = snapshot.batteryHealth,
        )
    }
}

@Dao
interface BatterySnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: BatterySnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<BatterySnapshotEntity>)

    @Query("SELECT * FROM usageaudit_battery_snapshots WHERE timestampMillis <= :timestampMillis ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun latestAtOrBefore(timestampMillis: Long): BatterySnapshotEntity?

    @Query("SELECT * FROM usageaudit_battery_snapshots WHERE timestampMillis >= :startMillis AND timestampMillis <= :endMillis ORDER BY timestampMillis ASC")
    suspend fun between(startMillis: Long, endMillis: Long): List<BatterySnapshotEntity>
}

private fun String.toChargingState(): ChargingState =
    ChargingState.entries.firstOrNull { it.name == this } ?: ChargingState.UNKNOWN
