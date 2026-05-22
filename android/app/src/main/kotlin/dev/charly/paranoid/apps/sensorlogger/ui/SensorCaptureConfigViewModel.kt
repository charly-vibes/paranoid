package dev.charly.paranoid.apps.sensorlogger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfileStore
import dev.charly.paranoid.apps.sensorlogger.config.from
import kotlinx.coroutines.launch

/**
 * Android-bound view-model for `SensorCaptureConfigActivity`. Owns a
 * [SensorCaptureConfigState] working copy of the user's
 * [dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile] and exposes
 * its `workingProfile` flow for the UI to bind against.
 */
class SensorCaptureConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val store: RecordingProfileStore = RecordingProfileStore.from(app)
    val state: SensorCaptureConfigState = SensorCaptureConfigState(store)

    init {
        viewModelScope.launch { state.loadFromStore() }
    }

    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            state.save()
            onSaved()
        }
    }
}
