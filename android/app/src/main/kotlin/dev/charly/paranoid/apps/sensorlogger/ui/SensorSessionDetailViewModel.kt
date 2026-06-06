package dev.charly.paranoid.apps.sensorlogger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.sensorlogger.data.exportSensorCsv
import dev.charly.paranoid.apps.sensorlogger.data.exportSensorJson
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.model.aggregateSensorCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SensorSessionDetailViewModel(app: Application) : AndroidViewModel(app) {

    sealed class State {
        object Loading : State()
        object NotFound : State()
        data class Loaded(
            val session: SensorSessionEntity,
            val totalEvents: Int,
            val bySensor: Map<SensorType, Int>,
        ) : State()
    }

    private val db = ParanoidDatabase.getInstance(app)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    enum class ExportFormat(val extension: String, val mimeType: String) {
        CSV("csv", "text/csv"),
        JSON("json", "application/json"),
    }

    data class ExportPayload(val content: String, val filename: String, val mimeType: String)

    private val _exports = MutableSharedFlow<ExportPayload>(extraBufferCapacity = 1)
    val exports: SharedFlow<ExportPayload> = _exports.asSharedFlow()

    private var sessionId: Long = -1L

    fun load(id: Long) {
        sessionId = id
        viewModelScope.launch {
            val session = db.sensorSessionDao().getById(id)
            if (session == null) {
                _state.value = State.NotFound
                return@launch
            }
            val rows = db.sensorEventDao().countsBySensorType(id)
            val bySensor = aggregateSensorCounts(rows.map { it.sensorType to it.count })
            val total = bySensor.values.sum()
            _state.value = State.Loaded(session, total, bySensor)
        }
    }

    fun markAsClosed() {
        viewModelScope.launch {
            val session = db.sensorSessionDao().getById(sessionId) ?: return@launch
            if (session.endedAt != null) return@launch
            db.sensorSessionDao().update(session.copy(endedAt = System.currentTimeMillis()))
            load(sessionId)
        }
    }

    fun requestExport(format: ExportFormat) {
        viewModelScope.launch(Dispatchers.Default) {
            val session = db.sensorSessionDao().getById(sessionId) ?: return@launch
            val events = db.sensorEventDao().getBySession(sessionId)
            val content = when (format) {
                ExportFormat.CSV -> exportSensorCsv(session, events)
                ExportFormat.JSON -> exportSensorJson(session, events)
            }
            val filename = "sensor_session_${session.id}.${format.extension}"
            _exports.emit(ExportPayload(content, filename, format.mimeType))
        }
    }

    fun delete() {
        viewModelScope.launch {
            db.sensorSessionDao().deleteById(sessionId)
            _deleted.tryEmit(Unit)
        }
    }
}
