package com.example

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.SparseArray
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import com.example.debug.PerformanceProfiler
import com.quran.engine.model.SearchVerse
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.squareup.moshi.JsonReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.Immutable

@Immutable
data class Verse(
    val id: Int,
    val text: String,
    val numberInSurah: Int,
    val surahName: String,
    val surahNumber: Int,
    val verseKey: String,
    val pageNumber: Int
)

@Immutable
data class ChapterMetadata(
    val id: Int,
    val name: String,
    val startingPage: Int
)

class QcfRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val translationMutex = Mutex()
    private var chapterIndexCache: ChapterIndex? = null
    /** Lazily-loaded in-memory cache for quran_translations.json (one-time parse) */
    private var translationsCache: JSONObject? = null

    /** O(1) verse lookup built once from search_index.json + surah names */
    private var verseByIdCache: SparseArray<Verse>? = null

    data class QuranBundle(val chapters: List<ChapterMetadata>)

    private data class ChapterIndex(
        val chapters: List<ChapterMetadata>,
        val surahNames: Map<Int, String>,
        val surahOffsets: Map<Int, Int>
    )

    suspend fun getQuranBundle(): Result<QuranBundle> = withContext(Dispatchers.IO) {
        PerformanceProfiler.recordRepositoryCall("QcfRepository.getQuranBundle")
        val start = SystemClock.elapsedRealtime()
        try {
            val index = awaitChapterIndex()
            Result.success(QuranBundle(chapters = index.chapters))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun awaitChapterIndex(): ChapterIndex = withContext(Dispatchers.IO) {
        PerformanceProfiler.recordRepositoryCall("QcfRepository.awaitChapterIndex")
        chapterIndexCache?.let {
            PerformanceProfiler.recordCacheResult("QcfRepository.chapterIndexCache", true)
            return@withContext it
        }
        PerformanceProfiler.recordCacheResult("QcfRepository.chapterIndexCache", false)
        val json = context.assets.open("qcf/index.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val chaptersJson = root.getJSONArray("chapters")
        val chapters = mutableListOf<ChapterMetadata>()
        val surahNames = mutableMapOf<Int, String>()
        val surahOffsets = mutableMapOf<Int, Int>()

        var cumulativeOffset = 0
        for (index in 0 until chaptersJson.length()) {
            val chapterJson = chaptersJson.getJSONObject(index)
            val chapterId = chapterJson.getInt("id")
            val arabicName = chapterJson.getString("name_arabic")
            val pagesJson = chapterJson.getJSONArray("pages")
            val versesCount = chapterJson.getInt("verses_count")

            chapters += ChapterMetadata(
                id = chapterId,
                name = arabicName,
                startingPage = pagesJson.getInt(0)
            )
            surahNames[chapterId] = arabicName
            surahOffsets[chapterId] = cumulativeOffset
            cumulativeOffset += versesCount
        }

        val idx = ChapterIndex(
            chapters = chapters.sortedBy { it.id },
            surahNames = surahNames,
            surahOffsets = surahOffsets
        )
        chapterIndexCache = idx
        idx
    }

    suspend fun resolveVerseKey(verseId: Int): String? = withContext(Dispatchers.IO) {
        val start = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
        val result = awaitVerseByIdCache()[verseId]?.verseKey
        if (BuildConfig.DEBUG) {
            val dur = SystemClock.elapsedRealtime() - start
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] resolveVerseKey END (${dur}ms)")
            if (dur > 16) {
                DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] WARNING Main thread blocked for ${dur}ms (resolveVerseKey)")
            }
        }
        result
    }

    suspend fun resolveVerseByKey(verseKey: String): Verse? = withContext(Dispatchers.IO) {
        val start = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
        val parts = verseKey.split(":")
        if (parts.size != 2) return@withContext null
        val surahId = parts[0].toIntOrNull() ?: return@withContext null
        val ayahId = parts[1].toIntOrNull() ?: return@withContext null
        val chapterIndex = awaitChapterIndex()
        val verseId = (chapterIndex.surahOffsets[surahId] ?: return@withContext null) + ayahId
        val result = awaitVerseByIdCache()[verseId]
        if (BuildConfig.DEBUG) {
            val dur = SystemClock.elapsedRealtime() - start
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] resolveVerseByKey END (${dur}ms)")
            if (dur > 16) {
                DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] WARNING Main thread blocked for ${dur}ms (resolveVerseByKey)")
            }
        }
        result
    }

    suspend fun resolveVerseById(verseId: Int): Verse? = withContext(Dispatchers.IO) {
        val start = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
        val result = awaitVerseByIdCache()[verseId]
        if (BuildConfig.DEBUG) {
            val dur = SystemClock.elapsedRealtime() - start
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] resolveVerseById END (${dur}ms)")
            if (dur > 16) {
                DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] WARNING Main thread blocked for ${dur}ms (resolveVerseById)")
            }
        }
        result
    }

    /** Returns the entire verse cache — builds it once from search_index.json + surah names */
    private suspend fun awaitVerseByIdCache(): SparseArray<Verse> = withContext(Dispatchers.IO) {
        val start = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
        verseByIdCache?.let {
            if (BuildConfig.DEBUG) {
                val dur = SystemClock.elapsedRealtime() - start
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] awaitVerseByIdCache (cached) END (${dur}ms)")
            }
            return@withContext it
        }
        val buildStart = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
        preloadSearchCache(context)
        val chapterIndex = awaitChapterIndex()
        val cache = searchIndexCache ?: emptyList()
        val sparse = SparseArray<Verse>(cache.size)
        for (sv in cache) {
            sparse.put(
                sv.id,
                Verse(
                    id = sv.id,
                    text = sv.text,
                    numberInSurah = sv.verse,
                    surahName = chapterIndex.surahNames[sv.chapter] ?: "",
                    surahNumber = sv.chapter,
                    verseKey = "${sv.chapter}:${sv.verse}",
                    pageNumber = sv.page
                )
            )
        }
        verseByIdCache = sparse
        if (BuildConfig.DEBUG) {
            val dur = SystemClock.elapsedRealtime() - buildStart
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] awaitVerseByIdCache (built) END (${dur}ms)")
            if (dur > 16) {
                DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] WARNING Main thread blocked for ${dur}ms (awaitVerseByIdCache build)")
            }
        }
        sparse
    }

    suspend fun searchQuran(query: String): List<Verse> = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        preloadSearchCache(context)
        val normalizedQuery = query.normalizeArabic()
        val cache = searchIndexCache ?: emptyList()
        val names = chapterIndexCache?.surahNames
        val results = ArrayList<Verse>(50)
        for (sv in cache) {
            if (sv.normalized.contains(normalizedQuery)) {
                results += Verse(
                    id = sv.id,
                    text = sv.text,
                    numberInSurah = sv.verse,
                    surahName = names?.get(sv.chapter) ?: "",
                    surahNumber = sv.chapter,
                    verseKey = "${sv.chapter}:${sv.verse}",
                    pageNumber = sv.page
                )
                if (results.size == 50) break
            }
        }
        val durationMs = SystemClock.elapsedRealtime() - start
        PerformanceProfiler.recordSearchTime(query, results.size, durationMs)
        if (BuildConfig.DEBUG) {
            Log.d("SEARCH", "\"$query\" → ${results.size} results in ${durationMs}ms")
        }
        results
    }

    suspend fun getVerseTranslationAndTafsir(verseId: Int): Result<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        try {
            val jsonLoadStart = SystemClock.elapsedRealtime()
            // Lazy-load and cache the full translations JSON once
            val translationsRoot: JSONObject = translationMutex.withLock {
                translationsCache ?: run {
                    val json = context.assets.open("quran_translations.json").bufferedReader().use { it.readText() }
                    val parsed = JSONObject(json)
                    PerformanceProfiler.recordCacheResult("QcfRepository.translationsCache", false)
                    translationsCache = parsed
                    parsed
                }
            }
            val loadDurationMs = SystemClock.elapsedRealtime() - jsonLoadStart
            PerformanceProfiler.logJsonOperation(
                "Translation Cache Load",
                loadDurationMs,
                loadDurationMs
            )
            val offlineData = try {
                val verseObj = translationsRoot.optJSONObject(verseId.toString())
                if (verseObj != null) {
                    Triple(
                        verseObj.optString("fr", "Translation unavailable"),
                        verseObj.optString("en", "Translation unavailable"),
                        verseObj.optString("tafsir", "التفسير غير متوفر")
                    )
                } else null
            } catch (e: Exception) { null }

            if (offlineData != null) return@withContext Result.success(offlineData)

            // Network fallback
            var translationFr = ""
            var translationEn = ""
            var tafsir = ""
            var networkCallSucceeded = false
            try {
                client.newCall(Request.Builder().url("https://api.alquran.cloud/v1/ayah/$verseId/fr.hamidullah").build()).execute().use { response ->
                    if (response.isSuccessful) {
                        try {
                            val bodyStr = response.body?.string() ?: ""
                            if (bodyStr.isNotEmpty()) {
                                translationFr = JSONObject(bodyStr).getJSONObject("data").getString("text")
                                networkCallSucceeded = true
                            }
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {}

            if (networkCallSucceeded) {
                Result.success(Triple(translationFr, translationEn, tafsir))
            } else {
                Result.success(Triple("الترجمة غير متوفرة دون اتصال بالإنترنت حالياً.", "The translation is not available without an internet connection right now.", "التفسير غير متوفر دون اتصال بالإنترنت حالياً."))
            }
        } catch (e: Exception) {
            Result.success(Triple("الترجمة غير متوفرة دون اتصال.", "Translation unavailable.", "التفسير غير متوفر دون اتصال."))
        }
    }

    companion object {
        @Volatile
        private var searchIndexCache: List<SearchVerse>? = null
        private val preloadMutex = Mutex()

        suspend fun preloadSearchCache(context: Context) {
            if (searchIndexCache != null) return
            preloadMutex.withLock {
                if (searchIndexCache != null) return
                val loadStart = SystemClock.elapsedRealtime()
                val json = context.assets
                    .open("qcf/search_index.json")
                    .bufferedReader()
                    .use { it.readText() }
                val cache = decodeSearchIndex(json)
                val loadMs = SystemClock.elapsedRealtime() - loadStart
                val memoryBytes = estimateMemoryBytes(cache.size)
                searchIndexCache = cache
                if (BuildConfig.DEBUG) {
                    val memKb = memoryBytes / 1024
                    Log.d("SEARCH", "Search index loaded: ${cache.size} verses in ${loadMs}ms, memory ≈ ${memKb}KB")
                }
            }
        }

        private fun estimateMemoryBytes(verseCount: Int): Long {
            val perVerse = 4 * 4 + // 4 Ints × 4 bytes
                    2 * 38 +      // 2 Strings × ~38 UTF-16 chars (text + normalized)
                    16            // object header
            return verseCount.toLong() * perVerse
        }

        fun getSearchCacheStats(): String {
            val cache = searchIndexCache
            if (cache == null) return "Search Cache: not loaded"
            val mb = estimateMemoryBytes(cache.size) / (1024.0 * 1024.0)
            return "Search Cache: ${cache.size} verses, loaded=true, memory≈${"%.2f".format(mb)}MB"
        }

        private fun decodeSearchIndex(json: String): List<SearchVerse> {
            val reader = JsonReader.of(Buffer().writeUtf8(json))
            val list = mutableListOf<SearchVerse>()
            var version = 0
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "version" -> version = reader.nextInt()
                    "generated" -> reader.skipValue()
                    "verses" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var id = 0; var chapter = 0; var verse = 0
                            var page = 0; var text = ""; var normalized = ""
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "id" -> id = reader.nextInt()
                                    "chapter" -> chapter = reader.nextInt()
                                    "verse" -> verse = reader.nextInt()
                                    "page" -> page = reader.nextInt()
                                    "text" -> text = reader.nextString()
                                    "normalized" -> normalized = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            list += SearchVerse(id, chapter, verse, page, text, normalized)
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (version != 1) {
                throw IllegalStateException("Unsupported search index version: $version")
            }
            return list
        }
    }
}
