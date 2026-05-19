package dev.charly.paranoid.apps.sensorlogger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import kotlinx.coroutines.flow.Flow

class SensorSessionsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ParanoidDatabase.getInstance(app)

    val sessions: Flow<List<SensorSessionEntity>> = db.sensorSessionDao().observeAll()
}
