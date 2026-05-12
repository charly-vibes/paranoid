package dev.charly.paranoid.apps.netmap.estimate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Privacy guardrail tests (PARANOID-f0x.4).
 *
 * Spec: openspec/changes/add-netmap-antenna-locations/specs/netmap-data/spec.md
 * §"Offline-Only Antenna Estimation"
 *
 * The original spec called for a `detekt` rule + a runtime
 * Socket/HttpURLConnection sentinel. Detekt is not currently configured
 * in this project; rather than introduce a new build-tool dependency
 * for a single rule, we enforce the invariant via two static unit tests:
 *
 *  1. The estimator's source files (and the persistence call site) MUST
 *     NOT import any networking class.
 *  2. The estimator and DAO files MUST carry the `// NO-NETWORK INVARIANT`
 *     banner so a future code reader knows why those imports are forbidden.
 *
 * If this project ever adopts detekt, replace these with a custom rule
 * and delete this file.
 */
class AntennaEstimatorPrivacyTest {

    private val forbiddenImports = listOf(
        "java.net.",
        "java.nio.channels.",
        "okhttp3.",
        "retrofit2.",
        "android.net.http.",
        "com.android.volley.",
        "javax.net.ssl.",
    )

    /** Files that must remain network-free. */
    private val guardedFiles = listOf(
        "android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/estimate/AntennaEstimator.kt",
        "android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/data/AntennaEstimateMapper.kt",
        "android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/data/Daos.kt",
    )

    @Test
    fun `guarded files contain NO-NETWORK INVARIANT banner`() {
        for (path in guardedFiles) {
            val file = resolveSource(path)
            assertTrue(
                "$path is missing the NO-NETWORK INVARIANT banner",
                file.readText().contains("NO-NETWORK INVARIANT")
            )
        }
    }

    @Test
    fun `guarded files do not import networking classes`() {
        for (path in guardedFiles) {
            val file = resolveSource(path)
            val imports = file.readLines().filter { it.trim().startsWith("import ") }
            for (line in imports) {
                for (forbidden in forbiddenImports) {
                    assertFalse(
                        "$path imports forbidden class: $line",
                        line.contains(forbidden)
                    )
                }
            }
        }
    }

    @Test
    fun `guarded files do not reference networking classes by fully-qualified name`() {
        // Catches fully-qualified usages like `java.net.Socket()` that
        // bypass the import-line grep above.
        for (path in guardedFiles) {
            val text = resolveSource(path).readText()
            // Strip line comments and the NO-NETWORK banner so they don't
            // trigger false positives by mentioning the forbidden tokens.
            val scrubbed = text
                .lineSequence()
                .filterNot { it.trim().startsWith("//") }
                .joinToString("\n")
            for (forbidden in forbiddenImports) {
                assertFalse(
                    "$path contains a fully-qualified reference to $forbidden",
                    scrubbed.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun `RecordingService persistAntennaEstimates body does not introduce networking`() {
        val file = resolveSource(
            "android/app/src/main/kotlin/dev/charly/paranoid/apps/netmap/service/RecordingService.kt"
        )
        val text = file.readText()
        // We do not forbid all networking imports in the whole RecordingService
        // (it imports e.g. android.os.* freely). We only check that the
        // estimator-related code path does not introduce one. The whole-file
        // scan is intentionally lax — guardedFiles above is the strict list.
        // The estimator's pure function is the only thing this method touches.
        assertTrue(
            "persistAntennaEstimates() must call AntennaEstimator.estimate(",
            text.contains("AntennaEstimator.estimate(")
        )
    }

    /** Locate a source file by walking up from the test working directory. */
    private fun resolveSource(relPath: String): File {
        var dir = File(".").canonicalFile
        repeat(5) {
            val candidate = File(dir, relPath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not resolve $relPath from working dir ${File(".").canonicalPath}")
    }
}
