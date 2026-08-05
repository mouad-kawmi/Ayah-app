package com.example

import android.util.Log
import com.aistudio.quran.mwkpqz.R
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.debug.DebugLogger
import com.example.debug.DiagnosticsDashboard
import com.example.debug.LogCategory
import com.example.debug.PipelineStageType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SunnanNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("extra_title") ?: "تذكير بالسنة"
        val message = intent.getStringExtra("extra_message") ?: ""
        val traceId = intent.getStringExtra("extra_trace_id") ?: "unknown"
        val pipelineKey = intent.getStringExtra("extra_pipeline_key") ?: "sunnan_unknown"

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SunnanNotificationReceiver:$pipelineKey")
        wakeLock.acquire(15_000L)

        try {
            DebugLogger.info(LogCategory.NOTIFICATION, "Sunnan reminder fired — title=$title traceId=$traceId")

            DiagnosticsDashboard.updateStage(pipelineKey, traceId, PipelineStageType.RECEIVER_STARTED, "title=$title")
            DiagnosticsDashboard.updateStage(pipelineKey, traceId, PipelineStageType.WAKE_LOCK_ACQUIRED)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "SUNNAN_ALERTS_CHANNEL"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "السنن - تذكيرات",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "قناة لتنبيهات السنن الرواتب والمستحبة"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notifyIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (!notificationManager.areNotificationsEnabled()) {
                DebugLogger.warning(
                    LogCategory.NOTIFICATION,
                    "Sunnan notifications are disabled. Cannot show reminder — title=$title"
                )
                DiagnosticsDashboard.recordFailure(pipelineKey, traceId, PipelineStageType.NOTIFICATION_BLOCKED_PERMISSION, "POST_NOTIFICATIONS denied or notifications disabled by user")
            } else {
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_prayer_alert)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                val notificationId = title.hashCode()
                DebugLogger.info(LogCategory.NOTIFICATION, "Sunnan notification shown — title=$title id=$notificationId")
                notificationManager.notify(notificationId, notification)
                DiagnosticsDashboard.updateStage(pipelineKey, traceId, PipelineStageType.NOTIFICATION_SHOWN, "id=$notificationId")
            }

            DiagnosticsDashboard.updateStage(pipelineKey, traceId, PipelineStageType.PIPELINE_COMPLETED)
        } catch (e: Exception) {
            DebugLogger.error(LogCategory.NOTIFICATION, "SunnanNotificationReceiver — unhandled exception", e)
            DiagnosticsDashboard.recordFailure(pipelineKey, traceId, PipelineStageType.PIPELINE_FAILED, "Receiver exception: ${e.message}")
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}

object SunnanAlarmScheduler {
    fun updateSunnanAlarms(context: Context) {
        DebugLogger.info(LogCategory.ALARM, "updateSunnanAlarms() started")
        val prefs = context.getSharedPreferences("sunnan_prefs", Context.MODE_PRIVATE)
        val fastingEnabled = prefs.getBoolean("fasting", false)
        val kahfEnabled = prefs.getBoolean("kahf", false)
        val duhaEnabled = prefs.getBoolean("duha", false)
        val witrEnabled = prefs.getBoolean("witr", false)
        val sabahEnabled = prefs.getBoolean("sabah", false)
        val masaaEnabled = prefs.getBoolean("masaa", false)

        DebugLogger.debug(
            LogCategory.ALARM,
            "Sunnan switches — fasting=$fastingEnabled kahf=$kahfEnabled duha=$duhaEnabled witr=$witrEnabled sabah=$sabahEnabled masaa=$masaaEnabled"
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel all first to refresh
        cancelAllSunnanAlarms(context, alarmManager)
        
        val now = LocalDateTime.now()

        // 1. Fasting Mon/Thu (Remind Sun 20:00 and Wed 20:00)
        if (fastingEnabled) {
            scheduleWeekly(context, alarmManager, "صيام الإثنين والخميس", "تذكير: غداً يوم يسن فيه الصيام. تقبل الله طاعتكم.", DayOfWeek.SUNDAY, LocalTime.of(20, 0), now)
            scheduleWeekly(context, alarmManager, "صيام الإثنين والخميس", "تذكير: غداً يوم يسن فيه الصيام. تقبل الله طاعتكم.", DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), now)
        }

        // 2. Surah Al-Kahf (Remind Fri 09:00)
        if (kahfEnabled) {
            scheduleWeekly(context, alarmManager, "سورة الكهف", "نذكرك بقراءة سورة الكهف في هذا اليوم الفضيل.", DayOfWeek.FRIDAY, LocalTime.of(9, 0), now)
        }

        // 3. Duha (Daily 09:00)
        if (duhaEnabled) {
            scheduleDaily(context, alarmManager, "صلاة الضحى", "حان وقت صلاة الضحى، صلاة الأوابين.", LocalTime.of(9, 0), now)
        }

        // 4. Witr (Daily 22:00)
        if (witrEnabled) {
            scheduleDaily(context, alarmManager, "صلاة الوتر", "لا تنس صلاة الوتر قبل أن تنام، واختم يومك بطاعة.", LocalTime.of(22, 0), now)
        }

        // 5. Sabah Adhkar (Daily 07:00)
        if (sabahEnabled) {
            scheduleDaily(context, alarmManager, "أذكار الصباح", "حان وقت أذكار الصباح، احفظ يومك بذكر الله.", LocalTime.of(7, 0), now)
        }

        // 6. Masaa Adhkar (Daily 17:00)
        if (masaaEnabled) {
            scheduleDaily(context, alarmManager, "أذكار المساء", "حان وقت أذكار المساء، لا تنس ذكر الله بنهاية يومك.", LocalTime.of(17, 0), now)
        }
    }

    private fun cancelAllSunnanAlarms(context: Context, alarmManager: AlarmManager) {
        val keys = listOf("صيام الإثنين والخميس", "سورة الكهف", "صلاة الضحى", "صلاة الوتر", "أذكار الصباح", "أذكار المساء")
        val ids = mutableListOf<Int>()

        keys.forEach { key ->
            ids.add(key.hashCode())
            ids.add(key.hashCode() + 1) // For the second fasting alert
        }
        
        val intent = Intent(context, SunnanNotificationReceiver::class.java)
        intent.action = "com.example.ACTION_SUNNAN_NOTIFICATION"
        
        for (id in ids) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleDaily(
        context: Context,
        alarmManager: AlarmManager,
        title: String,
        message: String,
        time: LocalTime,
        now: LocalDateTime
    ) {
        var alarmTime = now.with(time)
        if (alarmTime.isBefore(now)) {
            alarmTime = alarmTime.plusDays(1)
        }
        
        setAlarm(context, alarmManager, title, message, alarmTime, title.hashCode())
    }

    private fun scheduleWeekly(
        context: Context,
        alarmManager: AlarmManager,
        title: String,
        message: String,
        dayOfWeek: DayOfWeek,
        time: LocalTime,
        now: LocalDateTime
    ) {
        var alarmTime = now.with(time)
        while (alarmTime.dayOfWeek != dayOfWeek || alarmTime.isBefore(now)) {
            alarmTime = alarmTime.plusDays(1)
        }
        
        setAlarm(context, alarmManager, title, message, alarmTime, title.hashCode() + dayOfWeek.value)
    }

    /**
     * Developer test: schedules a sunnan-style alarm 1 minute from now using the
     * full production path (setAlarm → SunnanNotificationReceiver → Notification).
     * Lets us verify the entire chain end-to-end without waiting for a real time.
     */
    fun scheduleTestSunnanAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmTime = LocalDateTime.now().plusMinutes(1)
        DebugLogger.info(
            LogCategory.ALARM,
            "TEST: scheduling sunnan alarm at $alarmTime (canScheduleExactAlarms=${alarmManager.canScheduleExactAlarms()})"
        )
        setAlarm(
            context = context,
            alarmManager = alarmManager,
            title = "TEST SUNNAN",
            message = "Test notification from developer tools",
            alarmTime = alarmTime,
            requestCode = 999999
        )
    }

    private fun setAlarm(context: Context, alarmManager: AlarmManager, title: String, message: String, alarmTime: LocalDateTime, requestCode: Int) {
        val canScheduleExact = alarmManager.canScheduleExactAlarms()
        DebugLogger.info(
            LogCategory.ALARM,
            "setAlarm() — title=$title canScheduleExactAlarms=$canScheduleExact time=$alarmTime code=$requestCode"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact) {
            Log.e("SunnanAlarmScheduler", "Cannot schedule alarm for $title: exact alarm permission not granted")
            DebugLogger.warning(LogCategory.ALARM, "Exact alarm permission not granted — skipped $title")
            return
        }

        val zonedDateTime = alarmTime.atZone(ZoneId.systemDefault())
        val epochMilli = zonedDateTime.toInstant().toEpochMilli()
        val pipelineKey = "sunnan_$title"
        val traceId = DiagnosticsDashboard.generateTraceId(pipelineKey, epochMilli, 0)

        val intent = Intent(context, SunnanNotificationReceiver::class.java).apply {
            action = "com.example.ACTION_SUNNAN_NOTIFICATION"
            putExtra("extra_title", title)
            putExtra("extra_message", message)
            putExtra("extra_trace_id", traceId)
            putExtra("extra_pipeline_key", pipelineKey)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ->
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                else ->
                    alarmManager.set(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            }
            DebugLogger.debug(LogCategory.ALARM, "Sunnan reminder scheduled — title=$title time=$alarmTime code=$requestCode")
            DiagnosticsDashboard.startNewTrace(pipelineKey, traceId)
            DiagnosticsDashboard.updateStage(pipelineKey, traceId, PipelineStageType.ALARM_SCHEDULED, "title=$title time=$alarmTime")
        } catch (e: Exception) {
            Log.e("SunnanAlarmScheduler", "Failed to schedule alarm for $title", e)
            DebugLogger.error(LogCategory.ALARM, "Failed to schedule sunnan reminder — title=$title", e)
            DiagnosticsDashboard.recordFailure(pipelineKey, traceId, PipelineStageType.ALARM_SCHEDULED, "Scheduling failed: ${e.message}")
        }
    }
}
