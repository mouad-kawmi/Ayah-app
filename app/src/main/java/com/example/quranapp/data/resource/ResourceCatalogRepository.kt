package com.example.quranapp.data.resource

import android.content.Context
import com.example.quranapp.BuildConfig
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class CatalogRefreshResult(val success: Boolean, val catalog: ResourceCatalog)

/**
 * Remote + embedded resource catalog (architecture §3.2.4).
 *
 * The embedded catalog guarantees full offline operation; the remote catalog
 * (our own infrastructure only, base URL from BuildConfig.RESOURCE_BASE_URL)
 * is fetched lazily, cached on disk and in memory, and merged over the embedded
 * entries. Bundled entries are never removed and can never be deleted.
 */
class ResourceCatalogRepository(private val appContext: Context) {

    companion object {
        const val CATALOG_SCHEMA_VERSION = 1
        private const val CATALOG_CACHE_FILE_NAME = "catalog-cache.json"
        private const val REMOTE_CATALOG_PATH = "resources/catalog.json"

        @Volatile
        private var INSTANCE: ResourceCatalogRepository? = null

        fun getInstance(context: Context): ResourceCatalogRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceCatalogRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    val baseUrl: String = BuildConfig.RESOURCE_BASE_URL

    fun remoteEnabled(): Boolean = baseUrl.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }
    private val catalogClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val embeddedCatalog: ResourceCatalog = ResourceCatalog(
        schemaVersion = CATALOG_SCHEMA_VERSION,
        generatedAt = "2026-08-02T00:00:00Z",
        resources = listOf(
            ResourceMeta(
                id = TafsirBundled.ID,
                type = ResourceType.TAFSIR,
                name = TafsirBundled.NAME,
                nameLatin = TafsirBundled.NAME_LATIN,
                author = TafsirBundled.AUTHOR,
                license = "Free",
                version = TafsirBundled.VERSION,
                language = TafsirBundled.LANGUAGE,
                lastUpdated = "2026-08-02T00:00:00Z",
                minAppVersion = "1.0",
                schemaVersion = 1,
                downloadSizeBytes = TafsirBundled.SIZE_BYTES,
                installedSizeBytes = TafsirBundled.SIZE_BYTES,
                downloadUrl = "",
                sha256 = TafsirBundled.SHA256,
                bundled = true
            )
        )
    )

    @Volatile
    private var remoteCatalog: ResourceCatalog? = null

    @Volatile
    private var diskCacheLoaded = false

    private fun logLine(detail: String): String =
        Instrumentation.line("catalog", Instrumentation.NO_TRACE, null, detail)

    /**
     * Merged catalog (embedded + remote). Never performs network work; returns
     * the last known state. Safe to call from the UI thread.
     */
    suspend fun getCatalog(): ResourceCatalog = withContext(Dispatchers.IO) {
        val remote = remoteCatalog ?: loadDiskCacheIfNeeded()
        merge(embeddedCatalog, remote)
    }

    /**
     * Fetches the remote catalog from our infrastructure, refreshes the disk
     * cache and the in-memory state. Never blocks the caller (suspend).
     * On failure the previous state is preserved and [CatalogRefreshResult.success]
     * is false — used by the UI to show the offline banner.
     */
    suspend fun refresh(): CatalogRefreshResult = withContext(Dispatchers.IO) {
        if (!remoteEnabled()) {
            return@withContext CatalogRefreshResult(true, merge(embeddedCatalog, null))
        }
        val url = baseUrl + REMOTE_CATALOG_PATH
        try {
            val request = Request.Builder().url(url).build()
            catalogClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DebugLogger.warning(
                        LogCategory.DOWNLOAD,
                        logLine(ErrorCode.DOWNLOAD_INVALID_CATALOG.prefix("Catalog fetch failed with HTTP ${response.code}"))
                    )
                    return@withContext CatalogRefreshResult(false, merge(embeddedCatalog, remoteCatalog))
                }
                val body = response.body?.string() ?: return@withContext CatalogRefreshResult(false, merge(embeddedCatalog, remoteCatalog))
                val parsed = runCatching { json.decodeFromString<ResourceCatalog>(body) }.getOrNull()
                if (parsed == null || parsed.schemaVersion != CATALOG_SCHEMA_VERSION || parsed.resources.isEmpty()) {
                    DebugLogger.warning(
                        LogCategory.DOWNLOAD,
                        logLine(ErrorCode.DOWNLOAD_INVALID_CATALOG.prefix("Catalog malformed (schema=${parsed?.schemaVersion}, resources=${parsed?.resources?.size})"))
                    )
                    return@withContext CatalogRefreshResult(false, merge(embeddedCatalog, remoteCatalog))
                }
                // Entry-level validation (provenance §13.1, sha256, URL, schema,
                // duplicates): invalid entries are dropped, never crash the app.
                val validated = ResourceCatalogValidator.validate(parsed)
                if (validated.catalog.resources.isEmpty()) {
                    DebugLogger.warning(
                        LogCategory.DOWNLOAD,
                        logLine(ErrorCode.DOWNLOAD_INVALID_CATALOG.prefix("Catalog invalid: all ${parsed.resources.size} entries dropped"))
                    )
                    return@withContext CatalogRefreshResult(false, merge(embeddedCatalog, remoteCatalog))
                }
                if (validated.droppedEntries > 0) {
                    DebugLogger.warning(
                        LogCategory.DOWNLOAD,
                        logLine(ErrorCode.DOWNLOAD_INVALID_CATALOG.prefix("Catalog: ${validated.droppedEntries} invalid entrie(s) dropped (${validated.catalog.resources.size} kept)"))
                    )
                }
                remoteCatalog = validated.catalog
                writeDiskCache(validated.catalog)
                DebugLogger.info(
                    LogCategory.DOWNLOAD,
                    logLine("Catalog downloaded (${validated.catalog.resources.size} entries, generated ${validated.catalog.generatedAt})")
                )
                CatalogRefreshResult(true, merge(embeddedCatalog, validated.catalog))
            }
        } catch (e: Exception) {
            DebugLogger.warning(
                LogCategory.DOWNLOAD,
                logLine(ErrorCode.DOWNLOAD_FAILED.prefix("Catalog refresh failed")),
                e
            )
            CatalogRefreshResult(false, merge(embeddedCatalog, remoteCatalog))
        }
    }

    private fun loadDiskCacheIfNeeded(): ResourceCatalog? {
        if (diskCacheLoaded) return remoteCatalog
        diskCacheLoaded = true
        val file = cacheFile()
        if (!file.exists()) return remoteCatalog
        return try {
            val parsed = json.decodeFromString<ResourceCatalog>(file.readText())
            val validated = ResourceCatalogValidator.validate(parsed)
            remoteCatalog = if (validated.catalog.resources.isEmpty()) {
                DebugLogger.warning(
                    LogCategory.DOWNLOAD,
                    logLine(ErrorCode.DOWNLOAD_INVALID_CATALOG.prefix("Catalog cache: all entries invalid — keeping previous state"))
                )
                remoteCatalog
            } else {
                validated.catalog
            }
            DebugLogger.info(LogCategory.DOWNLOAD, logLine("Catalog cache hit"))
            remoteCatalog
        } catch (e: Exception) {
            DebugLogger.warning(
                LogCategory.DOWNLOAD,
                logLine(ErrorCode.DOWNLOAD_INVALID_CATALOG.prefix("Catalog cache unreadable")),
                e
            )
            remoteCatalog
        }
    }

    private fun writeDiskCache(catalog: ResourceCatalog) {
        val file = cacheFile()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(catalog))
    }

    private fun cacheFile(): File =
        File(ResourceFileStore.getInstance(appContext).resourcesRoot(), CATALOG_CACHE_FILE_NAME)

    private fun merge(embedded: ResourceCatalog, remote: ResourceCatalog?): ResourceCatalog {
        val merged = linkedMapOf<String, ResourceMeta>()
        for (entry in embedded.resources) {
            merged[entry.type.name.lowercase() + ":" + entry.id] = entry
        }
        remote?.resources?.forEach { entry ->
            val key = entry.type.name.lowercase() + ":" + entry.id
            val existing = merged[key]
            if (existing != null && existing.bundled) {
                // Bundled entries stay bundled and never lose the embedded base,
                // but a newer published version replaces the fields (update path).
                merged[key] = entry.copy(bundled = true)
            } else {
                merged[key] = entry
            }
        }
        return ResourceCatalog(
            schemaVersion = CATALOG_SCHEMA_VERSION,
            generatedAt = remote?.generatedAt ?: embedded.generatedAt,
            resources = merged.values.toList()
        )
    }
}
