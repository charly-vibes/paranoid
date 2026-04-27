package dev.charly.paranoid.apps.usageaudit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(
    val dateFormatted: String,
    val batteryDelta: String,
    val appCount: Int,
    val hasWarnings: Boolean,
    val audit: OvernightAudit,
)

sealed interface HistoryScreenState {
    data object Empty : HistoryScreenState
    data class Populated(val entries: List<HistoryEntry>) : HistoryScreenState
}

object HistoryScreenPresenter {
    private val dateFmt = SimpleDateFormat("MMM d", Locale.US)

    fun present(audits: List<OvernightAudit>): HistoryScreenState {
        if (audits.isEmpty()) return HistoryScreenState.Empty
        return HistoryScreenState.Populated(
            entries = audits.map { audit ->
                HistoryEntry(
                    dateFormatted = dateFmt.format(Date(audit.windowStartMillis)),
                    batteryDelta = audit.batteryDeltaPercent?.let { formatDelta(it) } ?: "—",
                    appCount = audit.activeAppsCount,
                    hasWarnings = audit.warningFlags.isNotEmpty(),
                    audit = audit,
                )
            },
        )
    }

    private fun formatDelta(delta: Int): String = when {
        delta < 0 -> "−${-delta}%"
        delta > 0 -> "+$delta%"
        else -> "0%"
    }
}
