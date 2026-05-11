package dev.charly.paranoid.apps.netmap.data

import dev.charly.paranoid.apps.netmap.model.AntennaEstimate
import dev.charly.paranoid.apps.netmap.model.CellTech
import dev.charly.paranoid.apps.netmap.model.DataState
import dev.charly.paranoid.apps.netmap.model.GeoPoint
import dev.charly.paranoid.apps.netmap.model.NetworkType
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class AntennaEstimateMapperTest {

    @Test
    fun `AntennaEstimate round-trips through entity`() {
        val original = AntennaEstimate(
            recordingId = "rec-42",
            cellKey = "310-260-7-100",
            technology = CellTech.LTE,
            location = GeoPoint(40.0, -74.0),
            radiusM = 123.5f,
            sampleCount = 17,
            strongestSignal = SignalLevel.EXCELLENT,
            isPciOnly = false
        )
        val entity = original.toEntity()
        assertEquals(original, entity.toDomain())
    }

    @Test
    fun `AntennaEstimate with PCI-only flag round-trips`() {
        val original = AntennaEstimate(
            recordingId = "rec-7",
            cellKey = "pci-LTE-42-1850",
            technology = CellTech.LTE,
            location = GeoPoint(0.0, 0.0),
            radiusM = 50f,
            sampleCount = 1,
            strongestSignal = SignalLevel.NONE,
            isPciOnly = true
        )
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `unknown technology string degrades to UNKNOWN on read`() {
        val entity = AntennaEstimateEntity(
            recordingId = "rec-1",
            cellKey = "k",
            technology = "MARTIAN_6G",
            lat = 0.0, lng = 0.0,
            radiusM = 50f,
            sampleCount = 1,
            strongestSignal = "NONE",
            isPciOnly = false
        )
        assertEquals(CellTech.UNKNOWN, entity.toDomain().technology)
    }

    @Test
    fun `unknown signal string degrades to NONE on read`() {
        val entity = AntennaEstimateEntity(
            recordingId = "rec-1",
            cellKey = "k",
            technology = "LTE",
            lat = 0.0, lng = 0.0,
            radiusM = 50f,
            sampleCount = 1,
            strongestSignal = "BLINDINGLY_BRIGHT",
            isPciOnly = false
        )
        assertEquals(SignalLevel.NONE, entity.toDomain().strongestSignal)
    }

    @Test
    fun `MeasurementEntity toDomain reconstructs cells from JSON`() {
        val entity = MeasurementEntity(
            id = 5, recordingId = "rec-1", timestamp = 1_700_000_000_000L,
            lat = 19.43, lng = -99.13, accuracyM = 8f,
            speedKmh = 30f, bearing = 90f, altitude = 2240.0,
            networkType = "LTE", dataState = "CONNECTED",
            cellsJson = """[{"serving":true,"tech":"LTE","rsrp":-85,"cellId":456,"pci":42,"level":"GOOD"}]"""
        )
        val domain = entity.toDomain()
        assertEquals("rec-1", domain.recordingId)
        assertEquals(GeoPoint(19.43, -99.13), domain.location)
        assertEquals(8f, domain.gpsAccuracyM)
        assertEquals(NetworkType.LTE, domain.networkType)
        assertEquals(DataState.CONNECTED, domain.dataState)
        assertEquals(1, domain.cells.size)
        assertEquals(CellTech.LTE, domain.cells[0].technology)
        assertEquals(456L, domain.cells[0].cellId)
        assertEquals(-85, domain.cells[0].rsrp)
    }

    @Test
    fun `MeasurementEntity toDomain handles unknown enums gracefully`() {
        val entity = MeasurementEntity(
            id = 1, recordingId = "r", timestamp = 0L,
            lat = 0.0, lng = 0.0, accuracyM = 0f,
            networkType = "FUTURE_NETWORK",
            dataState = "QUANTUM_ENTANGLED",
            cellsJson = ""
        )
        val domain = entity.toDomain()
        assertEquals(NetworkType.NONE, domain.networkType)
        assertEquals(DataState.DISCONNECTED, domain.dataState)
        assertEquals(emptyList<Any>(), domain.cells)
    }
}
