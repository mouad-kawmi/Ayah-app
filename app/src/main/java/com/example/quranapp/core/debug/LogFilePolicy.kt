package com.example.quranapp.core.debug

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogFilePolicy {
    const val APP_LOG_PREFIX = "app-"
    const val CRASH_LOG_PREFIX = "crash-"
    const val LOG_FILE_EXTENSION = ".log"
    const val RETENTION_DAYS = 3L
    const val MAX_TOTAL_LOG_BYTES = 10L * 1024L * 1024L // 10 MB

    val RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1000L

    fun dailyFileName(prefix: String, date: Date): String {
        return prefix + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date) + LOG_FILE_EXTENSION
    }

    fun isLogFile(file: File): Boolean = file.isFile && file.name.endsWith(LOG_FILE_EXTENSION)

    fun isExpired(file: File, now: Long = System.currentTimeMillis()): Boolean {
        return now - file.lastModified() > RETENTION_MS
    }
}
