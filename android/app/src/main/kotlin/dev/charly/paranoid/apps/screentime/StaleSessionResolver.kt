package dev.charly.paranoid.apps.screentime

import java.time.Instant
import java.time.ZoneId

/**
 * Resolves the end time for a session that was left open (null end) in the store when the
 * monitoring service is killed and restarted by the OS. Pure and Android-free so it is
 * unit-testable. See screen-time-session spec (session recovery) and tasks 3.9.
 */
object StaleSessionResolver {
    /**
     * Computes the end timestamp (UTC epoch millis) at which a recovered open session should
     * be closed.
     *
     * The session is closed at the last known activity: [lastSampleMillis] if a foreground
     * sample was recorded, otherwise [serviceStartMillis]. The candidate is never earlier than
     * [sessionStartMillis]. If the candidate falls on a later local calendar day than the
     * session start, the session is clamped to 23:59:59.999 of its start day so foreground
     * time is never attributed across a day boundary.
     */
    fun resolveEnd(
        sessionStartMillis: Long,
        lastSampleMillis: Long?,
        serviceStartMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val candidate = (lastSampleMillis ?: serviceStartMillis).coerceAtLeast(sessionStartMillis)
        val startDay = Instant.ofEpochMilli(sessionStartMillis).atZone(zone).toLocalDate()
        val candidateDay = Instant.ofEpochMilli(candidate).atZone(zone).toLocalDate()
        if (candidateDay.isAfter(startDay)) {
            return startDay.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
        }
        return candidate
    }
}
