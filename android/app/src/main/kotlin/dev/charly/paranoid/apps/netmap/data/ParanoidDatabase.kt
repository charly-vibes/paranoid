package dev.charly.paranoid.apps.netmap.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RecordingEntity::class, MeasurementEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ParanoidDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var instance: ParanoidDatabase? = null

        fun getInstance(context: Context): ParanoidDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ParanoidDatabase::class.java,
                    "paranoid.db"
                ).build().also { instance = it }
            }
    }
}
