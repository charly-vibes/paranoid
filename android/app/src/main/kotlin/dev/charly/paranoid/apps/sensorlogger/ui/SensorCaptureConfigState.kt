package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.config.SamplingRate
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
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
 * In addition to the profile itself, it tracks one [RateDraft] per
 * [SensorType] capturing the user-facing rate selector state: which mode
 * ([RateMode.Off] / [RateMode.Auto] / [RateMode.Custom]) is selected, the raw
 * text currently in the Custom Hz input, and whether that text parses to a
 * positive integer. The working profile only advances when the draft is valid
 * — invalid drafts gate `save()` via [canSave].
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

    private val _drafts = MutableStateFlow<Map<SensorType, RateDraft>>(
        SensorType.values().associateWith { type ->
            RateDraft.from(RecordingProfile.Default[type].samplingRate)
        }
    )
    val drafts: StateFlow<Map<SensorType, RateDraft>> = _drafts

    private val _canSave = MutableStateFlow(true)
    val canSave: StateFlow<Boolean> = _canSave

    /**
     * Initialise the working copy from the store's current value. Subsequent
     * external `store.update(...)` calls do NOT propagate into the working
     * copy until [loadFromStore] is called again.
     */
    suspend fun loadFromStore() {
        val profile = store.flow.first()
        _workingProfile.value = profile
        _drafts.value = SensorType.values().associateWith { type ->
            RateDraft.from(profile[type].samplingRate)
        }
        recomputeCanSave()
    }

    /**
     * Toggle the per-sensor `enabled` flag. When flipping from off→on while
     * the row's `samplingRate` is [SamplingRate.Off], the row defaults to
     * [SamplingRate.Auto] (single-interaction enablement, amendment EXEC-004).
     */
    fun setEnabled(type: SensorType, enabled: Boolean) {
        val current = _workingProfile.value[type]
        val newRate = if (
            enabled && !current.enabled && current.samplingRate == SamplingRate.Off
        ) {
            SamplingRate.Auto
        } else {
            current.samplingRate
        }
        mutate(type) { it.copy(enabled = enabled, samplingRate = newRate) }
        if (newRate != current.samplingRate) {
            updateDraft(type) { RateDraft.from(newRate) }
            recomputeCanSave()
        }
    }

    fun setVisibleOnGraph(type: SensorType, visible: Boolean) =
        mutate(type) { it.copy(visibleOnGraph = visible) }

    /**
     * Switch the row's rate mode. [RateMode.Off] and [RateMode.Auto] update
     * the working profile immediately. [RateMode.Custom] keeps the row's
     * previous valid [SamplingRate.Hz] (or seeds a placeholder) and waits for
     * [setCustomHzInput].
     */
    fun setRateMode(type: SensorType, mode: RateMode) {
        when (mode) {
            RateMode.Off -> {
                updateDraft(type) { RateDraft(mode = RateMode.Off, customInput = "", isValid = true) }
                mutate(type) { it.copy(samplingRate = SamplingRate.Off) }
            }
            RateMode.Auto -> {
                updateDraft(type) { RateDraft(mode = RateMode.Auto, customInput = "", isValid = true) }
                mutate(type) { it.copy(samplingRate = SamplingRate.Auto) }
            }
            RateMode.Custom -> {
                val current = _workingProfile.value[type].samplingRate
                val seedHz = (current as? SamplingRate.Hz)?.value ?: CUSTOM_HZ_PLACEHOLDER
                updateDraft(type) {
                    RateDraft(mode = RateMode.Custom, customInput = seedHz.toString(), isValid = true)
                }
                mutate(type) { it.copy(samplingRate = SamplingRate.Hz(seedHz)) }
            }
        }
        recomputeCanSave()
    }

    /**
     * Update the raw text of the row's Custom Hz input. Parsing rules:
     *  - empty / non-integer / non-positive → row invalid, working profile
     *    not advanced (last valid value is retained).
     *  - positive integer → working profile's `samplingRate = Hz(n)`.
     */
    fun setCustomHzInput(type: SensorType, raw: String) {
        val parsed = raw.toIntOrNull()?.takeIf { it > 0 }
        if (parsed != null) {
            updateDraft(type) { RateDraft(RateMode.Custom, raw, isValid = true) }
            mutate(type) { it.copy(samplingRate = SamplingRate.Hz(parsed)) }
        } else {
            updateDraft(type) { RateDraft(RateMode.Custom, raw, isValid = false) }
        }
        recomputeCanSave()
    }

    /** Persist the working profile to the store. Caller MUST check [canSave]. */
    suspend fun save() {
        check(_canSave.value) { "save() called while at least one row is invalid" }
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

    private inline fun updateDraft(type: SensorType, transform: (RateDraft) -> RateDraft) {
        val current = _drafts.value
        val existing = current[type] ?: RateDraft.from(SamplingRate.Off)
        _drafts.value = current + (type to transform(existing))
    }

    private fun recomputeCanSave() {
        _canSave.value = _drafts.value.values.all { it.isValid }
    }

    companion object {
        /** Seed value for the Custom Hz input when no prior Hz value exists. */
        const val CUSTOM_HZ_PLACEHOLDER: Int = 50
    }
}

/** UI-facing rate selector states (one of three). */
enum class RateMode { Off, Auto, Custom }

/**
 * Per-row rate selector state held by [SensorCaptureConfigState].
 *
 * `customInput` is the raw text in the Custom Hz field; `isValid` is `false`
 * exactly when the user is in [RateMode.Custom] and the input does not parse
 * as a positive integer.
 */
data class RateDraft(
    val mode: RateMode,
    val customInput: String,
    val isValid: Boolean,
) {
    companion object {
        fun from(rate: SamplingRate): RateDraft = when (rate) {
            SamplingRate.Off ->
                RateDraft(RateMode.Off, customInput = "", isValid = true)
            SamplingRate.Auto ->
                RateDraft(RateMode.Auto, customInput = "", isValid = true)
            is SamplingRate.Hz ->
                RateDraft(RateMode.Custom, customInput = rate.value.toString(), isValid = true)
        }
    }
}
