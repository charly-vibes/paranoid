package dev.charly.paranoid.apps.netmap.estimate

import dev.charly.paranoid.apps.netmap.model.CellMeasurement
import dev.charly.paranoid.apps.netmap.model.CellTech
import dev.charly.paranoid.apps.netmap.model.GeoPoint
import dev.charly.paranoid.apps.netmap.model.Measurement
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AntennaEstimatorTest {

    private val rec = "rec-1"

    private fun lteCell(
        cid: Long? = 100L,
        mcc: Int? = 310, mnc: Int? = 260, tac: Int? = 7,
        pci: Int? = null, earfcn: Int? = null,
        rsrp: Int? = -90,
        signal: SignalLevel = SignalLevel.GOOD
    ) = CellMeasurement(
        isServing = true, technology = CellTech.LTE,
        mcc = mcc, mnc = mnc, cellId = cid, tac = tac,
        pci = pci, earfcn = earfcn,
        rsrp = rsrp, signalLevel = signal
    )

    private fun m(
        lat: Double, lng: Double,
        accuracyM: Float = 8f,
        cells: List<CellMeasurement> = listOf(lteCell())
    ) = Measurement(
        recordingId = rec,
        timestamp = System.currentTimeMillis(),
        location = GeoPoint(lat, lng),
        gpsAccuracyM = accuracyM,
        cells = cells
    )

    @Test
    fun `single sample yields 50 m floor radius`() {
        val out = AntennaEstimator.estimate(rec, listOf(m(40.0, -74.0, accuracyM = 8f)))
        assertEquals(1, out.size)
        assertEquals(50.0f, out[0].radiusM, 0.001f)
        assertEquals(1, out[0].sampleCount)
        assertEquals(false, out[0].isPciOnly)
    }

    @Test
    fun `mean GPS accuracy of 75 m dominates a tight cluster`() {
        val measurements = (0 until 5).map { i ->
            m(40.0 + i * 1e-6, -74.0, accuracyM = 75f) // ~0.1 m apart, 75 m accuracy
        }
        val out = AntennaEstimator.estimate(rec, measurements)
        assertEquals(1, out.size)
        assertEquals(75.0f, out[0].radiusM, 0.5f)
    }

    @Test
    fun `five collinear samples — centroid lies on the line`() {
        val measurements = (0 until 5).map { i ->
            m(40.0 + i * 1e-3, -74.0)
        }
        val out = AntennaEstimator.estimate(rec, measurements)
        assertEquals(1, out.size)
        assertEquals(-74.0, out[0].location.lng, 1e-9)
        // Centroid of 0..4 mean = 2 → 40.002
        assertEquals(40.002, out[0].location.lat, 1e-6)
    }

    @Test
    fun `200 stationary plus 1 distant sample — centroid is pulled toward distant`() {
        // 200 samples at point A (clustered within 5m) + 1 sample 500m away.
        val cluster = List(200) { m(40.0, -74.0) } // identical position
        val distant = m(40.005, -74.0) // ~556 m north
        val out = AntennaEstimator.estimate(rec, cluster + distant)
        assertEquals(1, out.size)
        // After dedup: 2 representatives, weights are sum of their constituents.
        // Both reps have the same per-sample weight (RSRP -90), so cluster rep
        // weight = 200×w, distant weight = 1×w. Centroid stays close to A but
        // is meaningfully shifted compared to an unweighted-by-cluster scheme.
        // Concretely: centroidLat = (40.0×200 + 40.005×1) / 201 ≈ 40.0000249
        assertEquals(40.0 + 0.005 / 201.0, out[0].location.lat, 1e-7)
    }

    @Test
    fun `mixed serving and neighbor cells produce one estimate per unique cell`() {
        val serving = lteCell(cid = 100L)
        val neighbor1 = lteCell(cid = 200L).copy(isServing = false)
        val neighbor2 = lteCell(cid = 300L).copy(isServing = false)
        val measurements = listOf(
            m(40.0, -74.0, cells = listOf(serving, neighbor1, neighbor2)),
            m(40.001, -74.0, cells = listOf(serving, neighbor2))
        )
        val out = AntennaEstimator.estimate(rec, measurements)
        assertEquals(3, out.size)
        assertEquals(setOf("310-260-7-100", "310-260-7-200", "310-260-7-300"),
            out.map { it.cellKey }.toSet())
    }

    @Test
    fun `cells with no usable identifiers are skipped`() {
        val unidentified = lteCell(cid = null, mcc = null, mnc = null, tac = null)
        val out = AntennaEstimator.estimate(rec, listOf(m(40.0, -74.0, cells = listOf(unidentified))))
        assertEquals(0, out.size)
    }

    @Test
    fun `PCI-only fallback sets isPciOnly true`() {
        val pciOnly = lteCell(cid = null, mcc = null, mnc = null, tac = null,
            pci = 42, earfcn = 1850)
        val out = AntennaEstimator.estimate(rec, listOf(m(40.0, -74.0, cells = listOf(pciOnly))))
        assertEquals(1, out.size)
        assertEquals(true, out[0].isPciOnly)
        assertEquals("pci-LTE-42-1850", out[0].cellKey)
    }

    @Test
    fun `non-finite coordinates are dropped`() {
        val bad = m(Double.NaN, -74.0)
        val good = m(40.0, -74.0)
        val out = AntennaEstimator.estimate(rec, listOf(bad, good))
        assertEquals(1, out.size)
        assertEquals(1, out[0].sampleCount)
    }

    @Test
    fun `samples with poor GPS accuracy are dropped`() {
        val bad = m(40.0, -74.0, accuracyM = 350f)
        val good = m(40.001, -74.0, accuracyM = 8f)
        val out = AntennaEstimator.estimate(rec, listOf(bad, good))
        assertEquals(1, out.size)
        assertEquals(1, out[0].sampleCount)
        assertEquals(40.001, out[0].location.lat, 1e-9)
    }

    @Test
    fun `weighting prefers stronger RSRP over weaker`() {
        // Two distinct cluster positions (>5 m apart) so dedup leaves them separate.
        val weak = m(40.0, -74.0, cells = listOf(lteCell(rsrp = -110)))
        val strong = m(40.001, -74.0, cells = listOf(lteCell(rsrp = -70)))
        val out = AntennaEstimator.estimate(rec, listOf(weak, strong))
        assertEquals(1, out.size)
        // Weak weight = max(0, -110+140) = 30; strong = -70+140 = 70.
        // centroidLat = (40.0*30 + 40.001*70) / 100 = 40.0007
        assertEquals(40.0007, out[0].location.lat, 1e-6)
        // Closer to strong (40.001) than to weak (40.0).
        assertTrue(abs(out[0].location.lat - 40.001) < abs(out[0].location.lat - 40.0))
    }

    @Test
    fun `recordingId is propagated`() {
        val out = AntennaEstimator.estimate("custom-rec", listOf(m(40.0, -74.0)))
        assertEquals("custom-rec", out[0].recordingId)
    }

    @Test
    fun `strongest signal is the maximum SignalLevel observed`() {
        val measurements = listOf(
            m(40.0, -74.0, cells = listOf(lteCell(signal = SignalLevel.POOR))),
            m(40.001, -74.0, cells = listOf(lteCell(signal = SignalLevel.EXCELLENT))),
            m(40.002, -74.0, cells = listOf(lteCell(signal = SignalLevel.GOOD)))
        )
        val out = AntennaEstimator.estimate(rec, measurements)
        assertEquals(SignalLevel.EXCELLENT, out[0].strongestSignal)
    }

    @Test
    fun `no measurements returns empty list`() {
        assertEquals(emptyList<Any>(), AntennaEstimator.estimate(rec, emptyList()))
    }

    @Test
    fun `measurement with no cells contributes nothing`() {
        val out = AntennaEstimator.estimate(rec, listOf(m(40.0, -74.0, cells = emptyList())))
        assertEquals(0, out.size)
    }

    @Test
    fun `radius reflects spread when samples are widely separated`() {
        // Two clusters about 100 m apart → max-pairwise ≈ 100 m → 0.5×100 = 50 m,
        // tied with floor; with 3 clusters spanning 1 km → radius ≈ 500 m.
        val measurements = listOf(
            m(40.0, -74.0),
            m(40.0, -74.0 + 0.012) // ~1 km east at lat 40°
        )
        val out = AntennaEstimator.estimate(rec, measurements)
        assertTrue("Expected radius > floor for spread samples, got ${out[0].radiusM}",
            out[0].radiusM > 200f)
    }

    @Test
    fun `cell whose only samples are filtered produces no estimate`() {
        // Cell appears only on a measurement with NaN coords AND on one with
        // gpsAccuracyM > MAX_GPS_ACCURACY_M. After pre-filter, no samples remain.
        val cell = lteCell()
        val out = AntennaEstimator.estimate(rec, listOf(
            m(Double.NaN, -74.0, cells = listOf(cell)),
            m(40.0, -74.0, accuracyM = 350f, cells = listOf(cell))
        ))
        assertEquals(0, out.size)
    }

    @Test
    fun `negative or unknown MCC MNC TAC cellId fall through to PCI fallback`() {
        // CellInfo subclasses commonly return -1 for unknown. We must not
        // form a primary key from such values (would produce phantom cells
        // that collide across operators).
        val sentinel = lteCell(
            cid = -1L, mcc = -1, mnc = -1, tac = -1,
            pci = 99, earfcn = 1850
        )
        val out = AntennaEstimator.estimate(rec, listOf(m(40.0, -74.0, cells = listOf(sentinel))))
        assertEquals(1, out.size)
        assertEquals(true, out[0].isPciOnly)
        assertEquals("pci-LTE-99-1850", out[0].cellKey)
    }

    @Test
    fun `cellId Long MAX VALUE sentinel falls through to PCI fallback`() {
        val sentinel = lteCell(
            cid = Long.MAX_VALUE, mcc = 310, mnc = 260, tac = 7,
            pci = 99, earfcn = 1850
        )
        val out = AntennaEstimator.estimate(rec, listOf(m(40.0, -74.0, cells = listOf(sentinel))))
        assertEquals(1, out.size)
        assertEquals(true, out[0].isPciOnly)
    }

    @Test
    fun `cellKey distinguishes same PCI on different EARFCN`() {
        val a = lteCell(cid = null, mcc = null, mnc = null, tac = null, pci = 42, earfcn = 1850)
        val b = lteCell(cid = null, mcc = null, mnc = null, tac = null, pci = 42, earfcn = 6300)
        val out = AntennaEstimator.estimate(rec, listOf(
            m(40.0, -74.0, cells = listOf(a)),
            m(40.0, -74.0, cells = listOf(b))
        ))
        assertEquals(2, out.size)
        assertNotEquals(out[0].cellKey, out[1].cellKey)
    }
}
