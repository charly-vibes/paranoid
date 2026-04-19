package dev.charly.paranoid.apps.netmap.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.charly.paranoid.apps.netdiag.data.ComparisonDao
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsComparisonEntity
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSessionEntity
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshotEntity
import dev.charly.paranoid.apps.netdiag.data.SessionDao
import dev.charly.paranoid.apps.netdiag.data.SnapshotDao

@Database(
    entities = [
        RecordingEntity::class,
        MeasurementEntity::class,
        DiagnosticsSessionEntity::class,
        DiagnosticsSnapshotEntity::class,
        DiagnosticsComparisonEntity::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class ParanoidDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun sessionDao(): SessionDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun comparisonDao(): ComparisonDao

    companion object {
        @Volatile
        private var instance: ParanoidDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS netdiag_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        label TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        notes TEXT
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS netdiag_snapshots (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        capturedAtMs INTEGER NOT NULL,
                        deviceLabel TEXT NOT NULL,
                        deviceModel TEXT NOT NULL,
                        snapshotJson TEXT NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES netdiag_sessions(id) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_netdiag_snapshots_sessionId ON netdiag_snapshots(sessionId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS netdiag_comparisons (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        comparedAtMs INTEGER NOT NULL,
                        overallStatus TEXT NOT NULL,
                        comparisonJson TEXT NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES netdiag_sessions(id) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_netdiag_comparisons_sessionId ON netdiag_comparisons(sessionId)")
            }
        }

        fun getInstance(context: Context): ParanoidDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ParanoidDatabase::class.java,
                    "paranoid.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
