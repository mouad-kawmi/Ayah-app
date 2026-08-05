package com.example.quranapp.presentation.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.data.quran.Qcf4Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SurahListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Qcf4Repository(application.applicationContext)
    
    private val _uiState = MutableStateFlow(SurahUiState())
    val uiState: StateFlow<SurahUiState> = _uiState

    init {
        loadSurahs()
    }

    private fun loadSurahs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SurahUiState(surahs = repository.getSurahs())
        }
    }
}
