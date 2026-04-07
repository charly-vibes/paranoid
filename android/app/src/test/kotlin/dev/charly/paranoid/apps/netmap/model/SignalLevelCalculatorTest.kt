package dev.charly.paranoid.apps.netmap.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalLevelCalculatorTest {

    @Test
    fun `null RSRP returns NONE`() {
        assertEquals(SignalLevel.NONE, SignalLevelCalculator.fromLteRsrp(null))
    }

    @Test
    fun `RSRP -80 is EXCELLENT`() {
        assertEquals(SignalLevel.EXCELLENT, SignalLevelCalculator.fromLteRsrp(-80))
    }

    @Test
    fun `RSRP -85 is EXCELLENT (boundary)`() {
        assertEquals(SignalLevel.EXCELLENT, SignalLevelCalculator.fromLteRsrp(-85))
    }

    @Test
    fun `RSRP -86 is GOOD`() {
        assertEquals(SignalLevel.GOOD, SignalLevelCalculator.fromLteRsrp(-86))
    }

    @Test
    fun `RSRP -90 is GOOD`() {
        assertEquals(SignalLevel.GOOD, SignalLevelCalculator.fromLteRsrp(-90))
    }

    @Test
    fun `RSRP -95 is GOOD (boundary)`() {
        assertEquals(SignalLevel.GOOD, SignalLevelCalculator.fromLteRsrp(-95))
    }

    @Test
    fun `RSRP -96 is FAIR`() {
        assertEquals(SignalLevel.FAIR, SignalLevelCalculator.fromLteRsrp(-96))
    }

    @Test
    fun `RSRP -105 is FAIR (boundary)`() {
        assertEquals(SignalLevel.FAIR, SignalLevelCalculator.fromLteRsrp(-105))
    }

    @Test
    fun `RSRP -106 is POOR`() {
        assertEquals(SignalLevel.POOR, SignalLevelCalculator.fromLteRsrp(-106))
    }

    @Test
    fun `RSRP -115 is POOR (boundary)`() {
        assertEquals(SignalLevel.POOR, SignalLevelCalculator.fromLteRsrp(-115))
    }

    @Test
    fun `RSRP -116 is NONE`() {
        assertEquals(SignalLevel.NONE, SignalLevelCalculator.fromLteRsrp(-116))
    }

    @Test
    fun `RSRP -140 is NONE`() {
        assertEquals(SignalLevel.NONE, SignalLevelCalculator.fromLteRsrp(-140))
    }

    @Test
    fun `NR uses same thresholds as LTE`() {
        assertEquals(SignalLevel.GOOD, SignalLevelCalculator.fromNrRsrp(-90))
        assertEquals(SignalLevel.EXCELLENT, SignalLevelCalculator.fromNrRsrp(-80))
    }

    @Test
    fun `WCDMA RSCP thresholds`() {
        assertEquals(SignalLevel.EXCELLENT, SignalLevelCalculator.fromWcdmaRscp(-70))
        assertEquals(SignalLevel.GOOD, SignalLevelCalculator.fromWcdmaRscp(-80))
        assertEquals(SignalLevel.FAIR, SignalLevelCalculator.fromWcdmaRscp(-90))
        assertEquals(SignalLevel.POOR, SignalLevelCalculator.fromWcdmaRscp(-100))
        assertEquals(SignalLevel.NONE, SignalLevelCalculator.fromWcdmaRscp(-110))
    }

    @Test
    fun `GSM RSSI thresholds`() {
        assertEquals(SignalLevel.EXCELLENT, SignalLevelCalculator.fromGsmRssi(-65))
        assertEquals(SignalLevel.GOOD, SignalLevelCalculator.fromGsmRssi(-75))
        assertEquals(SignalLevel.FAIR, SignalLevelCalculator.fromGsmRssi(-85))
        assertEquals(SignalLevel.POOR, SignalLevelCalculator.fromGsmRssi(-95))
        assertEquals(SignalLevel.NONE, SignalLevelCalculator.fromGsmRssi(-105))
    }
}
