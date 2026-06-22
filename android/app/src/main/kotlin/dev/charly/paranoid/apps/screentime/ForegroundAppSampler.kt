package dev.charly.paranoid.apps.screentime

import dev.charly.paranoid.apps.screentime.model.SYSTEM_UNATTRIBUTED

/** The package in the foreground and the time it most recently came to the foreground. */
data class ForegroundApp(
    val packageName: String,
    val sinceMillis: Long,
)

/** Outcome of resolving the foreground app at one sample. */
sealed interface ForegroundSample {
    /** A foreground app was resolved. */
    data class Resolved(val app: ForegroundApp) : ForegroundSample

    /** No foreground app could be attributed (launcher, lock screen, system UI, empty window). */
    data object Unresolved : ForegroundSample

    /** Usage access is unavailable/revoked; the sample must be skipped. */
    data object AccessUnavailable : ForegroundSample
}

/** Resolves the foreground app at a point in time. Abstracted so the sampler is unit-testable. */
fun interface ForegroundAppSource {
    fun sample(nowMillis: Long): ForegroundSample
}

/**
 * Polls a [ForegroundAppSource] and forwards foreground observations to [onForegroundApp]
 * (typically `SessionStateMachine::onForegroundApp`). Pure and Android-free.
 *
 * Attribution timing (see screen-time-session spec, which marks per-app times approximate):
 * - While the same app stays in the foreground, each sample extends its interval to the sample
 *   time, so N samples at the 5 s cadence attribute ≈ N × 5 s.
 * - On an app switch, the boundary is attributed at the new app's foreground event time
 *   ([ForegroundApp.sinceMillis]), clamped into `(previous attribution, now]` to stay monotonic.
 * - An unresolved foreground is attributed to [SYSTEM_UNATTRIBUTED].
 * - When access is unavailable the sample is skipped and [onAccessUnavailable] fires once until
 *   access is restored, when [onAccessRestored] fires.
 */
class ForegroundAppSampler(
    private val source: ForegroundAppSource,
    private val onForegroundApp: (packageName: String, atMillis: Long) -> Unit,
    private val onAccessUnavailable: () -> Unit = {},
    private val onAccessRestored: () -> Unit = {},
) {
    private var warned = false
    private var lastPackage: String? = null
    private var lastAtMillis: Long = Long.MIN_VALUE

    fun sample(nowMillis: Long) {
        when (val result = source.sample(nowMillis)) {
            is ForegroundSample.Resolved -> {
                clearWarning()
                forward(result.app.packageName, result.app.sinceMillis, nowMillis)
            }
            ForegroundSample.Unresolved -> {
                clearWarning()
                forward(SYSTEM_UNATTRIBUTED, nowMillis, nowMillis)
            }
            ForegroundSample.AccessUnavailable -> {
                if (!warned) {
                    warned = true
                    onAccessUnavailable()
                }
            }
        }
    }

    private fun forward(packageName: String, sinceMillis: Long, nowMillis: Long) {
        val isSwitch = packageName != lastPackage
        val proposed = if (isSwitch) sinceMillis else nowMillis
        val lowerBound = if (lastAtMillis == Long.MIN_VALUE) proposed else lastAtMillis
        val atMillis = proposed.coerceIn(lowerBound.coerceAtMost(nowMillis), nowMillis)
        onForegroundApp(packageName, atMillis)
        lastPackage = packageName
        lastAtMillis = atMillis
    }

    private fun clearWarning() {
        if (warned) {
            warned = false
            onAccessRestored()
        }
    }
}
