package com.example.quranapp.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object DevToolsFileUtils {

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (file.extension == "zip") "application/zip" else "text/plain"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة السجلات"))
    }
}
