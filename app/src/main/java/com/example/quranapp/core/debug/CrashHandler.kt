package com.example.quranapp.core.debug

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {
    private const val TAG = "CrashHandler"

    private var appContext: Context? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            reportCrash(thread, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun reportCrash(thread: Thread, throwable: Throwable) {
        try {
            val report = buildCrashReport(thread, throwable)
            appendToCrashLog(report)
            DebugLogger.error(LogCategory.APP, ErrorCode.UNCAUGHT_EXCEPTION.prefix("Uncaught exception on thread=${thread.name}"), throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture crash report", e)
        }
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val memInfo = runCatching { memoryInfo() }.getOrElse { "unavailable" }
        return buildString {
            append("==== CRASH ====\n")
            append("time=").append(timestamp).append('\n')
            append("session=").append(DiagnosticsCollector.sessionId).append('\n')
            append("thread=").append(thread.name).append('\n')
            append("android=").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("memory=").append(memInfo).append('\n')
            append("stacktrace:\n").append(stackTrace(throwable)).append('\n')
            append("================\n")
        }
    }

    private fun memoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val heapMax = runtime.maxMemory() / (1024 * 1024)
        val heapAllocated = runtime.totalMemory() / (1024 * 1024)
        val heapFree = runtime.freeMemory() / (1024 * 1024)
        return "native=${nativeHeap}MB javaMax=${heapMax}MB javaAllocated=${heapAllocated}MB javaFree=${heapFree}MB"
    }

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun appendToCrashLog(report: String) {
        val context = appContext ?: return
        val dir = context.getExternalFilesDir("logs") ?: return
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, LogFilePolicy.dailyFileName(LogFilePolicy.CRASH_LOG_PREFIX, Date()))
        file.appendText(report)
        val now = System.currentTimeMillis()
        dir.listFiles()
            ?.filter { LogFilePolicy.isLogFile(it) && it.name.startsWith(LogFilePolicy.CRASH_LOG_PREFIX) && LogFilePolicy.isExpired(it, now) }
            ?.forEach { it.delete() }
    }
}
