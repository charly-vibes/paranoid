package dev.charly.paranoid.apps.netmap.data.export

import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.data.RecordingEntity
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
            cellsJson = """[{"serving":true,"tech":"LTE","rsrp":-90,"rsrq":-10,"pci":123,"cellId":456,"level":"GOOD"}]"""
        ),
        MeasurementEntity(
            id = 2, recordingId = "test-123", timestamp = 1700000002000L,
            lat = 19.4330, lng = -99.1335, accuracyM = 8f,
            speedKmh = 25f, bearing = null, altitude = null,
            networkType = "LTE", dataState = "CONNECTED",
            cellsJson = """[{"serving":true,"tech":"LTE","rsrp":-105,"level":"FAIR"}]"""
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
        // Two measurements = two features
        val count = Regex("\"Feature\"").findAll(json).count()
        assertTrue("Expected 2 features, got $count", count == 2)
    }

    @Test
    fun `CSV export has header and data rows`() {
        val csv = exportCsv(measurements)
        val lines = csv.trim().lines()
        assertTrue("Expected header + 2 data rows", lines.size == 3)
        assertTrue(lines[0].startsWith("timestamp,"))
        assertTrue(lines[1].contains("19.4326"))
        assertTrue(lines[1].contains("LTE"))
        assertTrue(lines[1].contains("-90"))
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
        // Second measurement has null altitude — should not have ele tag for that point
        val lines = gpx.lines()
        val secondTrkpt = lines.filter { it.contains("19.433") }
        assertTrue(secondTrkpt.none { it.contains("<ele>") })
    }

    @Test
    fun `CSV handles empty measurements`() {
        val csv = exportCsv(emptyList())
        val lines = csv.trim().lines()
        assertTrue("Only header expected", lines.size == 1)
    }
}
