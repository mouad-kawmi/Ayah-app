package com.example.quranapp.core.debug

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DebugLogger {
    private const val TAG = "DebugLogger"
    private const val TRIM_CHECK_INTERVAL_MS = 5_000L
    private const val MAINTENANCE_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val MAX_CONSECUTIVE_FAILURES = 3

    private val logFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val writerExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "debug-logger").apply { isDaemon = true } }
    private val maintenanceExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "log-maintenance").apply { isDaemon = true } }

    @Volatile
    private var appContext: Context? = null
    private var logFile: File? = null
    private var writer: BufferedWriter? = null
    private var maintenanceScheduled = false
    private var lastTrimMs = 0L

    @Volatile
    private var loggingDisabled = false
    @Volatile
    private var consecutiveFailures = 0

    // Startup/init messages that must be emitted at most once per application
    // session; "real" reinitialization (a fresh process) resets this set naturally.
    private val onceLoggedKeys: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

    fun initialize(context: Context) {
        appContext = context.applicationContext
        scheduleMaintenance()
    }

    @Synchronized
    private fun scheduleMaintenance() {
        if (maintenanceScheduled) return
        maintenanceScheduled = true
        maintenanceExecutor.scheduleWithFixedDelay(
            { runCleanup() },
            0L,
            MAINTENANCE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun debug(category: LogCategory, message: String) = write("DEBUG", category, message, null)

    fun debug(category: LogCategory, message: String, throwable: Throwable) = write("DEBUG", category, message, throwable)

    fun info(category: LogCategory, message: String) = write("INFO", category, message, null)

    fun info(category: LogCategory, message: String, throwable: Throwable) = write("INFO", category, message, throwable)

    fun warning(category: LogCategory, message: String) = write("WARN", category, message, null)

    fun warning(category: LogCategory, message: String, throwable: Throwable) = write("WARN", category, message, throwable)

    fun error(category: LogCategory, message: String) = write("ERROR", category, message, null)

    fun error(category: LogCategory, message: String, throwable: Throwable) = write("ERROR", category, message, throwable)

    /**
     * Emits an INFO message at most once per application session for the given
     * [key]. Intended for startup/reinitialization messages that would otherwise
     * be repeated by idempotent init paths (e.g. "State machine initialized").
     * A new process (fresh session) logs the message again.
     */
    fun logOnce(category: LogCategory, key: String, message: String) {
        if (appContext == null || loggingDisabled) return
        val dedupKey = "${category.name}|$key"
        if (!onceLoggedKeys.add(dedupKey)) return
        write("INFO", category, message, null)
    }

    private fun write(level: String, category: LogCategory, message: String, throwable: Throwable?) {
        if (appContext == null || loggingDisabled) return
        val line = buildString {
            append(logFormat.format(Date()))
            append(" | session=").append(DiagnosticsCollector.sessionId)
            append(" | thread=").append(Thread.currentThread().name)
            append(" | ").append(level)
            append(" | category=").append(category.name)
            append(" | ").append(message)
            if (throwable != null) {
                append('\n').append(formatThrowable(throwable))
            }
        }
        enqueue { writeLine(line) }
    }

    private fun enqueue(block: () -> Unit) {
        try {
            writerExecutor.execute(block)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue log write", e)
        }
    }

    private fun writeLine(line: String) {
        if (loggingDisabled) return
        try {
            val file = ensureWriterOpen() ?: return
            val buffer = getWriter(file)
            buffer.write(line)
            buffer.newLine()
            buffer.flush()
            consecutiveFailures = 0
            maybeTrim(file)
        } catch (e: Exception) {
            handleWriteFailure(e)
        }
    }

    private fun handleWriteFailure(e: Exception) {
        consecutiveFailures++
        Log.e(TAG, "Log write failed (${consecutiveFailures}/$MAX_CONSECUTIVE_FAILURES): ${e.javaClass.simpleName}: ${e.message}")
        runCatching { closeWriter() }
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            loggingDisabled = true
            Log.e(TAG, "DebugLogger disabled for this session after repeated write failures")
        }
    }

    private fun ensureWriterOpen(): File? {
        val dir = appContext?.getExternalFilesDir("logs") ?: return null
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, LogFilePolicy.dailyFileName(LogFilePolicy.APP_LOG_PREFIX, Date()))
        if (logFile != file) {
            closeWriter()
            logFile = file
            writer = BufferedWriter(FileWriter(file, true))
        }
        return file
    }

    private fun getWriter(file: File): BufferedWriter {
        return writer ?: BufferedWriter(FileWriter(file, true)).also { writer = it; logFile = file }
    }

    private fun maybeTrim(active: File) {
        val now = System.currentTimeMillis()
        if (now - lastTrimMs < TRIM_CHECK_INTERVAL_MS && active.length() <= LogFilePolicy.MAX_TOTAL_LOG_BYTES) return
        lastTrimMs = now
        trimToBudget()
    }

    private fun trimToBudget() {
        val context = appContext ?: return
        val dir = context.getExternalFilesDir("logs") ?: return
        if (!dir.exists()) return
        val now = System.currentTimeMillis()
        val activeNames = setOf(
            LogFilePolicy.dailyFileName(LogFilePolicy.APP_LOG_PREFIX, Date(now)),
            LogFilePolicy.dailyFileName(LogFilePolicy.CRASH_LOG_PREFIX, Date(now))
        )
        var files = dir.listFiles()
            ?.filter { LogFilePolicy.isLogFile(it) && !LogFilePolicy.isExpired(it, now) }
            ?.sortedBy { it.lastModified() }
            ?: return
        while (files.sumOf { it.length() } > LogFilePolicy.MAX_TOTAL_LOG_BYTES) {
            val victim = files.firstOrNull { it.name !in activeNames } ?: break
            victim.delete()
            files = dir.listFiles()
                ?.filter { LogFilePolicy.isLogFile(it) && !LogFilePolicy.isExpired(it, now) }
                ?.sortedBy { it.lastModified() }
                ?: return
        }
    }

    private fun runCleanup() {
        runCatching {
            val context = appContext ?: return@runCatching
            val dir = context.getExternalFilesDir("logs") ?: return@runCatching
            if (!dir.exists()) return@runCatching
            val now = System.currentTimeMillis()
            dir.listFiles()
                ?.filter { LogFilePolicy.isLogFile(it) && LogFilePolicy.isExpired(it, now) }
                ?.forEach { it.delete() }
            trimToBudget()
            info(
                LogCategory.APP,
                "Log maintenance: retention=${LogFilePolicy.RETENTION_DAYS} days, cap=${LogFilePolicy.MAX_TOTAL_LOG_BYTES / (1024 * 1024)} MB, expired purged"
            )
        }.onFailure { e ->
            Log.e(TAG, "Log maintenance failed", e)
        }
    }

    fun retainedLogFiles(context: Context): List<File> = runCatching {
        val dir = context.getExternalFilesDir("logs") ?: return@runCatching emptyList()
        val now = System.currentTimeMillis()
        dir.listFiles()
            ?.filter { LogFilePolicy.isLogFile(it) && !LogFilePolicy.isExpired(it, now) }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
            ?: emptyList()
    }.getOrDefault(emptyList())

    suspend fun clearAllLogs(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = context.getExternalFilesDir("logs") ?: return@runCatching false
            waitForWriterClose()
            val cleared = dir.listFiles()?.all { it.delete() } ?: false
            info(LogCategory.APP, "Logs cleared by developer")
            cleared
        }.getOrDefault(false)
    }

    suspend fun deleteLogFile(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = context.getExternalFilesDir("logs") ?: return@runCatching false
            val activeName = LogFilePolicy.dailyFileName(LogFilePolicy.APP_LOG_PREFIX, Date())
            if (fileName == activeName) waitForWriterClose()
            File(dir, fileName).delete()
        }.getOrDefault(false)
    }

    private fun waitForWriterClose() {
        val done = CompletableFuture<Unit>()
        enqueue {
            closeWriter()
            done.complete(Unit)
        }
        runCatching { done.get(2, TimeUnit.SECONDS) }
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
            // Ignore: best-effort close during rotation.
        }
        writer = null
        logFile = null
    }

    private fun formatThrowable(throwable: Throwable): String {
        val stack = java.io.StringWriter().also { throwable.printStackTrace(java.io.PrintWriter(it)) }
        return stack.toString()
    }

    fun shutdown() {
        enqueue { closeWriter() }
        writerExecutor.shutdown()
        try {
            writerExecutor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
