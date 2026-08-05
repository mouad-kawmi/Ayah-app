package com.example.quranapp.data.audio

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class QuranMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var audioPlayer: QuranAudioPlayer

    override fun onCreate() {
        super.onCreate()
        audioPlayer = QuranAudioPlayer(applicationContext)
        mediaSession = MediaSession.Builder(this, audioPlayer.underlyingPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        audioPlayer.release()
        super.onDestroy()
    }
}
