package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdhanPreviewPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var autoStopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    fun playFromResource(context: Context, resId: Int) {
        stop()
        try {
            val afd = context.resources.openRawResourceFd(resId)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                afd.use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                setOnCompletionListener { releasePlayer() }
                prepare()
                start()
            }
            scheduleAutoStop()
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    fun playFromUri(context: Context, uri: Uri) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener { releasePlayer() }
                prepare()
                start()
            }
            scheduleAutoStop()
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    fun stop() {
        releasePlayer()
    }

    private fun scheduleAutoStop() {
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(15_000L)
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        autoStopJob?.cancel()
        autoStopJob = null
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }
}
