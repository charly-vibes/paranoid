package dev.charly.paranoid.apps.screentime.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/**
 * Adds/removes and updates the screen-time [OverlayProgressView] via [WindowManager].
 *
 * The window is a `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` so it
 * floats over all apps without ever intercepting input. It is pinned to the very top edge of the
 * screen (over the status-bar area) so it never covers app content. All operations are guarded so
 * a missing/revoked SYSTEM_ALERT_WINDOW permission degrades gracefully instead of crashing.
 */
class OverlayManager(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: OverlayProgressView? = null

    val isShowing: Boolean get() = view != null

    /** Adds the overlay bar if permitted and not already shown. */
    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(appContext)) return

        val bar = OverlayProgressView(appContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeightPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Pin to the very top edge of the screen (over the status bar), so the bar never
            // overlaps app content below it.
            gravity = Gravity.TOP or Gravity.START
            y = 0
        }

        try {
            windowManager.addView(bar, params)
            view = bar
        } catch (_: Exception) {
            view = null
        }
    }

    fun setFillFraction(fraction: Float) {
        view?.setFillFraction(fraction)
    }

    /** Removes the overlay bar if present. Safe to call repeatedly. */
    fun hide() {
        val bar = view ?: return
        view = null
        try {
            windowManager.removeView(bar)
        } catch (_: Exception) {
            // View may already be detached (e.g. permission revoked); ignore.
        }
    }

    private fun barHeightPx(): Int = (BAR_HEIGHT_DP * appContext.resources.displayMetrics.density).toInt()

    companion object {
        private const val BAR_HEIGHT_DP = 4

        /** Whether the overlay permission is currently granted. */
        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
