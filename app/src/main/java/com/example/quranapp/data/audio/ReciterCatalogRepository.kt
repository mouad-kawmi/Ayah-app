package com.example.quranapp.data.audio

import android.content.Context
import android.util.Log
import com.example.quranapp.domain.model.Reciter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable
data class CachedReciter(
    val id: Int,
    val name: String,
    val style: String? = null,
    val translatedName: String? = null,
    val supportsVerseTimings: Boolean? = null
)

@Serializable
data class ReciterCatalogCache(
    val fetchedAtMs: Long,
    val reciters: List<CachedReciter>
)

class ReciterCatalogRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile: File
        get() = File(context.filesDir, "reciters_catalog_cache.json")
    private val fetchMutex = Mutex()

    companion object {
        private const val TAG = "ReciterCatalog"
        private const val CATALOG_URL = "https://api.qurancdn.com/api/qdc/audio/reciters"
        private const val PROBE_URL_TEMPLATE =
            "https://api.qurancdn.com/api/qdc/audio/reciters/%d/audio_files?chapter=1&segments=true"
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000L // 7 days
    }

    /** Synchronous cached read — safe to call on the main thread at startup. */
    fun getCachedCatalog(): List<Reciter> {
        val cached = readCache() ?: return emptyList()
        return cached.reciters.map { it.toReciter() }.displayable()
    }

    /** Cache-first load with periodic refresh. Blocks only when no fresh cache exists. */
    suspend fun loadCatalog(): List<Reciter> = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs < REFRESH_INTERVAL_MS) {
            Log.i(TAG, "Using fresh cached catalog (${cached.reciters.size} reciters)")
            return@withContext cached.reciters.map { it.toReciter() }.displayable()
        }

        fetchAndCache(cached)
    }

    /** Force a refresh; falls back to cache on failure. */
    suspend fun refreshCatalog(): List<Reciter> = withContext(Dispatchers.IO) {
        fetchAndCache(readCache())
    }

    /**
     * Fetches the catalog once even if multiple callers request it concurrently.
     * Falls back to the existing cache on any failure.
     */
    private suspend fun fetchAndCache(cached: ReciterCatalogCache?): List<Reciter> {
        return fetchMutex.withLock {
            // Re-check the cache inside the lock: another caller may have refreshed it.
            val latest = readCache()
            val effective = latest ?: cached
            if (effective != null && System.currentTimeMillis() - effective.fetchedAtMs < REFRESH_INTERVAL_MS) {
                effective.reciters.map { it.toReciter() }.displayable()
            } else {
                val fetched = try {
                    fetchCatalog()
                } catch (e: Exception) {
                    Log.e(TAG, "Catalog fetch failed: ${e.message}")
                    emptyList()
                }

                if (fetched.isNotEmpty()) {
                    val merged = mergeSupportFlags(effective?.reciters ?: emptyList(), fetched)
                    val keepable = merged.filter { !isKidsRepeatStyle(it.style) }
                    writeCache(ReciterCatalogCache(System.currentTimeMillis(), keepable))
                    Log.i(TAG, "Fetched fresh catalog (${fetched.size} reciters)")
                    keepable.map { it.toReciter() }
                } else {
                    effective?.reciters?.map { it.toReciter() }?.displayable() ?: emptyList()
                }
            }
        }
    }

    /**
     * Probes the QDC audio_files endpoint for a reciter and determines whether
     * verse_timings are available.
     *
     * @return true when verse_timings were verified, false when QDC responded
     *         successfully but verse_timings are genuinely absent, null when the
     *         result could not be determined (network error, timeout, HTTP
     *         failure, or parsing error). A null result never overwrites a
     *         previously known value.
     */
    suspend fun probeVerseTimingsSupport(reciterId: Int): Boolean? = withContext(Dispatchers.IO) {
        val result = try {
            val url = URL(String.format(PROBE_URL_TEMPLATE, reciterId))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "Probe HTTP $responseCode for reciter $reciterId")
                conn.disconnect()
                null
            } else {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val root = json.parseToJsonElement(response).jsonObject
                val audioFileObj = root["audio_files"]?.jsonArray?.firstOrNull()?.jsonObject ?: root
                val verseTimings = audioFileObj["verse_timings"]?.jsonArray ?: JsonArray(emptyList())
                verseTimings.isNotEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Probe failed for reciter $reciterId: ${e.message}")
            null
        }

        if (result != null) {
            setVerseTimingsSupport(reciterId, result)
            Log.i(TAG, "Probe reciter=$reciterId supportsVerseTimings=$result")
        } else {
            Log.i(TAG, "Probe reciter=$reciterId indeterminate (kept previous knowledge)")
        }
        result
    }

    /**
     * Persist a known verse_timings flag for a reciter. Never called with null.
     * Serialized with catalog refresh so concurrent updates never lose data.
     */
    suspend fun setVerseTimingsSupport(reciterId: Int, supported: Boolean) {
        fetchMutex.withLock {
            val cached = readCache() ?: return
            val updated = cached.reciters.map {
                if (it.id == reciterId) it.copy(supportsVerseTimings = supported) else it
            }
            writeCache(cached.copy(reciters = updated))
            Log.i(TAG, "reciter $reciterId supportsVerseTimings=$supported")
        }
    }

    private fun fetchCatalog(): List<Reciter> {
        val url = URL(CATALOG_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        if (conn.responseCode != 200) {
            Log.w(TAG, "Catalog HTTP ${conn.responseCode}")
            conn.disconnect()
            return emptyList()
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return parseCatalog(body)
    }

    private fun parseCatalog(body: String): List<Reciter> {
        val root = json.parseToJsonElement(body).jsonObject
        val reciters = root["reciters"]?.jsonArray
            ?: root["recitations"]?.jsonArray
            ?: return emptyList()

        return reciters.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val translatedName = obj["translated_name"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                    ?: obj["translated_name"]?.jsonPrimitive?.content
                val style = obj["style"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                    ?: obj["style"]?.jsonPrimitive?.content
                Reciter(id = id, name = name, style = style, translatedName = translatedName)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.id }
    }

    private fun mergeSupportFlags(
        old: List<CachedReciter>,
        fresh: List<Reciter>
    ): List<CachedReciter> {
        val oldById = old.associateBy { it.id }
        return fresh.map { r ->
            val prev = oldById[r.id]
            CachedReciter(
                id = r.id,
                name = r.name,
                style = r.style,
                translatedName = r.translatedName,
                supportsVerseTimings = prev?.supportsVerseTimings
            )
        }
    }

    private fun readCache(): ReciterCatalogCache? {
        return try {
            val file = cacheFile
            if (!file.exists()) return null
            json.decodeFromString<ReciterCatalogCache>(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Cache read failed: ${e.message}")
            null
        }
    }

    private fun writeCache(cache: ReciterCatalogCache) {
        try {
            val file = cacheFile
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(cache))
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                Log.w(TAG, "Atomic rename failed, falling back to direct write")
                file.writeText(json.encodeToString(cache))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache write failed: ${e.message}")
        }
    }

    private fun CachedReciter.toReciter(): Reciter = Reciter(
        id = id,
        name = name,
        style = style,
        translatedName = translatedName,
        supportsVerseTimings = supportsVerseTimings
    )

    /** True when a reciter entry is a kids/repetition variant (e.g. "Kids repeat"). */
    private fun isKidsRepeatStyle(style: String?): Boolean {
        val s = style.orEmpty().trim().lowercase()
        return s.contains("kids") ||
            s.contains("child") ||
            s.contains("repeat") ||
            s.contains("اطفال") ||
            s.contains("أطفال") ||
            s.contains("طفل") ||
            s.contains("ترديد")
    }

    private fun List<Reciter>.displayable(): List<Reciter> =
        filter { !isKidsRepeatStyle(it.style) }
}
