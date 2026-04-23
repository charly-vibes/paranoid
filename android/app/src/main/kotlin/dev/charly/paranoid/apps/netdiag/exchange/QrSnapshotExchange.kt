package dev.charly.paranoid.apps.netdiag.exchange

import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshot
import kotlinx.serialization.json.Json
import java.util.Base64

object QrSnapshotExchange {

    const val CHUNK_SIZE_BYTES = 1500

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Encode a snapshot into a list of QR-ready chunk strings.
     * Each string has the format `"<index>/<total>|<base64data>"` (1-based index).
     * Small snapshots (≤ 1.5 KB) produce a single chunk.
     */
    fun encode(snapshot: DiagnosticsSnapshot): List<String> {
        val bytes = json.encodeToString(DiagnosticsSnapshot.serializer(), snapshot)
            .toByteArray(Charsets.UTF_8)

        val chunks = bytes.toList()
            .chunked(CHUNK_SIZE_BYTES)
            .map { it.toByteArray() }

        val total = chunks.size
        return chunks.mapIndexed { i, chunk ->
            val b64 = Base64.getEncoder().encodeToString(chunk)
            "${i + 1}/$total|$b64"
        }
    }

    sealed class ChunkResult {
        data class Added(val index: Int, val total: Int, val remaining: Int) : ChunkResult()
        data class Duplicate(val index: Int) : ChunkResult()
        data class Invalid(val reason: String) : ChunkResult()
    }

    /**
     * Stateful reassembler that collects chunks in any order and
     * produces a [DiagnosticsSnapshot] once all chunks have arrived.
     */
    class Reassembler {

        private var expectedTotal: Int? = null
        private val received = mutableMapOf<Int, String>() // index (1-based) → base64 data

        fun addChunk(data: String): ChunkResult {
            val pipeIdx = data.indexOf('|')
            if (pipeIdx < 0) return ChunkResult.Invalid("Missing '|' separator")

            val header = data.substring(0, pipeIdx)
            val payload = data.substring(pipeIdx + 1)

            val parts = header.split('/')
            if (parts.size != 2) return ChunkResult.Invalid("Bad header format")

            val index = parts[0].toIntOrNull()
                ?: return ChunkResult.Invalid("Non-numeric index")
            val total = parts[1].toIntOrNull()
                ?: return ChunkResult.Invalid("Non-numeric total")

            if (total < 1) return ChunkResult.Invalid("Total must be ≥ 1")
            if (index < 1 || index > total) return ChunkResult.Invalid("Index $index out of range 1..$total")

            val existing = expectedTotal
            if (existing != null && existing != total) {
                return ChunkResult.Invalid("Total mismatch: expected $existing, got $total")
            }
            expectedTotal = total

            if (received.containsKey(index)) return ChunkResult.Duplicate(index)

            received[index] = payload
            return ChunkResult.Added(index = index, total = total, remaining = total - received.size)
        }

        fun missingChunks(): List<Int> {
            val total = expectedTotal ?: return emptyList()
            return (1..total).filter { it !in received }
        }

        fun isComplete(): Boolean {
            val total = expectedTotal ?: return false
            return received.size == total
        }

        fun assemble(): Result<DiagnosticsSnapshot> {
            if (!isComplete()) {
                val missing = missingChunks()
                return Result.failure(
                    IllegalStateException("Cannot assemble: missing chunks $missing")
                )
            }

            return try {
                val total = expectedTotal!!
                val bytes = (1..total)
                    .map { Base64.getDecoder().decode(received[it]!!) }
                    .reduce { acc, chunk -> acc + chunk }

                val jsonStr = String(bytes, Charsets.UTF_8)
                val snapshot = json.decodeFromString<DiagnosticsSnapshot>(jsonStr)
                Result.success(snapshot)
            } catch (e: IllegalArgumentException) {
                Result.failure(IllegalArgumentException("Invalid Base64 data: ${e.message}"))
            } catch (e: kotlinx.serialization.SerializationException) {
                Result.failure(IllegalArgumentException("Invalid snapshot format: ${e.message}"))
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("Failed to reassemble snapshot: ${e.message}"))
            }
        }
    }
}
