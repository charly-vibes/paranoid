package dev.charly.paranoid.apps.netmap.data

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import dev.charly.paranoid.apps.netmap.model.CellMeasurement
import dev.charly.paranoid.apps.netmap.model.CellTech
import dev.charly.paranoid.apps.netmap.model.DataState
import dev.charly.paranoid.apps.netmap.model.NetworkType
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import dev.charly.paranoid.apps.netmap.model.SignalLevelCalculator

data class TelephonySnapshot(
    val cells: List<CellMeasurement>,
    val networkType: NetworkType,
    val carrierName: String?,
    val mccMnc: String?
)

class TelephonySource(context: Context) {
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    fun snapshot(): TelephonySnapshot {
        val cellInfos = try {
            telephonyManager.allCellInfo
        } catch (_: Exception) {
            null
        } ?: emptyList()

        val cells = cellInfos.mapNotNull { mapCellInfo(it) }

        return TelephonySnapshot(
            cells = cells,
            networkType = mapNetworkType(telephonyManager.dataNetworkType),
            carrierName = telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() },
            mccMnc = telephonyManager.networkOperator?.takeIf { it.length >= 5 }
        )
    }

    companion object {
        fun mapCellInfo(info: CellInfo): CellMeasurement? = when (info) {
            is CellInfoLte -> mapLte(info)
            is CellInfoNr -> mapNr(info)
            is CellInfoWcdma -> mapWcdma(info)
            is CellInfoGsm -> mapGsm(info)
            is CellInfoCdma -> mapCdma(info)
            else -> null
        }

        private fun mapLte(info: CellInfoLte): CellMeasurement {
            val id = info.cellIdentity
            val ss = info.cellSignalStrength
            val rsrp = ss.rsrp.validOrNull()
            return CellMeasurement(
                isServing = info.isRegistered,
                technology = CellTech.LTE,
                mcc = id.mccString?.toIntOrNull(),
                mnc = id.mncString?.toIntOrNull(),
                cellId = id.ci.validOrNull()?.toLong(),
                tac = id.tac.validOrNull(),
                pci = id.pci.validOrNull(),
                earfcn = id.earfcn.validOrNull(),
                band = if (Build.VERSION.SDK_INT >= 30) id.bands.firstOrNull()?.toString() else null,
                rssi = ss.rssi.validOrNull(),
                rsrp = rsrp,
                rsrq = ss.rsrq.validOrNull(),
                rssnr = ss.rssnr.validOrNull(),
                cqi = ss.cqi.validOrNull(),
                asuLevel = ss.asuLevel.validOrNull(),
                signalLevel = SignalLevelCalculator.fromLteRsrp(rsrp)
            )
        }

        private fun mapNr(info: CellInfoNr): CellMeasurement {
            val id = info.cellIdentity as android.telephony.CellIdentityNr
            val ss = info.cellSignalStrength as CellSignalStrengthNr
            val rsrp = ss.ssRsrp.validOrNull()
            return CellMeasurement(
                isServing = info.isRegistered,
                technology = CellTech.NR,
                mcc = id.mccString?.toIntOrNull(),
                mnc = id.mncString?.toIntOrNull(),
                cellId = id.nci.let { if (it == Long.MAX_VALUE) null else it },
                tac = id.tac.validOrNull(),
                pci = id.pci.validOrNull(),
                earfcn = id.nrarfcn.validOrNull(),
                band = if (Build.VERSION.SDK_INT >= 30) id.bands.firstOrNull()?.toString() else null,
                rsrp = rsrp,
                rsrq = ss.ssRsrq.validOrNull(),
                sinr = ss.ssSinr.validOrNull(),
                asuLevel = ss.asuLevel.validOrNull(),
                signalLevel = SignalLevelCalculator.fromNrRsrp(rsrp)
            )
        }

        private fun mapWcdma(info: CellInfoWcdma): CellMeasurement {
            val id = info.cellIdentity
            val ss = info.cellSignalStrength
            return CellMeasurement(
                isServing = info.isRegistered,
                technology = CellTech.WCDMA,
                mcc = id.mccString?.toIntOrNull(),
                mnc = id.mncString?.toIntOrNull(),
                cellId = id.cid.validOrNull()?.toLong(),
                tac = id.lac.validOrNull(),
                pci = id.psc.validOrNull(),
                earfcn = id.uarfcn.validOrNull(),
                rssi = ss.dbm,
                asuLevel = ss.asuLevel.validOrNull(),
                signalLevel = SignalLevelCalculator.fromWcdmaRscp(ss.dbm)
            )
        }

        private fun mapGsm(info: CellInfoGsm): CellMeasurement {
            val id = info.cellIdentity
            val ss = info.cellSignalStrength
            val rssi = ss.dbm
            return CellMeasurement(
                isServing = info.isRegistered,
                technology = CellTech.GSM,
                mcc = id.mccString?.toIntOrNull(),
                mnc = id.mncString?.toIntOrNull(),
                cellId = id.cid.validOrNull()?.toLong(),
                tac = id.lac.validOrNull(),
                earfcn = id.arfcn.validOrNull(),
                rssi = rssi,
                asuLevel = ss.asuLevel.validOrNull(),
                signalLevel = SignalLevelCalculator.fromGsmRssi(rssi)
            )
        }

        private fun mapCdma(info: CellInfoCdma): CellMeasurement {
            val id = info.cellIdentity
            val ss = info.cellSignalStrength
            return CellMeasurement(
                isServing = info.isRegistered,
                technology = CellTech.CDMA,
                cellId = id.basestationId.validOrNull()?.toLong(),
                rssi = ss.cdmaDbm,
                asuLevel = ss.asuLevel.validOrNull(),
                signalLevel = SignalLevelCalculator.fromGsmRssi(ss.cdmaDbm)
            )
        }

        @Suppress("DEPRECATION")
        fun mapNetworkType(type: Int): NetworkType = when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> NetworkType.GPRS
            TelephonyManager.NETWORK_TYPE_EDGE -> NetworkType.EDGE
            TelephonyManager.NETWORK_TYPE_UMTS -> NetworkType.UMTS
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA -> NetworkType.HSPA
            TelephonyManager.NETWORK_TYPE_HSPAP -> NetworkType.HSPA_PLUS
            TelephonyManager.NETWORK_TYPE_LTE -> NetworkType.LTE
            TelephonyManager.NETWORK_TYPE_NR -> NetworkType.NR_SA
            TelephonyManager.NETWORK_TYPE_GSM -> NetworkType.GSM
            else -> NetworkType.NONE
        }

        private fun Int.validOrNull(): Int? =
            if (this == Int.MAX_VALUE || this == Int.MIN_VALUE) null else this
    }
}
