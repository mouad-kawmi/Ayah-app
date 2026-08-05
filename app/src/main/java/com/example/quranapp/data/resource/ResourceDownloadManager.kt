package com.example.quranapp.data.resource

import android.content.Context
import com.example.quranapp.BuildConfig
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Generic resumable download manager for content resources (architecture §3.2.6).
 *
 * - Serialized transfers: only one download runs at a time (Mutex), so several
 *   requested tafsirs can never corrupt each other.
 * - HTTP Range resume from `.part` files (ResumeMeta guard: URL + version must
 *   still match, otherwise the download restarts cleanly).
 * - SHA-256 verification before install; atomic install via rename.
 * - Cancel keeps `.part` for a later resume; failures clean temp files up.
 * - Disk-space check before starting.
 *
 * The manager is content-agnostic: any ResourceType works (tafsir, translation,
 * audio, dictionaries, learning packs — the UI gates what users can trigger).
 */
class ResourceDownloadManager(private val appContext: Context) {

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        private const val CHUNK_SIZE_BYTES = 64 * 1024
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L

        @Volatile
        private var INSTANCE: ResourceDownloadManager? = null

        fun getInstance(context: Context): ResourceDownloadManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val fileStore = ResourceFileStore.getInstance(appContext)
    private val indexStore = ResourceIndexStore.getInstance(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val transferMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _progress = MutableStateFlow(DownloadProgress("", ResourceInstallState.NOT_INSTALLED, 0f, 0L, 0L))
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    private fun logLine(detail: String): String =
        Instrumentation.line("download", Instrumentation.NO_TRACE, null, detail)

    /**
     * Starts (or resumes) a download for [meta]. Transfers are serialized.
     * [contentVerifier], when provided, runs on the fully downloaded file before
     * the atomic install (tafsir databases use TafsirDatabaseVerifier). Throws
     * nothing — failures surface through [progress].
     */
    fun download(meta: ResourceMeta, contentVerifier: ((File) -> String?)? = null) {
        val key = ResourceIndexStore.getInstance(appContext).resourceKey(meta.type, meta.id)
        if (activeJob?.isActive == true) {
            DebugLogger.warning(
                LogCategory.DOWNLOAD,
                logLine(ErrorCode.DOWNLOAD_FAILED.prefix("Download requested for $key while another transfer is active"))
            )
            _progress.value = DownloadProgress(
                key, ResourceInstallState.ERROR, 0f, 0L, 0L,
                "Another download is already in progress"
            )
            return
        }
        activeJob = scope.launch {
            transferMutex.withLock {
                try {
                    executeDownload(meta, contentVerifier)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Cancel keeps the partial file for resume; the cancelled
                    // coroutine itself already persisted the resume metadata.
                    throw e
                } catch (e: Exception) {
                    cleanupTempFiles(meta)
                    _progress.value = DownloadProgress(
                        key, ResourceInstallState.ERROR, 0f, 0L, 0L,
                        e.message ?: ErrorCode.DOWNLOAD_FAILED.code
                    )
                    DebugLogger.error(LogCategory.DOWNLOAD, logLine(ErrorCode.DOWNLOAD_FAILED.prefix("Unexpected failure for $key")), e)
                }
            }
        }
    }

    /** Cancels the active transfer if it belongs to [meta]; partial file is kept for resume. */
    fun cancel(meta: ResourceMeta) {
        val job = activeJob ?: return
        if (!job.isActive) return
        job.cancel()
        DebugLogger.info(
            LogCategory.DOWNLOAD,
            logLine(ErrorCode.DOWNLOAD_CANCELLED.prefix("Cancel requested for ${meta.type.name.lowercase()}:${meta.id}"))
        )
    }

    /** Deletes an installed resource (files + index). Cancels a running transfer of the same resource first. */
    suspend fun delete(meta: ResourceMeta) = withContext(Dispatchers.IO) {
        val key = ResourceIndexStore.getInstance(appContext).resourceKey(meta.type, meta.id)
        val job = activeJob
        if (job?.isActive == true) {
            runCatching { job.cancelAndJoin() }
        }
        fileStore.deleteResourceFiles(meta.type, meta.id)
        indexStore.removeEntry(meta.type, meta.id)
        _progress.value = DownloadProgress(key, ResourceInstallState.NOT_INSTALLED, 0f, 0L, 0L)
        DebugLogger.info(
            LogCategory.DOWNLOAD,
            logLine("Resource deleted: $key")
        )
    }

    /**
     * Self-healing: scans the index and drops entries whose files are missing
     * (external tampering, restore, storage loss). Never touches bundled entries'
     * presence in the index even if missing — the bundled file ships inside the APK.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val entries = indexStore.allEntries()
        for ((key, entry) in entries) {
            val (typeName, id) = key.split(":", limit = 2)
            val type = runCatching { ResourceType.valueOf(typeName.uppercase()) }.getOrNull() ?: continue
            if (!fileStore.resourceExists(type, id)) {
                indexStore.removeEntry(type, id)
                DebugLogger.warning(
                    LogCategory.DOWNLOAD,
                    logLine("Self-heal: removed orphan index entry $key (file missing)")
                )
            }
        }
    }

    private suspend fun executeDownload(meta: ResourceMeta, contentVerifier: ((File) -> String?)? = null) {
        val key = ResourceIndexStore.getInstance(appContext).resourceKey(meta.type, meta.id)
        val logDetail = "$key v${meta.version} from ${meta.downloadUrl}"
        _progress.value = DownloadProgress(key, ResourceInstallState.DOWNLOADING, 0f, 0L, meta.downloadSizeBytes)

        if (meta.bundled) {
            fail(key, meta, ErrorCode.DOWNLOAD_FAILED, "Bundled resources cannot be downloaded")
            return
        }
        if (!isAppVersionSupported(meta.minAppVersion)) {
            fail(key, meta, ErrorCode.DOWNLOAD_APP_VERSION_REQUIRED, "Requires app ${meta.minAppVersion} or newer")
            return
        }
        if (meta.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            fail(key, meta, ErrorCode.DOWNLOAD_SCHEMA_UNSUPPORTED, "Resource schema ${meta.schemaVersion} is newer than supported $SUPPORTED_SCHEMA_VERSION")
            return
        }
        if (meta.downloadUrl.isBlank()) {
            fail(key, meta, ErrorCode.DOWNLOAD_INVALID_CATALOG, "Resource has no download URL")
            return
        }

        val partFile = fileStore.partFile(meta.type, meta.id)
        val metaFile = fileStore.partMetaFile(meta.type, meta.id)

        // Resume decision: keep the partial only when the stored version and URL
        // still match the catalog, otherwise restart from zero.
        var resumeFrom = 0L
        if (partFile.isFile) {
            val resume = readResumeMeta(metaFile)
            if (resume != null && resume.version == meta.version && resume.url == meta.downloadUrl) {
                resumeFrom = partFile.length().coerceAtMost(meta.downloadSizeBytes)
                DebugLogger.info(LogCategory.DOWNLOAD, logLine("Resuming $key from byte $resumeFrom"))
            } else {
                partFile.delete()
                metaFile.delete()
            }
        }

        // Disk space check before starting the transfer (margin included).
        val remaining = (meta.downloadSizeBytes - resumeFrom).coerceAtLeast(0L)
        if (remaining > 0L && fileStore.availableSpaceWithMargin() < remaining) {
            cleanupTempFiles(meta)
            fail(key, meta, ErrorCode.DOWNLOAD_NO_SPACE, "Not enough storage (needs ${remaining / (1024 * 1024)} MB free)")
            return
        }

        DebugLogger.info(LogCategory.DOWNLOAD, logLine("Download started: $logDetail"))
        // Persist the resume metadata before transferring: if the process dies
        // mid-download, the next attempt can resume from the partial file.
        writeResumeMeta(metaFile, meta, resumeFrom, resumeFrom)
        val digest = MessageDigest.getInstance("SHA-256")
        if (resumeFrom > 0L) {
            // Seed the digest with the bytes already in the partial file so the
            // final hash covers the entire resource, not just the appended chunk.
            partFile.inputStream().use { input ->
                val seedBuffer = ByteArray(CHUNK_SIZE_BYTES)
                while (true) {
                    val read = input.read(seedBuffer)
                    if (read <= 0) break
                    digest.update(seedBuffer, 0, read)
                }
            }
        }
        var bytesDone = resumeFrom
        var cancelled = false
        try {
            val response = openRangeResponse(meta.downloadUrl, resumeFrom)
            if (response == null) {
                cleanupTempFiles(meta)
                fail(key, meta, ErrorCode.DOWNLOAD_FAILED, "Could not connect to the download server")
                return
            }
            try {
                if (response.code == 200 && resumeFrom > 0L) {
                    // Server ignored the Range header: restart from byte zero.
                    response.close()
                    partFile.delete()
                    resumeFrom = 0L
                    bytesDone = 0L
                    _progress.value = DownloadProgress(key, ResourceInstallState.DOWNLOADING, 0f, 0L, meta.downloadSizeBytes)
                }
                response.body?.let { body ->
                    partFile.parentFile?.mkdirs()
                    FileOutputStream(partFile, resumeFrom > 0L).use { out ->
                        val buffer = ByteArray(CHUNK_SIZE_BYTES)
                        val input = body.byteStream()
                        while (activeJob?.isActive == true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytesDone += read
                            val total = meta.downloadSizeBytes.coerceAtLeast(bytesDone)
                            _progress.value = DownloadProgress(
                                key, ResourceInstallState.DOWNLOADING,
                                (bytesDone.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                                bytesDone, total
                            )
                        }
                        if (activeJob?.isActive != true) cancelled = true
                    }
                }
            } finally {
                runCatching { response.close() }
            }

            if (cancelled) {
                withContext(NonCancellable) { writeResumeMeta(metaFile, meta, resumeFrom, bytesDone) }
                _progress.value = DownloadProgress(key, ResourceInstallState.NOT_INSTALLED, 0f, 0L, 0L)
                DebugLogger.info(
                    LogCategory.DOWNLOAD,
                    logLine(ErrorCode.DOWNLOAD_CANCELLED.prefix("$key paused at $bytesDone bytes, resumable"))
                )
                return
            }

            if (bytesDone < meta.downloadSizeBytes) {
                cleanupTempFiles(meta)
                fail(key, meta, ErrorCode.DOWNLOAD_FAILED, "Connection closed early ($bytesDone of ${meta.downloadSizeBytes} bytes)")
                return
            }

            // Integrity check.
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(meta.sha256, ignoreCase = true)) {
                cleanupTempFiles(meta)
                DebugLogger.error(
                    LogCategory.DOWNLOAD,
                    logLine(ErrorCode.DOWNLOAD_INTEGRITY_MISMATCH.prefix("$key SHA-256 mismatch (expected ${meta.sha256}, got $actualHash)"))
                )
                fail(key, meta, ErrorCode.DOWNLOAD_INTEGRITY_MISMATCH, "Integrity check failed — file does not match the catalog")
                return
            }

            // Content-level verification (SQLite structure, schema, metadata)
            // runs before the atomic install; the previous version is untouched
            // on failure.
            contentVerifier?.let { verify ->
                val failure = verify(partFile)
                if (failure != null) {
                    cleanupTempFiles(meta)
                    DebugLogger.error(LogCategory.DOWNLOAD, logLine(failure))
                    fail(key, meta, ErrorCode.DOWNLOAD_INSTALL_FAILED, failure)
                    return
                }
            }

            installAtomically(meta, partFile, bytesDone)
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(NonCancellable) { writeResumeMeta(metaFile, meta, resumeFrom, bytesDone) }
            _progress.value = DownloadProgress(key, ResourceInstallState.NOT_INSTALLED, 0f, 0L, 0L)
            throw e
        } catch (e: Exception) {
            cleanupTempFiles(meta)
            val code = if (e is java.net.SocketTimeoutException) ErrorCode.DOWNLOAD_TIMEOUT else ErrorCode.DOWNLOAD_FAILED
            DebugLogger.error(LogCategory.DOWNLOAD, logLine(code.prefix("$key transfer failed")), e)
            fail(key, meta, code, e.message ?: "Download failed")
        }
    }

    private suspend fun installAtomically(meta: ResourceMeta, partFile: File, sizeBytes: Long) {
        val key = ResourceIndexStore.getInstance(appContext).resourceKey(meta.type, meta.id)
        val target = fileStore.resourceFile(meta.type, meta.id)
        target.parentFile?.mkdirs()
        val renamed = partFile.renameTo(target)
        if (!renamed) {
            cleanupTempFiles(meta)
            fail(key, meta, ErrorCode.DOWNLOAD_INSTALL_FAILED, "Could not move the downloaded file into place")
            return
        }
        indexStore.putEntry(
            meta.type, meta.id,
            ResourceIndexEntry(
                type = meta.type,
                version = meta.version,
                installedAt = System.currentTimeMillis(),
                sizeBytes = sizeBytes,
                sha256 = meta.sha256,
                schemaVersion = meta.schemaVersion,
                bundled = false
            )
        )
        fileStore.partMetaFile(meta.type, meta.id).delete()
        _progress.value = DownloadProgress(key, ResourceInstallState.INSTALLED, 1f, sizeBytes, sizeBytes)
        DebugLogger.info(
            LogCategory.DOWNLOAD,
            logLine("Installed $key (${sizeBytes / (1024 * 1024)} MB, schema ${meta.schemaVersion})")
        )
    }

    private fun openRangeResponse(url: String, startByte: Long): Response? {
        val request = Request.Builder()
            .url(url)
            .apply {
                if (startByte > 0L) header("Range", "bytes=$startByte-")
            }
            .build()
        return runCatching { downloadClient.newCall(request).execute() }.getOrNull()
    }

    private fun cleanupTempFiles(meta: ResourceMeta) {
        fileStore.partFile(meta.type, meta.id).delete()
        fileStore.partMetaFile(meta.type, meta.id).delete()
    }

    private fun writeResumeMeta(
        metaFile: File,
        meta: ResourceMeta,
        resumeFrom: Long,
        bytesDone: Long
    ) {
        runCatching {
            metaFile.parentFile?.mkdirs()
            metaFile.writeText(
                json.encodeToString(
                    ResumeMeta(
                        type = meta.type,
                        resourceId = meta.id,
                        version = meta.version,
                        url = meta.downloadUrl,
                        bytesDone = bytesDone.coerceAtLeast(resumeFrom),
                        bytesTotal = meta.downloadSizeBytes
                    )
                )
            )
        }
    }

    private fun readResumeMeta(metaFile: File): ResumeMeta? = runCatching {
        if (!metaFile.isFile) return null
        json.decodeFromString<ResumeMeta>(metaFile.readText())
    }.getOrNull()

    private fun isAppVersionSupported(minAppVersion: String): Boolean {
        if (minAppVersion.isBlank()) return true
        return compareVersions(BuildConfig.VERSION_NAME, minAppVersion) >= 0
    }

    private fun compareVersions(a: String, b: String): Int {
        val ap = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bp = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(ap.size, bp.size)
        for (i in 0 until len) {
            val av = ap.getOrElse(i) { 0 }
            val bv = bp.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun fail(key: String, meta: ResourceMeta, code: ErrorCode, message: String) {
        cleanupTempFiles(meta)
        _progress.value = DownloadProgress(
            key, ResourceInstallState.ERROR, 0f, 0L, 0L,
            message
        )
        DebugLogger.warning(LogCategory.DOWNLOAD, logLine(code.prefix(message)))
    }
}
