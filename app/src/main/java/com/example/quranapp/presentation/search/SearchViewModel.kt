package com.example.quranapp.presentation.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.data.quran.Qcf4Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SearchResult(
    val surahId: Int,
    val surahName: String,
    val verseNumber: Int,
    val pageNumber: Int,
    val verseText: String
)

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList()
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Qcf4Repository(application.applicationContext)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    // FIX 1: Mutex → only ONE coroutine can build the cache at a time
    private val cacheMutex = Mutex()
    // normalizedText pre-computed during cache build (avoids re-normalizing 6236 verses per search)
    private var allVersesCache: List<Pair<SearchResult, String>>? = null

    // FIX 2: Track active job → cancel stale search before starting a new one
    private var activeSearchJob: Job? = null

    init {
        // Pre-warm the cache as soon as the screen opens, before the user types anything
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "🔥 Pre-warming cache in background (screen just opened)...")
            cacheMutex.withLock { buildCacheIfNeeded() }
            Log.d(TAG, "🔥 Pre-warm complete → cache ready for instant search!")
        }
    }

    fun onQueryChanged(newQuery: String) {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔍 onQueryChanged → query='$newQuery'")

        // Cancel any in-flight search immediately → no stale results overwrite the UI
        activeSearchJob?.cancel()

        _uiState.value = _uiState.value.copy(query = newQuery)
        if (newQuery.isBlank()) {
            Log.d(TAG, "⚪ Query is blank → clearing results")
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false)
            return
        }

        activeSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // FIX 1: withLock → only ONE coroutine runs this block at a time
            cacheMutex.withLock {
                buildCacheIfNeeded()
            } // mutex released → waiting coroutines now use the ready cache

            val queryTrimmed = newQuery.trim()
            val normalizedQuery = normalizeArabicForSearch(queryTrimmed)
            Log.d(TAG, "🔎 Searching for: '$queryTrimmed'  →  normalized: '$normalizedQuery'")

            val searchStart = System.currentTimeMillis()
            // FIX 3: Use pre-computed normalizedText → no per-verse regex on every search
            val filtered = allVersesCache
                ?.filter { (_, normalizedText) -> normalizedText.contains(normalizedQuery) }
                ?.map { (result, _) -> result }
                ?: emptyList()
            val searchTime = System.currentTimeMillis() - searchStart

            Log.d(TAG, "📊 Search done in ${searchTime}ms → ${filtered.size} results found")
            if (filtered.isNotEmpty()) {
                Log.d(TAG, "   First: ${filtered.first().surahName} ${filtered.first().surahId}:${filtered.first().verseNumber}")
                Log.d(TAG, "   Last:  ${filtered.last().surahName} ${filtered.last().surahId}:${filtered.last().verseNumber}")
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                results = filtered
            )
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    /**
     * Builds the search cache if it hasn't been built yet.
     * Must be called inside cacheMutex.withLock { } to be thread-safe.
     */
    private suspend fun buildCacheIfNeeded() {
        if (allVersesCache != null) {
            Log.d(TAG, "✅ Cache already exists → ${allVersesCache!!.size} verses (skipping rebuild)")
            return
        }

        Log.d(TAG, "📦 Cache is NULL → building (mutex locked, others wait)...")
        val cacheStartTime = System.currentTimeMillis()

        val surahs = repository.getSurahs()
        Log.d(TAG, "📖 Loaded ${surahs.size} surahs")

        val versesList = mutableListOf<Pair<SearchResult, String>>()
        val seenVerseKeys = mutableSetOf<String>()
        var duplicatesSkipped = 0
        var pagesWithErrors = 0

        for (pageNum in 1..604) {
            val pageStart = System.currentTimeMillis()
            try {
                val page = repository.getPage(pageNum)
                val allWords = page.lines.flatMap { it.words }
                val wordsByVerse = allWords.groupBy { it.verse_key }

                wordsByVerse.forEach { (verseKey, words) ->
                    if (!verseKey.isNullOrEmpty()) {
                        val parts = verseKey.split(":")
                        if (parts.size == 2) {
                            val surahId = parts[0].toIntOrNull() ?: 1
                            val verseNum = parts[1].toIntOrNull() ?: 1

                            if (seenVerseKeys.contains(verseKey)) {
                                duplicatesSkipped++
                                return@forEach
                            }

                            val surah = surahs.find { it.id == surahId }
                            val surahName = surah?.nameArabic ?: ""
                            val normalWords = words.filter { it.type != "end" && !it.text.startsWith("V") }
                            val verseText = normalWords.joinToString(" ") { it.text.ifEmpty { it.char } }

                            if (verseText.isNotBlank()) {
                                seenVerseKeys.add(verseKey)
                                val result = SearchResult(
                                    surahId = surahId,
                                    surahName = surahName,
                                    verseNumber = verseNum,
                                    pageNumber = pageNum,
                                    verseText = verseText
                                )
                                val normalizedText = normalizeArabicForSearch(verseText)
                                versesList.add(Pair(result, normalizedText))
                            }
                        }
                    }
                }

                val pageTime = System.currentTimeMillis() - pageStart
                if (pageTime > 50) {
                    Log.w(TAG, "🐢 SLOW page: page $pageNum took ${pageTime}ms to parse")
                }
            } catch (e: Exception) {
                pagesWithErrors++
                Log.e(TAG, "❌ Error reading page $pageNum: ${e.message}")
            }
        }

        val cacheBuildTime = System.currentTimeMillis() - cacheStartTime
        allVersesCache = versesList

        Log.d(TAG, "✅ Cache built in ${cacheBuildTime}ms")
        Log.d(TAG, "   → Total verses in cache: ${versesList.size}")
        Log.d(TAG, "   → Duplicates skipped: $duplicatesSkipped")
        Log.d(TAG, "   → Pages with errors: $pagesWithErrors")
    }

    companion object {
        private const val TAG = "QuranSearch"

        fun normalizeArabicForSearch(text: String): String {
            return text
                .replace(Regex("[\\u064B-\\u0652\\u0657-\\u065F\\u0670]"), "")  // Remove tashkeel
                .replace("أ", "ا")        // Alif with hamza above → alif
                .replace("إ", "ا")        // Alif with hamza below → alif
                .replace("آ", "ا")        // Alif with madd → alif
                .replace("ى", "ي")        // Alif maqsura → ya
                .replace("ة", "ه")        // Teh marbuta → ha
                .replace("ـ", "")         // Tatweel / kashida
                .replace(Regex("\\s+"), " ") // Collapse multiple spaces
                .trim()
        }
    }
}
