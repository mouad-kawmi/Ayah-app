package com.example.quranapp.presentation.audio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.data.audio.AudioCacheManager
import com.example.quranapp.data.audio.AudioPlayerManager
import com.example.quranapp.data.audio.PlayerState
import com.example.quranapp.data.audio.RepeatMode
import com.example.quranapp.data.quran.Qcf4Repository
import com.example.quranapp.domain.model.AyahTimestamp
import com.example.quranapp.domain.model.Reciter
import com.example.quranapp.domain.model.Surah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioUiState(
    val reciters: List<Reciter> = emptyList(),
    val allReciters: List<Reciter> = emptyList(),
    val selectedReciter: Reciter? = null,
    val selectedAudioReciter: Reciter? = null,
    val surahs: List<Surah> = emptyList(),
    val selectedSurah: Surah? = null,
    val playerState: PlayerState = PlayerState(),
    val isOfflineCached: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val playerManager = AudioPlayerManager.getInstance(appContext)
    private val quranRepository = Qcf4Repository(appContext)

    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    val playbackPosition: StateFlow<Long> = playerManager.playbackPosition

    init {
        loadSurahs()
        observeManager()
    }

    private fun loadSurahs() {
        viewModelScope.launch {
            try {
                val surahs = quranRepository.getSurahs()
                _uiState.value = _uiState.value.copy(
                    surahs = surahs,
                    selectedSurah = surahs.firstOrNull()
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun observeManager() {
        viewModelScope.launch {
            playerManager.reciters.collect { reciters ->
                _uiState.value = _uiState.value.copy(reciters = reciters)
            }
        }
        viewModelScope.launch {
            playerManager.allReciters.collect { allReciters ->
                _uiState.value = _uiState.value.copy(allReciters = allReciters)
            }
        }
        viewModelScope.launch {
            playerManager.selectedReciter.collect { reciter ->
                _uiState.value = _uiState.value.copy(selectedReciter = reciter)
            }
        }
        viewModelScope.launch {
            playerManager.selectedAudioReciter.collect { reciter ->
                _uiState.value = _uiState.value.copy(selectedAudioReciter = reciter)
            }
        }
        viewModelScope.launch {
            playerManager.playerState.collect { pState ->
                _uiState.value = _uiState.value.copy(playerState = pState)
            }
        }
    }

    fun selectReciter(reciter: Reciter) {
        playerManager.selectReciter(reciter)
    }

    fun selectAudioReciter(reciter: Reciter) {
        playerManager.selectAudioReciter(reciter)
    }

    fun playAudioSurah(surahId: Int) {
        playerManager.playAudioSurah(surahId)
    }

    fun selectSurah(surah: Surah) {
        _uiState.value = _uiState.value.copy(selectedSurah = surah)
    }

    fun playSurah(surahId: Int, startAyah: AyahTimestamp? = null) {
        playerManager.playSurah(surahId, startAyah)
    }

    fun playSurahWithVerse(surahId: Int, verseKey: String) {
        android.util.Log.d("TIMESTAMP_DEBUG", "AudioPlayerViewModel.playSurahWithVerse surahId=$surahId verseKey=$verseKey")
        playerManager.playSurahWithVerse(surahId, verseKey)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun setRepeatMode(mode: RepeatMode) {
        playerManager.setRepeatMode(mode)
    }
}
