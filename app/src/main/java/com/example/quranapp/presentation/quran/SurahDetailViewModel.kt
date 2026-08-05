package com.example.quranapp.presentation.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.core.utils.QuranPreferences
import com.example.quranapp.data.model.MushafPageMetadata
import com.example.quranapp.data.model.Page
import com.example.quranapp.data.quran.Qcf4Repository
import com.example.quranapp.domain.model.Surah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class QuranReaderUiState(
    val isLoading: Boolean = true,
    val pagesMap: Map<Int, Page> = emptyMap(),
    val fontNamesMap: Map<Int, String> = emptyMap(),
    val metadataMap: Map<Int, MushafPageMetadata> = emptyMap(),
    val currentPageNumber: Int = 1,
    val currentSurahId: Int = 1,
    val totalQuranPages: Int = 604,
    val error: String? = null
) {
    val currentMetadata: MushafPageMetadata? get() = metadataMap[currentPageNumber]
}

class SurahDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Qcf4Repository(application.applicationContext)
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(QuranReaderUiState())
    val uiState: StateFlow<QuranReaderUiState> = _uiState

    private val verseKeyPageCache = mutableMapOf<String, Int>()

    private var surahLookupByPage: Map<Int, Surah>? = null
    private var allSurahsList: List<Surah>? = null

    private fun getSurahForPage(pageNumber: Int): Surah {
        if (surahLookupByPage == null) {
            val surahs = repository.getSurahs()
            allSurahsList = surahs
            surahLookupByPage = surahs.associateBy { surah ->
                surah.firstPage
            }
        }
        val lookup = surahLookupByPage!!
        return lookup.values.find { pageNumber in it.firstPage..it.lastPage }
            ?: allSurahsList!!.first()
    }

    fun loadQuran(initialPage: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = QuranReaderUiState(isLoading = true)
            try {
                val clampedPage = initialPage.coerceIn(1, 604)
                loadPageData(clampedPage)
                val currentSurah = getSurahForPage(clampedPage)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentPageNumber = clampedPage,
                    currentSurahId = currentSurah.id
                )
                QuranPreferences.saveLastRead(appContext, currentSurah.id, clampedPage, currentSurah.nameArabic)
                preloadPage(clampedPage + 1)
                preloadPage(clampedPage - 1)
            } catch (e: Exception) {
                _uiState.value = QuranReaderUiState(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun loadPageData(pageNumber: Int) {
        if (pageNumber < 1 || pageNumber > 604) return
        val state = _uiState.value
        if (state.pagesMap.containsKey(pageNumber)) return

        val page = repository.getPage(pageNumber)
        val fontName = repository.getFontNameForPage(pageNumber)
        val metadata = repository.getPageMetadata(pageNumber, page)

        // Preload fonts on IO thread to avoid blocking Compose UI thread
        try {
            com.example.quranapp.core.utils.Qcf4FontManager.getFontFamily(appContext, fontName)
            com.example.quranapp.core.utils.Qcf4FontManager.getFontFamily(appContext, "QCF4_QBSML")
        } catch (e: Exception) { /* ignore */ }

        for (line in page.lines) {
            for (word in line.words) {
                val vk = word.verse_key ?: continue
                if (vk !in verseKeyPageCache) {
                    verseKeyPageCache[vk] = pageNumber
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            pagesMap = _uiState.value.pagesMap + (pageNumber to page),
            fontNamesMap = _uiState.value.fontNamesMap + (pageNumber to fontName),
            metadataMap = _uiState.value.metadataMap + (pageNumber to metadata)
        )
    }

    fun findPageForVerseKey(verseKey: String): Int? {
        verseKeyPageCache[verseKey]?.let { return it }
        for ((pageNum, page) in _uiState.value.pagesMap) {
            for (line in page.lines) {
                for (word in line.words) {
                    if (word.verse_key == verseKey) {
                        verseKeyPageCache[verseKey] = pageNum
                        return pageNum
                    }
                }
            }
        }
        return null
    }

    fun ensurePageLoadedForVerse(verseKey: String) {
        if (findPageForVerseKey(verseKey) != null) return
        val parts = verseKey.split(":")
        val surahId = parts.getOrNull(0)?.toIntOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val surah = repository.getSurahById(surahId) ?: return@launch
            val currentPage = _uiState.value.currentPageNumber
            for (offset in 0..(surah.lastPage - currentPage).coerceAtMost(10)) {
                val pageToLoad = currentPage + offset
                if (pageToLoad in surah.firstPage..surah.lastPage) {
                    loadPageData(pageToLoad)
                    if (findPageForVerseKey(verseKey) != null) return@launch
                }
            }
        }
    }

    fun preloadPage(pageNumber: Int) {
        if (pageNumber < 1 || pageNumber > 604) return
        viewModelScope.launch(Dispatchers.IO) {
            loadPageData(pageNumber)
        }
    }

    fun onPageChanged(pageNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            loadPageData(pageNumber)
            val currentSurah = getSurahForPage(pageNumber)
            _uiState.value = _uiState.value.copy(
                currentPageNumber = pageNumber,
                currentSurahId = currentSurah.id
            )
            QuranPreferences.saveLastRead(appContext, currentSurah.id, pageNumber, currentSurah.nameArabic)
            preloadPage(pageNumber + 1)
            preloadPage(pageNumber - 1)
        }
    }
}
