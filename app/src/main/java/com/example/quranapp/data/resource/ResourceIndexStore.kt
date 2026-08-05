package com.example.quranapp.data.resource

import android.content.Context
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent installation index (architecture §3.2.3).
 *
 * Single source of truth for what is installed: one entry per resource key
 * "type:id", stored atomically in `files/resources/index.json`. The index holds
 * metadata only — content lives in the resource files themselves.
 */
class ResourceIndexStore(private val appContext: Context) {

    companion object {
        private const val INDEX_FILE_NAME = "index.json"
        private const val INDEX_TMP_NAME = "index.json.tmp"

        @Volatile
        private var INSTANCE: ResourceIndexStore? = null

        fun getInstance(context: Context): ResourceIndexStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceIndexStore(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val writeMutex = Mutex()

    private var loaded = false
    private var cachedIndex = ResourceIndex()

    private fun indexFile(): File =
        File(ResourceFileStore.getInstance(appContext).resourcesRoot(), INDEX_FILE_NAME)

    fun resourceKey(type: ResourceType, resourceId: String): String = "${type.name.lowercase()}:$resourceId"

    suspend fun load(): ResourceIndex = withContext(Dispatchers.IO) {
        if (loaded) return@withContext cachedIndex
        val file = indexFile()
        if (!file.exists()) {
            loaded = true
            cachedIndex = ResourceIndex()
            return@withContext cachedIndex
        }
        try {
            cachedIndex = json.decodeFromString<ResourceIndex>(file.readText())
        } catch (e: Exception) {
            DebugLogger.warning(
                LogCategory.CACHE,
                Instrumentation.line("index", Instrumentation.NO_TRACE, null, "Resource index corrupted — starting fresh"),
                e
            )
            cachedIndex = ResourceIndex()
        }
        loaded = true
        cachedIndex
    }

    suspend fun entry(type: ResourceType, resourceId: String): ResourceIndexEntry? =
        load().resources[resourceKey(type, resourceId)]

    suspend fun installedVersion(type: ResourceType, resourceId: String): String? =
        entry(type, resourceId)?.version

    suspend fun isBundled(type: ResourceType, resourceId: String): Boolean =
        entry(type, resourceId)?.bundled == true

    suspend fun installedSize(type: ResourceType, resourceId: String): Long =
        entry(type, resourceId)?.sizeBytes ?: 0L

    suspend fun putEntry(type: ResourceType, resourceId: String, entry: ResourceIndexEntry) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                load()
                cachedIndex = cachedIndex.copy(
                    resources = cachedIndex.resources + (resourceKey(type, resourceId) to entry)
                )
                persistLocked()
            }
        }
    }

    suspend fun removeEntry(type: ResourceType, resourceId: String) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                load()
                cachedIndex = cachedIndex.copy(
                    resources = cachedIndex.resources - resourceKey(type, resourceId)
                )
                persistLocked()
            }
        }
    }

    suspend fun allEntries(): Map<String, ResourceIndexEntry> = load().resources

    private fun persistLocked() {
        val file = indexFile()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, INDEX_TMP_NAME)
        tmp.writeText(json.encodeToString(cachedIndex))
        if (!tmp.renameTo(file)) {
            tmp.delete()
            file.writeText(json.encodeToString(cachedIndex))
        }
    }
}
