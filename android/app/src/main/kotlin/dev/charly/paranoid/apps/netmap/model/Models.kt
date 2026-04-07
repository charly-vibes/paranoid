package dev.charly.paranoid.apps.netmap.model

data class GeoPoint(val lat: Double, val lng: Double)

enum class CellTech { GSM, WCDMA, LTE, NR, CDMA, UNKNOWN }

enum class NetworkType {
    GSM, GPRS, EDGE, UMTS, HSPA, HSPA_PLUS,
    LTE, LTE_CA, NR_NSA, NR_SA, NONE
}

enum class DataState { CONNECTED, CONNECTING, DISCONNECTED, SUSPENDED }

enum class SignalLevel { NONE, POOR, FAIR, GOOD, EXCELLENT }

data class CellMeasurement(
    val isServing: Boolean,
    val technology: CellTech,
    val mcc: Int? = null,
    val mnc: Int? = null,
    val cellId: Long? = null,
    val tac: Int? = null,
    val pci: Int? = null,
    val earfcn: Int? = null,
    val band: String? = null,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssnr: Int? = null,
    val sinr: Int? = null,
    val cqi: Int? = null,
    val asuLevel: Int? = null,
    val signalLevel: SignalLevel = SignalLevel.NONE
)

data class Measurement(
    val id: Long = 0,
    val recordingId: String,
    val timestamp: Long,
    val location: GeoPoint,
    val gpsAccuracyM: Float,
    val gpsSpeedKmh: Float? = null,
    val gpsBearing: Float? = null,
    val gpsAltitude: Double? = null,
    val cells: List<CellMeasurement> = emptyList(),
    val networkType: NetworkType = NetworkType.NONE,
    val dataState: DataState = DataState.DISCONNECTED
)

data class Recording(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val carrier: String? = null,
    val notes: String? = null
)

object SignalLevelCalculator {
    fun fromLteRsrp(rsrp: Int?): SignalLevel = when {
        rsrp == null -> SignalLevel.NONE
        rsrp >= -85 -> SignalLevel.EXCELLENT
        rsrp >= -95 -> SignalLevel.GOOD
        rsrp >= -105 -> SignalLevel.FAIR
        rsrp >= -115 -> SignalLevel.POOR
        else -> SignalLevel.NONE
    }

    fun fromWcdmaRscp(rscp: Int?): SignalLevel = when {
        rscp == null -> SignalLevel.NONE
        rscp >= -75 -> SignalLevel.EXCELLENT
        rscp >= -85 -> SignalLevel.GOOD
        rscp >= -95 -> SignalLevel.FAIR
        rscp >= -105 -> SignalLevel.POOR
        else -> SignalLevel.NONE
    }

    fun fromGsmRssi(rssi: Int?): SignalLevel = when {
        rssi == null -> SignalLevel.NONE
        rssi >= -70 -> SignalLevel.EXCELLENT
        rssi >= -80 -> SignalLevel.GOOD
        rssi >= -90 -> SignalLevel.FAIR
        rssi >= -100 -> SignalLevel.POOR
        else -> SignalLevel.NONE
    }

    fun fromNrRsrp(rsrp: Int?): SignalLevel = fromLteRsrp(rsrp)
}
