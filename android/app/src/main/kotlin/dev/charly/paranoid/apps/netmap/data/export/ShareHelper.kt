package dev.charly.paranoid.apps.netmap.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {
    fun share(context: Context, content: String, filename: String, mimeType: String) {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, filename)
        file.writeText(content)
        shareFile(context, file, mimeType)
    }

    /** Share an already-written file (used for large streamed exports). */
    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export ${file.name}"))
    }
}
