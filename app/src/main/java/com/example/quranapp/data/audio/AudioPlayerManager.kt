package com.example.quranapp.data.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.quranapp.core.utils.QuranPreferences
import com.example.quranapp.domain.model.AyahTimestamp
import com.example.quranapp.domain.model.Reciter
import com.example.quranapp.domain.model.SurahAudio
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayerManager private constructor(private val context: Context) {
    private val audioRepository = AudioRepository(context)
    private val audioPlayer = QuranAudioPlayer(context)
    private val downloadManager = AudioDownloadManager.getInstance(context)
    private val reciterCatalog = ReciterCatalogRepository(context)

    private val _reciters = MutableStateFlow<List<Reciter>>(emptyList())
    val reciters: StateFlow<List<Reciter>> = _reciters.asStateFlow()

    private val _allReciters = MutableStateFlow<List<Reciter>>(emptyList())
    val allReciters: StateFlow<List<Reciter>> = _allReciters.asStateFlow()

    private val _selectedReciter = MutableStateFlow<Reciter?>(null)
    val selectedReciter: StateFlow<Reciter?> = _selectedReciter.asStateFlow()

    private val _selectedAudioReciter = MutableStateFlow<Reciter?>(null)
    val selectedAudioReciter: StateFlow<Reciter?> = _selectedAudioReciter.asStateFlow()

    val playerState: StateFlow<PlayerState> = audioPlayer.playerState
    val playbackPosition: StateFlow<Long> = audioPlayer.playbackPosition

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        loadReciters()
    }

    private fun loadReciters() {
        // 1. Instant, synchronous read from cache so the UI is never empty.
        val cached = reciterCatalog.getCachedCatalog()
        if (cached.isNotEmpty()) {
            _reciters.value = cached
            _allReciters.value = cached
        }

        // 2. Async refresh (network if stale, otherwise keeps cache).
        scope.launch {
            val catalog = reciterCatalog.loadCatalog()
            if (catalog.isNotEmpty()) {
                _reciters.value = catalog
                _allReciters.value = catalog
            }

            val savedId = QuranPreferences.getSelectedReciterId(context)
            val reciter = _reciters.value.find { it.id == savedId } ?: _reciters.value.firstOrNull()
            if (reciter != null && savedId != reciter.id) {
                QuranPreferences.saveSelectedReciterId(context, reciter.id)
            }
            _selectedReciter.value = reciter

            val audioSavedId = QuranPreferences.getSelectedAudioReciterId(context)
            val audioReciter = _allReciters.value.find { it.id == audioSavedId } ?: _allReciters.value.firstOrNull()
            if (audioReciter != null && audioSavedId != audioReciter.id) {
                QuranPreferences.saveSelectedAudioReciterId(context, audioReciter.id)
            }
            _selectedAudioReciter.value = audioReciter

            Log.d("AudioDebug", "[Audio] Catalog reciters: ${_reciters.value.size}")
            Log.d("AudioDebug", "[Audio] Selected reader reciter ID: ${reciter?.id} name: ${reciter?.name}")
            Log.d("AudioDebug", "[Audio] Selected audio reciter ID: ${audioReciter?.id} name: ${audioReciter?.name}")

            reciter?.let { refreshTimingsSupport(it) }
            audioReciter?.let { refreshTimingsSupport(it) }
        }
    }

    /**
     * Ensures [reciter].supportsVerseTimings is known. If unknown, probes QDC
     * and updates the catalog + in-memory flows. An indeterminate probe result
     * (null) keeps the previous value so the probe is retried next selection.
     */
    private fun refreshTimingsSupport(reciter: Reciter) {
        if (reciter.supportsVerseTimings != null) return
        scope.launch {
            val supported = reciterCatalog.probeVerseTimingsSupport(reciter.id)
            applyTimingsSupport(reciter.id, supported)
        }
    }

    private fun applyTimingsSupport(reciterId: Int, supported: Boolean?) {
        // Never overwrite a known true/false with an indeterminate result.
        if (supported == null) return
        fun withFlag(list: List<Reciter>): List<Reciter> =
            list.map { if (it.id == reciterId) it.copy(supportsVerseTimings = supported) else it }

        _reciters.value = withFlag(_reciters.value)
        _allReciters.value = withFlag(_allReciters.value)
        _selectedReciter.value = _selectedReciter.value?.let { if (it.id == reciterId) it.copy(supportsVerseTimings = supported) else it }
        _selectedAudioReciter.value = _selectedAudioReciter.value?.let { if (it.id == reciterId) it.copy(supportsVerseTimings = supported) else it }
    }

    /**
     * Ground truth from an actual audio_files response. Persists true when real
     * verse_timings were returned. Persists false only when the response is
     * reliable (came from QDC) and genuinely contained no verse_timings.
     * Unreliable empty responses (e.g. old-API fallback after a QDC network
     * failure) never change a known value.
     */
    private suspend fun applyRealTimestampsKnowledge(
        reciterId: Int,
        hasTimestamps: Boolean,
        timestampsReliable: Boolean
    ) {
        if (hasTimestamps) {
            if (_allReciters.value.firstOrNull { it.id == reciterId }?.supportsVerseTimings == true) return
            reciterCatalog.setVerseTimingsSupport(reciterId, true)
            applyTimingsSupport(reciterId, true)
        } else if (timestampsReliable) {
            if (_allReciters.value.firstOrNull { it.id == reciterId }?.supportsVerseTimings == false) return
            reciterCatalog.setVerseTimingsSupport(reciterId, false)
            applyTimingsSupport(reciterId, false)
        }
    }

    fun selectReciter(reciter: Reciter) {
        QuranPreferences.saveSelectedReciterId(context, reciter.id)
        _selectedReciter.value = reciter
        Log.d("AudioDebug", "[Audio] Selected reciter ID changed to: ${reciter.id}")
        Log.d("AudioDebug", "[Audio] Selected reciter name changed to: ${reciter.name}")
        Log.d("RECITER_DEBUG", "UI selected name=${reciter.name} UI selected id=${reciter.id}")
        refreshTimingsSupport(reciter)
    }

    fun selectAudioReciter(reciter: Reciter) {
        QuranPreferences.saveSelectedAudioReciterId(context, reciter.id)
        _selectedAudioReciter.value = reciter
        Log.d("AudioDebug", "[Audio] Selected audio reciter ID changed to: ${reciter.id}")
        Log.d("AudioDebug", "[Audio] Selected audio reciter name changed to: ${reciter.name}")
        refreshTimingsSupport(reciter)
    }

    fun playAudioSurah(surahId: Int) {
        scope.launch {
            val reciter = _selectedAudioReciter.value ?: _allReciters.value.firstOrNull() ?: return@launch
            Log.d("OFFLINE_AUDIO_DEBUG", "playAudioSurah surahId=$surahId reciterId=${reciter.id} reciterName=${reciter.name}")
            try {
                val surahAudio = audioRepository.getSurahAudio(reciter.id, surahId)
                val resolved = resolveLocalAudio(surahAudio, reciter.id, surahId)
                applyRealTimestampsKnowledge(reciter.id, surahAudio.ayahTimestamps.isNotEmpty(), surahAudio.verseTimingsReliable)
                audioPlayer.playSurah(resolved, null)
            } catch (e: Exception) {
                Log.e("AudioDebug", "playAudioSurah error: ${e.message}")
            }
        }
    }

    private fun resolveLocalAudio(surahAudio: SurahAudio, reciterId: Int, surahId: Int): SurahAudio {
        val localFile = downloadManager.getLocalAudioFile(reciterId, surahId)
        Log.d("OFFLINE_AUDIO_DEBUG", "resolveLocalAudio reciterId=$reciterId surahId=$surahId originalUrl=${surahAudio.audioUrl.take(80)}")
        if (localFile != null) {
            val localUri = Uri.fromFile(localFile).toString()
            Log.d("OFFLINE_AUDIO_DEBUG", "resolveLocalAudio resolved local URI=$localUri exists=${localFile.exists()} length=${localFile.length()} canRead=${localFile.canRead()}")
            return surahAudio.copy(audioUrl = localUri)
        }
        Log.d("OFFLINE_AUDIO_DEBUG", "resolveLocalAudio no local file, keeping remote URL")
        return surahAudio
    }

    fun playSurah(surahId: Int, startAyah: AyahTimestamp? = null) {
        scope.launch {
            val reciter = _selectedReciter.value ?: _reciters.value.firstOrNull() ?: return@launch
            Log.d("OFFLINE_AUDIO_DEBUG", "playSurah called surahId=$surahId reciterId=${reciter.id} reciterName=${reciter.name}")
            
            try {
                val surahAudio = audioRepository.getSurahAudio(reciter.id, surahId)
                Log.d("OFFLINE_AUDIO_DEBUG", "playSurah got audioUrl=${surahAudio.audioUrl.take(80)} duration=${surahAudio.durationMs} timestamps=${surahAudio.ayahTimestamps.size}")
                val resolved = resolveLocalAudio(surahAudio, reciter.id, surahId)
                applyRealTimestampsKnowledge(reciter.id, surahAudio.ayahTimestamps.isNotEmpty(), surahAudio.verseTimingsReliable)
                Log.d("OFFLINE_AUDIO_DEBUG", "playSurah resolvedUrl=${resolved.audioUrl.take(100)}")
                audioPlayer.playSurah(resolved, startAyah)
            } catch (e: Exception) {
                Log.e("OFFLINE_AUDIO_DEBUG", "playSurah error: ${e.message}")
            }
        }
    }

    fun playSurahWithVerse(surahId: Int, verseKey: String) {
        scope.launch {
            val reciter = _selectedReciter.value ?: _reciters.value.firstOrNull() ?: return@launch
            Log.d("OFFLINE_AUDIO_DEBUG", "playSurahWithVerse surahId=$surahId verseKey=$verseKey reciterId=${reciter.id}")

            try {
                val surahAudio = audioRepository.getSurahAudio(reciter.id, surahId)
                Log.d("OFFLINE_AUDIO_DEBUG", "playSurahWithVerse audioUrl=${surahAudio.audioUrl.take(80)} timestamps=${surahAudio.ayahTimestamps.size}")
                applyRealTimestampsKnowledge(reciter.id, surahAudio.ayahTimestamps.isNotEmpty(), surahAudio.verseTimingsReliable)
                val parts = verseKey.split(":")
                val targetAyahNum = parts.getOrNull(1)?.toIntOrNull() ?: 1

                var targetAyah = surahAudio.ayahTimestamps.find { it.verseKey == verseKey }
                if (targetAyah == null) {
                    targetAyah = surahAudio.ayahTimestamps.find { it.ayahNumber == targetAyahNum }
                }

                if (targetAyah != null) {
                    Log.d("TIMESTAMP_DEBUG", "selectedVerseKey=$verseKey timestampFound=true startMs=${targetAyah.startMs} endMs=${targetAyah.endMs}")
                } else {
                    Log.w("TIMESTAMP_DEBUG", "selectedVerseKey=$verseKey timestampFound=false (will start from beginning)")
                }

                val resolved = resolveLocalAudio(surahAudio, reciter.id, surahId)
                Log.d("OFFLINE_AUDIO_DEBUG", "playSurahWithVerse resolvedUrl=${resolved.audioUrl.take(100)}")
                audioPlayer.playSurah(resolved, targetAyah)
            } catch (e: Exception) {
                Log.e("OFFLINE_AUDIO_DEBUG", "playSurahWithVerse error: ${e.message}")
            }
        }
    }

    fun togglePlayPause() {
        audioPlayer.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun setRepeatMode(mode: RepeatMode) {
        audioPlayer.setRepeatMode(mode)
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioPlayerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
