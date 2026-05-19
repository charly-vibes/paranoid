package dev.charly.paranoid.apps.sensorlogger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SensorLoggerViewModel(app: Application) : AndroidViewModel(app) {

    sealed class IdleSummary {
        object Loading : IdleSummary()
        object Empty : IdleSummary()
        data class Latest(val durationSec: Long, val eventCount: Int) : IdleSummary()
    }

    private val db = ParanoidDatabase.getInstance(app)

    /**
     * Continuously reflects the most-recently-completed session. Auto-refreshes
     * whenever the sessions table changes (e.g. when `SensorRecordingService`
     * commits `endedAt` at the end of a stop sequence). Solves the stop-race
     * bug: the summary cannot show a stale prior session because every DB
     * commit triggers a fresh emission.
     */
    val idleSummary: StateFlow<IdleSummary> = db.sensorSessionDao().observeAll()
        .map { sessions ->
            val latest = sessions.firstOrNull { it.endedAt != null }
            if (latest == null) {
                IdleSummary.Empty
            } else {
                val durationSec = (latest.endedAt!! - latest.startedAt) / 1000
                val count = db.sensorEventDao().countForSession(latest.id)
                IdleSummary.Latest(durationSec, count)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdleSummary.Loading)
}
