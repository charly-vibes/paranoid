package dev.charly.paranoid.apps.usageaudit

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object UsageAuditShare {
    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }

    fun shareCsv(context: Context, csv: String, filename: String) {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, filename)
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $filename"))
    }
}
