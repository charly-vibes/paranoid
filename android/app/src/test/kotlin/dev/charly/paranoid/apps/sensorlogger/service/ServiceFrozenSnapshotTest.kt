package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.config.FakePreferencesDataStore
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the service's session-frozen snapshot contract: the value of
 * `sessionProfile` set at `startRecording()` is unaffected by subsequent
 * `RecordingProfileStore.update(...)` calls until the session ends.
 *
 * Models the service pattern with a plain `MutableStateFlow<RecordingProfile?>`
 * so this can run as a pure JVM test (no Robolectric).
 */
class ServiceFrozenSnapshotTest {

    @Test
    fun `snapshot taken at session start is not affected by subsequent store updates`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        val initial = RecordingProfile.Default
        store.update(initial)

        val sessionProfile = MutableStateFlow<RecordingProfile?>(null)
        // simulate startRecording(): snapshot the store ONCE
        sessionProfile.value = store.flow.first()
        assertEquals(initial, sessionProfile.value)

        // mid-session: user edits the profile via the config screen
        val mutated = RecordingProfile(
            initial.settings.mapValues { (_, setting) ->
                setting.copy(enabled = false, rateLevel = SensorRateLevel.OFF)
            }
        )
        store.update(mutated)

        // sanity: the store actually saw the mutation
        assertEquals(mutated, store.flow.first())
        // contract: the session-frozen snapshot did not change
        assertEquals(initial, sessionProfile.value)
    }

    @Test
    fun `clearing the snapshot to null models session end`() {
        val sessionProfile = MutableStateFlow<RecordingProfile?>(null)
        sessionProfile.value = RecordingProfile.Default
        sessionProfile.value = null
        assertEquals(null, sessionProfile.value)
    }

    @Test
    fun `mid-session store update with magnetometer toggle does not alter visualize-only filter decision`() = runBlocking {
        val before = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.MAGNETIC_FIELD ->
                        SensorCaptureSetting(false, SensorRateLevel.NORMAL, true)
                    else -> RecordingProfile.OffSetting
                }
            }
        )
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(before)
        val sessionProfile = MutableStateFlow<RecordingProfile?>(store.flow.first())

        // User flips magnetometer on mid-session
        val after = RecordingProfile(
            before.settings.toMutableMap().apply {
                this[SensorType.MAGNETIC_FIELD] =
                    SensorCaptureSetting(true, SensorRateLevel.NORMAL, true)
            }
        )
        store.update(after)

        // Frozen decision still says: don't write magnetometer
        assertEquals(false, shouldWrite(sessionProfile.value, SensorType.MAGNETIC_FIELD))
    }
}
