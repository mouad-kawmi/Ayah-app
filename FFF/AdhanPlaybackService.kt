package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.aistudio.quran.mwkpqz.R
import com.example.debug.DebugLogger
import com.example.debug.DiagnosticsDashboard
import com.example.debug.LogCategory
import com.example.debug.PerformanceProfiler
import com.example.debug.PipelineStageType

class AdhanPlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var currentTraceId: String? = null
    @Volatile private var currentPrayerKey: String? = null

    override fun onCreate() {
        super.onCreate()
        DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — onCreate")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            DebugLogger.info(LogCategory.ADHAN, "Adhan stop requested")
            DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — stop requested, releasing")
            PrayerStateMachine.markAdhanEnded()
            mediaPlayer?.release()
            mediaPlayer = null
            currentTraceId?.let { traceId ->
                currentPrayerKey?.let { key ->
                    DiagnosticsDashboard.updateStage(key, traceId, PipelineStageType.FOREGROUND_NOTIFICATION_REMOVED)
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        val prayerKey = intent?.getStringExtra(EXTRA_PRAYER_KEY)
        val adhanKey = intent?.getStringExtra(EXTRA_ADHAN_KEY)
        val traceId = intent?.getStringExtra(EXTRA_TRACE_ID)

        currentTraceId = traceId
        currentPrayerKey = prayerKey

        val adhanLabel: String
        if (adhanKey == CUSTOM_ADHAN_KEY) {
            val customPath = getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
                .getString("custom_adhan_path", null)
            if (customPath != null) {
                adhanLabel = "أذان مخصص"
                DebugLogger.info(LogCategory.ADHAN, "Service started — prayer=$prayerName adhan=custom path=$customPath traceId=$traceId")
                DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — before startForeground prayer=$prayerName adhan=custom")
                try {
                    startForeground(NOTIFICATION_ID, buildNotification(prayerName, adhanLabel))
                    if (prayerKey != null && traceId != null) {
                        DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.FOREGROUND_SERVICE_STARTED)
                        DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.FOREGROUND_NOTIFICATION_SHOWN)
                    }
                } catch (e: Exception) {
                    DebugLogger.error(LogCategory.ALARM, "AdhanPlaybackService — startForeground failed", e)
                    if (prayerKey != null && traceId != null) {
                        DiagnosticsDashboard.recordFailure(prayerKey, traceId, PipelineStageType.FOREGROUND_START_FAILED, "startForeground failed: ${e.message}")
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }
                playFromFile(customPath)
                return START_NOT_STICKY
            }
            // fallback to default if custom path is null
            val defaultOption = AdhanAudioCatalog.defaultOption()
            adhanLabel = defaultOption.label
            DebugLogger.info(LogCategory.ADHAN, "Custom adhan path missing, falling back to default — prayer=$prayerName adhan=${defaultOption.label} traceId=$traceId")
            DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — custom path missing, fallback to ${defaultOption.label}")
            try {
                startForeground(NOTIFICATION_ID, buildNotification(prayerName, adhanLabel))
            } catch (e: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
            play(defaultOption.rawResId)
            return START_NOT_STICKY
        }

        val option = AdhanAudioCatalog.findByKey(adhanKey)
        adhanLabel = option.label
        DebugLogger.info(LogCategory.ADHAN, "Service started — prayer=$prayerName adhan=${option.label} resId=${option.rawResId} traceId=$traceId")
        DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — before startForeground prayer=$prayerName adhan=${option.label}")

        try {
            startForeground(NOTIFICATION_ID, buildNotification(prayerName, adhanLabel))
            DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — after startForeground")
            DebugLogger.debug(LogCategory.ADHAN, "Foreground notification created")
            if (prayerKey != null && traceId != null) {
                DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.FOREGROUND_SERVICE_STARTED)
                DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.FOREGROUND_NOTIFICATION_SHOWN)
            }
        } catch (e: Exception) {
            DebugLogger.error(LogCategory.ALARM, "AdhanPlaybackService — startForeground failed", e)
            if (prayerKey != null && traceId != null) {
                DiagnosticsDashboard.recordFailure(prayerKey, traceId, PipelineStageType.FOREGROUND_START_FAILED, "startForeground failed: ${e.message}")
            }
            stopSelf()
            return START_NOT_STICKY
        }
        play(option.rawResId)
        return START_NOT_STICKY
    }

    private fun play(rawResId: Int) {
        val overallStart = System.currentTimeMillis()
        mediaPlayer?.release()
        mediaPlayer = null

        DebugLogger.debug(LogCategory.ADHAN, "Audio resource selected — rawResId=$rawResId")
        DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — play rawResId=$rawResId")
        try {
            val prepareStart = System.currentTimeMillis()
            val assetFileDescriptor = resources.openRawResourceFd(rawResId)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                assetFileDescriptor.use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                setOnCompletionListener {
                    DebugLogger.info(LogCategory.ADHAN, "Playback completed")
                    DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — onCompletion")
                    PrayerStateMachine.markAdhanEnded()
                    val duration = System.currentTimeMillis() - overallStart
                    PerformanceProfiler.logAudioOperation("Playback Completed", duration)
                    currentPrayerKey?.let { key ->
                        currentTraceId?.let { tid ->
                            DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.ADHAN_PLAYBACK_COMPLETED)
                            DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.FOREGROUND_NOTIFICATION_REMOVED)
                        }
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                setOnErrorListener { _, _, extra ->
                    DebugLogger.error(LogCategory.ADHAN, "Playback error — what=$extra")
                    DebugLogger.error(LogCategory.ALARM, "AdhanPlaybackService — onError extra=$extra")
                    val duration = System.currentTimeMillis() - overallStart
                    PerformanceProfiler.logAudioOperation("Playback Error", duration)
                    currentPrayerKey?.let { key ->
                        currentTraceId?.let { tid ->
                            DiagnosticsDashboard.recordFailure(key, tid, PipelineStageType.PIPELINE_FAILED, "Playback error: $extra")
                        }
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    true
                }
                prepare()
                DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — prepared")
                val prepareDuration = System.currentTimeMillis() - prepareStart
                PerformanceProfiler.logAudioOperation("Audio Prepared", prepareDuration)
                start()
                DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — started")
                val startDuration = System.currentTimeMillis() - overallStart
                PerformanceProfiler.logAudioOperation("Playback Started", startDuration)
                DebugLogger.info(LogCategory.ADHAN, "Playback started")
                currentPrayerKey?.let { key ->
                    currentTraceId?.let { tid ->
                        DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.ADHAN_PLAYBACK_STARTED)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.error(LogCategory.ADHAN, "Playback failed to start", e)
            DebugLogger.error(LogCategory.ALARM, "AdhanPlaybackService — play failed rawResId=$rawResId", e)
            val duration = System.currentTimeMillis() - overallStart
            PerformanceProfiler.logAudioOperation("Playback Failed to Start", duration)
            currentPrayerKey?.let { key ->
                currentTraceId?.let { tid ->
                    DiagnosticsDashboard.recordFailure(key, tid, PipelineStageType.PIPELINE_FAILED, "Playback failed to start: ${e.message}")
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun playFromFile(path: String) {
        val overallStart = System.currentTimeMillis()
        mediaPlayer?.release()
        mediaPlayer = null

        DebugLogger.debug(LogCategory.ADHAN, "Audio file selected — path=$path")
        DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — playFromFile path=$path")
        try {
            val prepareStart = System.currentTimeMillis()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(path)
                setOnCompletionListener {
                    DebugLogger.info(LogCategory.ADHAN, "Playback completed")
                    DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — onCompletion")
                    PrayerStateMachine.markAdhanEnded()
                    val duration = System.currentTimeMillis() - overallStart
                    PerformanceProfiler.logAudioOperation("Playback Completed", duration)
                    currentPrayerKey?.let { key ->
                        currentTraceId?.let { tid ->
                            DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.ADHAN_PLAYBACK_COMPLETED)
                            DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.FOREGROUND_NOTIFICATION_REMOVED)
                        }
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                setOnErrorListener { _, _, extra ->
                    DebugLogger.error(LogCategory.ADHAN, "Playback error — what=$extra")
                    DebugLogger.error(LogCategory.ALARM, "AdhanPlaybackService — onError extra=$extra")
                    val duration = System.currentTimeMillis() - overallStart
                    PerformanceProfiler.logAudioOperation("Playback Error", duration)
                    currentPrayerKey?.let { key ->
                        currentTraceId?.let { tid ->
                            DiagnosticsDashboard.recordFailure(key, tid, PipelineStageType.PIPELINE_FAILED, "Playback error: $extra")
                        }
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    true
                }
                prepare()
                DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — prepared")
                val prepareDuration = System.currentTimeMillis() - prepareStart
                PerformanceProfiler.logAudioOperation("Audio Prepared", prepareDuration)
                start()
                DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — started")
                val startDuration = System.currentTimeMillis() - overallStart
                PerformanceProfiler.logAudioOperation("Playback Started", startDuration)
                DebugLogger.info(LogCategory.ADHAN, "Playback started")
                currentPrayerKey?.let { key ->
                    currentTraceId?.let { tid ->
                        DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.ADHAN_PLAYBACK_STARTED)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.error(LogCategory.ADHAN, "Playback failed to start", e)
            DebugLogger.error(LogCategory.ALARM, "AdhanPlaybackService — playFromFile failed path=$path", e)
            val duration = System.currentTimeMillis() - overallStart
            PerformanceProfiler.logAudioOperation("Playback Failed to Start", duration)
            currentPrayerKey?.let { key ->
                currentTraceId?.let { tid ->
                    DiagnosticsDashboard.recordFailure(key, tid, PipelineStageType.PIPELINE_FAILED, "Playback failed to start: ${e.message}")
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(prayerName: String, adhanLabel: String): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "الأذان الجاري",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تشغيل الأذان عند دخول وقت الصلاة"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, AdhanPlaybackService::class.java).apply {
            action = ACTION_STOP_ADHAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_prayer_alert)
            .setContentTitle("حان وقت صلاة $prayerName")
            .setContentText("يتم الآن تشغيل $adhanLabel")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_prayer_alert, "إيقاف", stopPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        DebugLogger.debug(LogCategory.ADHAN, "Service destroyed")
        DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — onDestroy")
        PrayerStateMachine.markAdhanEnded()
        currentPrayerKey?.let { key ->
            currentTraceId?.let { tid ->
                DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.FOREGROUND_NOTIFICATION_REMOVED)
            }
        }
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        DebugLogger.debug(LogCategory.ADHAN, "Task removed — stopping playback")
        DebugLogger.debug(LogCategory.ALARM, "AdhanPlaybackService — onTaskRemoved")
        PrayerStateMachine.markAdhanEnded()
        currentPrayerKey?.let { key ->
            currentTraceId?.let { tid ->
                DiagnosticsDashboard.updateStage(key, tid, PipelineStageType.FOREGROUND_NOTIFICATION_REMOVED)
            }
        }
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STOP_ADHAN = "com.example.ACTION_STOP_ADHAN"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_KEY = "extra_prayer_key"
        const val EXTRA_ADHAN_KEY = "extra_adhan_key"
        const val EXTRA_TRACE_ID = "extra_trace_id"
        const val CUSTOM_ADHAN_KEY = "custom"
        private const val CHANNEL_ID = "ADHAN_PLAYBACK_CHANNEL"
        private const val NOTIFICATION_ID = 7001
    }
}
