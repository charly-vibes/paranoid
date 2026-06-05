package dev.charly.paranoid.apps.sensorlogger.config

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordingProfileStoreTest {

    @Test
    fun `round-trip preserves every per-sensor field for every SensorType`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())

        val mixed = SensorCaptureSetting(
            enabled = false,
            samplingRate = SamplingRate.Hz(50),
            visibleOnGraph = true,
        )
        val written = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.MAGNETIC_FIELD -> mixed
                    SensorType.ACCELEROMETER ->
                        SensorCaptureSetting(true, SamplingRate.Hz(200), true)
                    SensorType.GYROSCOPE ->
                        SensorCaptureSetting(true, SamplingRate.Hz(16), false)
                    SensorType.LINEAR_ACCELERATION ->
                        SensorCaptureSetting(false, SamplingRate.Auto, false)
                    else -> RecordingProfile.OffSetting
                }
            }
        )

        store.update(written)
        val read = store.flow.first()

        for (type in SensorType.values()) {
            assertEquals("setting for $type", written[type], read[type])
        }
    }

    @Test
    fun `defaults-dialog flag round-trips`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())

        assertEquals(false, store.hasSeenDefaultsDialog().first())
        store.markDefaultsDialogSeen()
        assertEquals(true, store.hasSeenDefaultsDialog().first())
    }

    @Test
    fun `legacy v0_10_0-rc_1 rate strings decode to equivalent SamplingRate`() = runBlocking {
        // Seed the DataStore with the OLD encoding (`NORMAL`/`UI`/`GAME`/`FASTEST`/`OFF`)
        // that v0.10.0-rc.1 would have written, then verify the new store reads
        // them as their SamplingRate equivalents.
        val ds = FakePreferencesDataStore()
        ds.edit { prefs ->
            for ((type, rateName) in mapOf(
                SensorType.ACCELEROMETER       to "NORMAL",
                SensorType.GYROSCOPE           to "GAME",
                SensorType.LINEAR_ACCELERATION to "FASTEST",
                SensorType.MAGNETIC_FIELD      to "UI",
                SensorType.PRESSURE            to "OFF",
            )) {
                val base = type.name.lowercase()
                prefs[booleanPreferencesKey("${base}_enabled")] = true
                prefs[stringPreferencesKey("${base}_rate")] = rateName
                prefs[booleanPreferencesKey("${base}_visible")] = true
            }
        }

        val store = RecordingProfileStore(ds)
        val read = store.flow.first()

        assertEquals(SamplingRate.Auto,    read[SensorType.ACCELEROMETER].samplingRate)
        assertEquals(SamplingRate.Hz(50),  read[SensorType.GYROSCOPE].samplingRate)
        assertEquals(SamplingRate.Hz(200), read[SensorType.LINEAR_ACCELERATION].samplingRate)
        assertEquals(SamplingRate.Hz(16),  read[SensorType.MAGNETIC_FIELD].samplingRate)
        assertEquals(SamplingRate.Off,     read[SensorType.PRESSURE].samplingRate)
    }

    @Test
    fun `update rewrites legacy rate strings in the new encoding`() = runBlocking {
        val ds = FakePreferencesDataStore()
        ds.edit { prefs ->
            val base = SensorType.GYROSCOPE.name.lowercase()
            prefs[booleanPreferencesKey("${base}_enabled")] = true
            prefs[stringPreferencesKey("${base}_rate")] = "GAME"
            prefs[booleanPreferencesKey("${base}_visible")] = true
        }
        val store = RecordingProfileStore(ds)

        // Decode through the store, then write it back unchanged.
        val read = store.flow.first()
        store.update(read)

        // Now the persisted value MUST be the new encoding.
        val raw = ds.data.first()
        val key = stringPreferencesKey("${SensorType.GYROSCOPE.name.lowercase()}_rate")
        assertEquals("HZ:50", raw[key])
    }

    @Test
    fun `malformed rate string falls back to per-sensor default`() = runBlocking {
        val ds = FakePreferencesDataStore()
        ds.edit { prefs ->
            val base = SensorType.ACCELEROMETER.name.lowercase()
            prefs[booleanPreferencesKey("${base}_enabled")] = true
            prefs[stringPreferencesKey("${base}_rate")] = "HZ:abc"
            prefs[booleanPreferencesKey("${base}_visible")] = true
        }
        val store = RecordingProfileStore(ds)
        val read = store.flow.first()
        // Accelerometer's default rate is Auto.
        assertEquals(SamplingRate.Auto, read[SensorType.ACCELEROMETER].samplingRate)
    }

    @Test
    fun `legacy _v2 acknowledgement does not suppress the new _v3 dialog`() = runBlocking {
        val ds = FakePreferencesDataStore()
        ds.edit { prefs ->
            // Pretend v0.10.0-rc.1 marked the old dialog as seen.
            prefs[booleanPreferencesKey("seen_capture_defaults_dialog_v2")] = true
        }
        val store = RecordingProfileStore(ds)
        assertFalse(
            "Bumped key must read false even when legacy _v2 is true",
            store.hasSeenDefaultsDialog().first(),
        )
    }
}
