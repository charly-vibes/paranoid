package dev.charly.paranoid.apps.sensorlogger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.sensorlogger.data.ExportSampling
import dev.charly.paranoid.apps.sensorlogger.data.SensorExportFilter
import dev.charly.paranoid.apps.sensorlogger.data.SensorExportFormat
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.sensorlogger.data.estimateExportBytes
import dev.charly.paranoid.apps.sensorlogger.data.estimateSampledCount
import dev.charly.paranoid.apps.sensorlogger.data.writeSensorCsvEvent
import dev.charly.paranoid.apps.sensorlogger.data.writeSensorCsvHeader
import dev.charly.paranoid.apps.sensorlogger.data.writeSensorJsonEnd
import dev.charly.paranoid.apps.sensorlogger.data.writeSensorJsonEvent
import dev.charly.paranoid.apps.sensorlogger.data.writeSensorJsonStart
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.model.aggregateSensorCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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

    sealed class ExportState {
        object Idle : ExportState()
        data class Running(
            val format: SensorExportFormat,
            val processedEvents: Long,
            val totalEvents: Long,
        ) : ExportState()

        data class Ready(val file: File, val mimeType: String) : ExportState()
        data class Failed(val message: String) : ExportState()
    }

    private val db = ParanoidDatabase.getInstance(app)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    private var sessionId: Long = -1L
    private var exportJob: Job? = null

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

    fun requestExport(
        format: SensorExportFormat,
        includeTypes: Set<SensorType>,
        sampling: ExportSampling,
    ) {
        if (exportJob?.isActive == true) return
        if (includeTypes.isEmpty()) {
            _exportState.value = ExportState.Failed("Select at least one sensor")
            return
        }
        val typeNames = includeTypes.map { it.name }
        exportJob = viewModelScope.launch(Dispatchers.IO) {
            val session = db.sensorSessionDao().getById(sessionId)
            if (session == null) {
                _exportState.value = ExportState.Failed("Session not found")
                return@launch
            }
            if (session.endedAt == null) {
                _exportState.value = ExportState.Failed("Close this session before exporting")
                return@launch
            }

            val total = db.sensorEventDao().countForSessionTypesLong(sessionId, typeNames)
            val dir = File(getApplication<Application>().cacheDir, "exports")
            dir.mkdirs()
            cleanStaleExports(dir)

            // Disk-space gate uses the *sampled* output estimate, not the raw
            // selected count — otherwise heavy downsampling could be falsely
            // rejected on low-space devices.
            val typeNameSet = typeNames.toSet()
            val selectedCounts = db.sensorEventDao().countsBySensorType(sessionId)
                .filter { it.sensorType in typeNameSet }
                .map { it.count }
            val sampledEstimateCount = estimateSampledCount(
                sampling, selectedCounts, session.endedAt?.minus(session.startedAt),
            )
            val estimate = estimateExportBytes(format, sampledEstimateCount)
            if (estimate + SPACE_MARGIN_BYTES > dir.usableSpace) {
                _exportState.value = ExportState.Failed("Not enough free space for this export")
                return@launch
            }

            val finalFile = File(dir, "sensor_session_${session.id}.${format.extension}")
            val tmpFile = File(dir, "${finalFile.name}.tmp")
            _exportState.value = ExportState.Running(format, 0, total)

            val filter = SensorExportFilter(includeTypes = emptySet(), sampling = sampling)
            var success = false
            try {
                tmpFile.bufferedWriter(bufferSize = 64 * 1024).use { writer ->
                    var scanned = 0L
                    var kept = 0L
                    var first = true
                    var lastElapsed = Long.MIN_VALUE
                    var lastId = Long.MIN_VALUE

                    when (format) {
                        SensorExportFormat.CSV -> writeSensorCsvHeader(writer)
                        SensorExportFormat.JSON -> writeSensorJsonStart(session, writer)
                    }

                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val page = db.sensorEventDao().getBySessionAfterTypes(
                            sessionId, typeNames, lastElapsed, lastId, PAGE_SIZE,
                        )
                        if (page.isEmpty()) break

                        for (e in page) {
                            if (!filter.accept(e)) continue
                            when (format) {
                                SensorExportFormat.CSV -> writeSensorCsvEvent(e, writer)
                                SensorExportFormat.JSON -> {
                                    writeSensorJsonEvent(e, first, writer)
                                    first = false
                                }
                            }
                            kept++
                        }

                        val last = page.last()
                        lastElapsed = last.elapsedMs
                        lastId = last.id
                        scanned += page.size
                        _exportState.value = ExportState.Running(format, scanned, total)

                        if (page.size < PAGE_SIZE) break
                    }

                    if (format == SensorExportFormat.JSON) {
                        writeSensorJsonEnd(kept, sampling, writer)
                    }
                    currentCoroutineContext().ensureActive()
                }

                currentCoroutineContext().ensureActive()
                if (finalFile.exists()) finalFile.delete()
                check(tmpFile.renameTo(finalFile)) { "Could not finalize export file" }
                success = true
                _exportState.value = ExportState.Ready(finalFile, format.mimeType)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _exportState.value = ExportState.Idle
                    throw e
                }
                _exportState.value = ExportState.Failed(e.message ?: "Export failed")
            } finally {
                if (!success) tmpFile.delete()
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    /** Called after a terminal state was handled; clears only that exact state. */
    fun exportConsumed(consumed: ExportState) {
        _exportState.update { current -> if (current === consumed) ExportState.Idle else current }
    }

    fun delete() {
        viewModelScope.launch {
            db.sensorSessionDao().deleteById(sessionId)
            _deleted.tryEmit(Unit)
        }
    }

    private fun cleanStaleExports(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_EXPORT_MS
        dir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) f.delete()
        }
    }

    companion object {
        private const val PAGE_SIZE = 4000
        private const val SPACE_MARGIN_BYTES = 16L * 1024 * 1024 // 16 MB headroom
        private const val STALE_EXPORT_MS = 24L * 60 * 60 * 1000 // 24h
    }
}
