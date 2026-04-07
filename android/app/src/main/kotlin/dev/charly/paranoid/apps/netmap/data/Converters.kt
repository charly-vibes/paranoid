package dev.charly.paranoid.apps.netmap.data

import dev.charly.paranoid.apps.netmap.model.CellMeasurement
import dev.charly.paranoid.apps.netmap.model.CellTech
import dev.charly.paranoid.apps.netmap.model.SignalLevel
import org.json.JSONArray
import org.json.JSONObject

object CellsJsonConverter {
    fun toJson(cells: List<CellMeasurement>): String {
        val array = JSONArray()
        for (cell in cells) {
            val obj = JSONObject()
            obj.put("serving", cell.isServing)
            obj.put("tech", cell.technology.name)
            cell.mcc?.let { obj.put("mcc", it) }
            cell.mnc?.let { obj.put("mnc", it) }
            cell.cellId?.let { obj.put("cellId", it) }
            cell.tac?.let { obj.put("tac", it) }
            cell.pci?.let { obj.put("pci", it) }
            cell.earfcn?.let { obj.put("earfcn", it) }
            cell.band?.let { obj.put("band", it) }
            cell.rssi?.let { obj.put("rssi", it) }
            cell.rsrp?.let { obj.put("rsrp", it) }
            cell.rsrq?.let { obj.put("rsrq", it) }
            cell.rssnr?.let { obj.put("rssnr", it) }
            cell.sinr?.let { obj.put("sinr", it) }
            cell.cqi?.let { obj.put("cqi", it) }
            cell.asuLevel?.let { obj.put("asu", it) }
            obj.put("level", cell.signalLevel.name)
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(json: String): List<CellMeasurement> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CellMeasurement(
                isServing = obj.optBoolean("serving", false),
                technology = runCatching { CellTech.valueOf(obj.optString("tech", "UNKNOWN")) }
                    .getOrDefault(CellTech.UNKNOWN),
                mcc = obj.optIntOrNull("mcc"),
                mnc = obj.optIntOrNull("mnc"),
                cellId = obj.optLongOrNull("cellId"),
                tac = obj.optIntOrNull("tac"),
                pci = obj.optIntOrNull("pci"),
                earfcn = obj.optIntOrNull("earfcn"),
                band = obj.optStringOrNull("band"),
                rssi = obj.optIntOrNull("rssi"),
                rsrp = obj.optIntOrNull("rsrp"),
                rsrq = obj.optIntOrNull("rsrq"),
                rssnr = obj.optIntOrNull("rssnr"),
                sinr = obj.optIntOrNull("sinr"),
                cqi = obj.optIntOrNull("cqi"),
                asuLevel = obj.optIntOrNull("asu"),
                signalLevel = runCatching { SignalLevel.valueOf(obj.optString("level", "NONE")) }
                    .getOrDefault(SignalLevel.NONE)
            )
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key)) getInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key)) getLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key)) getString(key) else null
}
