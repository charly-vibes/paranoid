package dev.charly.paranoid.apps.screentime.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat

/**
 * Adds/removes and updates the screen-time [OverlayProgressView] via [WindowManager].
 *
 * The window is a `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` so it
 * floats over all apps without ever intercepting input. It is positioned just below the status
 * bar. All operations are guarded so a missing/revoked SYSTEM_ALERT_WINDOW permission degrades
 * gracefully instead of crashing.
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = statusBarHeightPx()
        }

        try {
            windowManager.addView(bar, params)
            view = bar
            // Refine the offset using real insets once the window is attached.
            bar.post { applyStatusBarInset(bar, params) }
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

    private fun applyStatusBarInset(bar: OverlayProgressView, params: WindowManager.LayoutParams) {
        val insetTop = bar.rootWindowInsets
            ?.let { WindowInsetsCompat.toWindowInsetsCompat(it) }
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: return
        if (insetTop > 0 && insetTop != params.y) {
            params.y = insetTop
            try {
                windowManager.updateViewLayout(bar, params)
            } catch (_: Exception) {
                // Ignore; the resource/fallback offset already applied at add time.
            }
        }
    }

    private fun barHeightPx(): Int = (BAR_HEIGHT_DP * appContext.resources.displayMetrics.density).toInt()

    /**
     * Status bar height fallback used before insets are available. Prefers the platform resource,
     * then a 24 dp constant (API ≤ 29 may not report overlay-window insets reliably).
     */
    private fun statusBarHeightPx(): Int {
        val resourceId = appContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) return appContext.resources.getDimensionPixelSize(resourceId)
        return (DEFAULT_STATUS_BAR_DP * appContext.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val BAR_HEIGHT_DP = 4
        private const val DEFAULT_STATUS_BAR_DP = 24

        /** Whether the overlay permission is currently granted. */
        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
