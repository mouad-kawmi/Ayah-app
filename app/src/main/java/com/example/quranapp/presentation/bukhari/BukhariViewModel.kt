package com.example.quranapp.presentation.bukhari

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.data.bukhari.BukhariHadith
import com.example.quranapp.data.bukhari.BukhariKitab
import com.example.quranapp.data.bukhari.BukhariRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BukhariUiState(
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: Boolean = false,
    val fileSizeMB: Double = 9.3,
    val kitabs: List<BukhariKitab> = emptyList(),
    val showDeleteConfirm: Boolean = false
)

data class HadithListUiState(
    val hadiths: List<BukhariHadith> = emptyList(),
    val isLoading: Boolean = false,
    val kitab: BukhariKitab? = null
)

class BukhariViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BukhariRepository(application)

    private val _uiState = MutableStateFlow(BukhariUiState())
    val uiState: StateFlow<BukhariUiState> = _uiState

    private val _hadithListState = MutableStateFlow(HadithListUiState())
    val hadithListState: StateFlow<HadithListUiState> = _hadithListState

    init {
        checkDownloadStatus()
    }

    private fun checkDownloadStatus() {
        val isDownloaded = repository.isDownloaded()
        _uiState.value = _uiState.value.copy(
            isDownloaded = isDownloaded,
            kitabs = if (isDownloaded) repository.kitabs else emptyList(),
            fileSizeMB = if (isDownloaded) repository.getFileSizeMB() else 9.3
        )
    }

    fun startDownload() {
        if (_uiState.value.isDownloading) return
        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadError = false
        )
        viewModelScope.launch {
            val success = repository.downloadBukhari { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }
            if (success) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    isDownloaded = true,
                    kitabs = repository.kitabs,
                    fileSizeMB = repository.getFileSizeMB()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadError = true
                )
            }
        }
    }

    fun showDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun dismissDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun deleteDownload() {
        repository.deleteDownloadedFile()
        _uiState.value = BukhariUiState(kitabs = emptyList())
    }

    fun loadHadithsForKitab(kitab: BukhariKitab) {
        _hadithListState.value = HadithListUiState(isLoading = true, kitab = kitab)
        viewModelScope.launch {
            val hadiths = repository.loadHadithsForKitab(kitab)
            _hadithListState.value = HadithListUiState(hadiths = hadiths, isLoading = false, kitab = kitab)
        }
    }
}
