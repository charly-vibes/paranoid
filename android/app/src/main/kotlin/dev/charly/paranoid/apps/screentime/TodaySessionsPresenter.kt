package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.SYSTEM_UNATTRIBUTED
import dev.charly.paranoid.apps.screentime.model.Session
import java.time.Instant
import java.time.ZoneId

/** One row in the today's-sessions list: when it started, how long, and the most-used app. */
data class SessionRow(
    val startMillis: Long,
    val durationMillis: Long,
    /** Package with the most foreground time, excluding system-unattributed; null if none. */
    val topAppPackage: String?,
    val isOpen: Boolean,
)

/**
 * Pure, Android-free presenter for the today's-sessions list. Selects sessions that started on the
 * current local calendar day, computes each session's duration (open sessions run to [nowMillis]),
 * and finds the most-used app per session. Newest session first. Unit-testable.
 */
object TodaySessionsPresenter {
    fun present(
        sessions: List<Session>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SessionRow> {
        val todayStart = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return sessions
            .filter { it.startMillis in todayStart..nowMillis }
            .sortedByDescending { it.startMillis }
            .map { session ->
                val end = session.endMillis ?: nowMillis
                val top = SessionAggregator.foregroundByApp(listOf(session), session.startMillis, end)
                    .firstOrNull { it.packageName != SYSTEM_UNATTRIBUTED }
                SessionRow(
                    startMillis = session.startMillis,
                    durationMillis = (end - session.startMillis).coerceAtLeast(0L),
                    topAppPackage = top?.packageName,
                    isOpen = session.isOpen,
                )
            }
    }
}
