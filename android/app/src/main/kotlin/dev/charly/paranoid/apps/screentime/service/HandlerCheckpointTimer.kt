package dev.charly.paranoid.apps.screentime.service

import android.os.Handler
import dev.charly.paranoid.apps.screentime.CheckpointScheduler

/** [CheckpointScheduler.Timer] backed by an Android [Handler.postDelayed]. */
class HandlerCheckpointTimer(private val handler: Handler) : CheckpointScheduler.Timer {
    private var runnable: Runnable? = null

    override fun schedule(delayMillis: Long, action: () -> Unit) {
        cancel()
        val r = Runnable {
            runnable = null
            action()
        }
        runnable = r
        handler.postDelayed(r, delayMillis)
    }

    override fun cancel() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }
}
