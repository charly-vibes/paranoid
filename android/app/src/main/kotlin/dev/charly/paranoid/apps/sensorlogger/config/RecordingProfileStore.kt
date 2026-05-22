package dev.charly.paranoid.apps.sensorlogger.config

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DataStore Preferences-backed persistence for the [RecordingProfile] and the
 * first-launch defaults-dialog bookkeeping flag.
 *
 * Per-sensor settings are flattened into three primitive keys per [SensorType]:
 *   `<name>_enabled : Boolean`
 *   `<name>_rate    : String`  (one of [SensorRateLevel] names)
 *   `<name>_visible : Boolean`
 *
 * Reads reconstruct a full profile by combining persisted keys with
 * [RecordingProfile.Default] as a per-sensor fallback. IO errors on the
 * upstream `Flow<Preferences>` emit [RecordingProfile.Default] rather than
 * propagating, so a corrupted preferences file does not crash callers.
 */
class RecordingProfileStore(
    private val dataStore: DataStore<Preferences>,
) {

    val flow: Flow<RecordingProfile> = dataStore.data
        .withIoFallback("Reading RecordingProfile failed; falling back to default")
        .map { prefs -> readProfile(prefs) }

    suspend fun update(profile: RecordingProfile) {
        dataStore.edit { prefs ->
            for (type in SensorType.values()) {
                val keys = keysFor(type)
                val setting = profile[type]
                prefs[keys.enabled] = setting.enabled
                prefs[keys.rate] = setting.rateLevel.name
                prefs[keys.visible] = setting.visibleOnGraph
            }
        }
    }

    fun hasSeenDefaultsDialog(): Flow<Boolean> = dataStore.data
        .withIoFallback("Reading defaults-dialog flag failed; treating as unseen")
        .map { prefs -> prefs[SEEN_DEFAULTS_DIALOG_KEY] ?: false }

    suspend fun markDefaultsDialogSeen() {
        dataStore.edit { prefs -> prefs[SEEN_DEFAULTS_DIALOG_KEY] = true }
    }

    private fun readProfile(prefs: Preferences): RecordingProfile {
        val settings = SensorType.values().associateWith { type ->
            val keys = keysFor(type)
            val default = RecordingProfile.Default[type]
            val rateName = prefs[keys.rate]
            val rate = rateName?.let { name ->
                runCatching { SensorRateLevel.valueOf(name) }.getOrNull()
            } ?: default.rateLevel
            SensorCaptureSetting(
                enabled = prefs[keys.enabled] ?: default.enabled,
                rateLevel = rate,
                visibleOnGraph = prefs[keys.visible] ?: default.visibleOnGraph,
            )
        }
        return RecordingProfile(settings)
    }

    private data class SensorKeys(
        val enabled: Preferences.Key<Boolean>,
        val rate: Preferences.Key<String>,
        val visible: Preferences.Key<Boolean>,
    )

    private fun keysFor(sensor: SensorType): SensorKeys {
        val base = sensor.name.lowercase()
        return SensorKeys(
            enabled = booleanPreferencesKey("${base}_enabled"),
            rate = stringPreferencesKey("${base}_rate"),
            visible = booleanPreferencesKey("${base}_visible"),
        )
    }

    /**
     * Swallow `IOException` from the upstream `Preferences` flow and emit an
     * empty Preferences instead (which `readProfile`/key lookups translate to
     * per-sensor defaults). Other exceptions propagate.
     */
    private fun Flow<Preferences>.withIoFallback(message: String): Flow<Preferences> =
        catch { cause ->
            if (cause is IOException) {
                Log.w(TAG, message, cause)
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }

    companion object {
        private const val TAG = "RecordingProfileStore"

        /** Bookkeeping key for the v2 defaults migration dialog. */
        val SEEN_DEFAULTS_DIALOG_KEY: Preferences.Key<Boolean> =
            booleanPreferencesKey("seen_capture_defaults_dialog_v2")
    }
}
