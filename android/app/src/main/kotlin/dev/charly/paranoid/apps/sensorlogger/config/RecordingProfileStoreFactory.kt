package dev.charly.paranoid.apps.sensorlogger.config

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Process-wide DataStore for the SensorLogger recording profile. Backed by a
 * single file `sensor_recording_profile.preferences_pb` in the app's private
 * data directory. The delegate ensures only one DataStore instance per file
 * is ever created.
 */
private val Context.sensorRecordingProfileDataStore by preferencesDataStore(
    name = "sensor_recording_profile",
)

/**
 * Service-locator factory for [RecordingProfileStore], consistent with how
 * `ParanoidDatabase.getInstance(context)` is used elsewhere in the project.
 */
fun RecordingProfileStore.Companion.from(context: Context): RecordingProfileStore =
    RecordingProfileStore(context.applicationContext.sensorRecordingProfileDataStore)
