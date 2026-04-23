package dev.charly.paranoid.apps.netdiag.exchange

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshot
import kotlinx.serialization.json.Json
import java.io.File

object SnapshotFileExchange {

    private const val MAX_IMPORT_BYTES = 1_000_000L // 1 MB
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Export a snapshot as a JSON file via the system share sheet.
     * Returns the share intent (caller is responsible for startActivity).
     */
    fun createExportIntent(context: Context, snapshot: DiagnosticsSnapshot): Intent {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "netdiag-${snapshot.deviceLabel}-${snapshot.id.take(8)}.json"
        val file = File(exportDir, fileName)
        val jsonStr = json.encodeToString(DiagnosticsSnapshot.serializer(), snapshot)
        file.writeText(jsonStr, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Import a snapshot from a content URI.
     * Returns Result.success with the snapshot, or Result.failure with a user-friendly error.
     */
    fun importFromUri(context: Context, uri: Uri): Result<DiagnosticsSnapshot> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Cannot open file"))

            inputStream.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_IMPORT_BYTES) {
                    return Result.failure(
                        IllegalArgumentException("File too large (${bytes.size / 1024} KB). Maximum is 1 MB.")
                    )
                }

                val jsonStr = String(bytes, Charsets.UTF_8)
                val snapshot = json.decodeFromString<DiagnosticsSnapshot>(jsonStr)
                Result.success(snapshot)
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            Result.failure(IllegalArgumentException("Invalid snapshot format: not a valid NetDiag JSON file"))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Failed to import file: ${e.message}"))
        }
    }
}
