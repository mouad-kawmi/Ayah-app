package com.example.quranapp.data.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.quranapp.domain.model.AyahTimestamp
import com.example.quranapp.domain.model.SurahAudio
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode {
    OFF, AYAH, SURAH
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentSurahAudio: SurahAudio? = null,
    val currentAyah: AyahTimestamp? = null, // playingAyah
    val selectedAyah: AyahTimestamp? = null, // selectedAyah
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLoading: Boolean = false
) {
    val currentPlayingVerseKey: String? get() = currentAyah?.verseKey
}

class QuranAudioPlayer(private val context: Context) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Separate high-frequency flow for playback position to avoid polluting PlayerState
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private var positionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startPositionMonitoring()
                } else {
                    stopPositionMonitoring()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    Log.d("OFFLINE_AUDIO_DEBUG", "ExoPlayer STATE_READY duration=$duration")
                    _playerState.value = _playerState.value.copy(
                        durationMs = duration,
                        isLoading = false
                    )
                } else if (playbackState == Player.STATE_BUFFERING) {
                    Log.d("OFFLINE_AUDIO_DEBUG", "ExoPlayer STATE_BUFFERING")
                    _playerState.value = _playerState.value.copy(isLoading = true)
                } else if (playbackState == Player.STATE_ENDED) {
                    Log.d("OFFLINE_AUDIO_DEBUG", "ExoPlayer STATE_ENDED")
                    handlePlaybackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val currentUri = _playerState.value.currentSurahAudio?.audioUrl ?: "unknown"
                Log.e("OFFLINE_AUDIO_DEBUG", "Playback error uri=$currentUri errorCode=${error.errorCode} message=${error.message} cause=${error.cause}")
                _playerState.value = _playerState.value.copy(isLoading = false)
            }
        })
    }

    fun playSurah(surahAudio: SurahAudio, startAyah: AyahTimestamp? = null) {
        val isLocalFile = surahAudio.audioUrl.startsWith("file://")
        Log.d("OFFLINE_AUDIO_DEBUG", "playSurah reciterId=${surahAudio.reciterId} surahId=${surahAudio.surahId} isLocalFile=$isLocalFile")
        Log.d("OFFLINE_AUDIO_DEBUG", "playSurah finalURI=${surahAudio.audioUrl} durationMs=${surahAudio.durationMs} timestamps=${surahAudio.ayahTimestamps.size}")

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("QuranAppKotlin")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)

        // Use DefaultDataSource as upstream so both file:// and http:// URIs work
        val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)

        val cache = AudioCacheManager.getInstance(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaItem = MediaItem.fromUri(surahAudio.audioUrl)
        val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .createMediaSource(mediaItem)

        Log.d("TIMESTAMP_DEBUG", "playSurah startAyah=${startAyah?.verseKey} startMs=${startAyah?.startMs}")

        exoPlayer.setMediaSource(mediaSource)

        // Seek BEFORE prepare so ExoPlayer buffers from the correct position
        val seekPos = startAyah?.startMs ?: 0L
        if (seekPos > 0) {
            exoPlayer.seekTo(seekPos)
            Log.d("TIMESTAMP_DEBUG", "seekTo=$seekPos (before prepare)")
        }

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        val initialAyah = startAyah ?: surahAudio.ayahTimestamps.firstOrNull()

        _playerState.value = _playerState.value.copy(
            currentSurahAudio = surahAudio,
            durationMs = surahAudio.durationMs,
            currentAyah = initialAyah,
            selectedAyah = startAyah,
            isLoading = true
        )
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updateCurrentAyah(positionMs)
    }

    fun seekToAyah(ayah: AyahTimestamp) {
        exoPlayer.seekTo(ayah.startMs)
        _playerState.value = _playerState.value.copy(
            currentAyah = ayah,
            selectedAyah = ayah
        )
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playerState.value = _playerState.value.copy(repeatMode = mode)
    }

    fun release() {
        stopPositionMonitoring()
        scope.cancel()
        exoPlayer.release()
    }

    private fun startPositionMonitoring() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition
                _playbackPosition.value = pos
                updateCurrentAyah(pos)
                delay(200L)
            }
        }
    }

    private fun stopPositionMonitoring() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun updateCurrentAyah(positionMs: Long) {
        val surahAudio = _playerState.value.currentSurahAudio ?: return
        if (surahAudio.ayahTimestamps.isEmpty()) return

        val state = _playerState.value
        if (state.repeatMode == RepeatMode.AYAH && state.currentAyah != null) {
            if (positionMs >= state.currentAyah.endMs) {
                exoPlayer.seekTo(state.currentAyah.startMs)
                return
            }
        }

        val matchingAyah = surahAudio.ayahTimestamps.find { 
            positionMs >= it.startMs && positionMs <= it.endMs 
        } ?: surahAudio.ayahTimestamps.lastOrNull { positionMs >= it.startMs }

        if (matchingAyah != null && matchingAyah != state.currentAyah) {
            _playerState.value = state.copy(currentAyah = matchingAyah)
        }
    }

    private fun handlePlaybackEnded() {
        val state = _playerState.value
        if (state.repeatMode == RepeatMode.SURAH && state.currentSurahAudio != null) {
            exoPlayer.seekTo(0)
            exoPlayer.play()
        } else if (state.repeatMode == RepeatMode.AYAH && state.currentAyah != null) {
            exoPlayer.seekTo(state.currentAyah.startMs)
            exoPlayer.play()
        } else {
            _playerState.value = state.copy(isPlaying = false)
        }
    }

    val underlyingPlayer: ExoPlayer get() = exoPlayer
}
