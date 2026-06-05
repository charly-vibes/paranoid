package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import dev.charly.paranoid.apps.sensorlogger.service.SensorSample

/**
 * Pure geometry primitives for `LiveGraphView`. Extracted so the rendering
 * math (auto-scale, band layout, channel mapping) can be unit-tested on the
 * JVM without instantiating an Android `Canvas`. The View calls these helpers
 * inside `onDraw` and feeds the results to `Canvas.drawLines` / `Paint`.
 *
 * Per ticket-5 amendment EXEC-002: the contract is behavioral — segments
 * must be finite and within the band's bounds rectangle, regardless of how
 * the View ultimately rasterizes them.
 */

/** Rectangular region on the View canvas occupied by one sensor's plot. */
data class Band(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
) {
    val midY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** One line segment in canvas (View) coordinates. */
data class StrokeSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/** Number of channels rendered for [type] (3 for vector sensors, 1 for scalars). */
fun channelsOf(type: SensorType): Int = when (type) {
    SensorType.ACCELEROMETER,
    SensorType.GYROSCOPE,
    SensorType.LINEAR_ACCELERATION,
    SensorType.GRAVITY,
    SensorType.ROTATION_VECTOR,
    SensorType.MAGNETIC_FIELD -> 3
    SensorType.PRESSURE,
    SensorType.LIGHT,
    SensorType.PROXIMITY -> 1
}

/**
 * Compute the (N-1) line segments for one channel of [samples] mapped into
 * [band]. X is mapped uniformly across `[band.left .. band.right]`; Y is
 * auto-scaled from the channel's visible min/max into `[band.top .. band.bottom]`.
 * When the visible value range is zero (constant channel), every Y collapses
 * onto [Band.midY] — avoiding division-by-zero and matching the placeholder
 * convention for empty windows.
 *
 * Returns an empty list when [samples] has fewer than 2 entries — the View
 * is expected to substitute [placeholderStroke] in that case.
 */
fun computeChannelStrokes(
    samples: List<SensorSample>,
    channel: Int,
    band: Band,
): List<StrokeSegment> {
    if (samples.size < 2) return emptyList()

    // Auto-scale Y from the per-channel visible range.
    var minV = Float.POSITIVE_INFINITY
    var maxV = Float.NEGATIVE_INFINITY
    for (s in samples) {
        val v = s.values.getOrNull(channel) ?: continue
        if (!v.isFinite()) continue
        if (v < minV) minV = v
        if (v > maxV) maxV = v
    }
    val constant = !minV.isFinite() || !maxV.isFinite() || (maxV - minV == 0f)

    fun yOf(v: Float): Float {
        if (constant) return band.midY
        val norm = (v - minV) / (maxV - minV)
        // Y grows downward on Android Canvas; invert so higher values plot higher.
        return band.bottom - norm * band.height
    }

    val n = samples.size
    val stepX = if (n > 1) band.width / (n - 1).toFloat() else 0f
    val out = ArrayList<StrokeSegment>(n - 1)
    for (i in 0 until n - 1) {
        val v1 = samples[i].values.getOrNull(channel) ?: continue
        val v2 = samples[i + 1].values.getOrNull(channel) ?: continue
        // Skip segments touching a non-finite reading so no NaN/Inf coordinate
        // reaches Canvas.drawLine (the min/max scan already ignores them).
        if (!v1.isFinite() || !v2.isFinite()) continue
        out.add(
            StrokeSegment(
                x1 = band.left + stepX * i,
                y1 = yOf(v1),
                x2 = band.left + stepX * (i + 1),
                y2 = yOf(v2),
            )
        )
    }
    return out
}

/** Flat horizontal line spanning [band] at its vertical midpoint. */
fun placeholderStroke(band: Band): StrokeSegment =
    StrokeSegment(band.left, band.midY, band.right, band.midY)

/**
 * Stack [sensorCount] equal-height bands vertically across [viewWidth] ×
 * [viewHeight]. Returns an empty list when [sensorCount] is 0 (caller is
 * expected to draw an empty-state overlay instead).
 */
fun stackBands(viewWidth: Float, viewHeight: Float, sensorCount: Int): List<Band> {
    if (sensorCount <= 0) return emptyList()
    val h = viewHeight / sensorCount
    return List(sensorCount) { i ->
        Band(
            top = i * h,
            bottom = (i + 1) * h,
            left = 0f,
            right = viewWidth,
        )
    }
}

/**
 * Intersect the [snapshot]'s sensor set with sensors marked
 * `visibleOnGraph == true` in the session-frozen [sessionProfile]. Returns an
 * empty map when [sessionProfile] is `null` (no active session).
 */
fun filterVisibleSensors(
    snapshot: Map<SensorType, List<SensorSample>>,
    sessionProfile: RecordingProfile?,
): Map<SensorType, List<SensorSample>> {
    val profile = sessionProfile ?: return emptyMap()
    return snapshot.filterKeys { type -> profile[type].visibleOnGraph }
}

/**
 * Compute the rolling delivered rate over the visible window of [samples].
 * Returns `null` when fewer than two samples are present or when the
 * `elapsedMs` span is non-positive (clock anomaly), so the caller can render
 * an em-dash placeholder rather than a misleading value.
 *
 * Note: `elapsedMs` is the sample's wall-clock arrival time. The live graph is
 * a foreground, screen-on surface where the OS delivers continuously, so this
 * tracks the true cadence in practice. If the OS batches a normally-slow
 * sensor (e.g. pressure) the value can momentarily overstate the rate; this is
 * acceptable for a glanceable label and never crashes.
 */
fun computeRollingHz(samples: List<SensorSample>): Double? {
    if (samples.size < 2) return null
    val spanMs = samples.last().elapsedMs - samples.first().elapsedMs
    if (spanMs <= 0L) return null
    return (samples.size - 1).toDouble() * 1000.0 / spanMs.toDouble()
}

/**
 * Format a rolling-Hz value for the per-band label. Rounds to the nearest
 * integer and prefixes with a tilde to make the "best-effort" / "observed"
 * nature visible. Returns `"—"` (em-dash) for `null`.
 */
fun formatRateLabel(hz: Double?): String =
    if (hz == null) "\u2014" else "~${Math.round(hz)} Hz"
