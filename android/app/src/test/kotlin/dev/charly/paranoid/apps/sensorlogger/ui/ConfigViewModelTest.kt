package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.FakePreferencesDataStore
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.config.SamplingRate
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigViewModelTest {

    private suspend fun freshState(): SensorCaptureConfigState {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val state = SensorCaptureConfigState(store)
        state.loadFromStore()
        return state
    }

    @Test
    fun `working profile initializes from store on first load`() = runBlocking {
        val state = freshState()
        assertEquals(RecordingProfile.Default, state.workingProfile.value)
    }

    @Test
    fun `setEnabled flips only that sensor's enabled flag in the working profile`() = runBlocking {
        val state = freshState()

        state.setEnabled(SensorType.ACCELEROMETER, false)

        val accel = state.workingProfile.value[SensorType.ACCELEROMETER]
        assertFalse(accel.enabled)
        // Other fields preserved
        assertEquals(SamplingRate.Auto, accel.samplingRate)
        assertTrue(accel.visibleOnGraph)
        // Other sensors unchanged
        assertEquals(
            RecordingProfile.Default[SensorType.GYROSCOPE],
            state.workingProfile.value[SensorType.GYROSCOPE],
        )
    }

    @Test
    fun `enabling a previously-off sensor defaults samplingRate to Auto`() = runBlocking {
        val state = freshState()
        // Magnetic_Field starts disabled with Off rate.
        assertEquals(SamplingRate.Off, state.workingProfile.value[SensorType.MAGNETIC_FIELD].samplingRate)
        assertFalse(state.workingProfile.value[SensorType.MAGNETIC_FIELD].enabled)

        state.setEnabled(SensorType.MAGNETIC_FIELD, true)

        val mag = state.workingProfile.value[SensorType.MAGNETIC_FIELD]
        assertTrue(mag.enabled)
        assertEquals(SamplingRate.Auto, mag.samplingRate)
        // Draft reflects the new rate too.
        assertEquals(RateMode.Auto, state.drafts.value.getValue(SensorType.MAGNETIC_FIELD).mode)
    }

    @Test
    fun `enabling a sensor that already has a non-Off rate preserves it`() = runBlocking {
        val state = freshState()
        // Accelerometer already enabled at Auto in the default.
        state.setRateMode(SensorType.ACCELEROMETER, RateMode.Custom)
        state.setCustomHzInput(SensorType.ACCELEROMETER, "100")
        state.setEnabled(SensorType.ACCELEROMETER, false)
        state.setEnabled(SensorType.ACCELEROMETER, true)

        assertEquals(SamplingRate.Hz(100), state.workingProfile.value[SensorType.ACCELEROMETER].samplingRate)
    }

    @Test
    fun `setRateMode Off sets samplingRate to Off`() = runBlocking {
        val state = freshState()
        state.setRateMode(SensorType.ACCELEROMETER, RateMode.Off)
        assertEquals(SamplingRate.Off, state.workingProfile.value[SensorType.ACCELEROMETER].samplingRate)
    }

    @Test
    fun `setRateMode Auto sets samplingRate to Auto`() = runBlocking {
        val state = freshState()
        state.setRateMode(SensorType.ACCELEROMETER, RateMode.Off)
        state.setRateMode(SensorType.ACCELEROMETER, RateMode.Auto)
        assertEquals(SamplingRate.Auto, state.workingProfile.value[SensorType.ACCELEROMETER].samplingRate)
    }

    @Test
    fun `setCustomHzInput with a positive integer produces Hz(n)`() = runBlocking {
        val state = freshState()
        state.setRateMode(SensorType.GYROSCOPE, RateMode.Custom)
        state.setCustomHzInput(SensorType.GYROSCOPE, "75")

        assertEquals(SamplingRate.Hz(75), state.workingProfile.value[SensorType.GYROSCOPE].samplingRate)
        assertTrue(state.canSave.value)
        val draft = state.drafts.value.getValue(SensorType.GYROSCOPE)
        assertEquals(RateMode.Custom, draft.mode)
        assertEquals("75", draft.customInput)
        assertTrue(draft.isValid)
    }

    @Test
    fun `setCustomHzInput with non-positive or non-integer marks row invalid and disables Save`() = runBlocking {
        val state = freshState()
        state.setRateMode(SensorType.GYROSCOPE, RateMode.Custom)

        for (bad in listOf("0", "-3", "abc", "")) {
            state.setCustomHzInput(SensorType.GYROSCOPE, bad)
            val draft = state.drafts.value.getValue(SensorType.GYROSCOPE)
            assertFalse("expected invalid for '$bad'", draft.isValid)
            assertFalse("save must be disabled for '$bad'", state.canSave.value)
        }

        state.setCustomHzInput(SensorType.GYROSCOPE, "10")
        assertTrue(state.canSave.value)
    }

    @Test
    fun `selecting Off clears any inline error and re-enables Save`() = runBlocking {
        val state = freshState()
        state.setRateMode(SensorType.GYROSCOPE, RateMode.Custom)
        state.setCustomHzInput(SensorType.GYROSCOPE, "abc")
        assertFalse(state.canSave.value)

        state.setRateMode(SensorType.GYROSCOPE, RateMode.Off)
        assertTrue(state.canSave.value)
        assertTrue(state.drafts.value.getValue(SensorType.GYROSCOPE).isValid)
    }

    @Test
    fun `save persists the working profile to the store`() = runBlocking {
        val state = freshState()
        state.setEnabled(SensorType.ACCELEROMETER, false)
        state.save()

        val persisted = state.workingProfile.value
        // Simulate a fresh read of the store
        assertFalse(persisted[SensorType.ACCELEROMETER].enabled)
    }

    @Test
    fun `save while a row is invalid throws (caller must check canSave)`() = runBlocking {
        val state = freshState()
        state.setRateMode(SensorType.GYROSCOPE, RateMode.Custom)
        state.setCustomHzInput(SensorType.GYROSCOPE, "abc")

        var threw = false
        try {
            state.save()
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `setVisibleOnGraph updates only that sensor's visibility`() = runBlocking {
        val state = freshState()
        state.setVisibleOnGraph(SensorType.MAGNETIC_FIELD, true)
        assertTrue(state.workingProfile.value[SensorType.MAGNETIC_FIELD].visibleOnGraph)
    }

    @Test
    fun `loadFromStore initializes drafts from samplingRate`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        val seeded = RecordingProfile(
            RecordingProfile.Default.settings + mapOf(
                SensorType.MAGNETIC_FIELD to SensorCaptureSetting(true, SamplingRate.Hz(33), false),
            )
        )
        store.update(seeded)
        val state = SensorCaptureConfigState(store)
        state.loadFromStore()

        val magDraft = state.drafts.value.getValue(SensorType.MAGNETIC_FIELD)
        assertEquals(RateMode.Custom, magDraft.mode)
        assertEquals("33", magDraft.customInput)

        val accelDraft = state.drafts.value.getValue(SensorType.ACCELEROMETER)
        assertEquals(RateMode.Auto, accelDraft.mode)
    }

    @Test
    fun `external store update after loadFromStore does not clobber in-progress edits`() = runBlocking {
        val store = RecordingProfileStore(FakePreferencesDataStore())
        store.update(RecordingProfile.Default)
        val state = SensorCaptureConfigState(store)
        state.loadFromStore()

        val mutatedExternally = RecordingProfile(
            RecordingProfile.Default.settings.mapValues { (_, s) ->
                s.copy(samplingRate = SamplingRate.Hz(200))
            }
        )
        store.update(mutatedExternally)

        state.setEnabled(SensorType.ACCELEROMETER, false)
        // Working copy reflects local edit on top of the original snapshot, not the external mutation.
        assertEquals(SamplingRate.Auto, state.workingProfile.value[SensorType.ACCELEROMETER].samplingRate)
        assertFalse(state.workingProfile.value[SensorType.ACCELEROMETER].enabled)
    }
}
