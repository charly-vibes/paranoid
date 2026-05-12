package dev.charly.paranoid.apps.netmap

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import dev.charly.paranoid.apps.netmap.model.AntennaEstimate

/**
 * Lightweight bottom-sheet replacement for the Material BottomSheetDialog.
 *
 * The project intentionally avoids depending on com.google.android.material
 * (heavy for one sheet), so this is a plain [Dialog] anchored to the bottom
 * of the window. Functionally equivalent for our purposes.
 */
object AntennaDetailSheet {

    /** Display threshold below which we badge as "low confidence". */
    private const val LOW_SAMPLE_THRESHOLD = 5

    fun show(context: Context, e: AntennaEstimate) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }

        val padPx = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padPx, padPx, padPx, padPx + padPx) // extra bottom for system bar
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        container.addView(label(context, "Antenna estimate", 18, true))
        container.addView(divider(context, padPx / 2))

        if (e.sampleCount < LOW_SAMPLE_THRESHOLD || e.isPciOnly) {
            val text = if (e.isPciOnly) {
                "Low confidence — identified by PCI only, may collide with another sector"
            } else {
                "Low confidence — only ${e.sampleCount} samples"
            }
            container.addView(label(context, text, 12, color = "#FFAA66"))
            container.addView(divider(context, padPx / 2))
        }

        container.addView(row(context, "Technology", e.technology.name))
        container.addView(row(context, "Cell key", e.cellKey))
        container.addView(row(context, "Strongest signal", e.strongestSignal.name))
        container.addView(row(context, "Samples", e.sampleCount.toString()))
        container.addView(row(context, "Estimated radius", "%.0f m".format(e.radiusM)))
        container.addView(row(context, "Estimated location",
            "%.5f, %.5f".format(e.location.lat, e.location.lng)))

        dialog.setContentView(container)
        dialog.show()
    }

    private fun label(
        context: Context, text: String, sizeSp: Int,
        bold: Boolean = false, color: String = "#FFFFFF"
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = sizeSp.toFloat()
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun row(context: Context, key: String, value: String): LinearLayout {
        val pad = (4 * context.resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad, 0, pad)
            addView(TextView(context).apply {
                text = key
                setTextColor(Color.parseColor("#888888"))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = value
                setTextColor(Color.parseColor("#FFFFFF"))
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
            })
        }
    }

    private fun divider(context: Context, height: Int) = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
    }
}
