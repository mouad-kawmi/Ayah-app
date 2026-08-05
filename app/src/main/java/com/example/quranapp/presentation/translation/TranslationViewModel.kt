package com.example.quranapp.presentation.translation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.data.translation.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TranslationUiState(
    val downloadedEditions: Set<String> = emptySet(),
    val downloadingEditions: Set<String> = emptySet(),
    val currentVerseText: Map<String, String> = emptyMap() // map of identifier -> text
)

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TranslationRepository(application)
    
    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState
    
    val supportedEditions = repository.supportedEditions

    init {
        checkDownloadedEditions()
    }

    private fun checkDownloadedEditions() {
        val downloaded = supportedEditions.filter { repository.isEditionDownloaded(it.identifier) }.map { it.identifier }.toSet()
        _uiState.value = _uiState.value.copy(downloadedEditions = downloaded)
    }

    fun downloadEdition(identifier: String) {
        if (_uiState.value.downloadingEditions.contains(identifier)) return
        
        _uiState.value = _uiState.value.copy(
            downloadingEditions = _uiState.value.downloadingEditions + identifier
        )
        
        viewModelScope.launch {
            val success = repository.downloadEdition(identifier)
            
            _uiState.value = _uiState.value.copy(
                downloadingEditions = _uiState.value.downloadingEditions - identifier
            )
            
            if (success) {
                checkDownloadedEditions()
            }
        }
    }

    fun loadVerseTranslation(identifier: String, verseKey: String) {
        viewModelScope.launch {
            val text = repository.getTranslationText(identifier, verseKey)
            if (text != null) {
                val currentMap = _uiState.value.currentVerseText.toMutableMap()
                currentMap[identifier] = text
                _uiState.value = _uiState.value.copy(currentVerseText = currentMap)
            }
        }
    }
    
    fun clearVerseTranslation() {
        _uiState.value = _uiState.value.copy(currentVerseText = emptyMap())
    }
}
