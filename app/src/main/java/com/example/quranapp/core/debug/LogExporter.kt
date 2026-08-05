package com.example.quranapp.core.debug

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LogExporter {
    private const val TAG = "LogExporter"
    private const val EXPORT_DIR_NAME = "exports"

    suspend fun exportLogs(context: Context): File? = withContext(Dispatchers.IO) {
        runCatching {
            val logsDir = context.getExternalFilesDir("logs") ?: return@withContext null
            val files = logsDir.listFiles()
                ?.filter { LogFilePolicy.isLogFile(it) && !LogFilePolicy.isExpired(it) }
                ?.sortedBy { it.name }
                ?: return@withContext null
            if (files.isEmpty()) return@withContext null

            val exportDir = File(context.getExternalFilesDir(EXPORT_DIR_NAME), "logs")
            exportDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(exportDir, "logs_$timestamp.zip")

            zipFiles(files, zipFile)
            zipFile
        }.getOrElse { e ->
            Log.e(TAG, "Failed to export logs", e)
            null
        }
    }

    private fun zipFiles(files: List<File>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            for (file in files) {
                zip.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
    }
}
