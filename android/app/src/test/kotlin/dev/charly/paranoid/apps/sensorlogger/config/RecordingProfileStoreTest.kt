package dev.charly.paranoid.apps.sensorlogger.config

import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingProfileStoreTest {

    @Test
    fun `round-trip preserves every per-sensor field for every SensorType`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())

        val mixed = SensorCaptureSetting(
            enabled = false,
            rateLevel = SensorRateLevel.GAME,
            visibleOnGraph = true,
        )
        val written = RecordingProfile(
            SensorType.values().associateWith { type ->
                when (type) {
                    SensorType.MAGNETIC_FIELD -> mixed
                    SensorType.ACCELEROMETER ->
                        SensorCaptureSetting(true, SensorRateLevel.FASTEST, true)
                    SensorType.GYROSCOPE ->
                        SensorCaptureSetting(true, SensorRateLevel.UI, false)
                    SensorType.LINEAR_ACCELERATION ->
                        SensorCaptureSetting(false, SensorRateLevel.NORMAL, false)
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
}
