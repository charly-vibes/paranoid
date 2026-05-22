package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.FakePreferencesDataStore
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigViewModelTest {

    @Test
    fun `working profile initializes from store on first load`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val vm = SensorCaptureConfigState(store)
        vm.loadFromStore()
        assertEquals(RecordingProfile.Default, vm.workingProfile.value)
    }

    @Test
    fun `setEnabled flips only that sensor's enabled flag in the working profile`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val vm = SensorCaptureConfigState(store)
        vm.loadFromStore()

        vm.setEnabled(SensorType.ACCELEROMETER, false)

        val accel = vm.workingProfile.value[SensorType.ACCELEROMETER]
        assertFalse(accel.enabled)
        // Other fields preserved
        assertEquals(SensorRateLevel.NORMAL, accel.rateLevel)
        assertTrue(accel.visibleOnGraph)
        // Other sensors unchanged
        assertEquals(
            RecordingProfile.Default[SensorType.GYROSCOPE],
            vm.workingProfile.value[SensorType.GYROSCOPE],
        )
    }

    @Test
    fun `setRate updates only that sensor's rate`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val vm = SensorCaptureConfigState(store)
        vm.loadFromStore()

        vm.setRate(SensorType.GYROSCOPE, SensorRateLevel.FASTEST)

        assertEquals(SensorRateLevel.FASTEST, vm.workingProfile.value[SensorType.GYROSCOPE].rateLevel)
    }

    @Test
    fun `setVisibleOnGraph updates only that sensor's visibility`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val vm = SensorCaptureConfigState(store)
        vm.loadFromStore()

        vm.setVisibleOnGraph(SensorType.MAGNETIC_FIELD, true)

        assertTrue(vm.workingProfile.value[SensorType.MAGNETIC_FIELD].visibleOnGraph)
    }

    @Test
    fun `save toggling Record off for accelerometer persists accel enabled=false to the store`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val vm = SensorCaptureConfigState(store)
        vm.loadFromStore()

        vm.setEnabled(SensorType.ACCELEROMETER, false)
        vm.save()

        val persisted = store.flow.first()
        assertFalse(persisted[SensorType.ACCELEROMETER].enabled)
    }

    @Test
    fun `setEnabled on an already-loaded profile does not require a re-load`() = runBlocking {
        // Working copy must be a snapshot — store updates after loadFromStore
        // must not silently overwrite in-progress edits.
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val vm = SensorCaptureConfigState(store)
        vm.loadFromStore()

        val mutatedExternally = RecordingProfile(
            RecordingProfile.Default.settings.mapValues { (_, s) ->
                s.copy(rateLevel = SensorRateLevel.FASTEST)
            }
        )
        store.update(mutatedExternally)

        vm.setEnabled(SensorType.ACCELEROMETER, false)
        // Working copy reflects local edit on top of the snapshot,
        // not the external mutation.
        assertEquals(SensorRateLevel.NORMAL, vm.workingProfile.value[SensorType.ACCELEROMETER].rateLevel)
        assertFalse(vm.workingProfile.value[SensorType.ACCELEROMETER].enabled)
    }
}
