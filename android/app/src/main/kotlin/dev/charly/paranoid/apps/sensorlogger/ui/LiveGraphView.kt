package dev.charly.paranoid.apps.sensorlogger.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.service.SensorSample

/**
 * Custom-`View` line graph for the live sensor stream.
 *
 * The pure geometry primitives live in [LiveGraphGeometry] (computeChannelStrokes,
 * placeholderStroke, stackBands, channelsOf). `onDraw` is a thin renderer that
 * walks the visible sensor map, lays out bands top-to-bottom, and paints each
 * channel in a distinct color via `Canvas.drawLine`.
 */
class LiveGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Channel colors. Index 0/1/2 → x/y/z (or the single scalar at index 0). */
    private val channelColors = intArrayOf(
        Color.parseColor("#FF6B6B"), // x — red
        Color.parseColor("#4ECDC4"), // y — teal
        Color.parseColor("#FFD93D"), // z — yellow
    )

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH_PX
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBBBBB")
        textSize = LABEL_TEXT_SIZE_PX
    }
    private val bandDividerPaint = Paint().apply {
        color = Color.parseColor("#222222")
        strokeWidth = 1f
    }

    private var data: Map<SensorType, List<SensorSample>> = emptyMap()

    /**
     * Replace the current rendered snapshot. Triggers an [invalidate] so the
     * View redraws on the next frame. Safe to call from any thread that
     * Android allows for `View.invalidate` (UI thread; `postInvalidate` from
     * background — collectors here run on the lifecycle scope, i.e. Main).
     */
    fun setData(snapshot: Map<SensorType, List<SensorSample>>) {
        data = snapshot
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visibleTypes = data.keys.toList().sortedBy { it.ordinal }
        if (visibleTypes.isEmpty()) return

        val bands = stackBands(
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            sensorCount = visibleTypes.size,
        )

        for ((index, type) in visibleTypes.withIndex()) {
            val band = bands[index]
            val samples = data[type] ?: continue
            drawBand(canvas, type, band, samples)
            // Divider line between bands (skip top of first).
            if (index > 0) {
                canvas.drawLine(band.left, band.top, band.right, band.top, bandDividerPaint)
            }
        }
    }

    /**
     * Render one sensor's [band]: per-channel strokes auto-scaled into the
     * band, plus a small label in the top-left corner. Empty / singleton
     * windows fall back to [placeholderStroke] so the user sees a stable
     * baseline rather than a flicker as data arrives.
     */
    private fun drawBand(
        canvas: Canvas,
        type: SensorType,
        band: Band,
        samples: List<SensorSample>,
    ) {
        val channels = channelsOf(type)
        if (samples.size < 2) {
            // Empty / singleton window: flat midline in the first channel color.
            strokePaint.color = channelColors[0]
            val p = placeholderStroke(band)
            canvas.drawLine(p.x1, p.y1, p.x2, p.y2, strokePaint)
        } else {
            for (c in 0 until channels) {
                strokePaint.color = channelColors[c % channelColors.size]
                for (s in computeChannelStrokes(samples, c, band)) {
                    canvas.drawLine(s.x1, s.y1, s.x2, s.y2, strokePaint)
                }
            }
        }
        val rateText = formatRateLabel(computeRollingHz(samples))
        canvas.drawText(
            "${sensorTypeLabel(type)}  $rateText",
            band.left + LABEL_PADDING_PX,
            band.top + LABEL_TEXT_SIZE_PX + LABEL_PADDING_PX,
            labelPaint,
        )
    }

    companion object {
        private const val STROKE_WIDTH_PX = 2f
        private const val LABEL_TEXT_SIZE_PX = 28f
        private const val LABEL_PADDING_PX = 8f
    }
}
