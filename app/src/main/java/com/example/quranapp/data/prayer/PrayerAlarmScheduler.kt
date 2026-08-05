package com.example.quranapp.data.prayer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.DiagnosticsDashboard
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.core.debug.PipelineStageType
import com.example.quranapp.core.utils.QuranPreferences
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

const val EXTRA_TRACE_ID = "extra_trace_id"

class PrayerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra("extra_prayer_key") ?: ""
        val prayerName = intent.getStringExtra("extra_prayer_name") ?: "الصلاة"
        val minutesBefore = intent.getIntExtra("extra_minutes_before", 0)
        val traceId = intent.getStringExtra(EXTRA_TRACE_ID) ?: Instrumentation.NO_TRACE
        val city = runCatching { PrayerTimesCacheStore.getCachedCityName(context) }.getOrNull()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrayerNotification:$prayerKey")
        wakeLock.acquire(15_000L)

        try {
            if (prayerKey.isBlank()) return

            DiagnosticsDashboard.updateStage(
                prayerKey, traceId, PipelineStageType.RECEIVER_STARTED,
                "action=${intent.action} minBefore=$minutesBefore"
            )
            DiagnosticsDashboard.updateStage(
                prayerKey, traceId, PipelineStageType.WAKE_LOCK_ACQUIRED, "15s"
            )
            DebugLogger.info(
                LogCategory.ALARM,
                Instrumentation.line(prayerKey, traceId, city, "Receiver started prayer=$prayerName minBefore=$minutesBefore")
            )

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "PRAYER_ALERTS_CHANNEL"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "أوقات الصلاة - تذكيرات",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "قناة لإشعارات أوقات الصلاة وتنبيهات الأذان"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val prayerId = when (prayerKey) {
                "fajr" -> PrayerNotificationIds.FAJR
                "dhuhr" -> PrayerNotificationIds.DHUHR
                "asr" -> PrayerNotificationIds.ASR
                "maghrib" -> PrayerNotificationIds.MAGHRIB
                "isha" -> PrayerNotificationIds.ISHA
                else -> (prayerName.hashCode() * 31) + minutesBefore
            }

            val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
            val prayerEnabled = prefs.getBoolean("enable_$prayerKey", true)
            val adhanEnabled = prefs.getBoolean("adhan_audio_enabled_key", true)
            val adhanAllowed = prayerEnabled && adhanEnabled

            if (minutesBefore == 0) {
                notificationManager.cancel(prayerId)
            }

            val title = prayerName
            val message = if (minutesBefore > 0) {
                "أقل من $minutesBefore دقائق تفصل عن صلاة $prayerName."
            } else {
                "حان الآن وقت صلاة $prayerName."
            }

            val notifyIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val clickPendingIntent = if (notifyIntent != null) {
                PendingIntent.getActivity(
                    context, 0, notifyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val showNotification = if (minutesBefore == 0) {
                !adhanAllowed
            } else {
                Log.d("PRAYER_ALARM", "Pre-prayer alarm fired: $prayerKey $minutesBefore min before")
                true
            }

            if (showNotification) {
                val notification = NotificationCompat.Builder(context, channelId)
                            .setSmallIcon(com.example.quranapp.R.drawable.ic_monochrome)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(clickPendingIntent)
                    .build()
                notificationManager.notify(prayerId, notification)

                if (minutesBefore == 0) {
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId, PipelineStageType.NOTIFICATION_SHOWN, "prayer time — adhan disabled"
                    )
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId, PipelineStageType.PIPELINE_COMPLETED, "Notification-only flow"
                    )
                    DebugLogger.info(
                        LogCategory.NOTIFICATION,
                        Instrumentation.line(prayerKey, traceId, city, "Prayer notification shown id=$prayerId (adhan disabled)")
                    )
                } else {
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId, PipelineStageType.NOTIFICATION_SHOWN, "pre-prayer ${minutesBefore}m"
                    )
                    DebugLogger.info(
                        LogCategory.NOTIFICATION,
                        Instrumentation.line(prayerKey, traceId, city, "Pre-prayer notification shown id=$prayerId")
                    )
                }
            }

            if (minutesBefore == 0) {
                PrayerStateMachine.markAdhanStarted(prayerKey, traceId, city)

                if (!adhanAllowed) {
                    DiagnosticsDashboard.updateStage(
                        prayerKey, traceId, PipelineStageType.NOTIFICATION_SUPPRESSED, "prayer notification shown, adhan not played"
                    )
                }

                val widgetIntent = Intent("com.example.ACTION_UPDATE_PRAYER_WIDGET").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(widgetIntent)
                
                if (adhanAllowed) {
                    val selectedAdhanKey = QuranPreferences.getSelectedAdhanId(context)

                    val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

                    if (hasNotificationPermission) {
                        DiagnosticsDashboard.updateStage(
                            prayerKey, traceId, PipelineStageType.FOREGROUND_SERVICE_REQUESTED, "AdhanPlaybackService"
                        )
                        DebugLogger.info(
                            LogCategory.ADHAN,
                            Instrumentation.line(prayerKey, traceId, city, "Foreground service requested adhanKey=$selectedAdhanKey")
                        )
                        val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
                            putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
                            putExtra(AdhanPlaybackService.EXTRA_PRAYER_KEY, prayerKey)
                            putExtra(AdhanPlaybackService.EXTRA_ADHAN_KEY, selectedAdhanKey)
                            putExtra(AdhanPlaybackService.EXTRA_TRACE_ID, traceId)
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        DiagnosticsDashboard.updateStage(
                            prayerKey, traceId, PipelineStageType.NOTIFICATION_BLOCKED_PERMISSION,
                            "POST_NOTIFICATIONS denied"
                        )
                        DebugLogger.warning(
                            LogCategory.NOTIFICATION,
                            Instrumentation.line(prayerKey, traceId, city, ErrorCode.NOTIFICATION_PERMISSION_DENIED.prefix("Adhan blocked — notification permission denied"))
                        )
                        val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(com.example.quranapp.R.drawable.ic_monochrome)
                            .setContentTitle(prayerName)
                            .setContentText("حان الآن وقت صلاة $prayerName. الأذان لم ينطلق لأن الإشعارات معطلة. قم بتفعيل الإشعارات من الإعدادات.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .setContentIntent(clickPendingIntent)
                            .build()
                        notificationManager.notify(prayerId, notification)
                        DiagnosticsDashboard.updateStage(
                            prayerKey, traceId, PipelineStageType.NOTIFICATION_SHOWN, "permission-denied fallback"
                        )
                        DiagnosticsDashboard.recordFailure(
                            prayerKey, traceId, PipelineStageType.PIPELINE_FAILED,
                            "Adhan not played — POST_NOTIFICATIONS permission denied"
                        )
                    }
                }
            }

            val reason = when {
                !prayerEnabled -> "PrayerDisabled"
                !adhanEnabled -> "AdhanDisabled"
                else -> "None"
            }
            DebugLogger.info(
                LogCategory.ALARM,
                Instrumentation.line(
                    prayerKey, traceId, city,
                    "Decision Reminder=${minutesBefore > 0} PrayerNotification=${showNotification} Adhan=$adhanAllowed Reason=$reason"
                )
            )
        } catch (e: Exception) {
            Log.e("PRAYER_ALARM", "Receiver exception", e)
            DebugLogger.error(
                LogCategory.ALARM,
                Instrumentation.line(prayerKey, traceId, city, ErrorCode.RECEIVER_EXCEPTION.prefix("Receiver exception")),
                e
            )
            DiagnosticsDashboard.recordFailure(
                prayerKey, traceId, PipelineStageType.PIPELINE_FAILED,
                "Receiver exception: ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}

object PrayerNotificationIds {
    const val FAJR = 1001
    const val DHUHR = 1002
    const val ASR = 1003
    const val MAGHRIB = 1004
    const val ISHA = 1005
}

object PrayerAlarmScheduler {
    private const val ACTION_PRAYER_NOTIFICATION = "com.example.ACTION_PRAYER_NOTIFICATION"
    private const val TRACKING_PREFS = "scheduled_prayer_alarm_state"
    private const val TRACKING_KEY = "request_codes"

    fun scheduleTestPrayerAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmTime = LocalDateTime.now().plusMinutes(1)
        val epochMilli = alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val traceId = DiagnosticsDashboard.generateTraceId()
        DiagnosticsDashboard.startNewTrace("test", traceId)
        DiagnosticsDashboard.updateStage(
            "test", traceId, PipelineStageType.ALARM_SCHEDULED, "test alarm +1min"
        )
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_NOTIFICATION
            putExtra("extra_prayer_key", "test")
            putExtra("extra_prayer_name", "TEST")
            putExtra("extra_minutes_before", 0)
            putExtra(EXTRA_TRACE_ID, traceId)
        }
        DebugLogger.info(
            LogCategory.ALARM,
            Instrumentation.line("test", traceId, null, "Test alarm scheduled +1min")
        )
        val pendingIntent = PendingIntent.getBroadcast(
            context, 888888, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
    }

    fun scheduleUpcomingAlarms(context: Context) {
        Log.d("PRAYER_ALARM", "scheduleUpcomingAlarms() started")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val city = runCatching { PrayerTimesCacheStore.getCachedCityName(context) }.getOrNull()

        val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val minutesBefore = prefs.getInt("pre_prayer_alarm_minutes", 5)
        val trackedCodes = mutableSetOf<String>()

        val upcomingDates = (0L..14L).map { LocalDate.now().plusDays(it) }

        var allTimes: Map<LocalDate, PrayerTimes> = PrayerTimesCacheStore.getPrayerTimesForDates(context, upcomingDates)
        var daysWithData = allTimes.size
        val cityId = PrayerTimesCacheStore.getCachedCityId(context)

        if (daysWithData == 0) {
            val anyCityTimes = PrayerTimesCacheStore.getPrayerTimesForDatesAnyCity(context, upcomingDates)
            if (anyCityTimes.isNotEmpty()) {
                Log.w("PRAYER_ALARM", "Primary city ($cityId) has no cached data — falling back to any available")
                DebugLogger.warning(
                    LogCategory.ALARM,
                    Instrumentation.line("scheduler", Instrumentation.NO_TRACE, city, "Primary city ($cityId) has no cached data — falling back to any available")
                )
                allTimes = anyCityTimes
                daysWithData = anyCityTimes.size
            }
        }

        if (daysWithData == 0) {
            Log.w("PRAYER_ALARM", "Cache empty, preserving existing alarms")
            DebugLogger.warning(
                LogCategory.ALARM,
                Instrumentation.line("scheduler", Instrumentation.NO_TRACE, city, "Cache empty — preserving existing alarms")
            )
            return
        }

        cancelTrackedAlarms(context, alarmManager)

        upcomingDates.forEach { date ->
            val dayTimes = allTimes[date] ?: return@forEach
            val prayers = listOf(
                Triple("fajr", "الفجر", dayTimes.fajr),
                Triple("dhuhr", "الظهر", dayTimes.dhuhr),
                Triple("asr", "العصر", dayTimes.asr),
                Triple("maghrib", "المغرب", dayTimes.maghrib),
                Triple("isha", "العشاء", dayTimes.isha)
            )

            prayers.forEach { (key, displayName, timeStr) ->
                val time = parseTimeSafely(timeStr) ?: return@forEach
                val traceId = DiagnosticsDashboard.generateTraceId()
                val onTimeCode = scheduleSingleAlarm(context, alarmManager, key, displayName, date, time, 0, traceId, city)
                val reminderCode = if (minutesBefore > 0) {
                    scheduleSingleAlarm(context, alarmManager, key, displayName, date, time, minutesBefore, traceId, city)
                } else {
                    null
                }
                if (onTimeCode != null || reminderCode != null) {
                    DiagnosticsDashboard.startNewTrace(key, traceId)
                    DiagnosticsDashboard.updateStage(
                        key, traceId, PipelineStageType.ALARM_SCHEDULED,
                        "onTime=${onTimeCode != null} pre=${reminderCode != null} date=$date"
                    )
                    listOfNotNull(onTimeCode, reminderCode).forEach { trackedCodes += it.toString() }
                }
            }
        }

        context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(TRACKING_KEY, trackedCodes)
            .apply()

        Log.d("PRAYER_ALARM", "Scheduled ${trackedCodes.size} alarms")
        DebugLogger.info(
            LogCategory.ALARM,
            Instrumentation.line("scheduler", Instrumentation.NO_TRACE, city, "Scheduled ${trackedCodes.size} alarms")
        )
        scheduleMidnightRefresh(context)
    }

    private fun cancelTrackedAlarms(context: Context, alarmManager: AlarmManager) {
        val storedCodes = context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .getStringSet(TRACKING_KEY, emptySet())
            .orEmpty()

        DebugLogger.info(
            LogCategory.ALARM,
            Instrumentation.line("scheduler", Instrumentation.NO_TRACE, null, "Cancelling ${storedCodes.size} tracked alarms")
        )

        storedCodes.forEach { stored ->
            val requestCode = stored.toIntOrNull() ?: return@forEach
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = ACTION_PRAYER_NOTIFICATION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        prayerKey: String,
        prayerName: String,
        date: LocalDate,
        time: LocalTime,
        minutesBefore: Int,
        traceId: String,
        city: String?
    ): Int? {
        var alarmTime = LocalDateTime.of(date, time)
        if (minutesBefore > 0) {
            alarmTime = alarmTime.minusMinutes(minutesBefore.toLong())
        }

        if (alarmTime.isBefore(LocalDateTime.now())) return null

        val prayerIndex = listOf("fajr", "dhuhr", "asr", "maghrib", "isha").indexOf(prayerKey).coerceAtLeast(0)
        val requestCode = date.toEpochDay().toInt() * 1000 + prayerIndex * 100 + minutesBefore

        val zonedDateTime = alarmTime.atZone(ZoneId.systemDefault())
        val epochMilli = zonedDateTime.toInstant().toEpochMilli()

        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_NOTIFICATION
            putExtra("extra_prayer_key", prayerKey)
            putExtra("extra_prayer_name", prayerName)
            putExtra("extra_minutes_before", minutesBefore)
            putExtra(EXTRA_TRACE_ID, traceId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (minutesBefore == 0) {
                val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                val showPendingIntent = if (showIntent != null) {
                    PendingIntent.getActivity(
                        context, requestCode, showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else null
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(epochMilli, showPendingIntent),
                    pendingIntent
                )
            } else {
                val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || alarmManager.canScheduleExactAlarms()
                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                }
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
        }
        DebugLogger.info(
            LogCategory.ALARM,
            Instrumentation.line(
                prayerKey, traceId, city,
                "Alarm scheduled date=$date time=$time ${if (minutesBefore == 0) "(on time)" else "(${minutesBefore}m before)"} requestCode=$requestCode"
            )
        )
        return requestCode
    }

    private fun parseTimeSafely(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr.replace(" ", "").trim())
        } catch (e: Exception) {
            null
        }
    }

    fun scheduleMidnightRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerMaintenanceReceiver::class.java).apply {
            action = "com.example.ACTION_MIDNIGHT_REFRESH"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(5)
        val epochMilli = nextMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        DebugLogger.info(
            LogCategory.ALARM,
            Instrumentation.line("midnight", Instrumentation.NO_TRACE, null, "Midnight refresh scheduled at $nextMidnight")
        )

        try {
            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
        }
    }
}
