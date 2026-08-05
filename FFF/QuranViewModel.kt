package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.runtime.Immutable
import com.example.quran.data.PageLayoutProvider

@Immutable
data class SearchResultItem(
    val verse: Verse,
    val matches: List<MatchRange>
)

@Immutable
data class QuranUiState(
    val isLoading: Boolean = true,
    val chapters: List<ChapterMetadata> = emptyList(),
    val error: String? = null,
    val searchResults: List<SearchResultItem> = emptyList()
)

class QuranViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = QcfRepository(application)

    private val _pageLayoutProvider = PageLayoutProvider(application.assets)
    val pageLayoutProvider: PageLayoutProvider
        get() = _pageLayoutProvider

    private val _uiState = MutableStateFlow(QuranUiState())
    val uiState: StateFlow<QuranUiState> = _uiState.asStateFlow()

    init {
        android.os.Trace.beginSection("QuranViewModel.init")
        android.util.Log.d("NAV_TRACE", "VIEWMODEL_LOAD_START")
        try {
            loadQuran()
        } finally {
            android.os.Trace.endSection()
        }
    }

    private fun loadQuran() {
        viewModelScope.launch {
            android.util.Log.d("NAV_TRACE", "VIEWMODEL_LOAD_END")
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getQuranBundle().fold(
                onSuccess = { bundle ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            chapters = bundle.chapters,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load Quran index"
                        )
                    }
                }
            )
        }
    }

    suspend fun resolveVerseById(verseId: Int): Verse? {
        return repository.resolveVerseById(verseId)
    }

    suspend fun resolveVerseByKey(verseKey: String): Verse? {
        return repository.resolveVerseByKey(verseKey)
    }

    suspend fun fetchVerseTranslationAndTafsir(verseId: Int): Result<Triple<String, String, String>> {
        return repository.getVerseTranslationAndTafsir(verseId)
    }

    fun search(query: String) {
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val results = repository.searchQuran(query.trim())
            val highlightedResults = withContext(Dispatchers.Default) {
                val normQuery = query.trim().normalizeArabic()
                results.map { verse ->
                    val matches = findNormalizedMatches(verse.text, normQuery)
                    SearchResultItem(verse, matches)
                }
            }
            _uiState.update { it.copy(searchResults = highlightedResults) }
        }
    }
}
