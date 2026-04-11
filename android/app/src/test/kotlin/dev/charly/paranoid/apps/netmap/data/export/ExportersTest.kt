package dev.charly.paranoid.apps.netmap.data.export

import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.data.RecordingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportersTest {

    private val recording = RecordingEntity(
        id = "test-123",
        name = "Test Trip",
        startedAt = 1700000000000L,
        endedAt = 1700000060000L,
        carrier = "TestCarrier"
    )

    private val measurements = listOf(
        MeasurementEntity(
            id = 1, recordingId = "test-123", timestamp = 1700000000000L,
            lat = 19.4326, lng = -99.1332, accuracyM = 5f,
            speedKmh = 30f, bearing = 90f, altitude = 2240.0,
            networkType = "LTE", dataState = "CONNECTED",
            cellsJson = """[{"serving":true,"tech":"LTE","rsrp":-90,"rsrq":-10,"pci":123,"cellId":456,"level":"GOOD"},{"serving":false,"tech":"LTE","rsrp":-105,"pci":789,"cellId":999,"level":"FAIR"}]"""
        ),
        MeasurementEntity(
            id = 2, recordingId = "test-123", timestamp = 1700000002000L,
            lat = 19.4330, lng = -99.1335, accuracyM = 8f,
            speedKmh = 25f, bearing = null, altitude = null,
            networkType = "LTE", dataState = "CONNECTED",
            cellsJson = """[{"serving":true,"tech":"LTE","rsrp":-105,"cellId":456,"pci":123,"level":"FAIR"}]"""
        )
    )

    @Test
    fun `GeoJSON export contains FeatureCollection`() {
        val json = exportGeoJson(recording, measurements)
        assertTrue(json.contains("FeatureCollection"))
        assertTrue(json.contains("Test Trip"))
        assertTrue(json.contains("-99.1332"))
        assertTrue(json.contains("GOOD"))
    }

    @Test
    fun `GeoJSON export has correct number of features`() {
        val json = exportGeoJson(recording, measurements)
        val count = Regex("\"Feature\"").findAll(json).count()
        assertEquals("Expected 2 features", 2, count)
    }

    @Test
    fun `CSV exports one row per cell`() {
        val csv = exportCsv(recording, measurements)
        val lines = csv.trim().lines()
        // 1 header + 2 cells from measurement 1 + 1 cell from measurement 2 = 4
        assertEquals("Expected header + 3 data rows", 4, lines.size)
        assertTrue(lines[0].contains("serving"))
        assertTrue(lines[0].contains("carrier"))
        assertTrue(lines[0].contains("neighbor_count"))
    }

    @Test
    fun `CSV includes carrier and neighbor info`() {
        val csv = exportCsv(recording, measurements)
        val lines = csv.trim().lines()
        assertTrue(lines[1].contains("TestCarrier"))
        assertTrue(lines[1].contains("true")) // serving
        assertTrue(lines[2].contains("false")) // neighbor
    }

    @Test
    fun `CSV handles empty measurements`() {
        val csv = exportCsv(recording, emptyList())
        val lines = csv.trim().lines()
        assertEquals("Only header expected", 1, lines.size)
    }

    @Test
    fun `Cell towers export estimates positions`() {
        val csv = exportCellTowers(measurements)
        val lines = csv.trim().lines()
        assertTrue(lines[0].contains("cell_id"))
        assertTrue(lines[0].contains("estimated_lat"))
        assertTrue(lines[0].contains("observations"))
        // cell_id 456 appears in both measurements (serving)
        assertTrue("Should contain cell 456", csv.contains("456"))
        // cell_id 999 appears once (neighbor)
        assertTrue("Should contain cell 999", csv.contains("999"))
    }

    @Test
    fun `Cell towers observation count is correct`() {
        val csv = exportCellTowers(measurements)
        val lines = csv.trim().lines()
        // cell 456 appears in 2 measurements
        val cell456 = lines.find { it.startsWith("456,") }
        assertTrue("Cell 456 should have 2 observations", cell456 != null && cell456.contains(",2,"))
    }

    @Test
    fun `KML export is valid XML structure`() {
        val kml = exportKml(recording, measurements)
        assertTrue(kml.contains("<?xml"))
        assertTrue(kml.contains("<kml"))
        assertTrue(kml.contains("<Document>"))
        assertTrue(kml.contains("Test Trip"))
        assertTrue(kml.contains("<Placemark>"))
        assertTrue(kml.contains("</kml>"))
    }

    @Test
    fun `GPX export has track structure`() {
        val gpx = exportGpx(recording, measurements)
        assertTrue(gpx.contains("<?xml"))
        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.contains("<trkseg>"))
        assertTrue(gpx.contains("<trkpt"))
        assertTrue(gpx.contains("lat=\"19.4326\""))
        assertTrue(gpx.contains("<ele>2240.0</ele>"))
        assertTrue(gpx.contains("<rsrp>-90</rsrp>"))
        assertTrue(gpx.contains("</gpx>"))
    }

    @Test
    fun `GPX export omits altitude when null`() {
        val gpx = exportGpx(recording, measurements)
        val lines = gpx.lines()
        val secondTrkpt = lines.filter { it.contains("19.433") }
        assertTrue(secondTrkpt.none { it.contains("<ele>") })
    }
}
