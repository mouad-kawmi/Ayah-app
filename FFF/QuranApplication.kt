package com.aistudio.quran.mwkpqz

import android.app.Application
import android.util.Log
import com.example.PrayerStateMachine
import com.example.QcfRepository
import com.example.audio.di.AudioModule
import com.example.debug.CrashHandler
import com.example.debug.DebugLogger
import com.example.debug.DiagnosticsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuranApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize diagnostics infrastructure first so all subsequent
        // operations are captured by the logging system.
        DebugLogger.initialize(this)
        DiagnosticsCollector.initialize(this)
        CrashHandler.initialize(this)

        AudioModule.init(this)
        createPlaybackNotificationChannel()
        PrayerStateMachine.init(this)

        applicationScope.launch {
            try {
                QcfRepository.preloadSearchCache(this@QuranApplication)
            } catch (t: Throwable) {
                Log.e("QuranApplication", "Search preload failed", t)
            }
        }
    }

    private fun createPlaybackNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "quran_playback",
                "\u0645\u0634\u063A\u0644 \u0627\u0644\u0642\u0631\u0622\u0646",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "\u0625\u0634\u0639\u0627\u0631\u0627\u062A \u062A\u0634\u063A\u064A\u0644 \u0627\u0644\u0642\u0631\u0622\u0646"
                setShowBadge(false)
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
