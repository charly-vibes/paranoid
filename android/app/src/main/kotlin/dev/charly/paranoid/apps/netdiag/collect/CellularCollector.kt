package dev.charly.paranoid.apps.netdiag.collect

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfoLte
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import dev.charly.paranoid.apps.netdiag.data.CellRat
import dev.charly.paranoid.apps.netdiag.data.CellularSnapshot
import dev.charly.paranoid.apps.netdiag.data.Measured
import dev.charly.paranoid.apps.netdiag.data.SignalCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
class CellularCollector(private val context: Context) {

    suspend fun collect(): CellularSnapshot? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        if (tm.simState != TelephonyManager.SIM_STATE_READY) return null

        return try {
            collectInternal(tm)
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun collectInternal(tm: TelephonyManager): CellularSnapshot {
        val baseRat = mapNetworkType(tm.dataNetworkType)
        val displayInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            collectDisplayInfo(tm)
        } else null

        val rat = resolveRat(baseRat, displayInfo)

        val signalStrength = tm.signalStrength
        var rsrp: Measured<Int>? = null
        var rsrq: Measured<Int>? = null
        var rssnr: Measured<Int>? = null
        var cqi: Measured<Int>? = null
        var rssi: Measured<Int>? = null
        var ssRsrp: Measured<Int>? = null
        var ssRsrq: Measured<Int>? = null
        var ssSinr: Measured<Int>? = null

        if (signalStrength != null) {
            val now = System.currentTimeMillis()
            for (group in signalStrength.cellSignalStrengths) {
                when (group) {
                    is CellSignalStrengthLte -> {
                        rsrp = group.rsrp.toMeasured("CellSignalStrengthLte", now)
                        rsrq = group.rsrq.toMeasured("CellSignalStrengthLte", now)
                        rssnr = group.rssnr.toMeasured("CellSignalStrengthLte", now)
                        cqi = group.cqi.toMeasured("CellSignalStrengthLte", now)
                        rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            group.rssi.toMeasured("CellSignalStrengthLte", now)
                        } else null
                    }
                    is CellSignalStrengthNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ssRsrp = group.ssRsrp.toMeasured("CellSignalStrengthNr", now)
                        ssRsrq = group.ssRsrq.toMeasured("CellSignalStrengthNr", now)
                        ssSinr = group.ssSinr.toMeasured("CellSignalStrengthNr", now)
                    }
                }
            }
        }

        val referenceRsrp = ssRsrp?.value ?: rsrp?.value
        val signalCategory = categorizeSignal(referenceRsrp)

        val carrierAggregation = rat == CellRat.LTE_CA
        val componentCarriers = countComponentCarriers(tm)

        val networkOp = tm.networkOperator
        val mcc = if (networkOp.length >= 3) networkOp.substring(0, 3) else null
        val mnc = if (networkOp.length > 3) networkOp.substring(3) else null

        val dataState = when (tm.dataState) {
            TelephonyManager.DATA_CONNECTED -> "CONNECTED"
            TelephonyManager.DATA_CONNECTING -> "CONNECTING"
            TelephonyManager.DATA_DISCONNECTED -> "DISCONNECTED"
            TelephonyManager.DATA_SUSPENDED -> "SUSPENDED"
            else -> "UNKNOWN"
        }

        val displayType = displayInfo?.second ?: tm.networkOperatorName?.takeIf { it.isNotBlank() }

        return CellularSnapshot(
            rat = rat,
            displayType = displayType,
            operatorName = tm.networkOperatorName?.takeIf { it.isNotBlank() },
            mcc = mcc,
            mnc = mnc,
            rsrp = rsrp,
            rsrq = rsrq,
            rssnr = rssnr,
            cqi = cqi,
            rssi = rssi,
            signalCategory = signalCategory,
            ssRsrp = ssRsrp,
            ssRsrq = ssRsrq,
            ssSinr = ssSinr,
            dataState = dataState,
            isRoaming = tm.isNetworkRoaming,
            isCarrierAggregation = carrierAggregation,
            numComponentCarriers = componentCarriers,
        )
    }

    private fun mapNetworkType(type: Int): CellRat = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE -> CellRat.GSM

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP -> CellRat.HSPA

        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_1xRTT -> CellRat.CDMA

        TelephonyManager.NETWORK_TYPE_LTE -> CellRat.LTE

        TelephonyManager.NETWORK_TYPE_NR -> CellRat.NR_SA

        else -> CellRat.UNKNOWN
    }

    @Suppress("NewApi")
    private fun resolveRat(baseRat: CellRat, displayInfo: Pair<Int, String?>?): CellRat {
        if (displayInfo == null) return baseRat
        val overrideType = displayInfo.first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
            ) {
                return CellRat.NR_NSA
            }
            if (overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA) {
                return CellRat.LTE_CA
            }
        }
        return baseRat
    }

    @Suppress("NewApi", "DEPRECATION")
    private suspend fun collectDisplayInfo(tm: TelephonyManager): Pair<Int, String?>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val executor = Executors.newSingleThreadExecutor()

        return withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                            try { tm.unregisterTelephonyCallback(this) } catch (_: Exception) {}
                            if (cont.isActive) {
                                cont.resume(Pair(info.overrideNetworkType, info.toString()))
                            }
                        }
                    }
                    try {
                        tm.registerTelephonyCallback(executor, callback)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    cont.invokeOnCancellation {
                        try { tm.unregisterTelephonyCallback(callback) } catch (_: Exception) {}
                    }
                } else {
                    val listener = object : PhoneStateListener() {
                        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                            tm.listen(this, PhoneStateListener.LISTEN_NONE)
                            if (cont.isActive) {
                                cont.resume(Pair(info.overrideNetworkType, info.toString()))
                            }
                        }
                    }
                    try {
                        tm.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    cont.invokeOnCancellation {
                        try { tm.listen(listener, PhoneStateListener.LISTEN_NONE) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun categorizeSignal(rsrp: Int?): SignalCategory = when {
        rsrp == null -> SignalCategory.UNUSABLE
        rsrp >= -80 -> SignalCategory.EXCELLENT
        rsrp >= -90 -> SignalCategory.GOOD
        rsrp >= -100 -> SignalCategory.FAIR
        rsrp >= -110 -> SignalCategory.POOR
        else -> SignalCategory.UNUSABLE
    }

    private fun countComponentCarriers(tm: TelephonyManager): Int? = try {
        val allCells = tm.allCellInfo ?: return null
        val lteCount = allCells.count { it is CellInfoLte }
        if (lteCount > 1) lteCount else null
    } catch (_: Exception) {
        null
    }

    private fun Int.toMeasured(source: String, timestampMs: Long): Measured<Int>? =
        if (this == Int.MAX_VALUE) null
        else Measured(value = this, confidence = 1.0f, source = source, timestampMs = timestampMs)
}
