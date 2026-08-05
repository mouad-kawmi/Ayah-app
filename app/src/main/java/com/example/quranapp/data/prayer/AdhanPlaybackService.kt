package com.example.quranapp.data.prayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.DiagnosticsDashboard
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.core.debug.PipelineStageType
import com.example.quranapp.core.utils.QuranPreferences

class AdhanPlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var currentPrayerKey: String? = null
    @Volatile private var currentTraceId: String = Instrumentation.NO_TRACE
    @Volatile private var currentCity: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("PRAYER", "AdhanPlaybackService — onCreate")
        DebugLogger.info(
            LogCategory.ADHAN,
            Instrumentation.line("service", Instrumentation.NO_TRACE, null, "AdhanPlaybackService created")
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            Log.d("PRAYER", "Adhan stop requested")
            DebugLogger.info(
                LogCategory.ADHAN,
                Instrumentation.line(currentPrayerKey ?: "service", currentTraceId, currentCity, "Adhan stop requested")
            )
            PrayerStateMachine.markAdhanEnded(currentTraceId, currentCity)
            DiagnosticsDashboard.updateStage(
                currentPrayerKey ?: "service", currentTraceId,
                PipelineStageType.ADHAN_PLAYBACK_COMPLETED, "stopped by user"
            )
            DiagnosticsDashboard.updateStage(
                currentPrayerKey ?: "service", currentTraceId,
                PipelineStageType.PIPELINE_COMPLETED, "stopped by user"
            )
            mediaPlayer?.release()
            mediaPlayer = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        val prayerKey = intent?.getStringExtra(EXTRA_PRAYER_KEY)
        val adhanKey = intent?.getStringExtra(EXTRA_ADHAN_KEY)
        val traceId = intent?.getStringExtra(EXTRA_TRACE_ID) ?: Instrumentation.NO_TRACE
        currentPrayerKey = prayerKey
        currentTraceId = traceId
        if (currentCity == null) {
            currentCity = runCatching { PrayerTimesCacheStore.getCachedCityName(this) }.getOrNull()
        }

        val isCustom = "custom" == adhanKey
        val customUri = if (isCustom) QuranPreferences.getCustomAdhanUri(this) else null

        val option = if (customUri != null) {
            AdhanCatalogEntry("custom", QuranPreferences.getCustomAdhanName(this) ?: "أذان مخصص", "")
        } else {
            AdhanAudioCatalog.findByKey(adhanKey)
        }

        if (prayerKey != null) {
            val prayerEnabled = getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
                .getBoolean("enable_$prayerKey", true)
            if (!prayerEnabled) {
                DebugLogger.info(
                    LogCategory.ADHAN,
                    Instrumentation.line(prayerKey, traceId, currentCity, "Service aborted playback — prayer disabled by user")
                )
                DiagnosticsDashboard.updateStage(
                    prayerKey, traceId, PipelineStageType.PIPELINE_COMPLETED,
                    "aborted — prayer disabled by user"
                )
                PrayerStateMachine.markAdhanEnded(traceId, currentCity)
                startForeground(NOTIFICATION_ID, buildNotification(prayerName, option.label))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        try {
            DiagnosticsDashboard.updateStage(
                prayerKey ?: "service", traceId, PipelineStageType.FOREGROUND_SERVICE_STARTED,
                "service started startId=$startId"
            )
            DebugLogger.info(
                LogCategory.ADHAN,
                Instrumentation.line(prayerKey ?: "service", traceId, currentCity, "Foreground service started adhan=$adhanKey name=$prayerName")
            )
            startForeground(NOTIFICATION_ID, buildNotification(prayerName, option.label))
            DiagnosticsDashboard.updateStage(
                prayerKey ?: "service", traceId, PipelineStageType.FOREGROUND_NOTIFICATION_SHOWN, "id=$NOTIFICATION_ID"
            )
            DebugLogger.info(
                LogCategory.NOTIFICATION,
                Instrumentation.line(prayerKey ?: "service", traceId, currentCity, "Foreground notification shown id=$NOTIFICATION_ID")
            )
        } catch (e: Exception) {
            Log.e("PRAYER", "startForeground failed", e)
            DebugLogger.error(
                LogCategory.ADHAN,
                Instrumentation.line(prayerKey ?: "service", traceId, currentCity, ErrorCode.FOREGROUND_SERVICE_FAILED.prefix("startForeground failed")),
                e
            )
            DiagnosticsDashboard.recordFailure(
                prayerKey ?: "service", traceId, PipelineStageType.FOREGROUND_START_FAILED,
                "startForeground failed: ${e.message ?: e.javaClass.simpleName}"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        if (customUri != null) {
            playFromUri(customUri)
        } else {
            playFromAsset(option.assetPath)
        }
        return START_NOT_STICKY
    }

    private fun playFromAsset(assetPath: String) {
        mediaPlayer?.release()
        mediaPlayer = null

        val traceId = currentTraceId
        val city = currentCity
        val prayerKey = currentPrayerKey ?: "service"

        try {
            val afd = assets.openFd(assetPath)
            val volume = QuranPreferences.getAdhanVolume(this)
            mediaPlayer = MediaPlayer()
            mediaPlayer!!.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setVolume(volume / 100f, volume / 100f)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener {
                    Log.d("PRAYER", "Adhan playback completed")
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId,
                        PipelineStageType.ADHAN_PLAYBACK_COMPLETED, "asset=$assetPath"
                    )
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId,
                        PipelineStageType.PIPELINE_COMPLETED, "adhan finished"
                    )
                    DebugLogger.info(
                        LogCategory.ADHAN,
                        Instrumentation.line(prayerKey, traceId, city, "Adhan playback completed")
                    )
                    PrayerStateMachine.markAdhanEnded(traceId, city)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                setOnErrorListener { _, _, extra ->
                    Log.e("PRAYER", "Adhan playback error extra=$extra")
                    DebugLogger.error(
                        LogCategory.ADHAN,
                        Instrumentation.line(prayerKey, traceId, city, ErrorCode.ADHAN_PLAYBACK_ERROR.prefix("Adhan playback error extra=$extra"))
                    )
                    DiagnosticsDashboard.recordFailure(
                        prayerKey, traceId, PipelineStageType.PIPELINE_FAILED,
                        "Adhan playback error extra=$extra"
                    )
                    PrayerStateMachine.markAdhanEnded(traceId, city)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    true
                }
                prepare()
                start()
                Log.d("PRAYER", "Adhan playback started: $assetPath")
                DiagnosticsDashboard.updateStage(
                    prayerKey, traceId,
                    PipelineStageType.ADHAN_PLAYBACK_STARTED, "asset=$assetPath"
                )
                DebugLogger.info(
                    LogCategory.ADHAN,
                    Instrumentation.line(prayerKey, traceId, city, "Adhan playback started asset=$assetPath")
                )
            }
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            Log.e("PRAYER", "Adhan playback failed to start", e)
            DebugLogger.error(
                LogCategory.ADHAN,
                Instrumentation.line(prayerKey, traceId, city, ErrorCode.ADHAN_AUDIO_MISSING.prefix("Adhan playback failed to start")),
                e
            )
            DiagnosticsDashboard.recordFailure(
                prayerKey, traceId, PipelineStageType.PIPELINE_FAILED,
                "Adhan playback failed to start: ${e.message ?: e.javaClass.simpleName}"
            )
            PrayerStateMachine.markAdhanEnded(traceId, city)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun playFromUri(uriString: String) {
        mediaPlayer?.release()
        mediaPlayer = null

        val traceId = currentTraceId
        val city = currentCity
        val prayerKey = currentPrayerKey ?: "service"

        try {
            val uri = Uri.parse(uriString)
            val volume = QuranPreferences.getAdhanVolume(this)
            mediaPlayer = MediaPlayer()
            mediaPlayer!!.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setVolume(volume / 100f, volume / 100f)
                setDataSource(this@AdhanPlaybackService, uri)
                setOnCompletionListener {
                    Log.d("PRAYER", "Adhan playback completed")
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId,
                        PipelineStageType.ADHAN_PLAYBACK_COMPLETED, "uri=$uriString"
                    )
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId,
                        PipelineStageType.PIPELINE_COMPLETED, "adhan finished"
                    )
                    DebugLogger.info(
                        LogCategory.ADHAN,
                        Instrumentation.line(prayerKey, traceId, city, "Adhan playback completed")
                    )
                    PrayerStateMachine.markAdhanEnded(traceId, city)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                setOnErrorListener { _, _, extra ->
                    Log.e("PRAYER", "Adhan URI playback error extra=$extra")
                    DebugLogger.error(
                        LogCategory.ADHAN,
                        Instrumentation.line(prayerKey, traceId, city, ErrorCode.ADHAN_PLAYBACK_ERROR.prefix("Adhan URI playback error extra=$extra"))
                    )
                    DiagnosticsDashboard.recordFailure(
                        prayerKey, traceId, PipelineStageType.PIPELINE_FAILED,
                        "Adhan URI playback error extra=$extra"
                    )
                    PrayerStateMachine.markAdhanEnded(traceId, city)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    true
                }
                prepare()
                start()
                Log.d("PRAYER", "Adhan playback started from URI: $uriString")
                DiagnosticsDashboard.updateStage(
                    prayerKey, traceId,
                    PipelineStageType.ADHAN_PLAYBACK_STARTED, "uri=$uriString"
                )
                DebugLogger.info(
                    LogCategory.ADHAN,
                    Instrumentation.line(prayerKey, traceId, city, "Adhan playback started uri=$uriString")
                )
            }
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            Log.e("PRAYER", "Adhan URI playback failed, falling back to default", e)
            DebugLogger.error(
                LogCategory.ADHAN,
                Instrumentation.line(prayerKey, traceId, city, ErrorCode.ADHAN_PLAYBACK_START_FAILED.prefix("Adhan URI playback failed — falling back to default")),
                e
            )
            playFromAsset(AdhanAudioCatalog.defaultOption().assetPath)
        }
    }

    private fun buildNotification(prayerName: String, adhanLabel: String): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "الأذان الجاري",
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
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.quranapp.R.drawable.ic_monochrome)
            .setContentTitle("حان وقت صلاة $prayerName")
            .setContentText("يتم الآن تشغيل $adhanLabel")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stopPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        Log.d("PRAYER", "AdhanPlaybackService — onDestroy")
        DebugLogger.info(
            LogCategory.ADHAN,
            Instrumentation.line(currentPrayerKey ?: "service", currentTraceId, currentCity, "AdhanPlaybackService destroyed")
        )
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("PRAYER", "AdhanPlaybackService — onTaskRemoved")
        DebugLogger.info(
            LogCategory.ADHAN,
            Instrumentation.line(currentPrayerKey ?: "service", currentTraceId, currentCity, "AdhanPlaybackService task removed")
        )
        PrayerStateMachine.markAdhanEnded(currentTraceId, currentCity)
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
        private const val CHANNEL_ID = "ADHAN_PLAYBACK_CHANNEL"
        private const val NOTIFICATION_ID = 7001
    }
}
