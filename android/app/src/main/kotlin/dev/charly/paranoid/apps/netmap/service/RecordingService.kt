package dev.charly.paranoid.apps.netmap.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: implement recording service
        return START_STICKY
    }
}
