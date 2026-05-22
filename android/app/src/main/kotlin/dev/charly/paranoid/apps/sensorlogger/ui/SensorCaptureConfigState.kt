package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Headless, pure-coroutine companion to `SensorCaptureConfigViewModel`. Holds
 * a mutable working copy of the [RecordingProfile] independent of the live
 * [RecordingProfileStore] so the user's in-progress edits are never clobbered
 * by an external `store.update(...)` mid-edit.
 *
 * `save()` is the only path that propagates the working copy back into the
 * store. The view-model layer wraps this class for lifecycle integration; this
 * class itself has no Android dependencies and is fully unit-testable.
 */
class SensorCaptureConfigState(
    private val store: RecordingProfileStore,
) {
    private val _workingProfile = MutableStateFlow(RecordingProfile.Default)
    val workingProfile: StateFlow<RecordingProfile> = _workingProfile

    /**
     * Initialise the working copy from the store's current value. Subsequent
     * external `store.update(...)` calls do NOT propagate into the working
     * copy until [loadFromStore] is called again.
     */
    suspend fun loadFromStore() {
        _workingProfile.value = store.flow.first()
    }

    fun setEnabled(type: SensorType, enabled: Boolean) =
        mutate(type) { it.copy(enabled = enabled) }

    fun setRate(type: SensorType, rate: SensorRateLevel) =
        mutate(type) { it.copy(rateLevel = rate) }

    fun setVisibleOnGraph(type: SensorType, visible: Boolean) =
        mutate(type) { it.copy(visibleOnGraph = visible) }

    /** Persist the working profile to the store. */
    suspend fun save() {
        store.update(_workingProfile.value)
    }

    private inline fun mutate(
        type: SensorType,
        transform: (SensorCaptureSetting) -> SensorCaptureSetting,
    ) {
        val current = _workingProfile.value
        val updated = current.copy(
            settings = current.settings + (type to transform(current[type])),
        )
        _workingProfile.value = updated
    }
}
