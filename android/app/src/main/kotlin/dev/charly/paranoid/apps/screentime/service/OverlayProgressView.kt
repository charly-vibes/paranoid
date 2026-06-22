package dev.charly.paranoid.apps.screentime.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * A thin horizontal progress bar drawn over all apps. Display-only; never interactive (the host
 * window uses FLAG_NOT_TOUCHABLE). [setFillFraction] sets how far the bar is filled, 0..1.
 */
@SuppressLint("ViewConstructor")
class OverlayProgressView(context: Context) : View(context) {

    private var fillFraction: Float = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3DDC84")
    }

    fun setFillFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped != fillFraction) {
            fillFraction = clamped
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, trackPaint)
        canvas.drawRect(0f, 0f, w * fillFraction, h, fillPaint)
    }
}
