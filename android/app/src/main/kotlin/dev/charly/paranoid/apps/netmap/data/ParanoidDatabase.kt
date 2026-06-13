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
import dev.charly.paranoid.apps.sensorlogger.data.SensorEventDao
import dev.charly.paranoid.apps.sensorlogger.data.SensorEventEntity
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionDao
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.usageaudit.BatterySnapshotDao
import dev.charly.paranoid.apps.usageaudit.BatterySnapshotEntity

@Database(
    entities = [
        RecordingEntity::class,
        MeasurementEntity::class,
        AntennaEstimateEntity::class,
        DiagnosticsSessionEntity::class,
        DiagnosticsSnapshotEntity::class,
        DiagnosticsComparisonEntity::class,
        BatterySnapshotEntity::class,
        SensorSessionEntity::class,
        SensorEventEntity::class,
    ],
    version = 6,
    exportSchema = false
)
abstract class ParanoidDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun antennaEstimateDao(): AntennaEstimateDao
    abstract fun sessionDao(): SessionDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun comparisonDao(): ComparisonDao
    abstract fun batterySnapshotDao(): BatterySnapshotDao
    abstract fun sensorSessionDao(): SensorSessionDao
    abstract fun sensorEventDao(): SensorEventDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS usageaudit_battery_snapshots (
                        timestampMillis INTEGER NOT NULL PRIMARY KEY,
                        batteryPercent INTEGER NOT NULL,
                        chargingState TEXT NOT NULL,
                        batteryStatus TEXT,
                        batteryHealth TEXT
                    )"""
                )
            }
        }

        // Additive: adds (sessionId, sensorType) index on sensor_events so the
        // per-sensor GROUP BY count used by the session detail screen is an
        // index scan instead of a full scan of every event row. Without it,
        // very long sessions (millions of rows) left the detail screen stuck
        // loading. See sensor-logger session detail.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val started = System.currentTimeMillis()
                android.util.Log.d("Paranoid", "MIGRATION_5_6: creating sensor_events(sessionId, sensorType) index")
                try {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_sensor_events_sessionId_sensorType " +
                            "ON sensor_events(sessionId, sensorType)"
                    )
                    android.util.Log.d(
                        "Paranoid",
                        "MIGRATION_5_6: done in ${System.currentTimeMillis() - started} ms",
                    )
                } catch (t: Throwable) {
                    android.util.Log.e("Paranoid", "MIGRATION_5_6 FAILED", t)
                    throw t
                }
            }
        }

        // Additive: adds sensor_sessions and sensor_events tables.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sensor_sessions (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sensor_events (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        sessionId INTEGER NOT NULL,
                        elapsedMs INTEGER NOT NULL,
                        sensorType TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        accuracy INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES sensor_sessions(id) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sensor_events_sessionId_elapsedMs " +
                        "ON sensor_events(sessionId, elapsedMs)"
                )
            }
        }

        // Additive: adds netmap_antenna_estimates only.
        // No existing tables are altered. See PARANOID-f0x.2.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS netmap_antenna_estimates (
                        recordingId TEXT NOT NULL,
                        cellKey TEXT NOT NULL,
                        technology TEXT NOT NULL,
                        lat REAL NOT NULL,
                        lng REAL NOT NULL,
                        radiusM REAL NOT NULL,
                        sampleCount INTEGER NOT NULL,
                        strongestSignal TEXT NOT NULL,
                        isPciOnly INTEGER NOT NULL,
                        PRIMARY KEY(recordingId, cellKey),
                        FOREIGN KEY(recordingId) REFERENCES recordings(id) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_netmap_antenna_estimates_recordingId " +
                        "ON netmap_antenna_estimates(recordingId)"
                )
            }
        }

        fun getInstance(context: Context): ParanoidDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ParanoidDatabase::class.java,
                    "paranoid.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    )
                    .build().also { instance = it }
            }
    }
}
