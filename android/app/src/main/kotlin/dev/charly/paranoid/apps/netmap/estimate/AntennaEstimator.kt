// NO-NETWORK INVARIANT
// This file (and any code path it touches) MUST NOT perform network I/O.
// No external geolocation lookup (OpenCellID, Google Geolocation API, …)
// is permitted. Enforced in CI by the detekt rule under PARANOID-f0x.4
// and by a runtime test that fails on Socket/HttpURLConnection open.

package dev.charly.paranoid.apps.netmap.estimate

import dev.charly.paranoid.apps.netmap.model.AntennaEstimate
import dev.charly.paranoid.apps.netmap.model.CellMeasurement
import dev.charly.paranoid.apps.netmap.model.CellTech
import dev.charly.paranoid.apps.netmap.model.GeoPoint
import dev.charly.paranoid.apps.netmap.model.Measurement
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure, offline antenna-location estimator.
 *
 * Spec: openspec/changes/add-netmap-antenna-locations/specs/netmap-data/spec.md
 * (Antenna Location Estimator + Antenna Estimate Domain Model)
 *
 * No I/O, no Context, no system calls — safe to call from anywhere.
 */
object AntennaEstimator {

    /** GPS-accuracy filter cutoff. Samples worse than this are dropped. */
    const val MAX_GPS_ACCURACY_M: Float = 200.0f

    /** Single-link clustering threshold (meters). */
    const val CLUSTER_THRESHOLD_M: Double = 5.0

    /** Floor for the heuristic uncertainty radius. */
    const val MIN_RADIUS_M: Float = 50.0f

    /**
     * Compute one [AntennaEstimate] per uniquely-identifiable observed cell.
     *
     * Pre-filters out [Measurement]s with non-finite coordinates or
     * `gpsAccuracyM > MAX_GPS_ACCURACY_M`. Cells without a `(mcc, mnc, tac,
     * cellId)` tuple or a `(pci, earfcn)` fallback are skipped. See the
     * spec for full requirements + scenarios.
     */
    fun estimate(
        recordingId: String,
        measurements: List<Measurement>
    ): List<AntennaEstimate> {
        val observations = collectObservations(measurements)
        return observations
            .groupBy { it.key }
            .map { (key, group) -> estimateOne(recordingId, key, group) }
    }

    /** Per-cell observation derived from one [Measurement] × one [CellMeasurement]. */
    private data class Obs(
        val key: String,
        val isPciOnly: Boolean,
        val tech: CellTech,
        val pos: GeoPoint,
        val accuracyM: Float,
        val weight: Double,
        val signal: SignalLevel
    )

    private fun collectObservations(measurements: List<Measurement>): List<Obs> {
        val out = ArrayList<Obs>()
        for (m in measurements) {
            if (!m.location.lat.isFinite() ||
                !m.location.lng.isFinite() ||
                !m.gpsAccuracyM.isFinite() ||
                m.gpsAccuracyM > MAX_GPS_ACCURACY_M
            ) continue

            for (c in m.cells) {
                val primary = primaryKey(c)
                val key = primary ?: fallbackKey(c) ?: continue
                out += Obs(
                    key = key,
                    isPciOnly = primary == null,
                    tech = c.technology,
                    pos = m.location,
                    accuracyM = m.gpsAccuracyM,
                    weight = signalWeight(c),
                    signal = c.signalLevel
                )
            }
        }
        return out
    }

    private fun estimateOne(
        recordingId: String,
        key: String,
        obs: List<Obs>
    ): AntennaEstimate {
        val sampleCount = obs.size
        val tech = obs.map { it.tech }.distinct()
            .let { if (it.size == 1) it.first() else CellTech.UNKNOWN }
        val isPciOnly = obs.all { it.isPciOnly }
        val strongestSignal = obs.maxByOrNull { it.signal.ordinal }?.signal
            ?: SignalLevel.NONE
        val meanAccuracy = obs.map { it.accuracyM.toDouble() }.average().toFloat()

        // 5 m single-link clustering using union-find.
        val parent = IntArray(obs.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var n = x
            while (parent[n] != n) {
                val nxt = parent[n]; parent[n] = r; n = nxt
            }
            return r
        }
        for (i in obs.indices) {
            for (j in i + 1 until obs.size) {
                if (haversineMeters(obs[i].pos, obs[j].pos) <= CLUSTER_THRESHOLD_M) {
                    val a = find(i); val b = find(j)
                    if (a != b) parent[a] = b
                }
            }
        }

        // Build cluster representatives: position = mean, weight = sum.
        val clusters = obs.indices.groupBy { find(it) }.values
        val reps = clusters.map { idx ->
            val meanLat = idx.sumOf { obs[it].pos.lat } / idx.size
            val meanLng = idx.sumOf { obs[it].pos.lng } / idx.size
            val sumW = idx.sumOf { obs[it].weight }
            GeoPoint(meanLat, meanLng) to sumW
        }

        // Signal-weighted centroid; if all weights are 0, fall back to uniform.
        val centroid = if (reps.sumOf { it.second } > 0.0) {
            weightedCentroid(reps)
        } else {
            weightedCentroid(reps.map { it.first to 1.0 })
        }

        // Max pairwise distance between cluster representatives.
        var maxPair = 0.0
        for (i in reps.indices) {
            for (j in i + 1 until reps.size) {
                val d = haversineMeters(reps[i].first, reps[j].first)
                if (d > maxPair) maxPair = d
            }
        }
        val radius = max(
            MIN_RADIUS_M,
            max((0.5 * maxPair).toFloat(), meanAccuracy)
        )

        return AntennaEstimate(
            recordingId = recordingId,
            cellKey = key,
            technology = tech,
            location = centroid,
            radiusM = radius,
            sampleCount = sampleCount,
            strongestSignal = strongestSignal,
            isPciOnly = isPciOnly
        )
    }

    private fun weightedCentroid(reps: List<Pair<GeoPoint, Double>>): GeoPoint {
        val total = reps.sumOf { it.second }
        val lat = reps.sumOf { it.first.lat * it.second } / total
        val lng = reps.sumOf { it.first.lng * it.second } / total
        return GeoPoint(lat, lng)
    }

    // --- helpers ---------------------------------------------------------

    private fun primaryKey(c: CellMeasurement): String? {
        // Reject -1 / negatives — CellInfo APIs return -1 (and Long.MAX_VALUE
        // for cellId) for "unknown", which would otherwise produce phantom
        // keys that collide across operators.
        val mcc = c.mcc?.takeIf { it > 0 }
        val mnc = c.mnc?.takeIf { it >= 0 }
        val tac = c.tac?.takeIf { it >= 0 }
        val cid = c.cellId?.takeIf { it >= 0 && it != Long.MAX_VALUE }
        return if (mcc != null && mnc != null && tac != null && cid != null) {
            "$mcc-$mnc-$tac-$cid"
        } else null
    }

    private fun fallbackKey(c: CellMeasurement): String? {
        val pci = c.pci?.takeIf { it >= 0 }
        val earfcn = c.earfcn?.takeIf { it >= 0 }
        return if (pci != null && earfcn != null) {
            "pci-${c.technology}-$pci-$earfcn"
        } else null
    }

    /**
     * Per-sample weight from signal strength.
     *
     * - LTE/NR: RSRP-based, `max(0, rsrp + 140)`.
     * - GSM / WCDMA / CDMA / UNKNOWN: RSSI-based, `max(0, rssi + 110)`.
     *   The spec describes WCDMA as "RSCP-based", but [CellMeasurement] does
     *   not currently expose an `rscp` field — RSSI is used as a pragmatic
     *   substitute. If RSCP is later added to the model, prefer it here.
     * - Missing both → uniform weight 1.0.
     */
    private fun signalWeight(c: CellMeasurement): Double = when (c.technology) {
        CellTech.LTE, CellTech.NR -> {
            val r = c.rsrp
            if (r != null) max(0.0, (r + 140).toDouble()) else 1.0
        }
        else -> {
            val r = c.rssi ?: c.rsrp
            if (r != null) max(0.0, (r + 110).toDouble()) else 1.0
        }
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val rEarth = 6_371_000.0
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }
        return 2 * rEarth * asin(sqrt(h))
    }
}
