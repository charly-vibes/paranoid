package dev.charly.paranoid.apps.netdiag.collect

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiCollectorFrequencyTest {

    @Test
    fun `2_4 GHz channel 1`() {
        assertEquals(1, WifiCollector.frequencyToChannel(2412))
    }

    @Test
    fun `2_4 GHz channel 6`() {
        assertEquals(6, WifiCollector.frequencyToChannel(2437))
    }

    @Test
    fun `2_4 GHz channel 11`() {
        assertEquals(11, WifiCollector.frequencyToChannel(2462))
    }

    @Test
    fun `2_4 GHz channel 14`() {
        assertEquals(14, WifiCollector.frequencyToChannel(2484))
    }

    @Test
    fun `5 GHz channel 36`() {
        assertEquals(36, WifiCollector.frequencyToChannel(5180))
    }

    @Test
    fun `5 GHz channel 44`() {
        assertEquals(44, WifiCollector.frequencyToChannel(5220))
    }

    @Test
    fun `5 GHz channel 149`() {
        assertEquals(149, WifiCollector.frequencyToChannel(5745))
    }

    @Test
    fun `5 GHz channel 165`() {
        assertEquals(165, WifiCollector.frequencyToChannel(5825))
    }

    @Test
    fun `6 GHz channel 1`() {
        assertEquals(1, WifiCollector.frequencyToChannel(5960))
    }

    @Test
    fun `unknown frequency returns 0`() {
        assertEquals(0, WifiCollector.frequencyToChannel(1234))
    }

    @Test
    fun `band detection 2_4 GHz`() {
        assertEquals("2.4 GHz", WifiCollector.frequencyToBand(2412))
    }

    @Test
    fun `band detection 5 GHz`() {
        assertEquals("5 GHz", WifiCollector.frequencyToBand(5180))
    }

    @Test
    fun `band detection 6 GHz`() {
        assertEquals("6 GHz", WifiCollector.frequencyToBand(5955))
    }

    @Test
    fun `band detection unknown`() {
        assertEquals("unknown", WifiCollector.frequencyToBand(1234))
    }
}
