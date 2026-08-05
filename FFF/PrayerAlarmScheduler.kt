package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aistudio.quran.mwkpqz.R
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.debug.DebugLogger
import com.example.debug.DiagnosticsDashboard
import com.example.debug.LogCategory
import com.example.debug.PipelineStageType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PrayerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra("extra_prayer_key") ?: ""
        val prayerName = intent.getStringExtra("extra_prayer_name") ?: "الصلاة"
        val minutesBefore = intent.getIntExtra("extra_minutes_before", 0)
        val traceId = intent.getStringExtra("extra_trace_id") ?: "unknown"

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrayerNotificationReceiver:$prayerKey")
        wakeLock.acquire(15_000L)

        try {
            DebugLogger.debug(LogCategory.ALARM, "onReceive — entered action=${intent.action} prayerKey=$prayerKey prayerName=$prayerName minutesBefore=$minutesBefore ts=${System.currentTimeMillis()}")
            if (BuildConfig.DEBUG) {
                DebugLogger.debug(LogCategory.ALARM, "onReceive — extras=${intent.extras?.keySet()?.joinToString(",") ?: "null"} traceId=$traceId")
            }

            if (prayerKey.isBlank() || intent.extras == null) {
                if (BuildConfig.DEBUG) {
                    DebugLogger.warning(
                        LogCategory.ALARM,
                        "PrayerNotificationReceiver received invalid or missing extras. prayerKey='$prayerKey' prayerName='$prayerName' minutesBefore=$minutesBefore extras=${intent.extras?.keySet()?.joinToString(",") ?: "null"}"
                    )
                }
            }

            DebugLogger.info(
                LogCategory.ALARM,
                "========== RECEIVER STARTED — prayer=$prayerName before=${minutesBefore}min =========="
            )
            DebugLogger.info(LogCategory.ALARM, "Receiver fired — prayer=$prayerName before=${minutesBefore}min key=$prayerKey traceId=$traceId")

            DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.RECEIVER_STARTED, "minutesBefore=$minutesBefore")
            DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.WAKE_LOCK_ACQUIRED)

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

            // At prayer time: cancel the pre-prayer notification for the same prayer
            // Both pre-prayer and prayer notifications use the same stable prayer ID,
            // so notifying with prayerId replaces any pre-prayer notification automatically.
            // Explicitly canceling here ensures removal even when the prayer notification is suppressed.
            if (minutesBefore == 0) {
                notificationManager.cancel(prayerId)
                DebugLogger.info(LogCategory.NOTIFICATION, "Pre-prayer notification cancelled — id=$prayerId prayer=$prayerName")
            }

            val title = prayerName
            val message = if (minutesBefore > 0) {
                "أقل من $minutesBefore دقائق تفصل عن صلاة $prayerName."
            } else {
                "حان الآن وقت صلاة $prayerName."
            }

            val notifyIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0,
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Suppress prayer notification at prayer time when adhan will play,
            // since the foreground service notification (7001) replaces it visually.
            val showNotification = if (minutesBefore == 0) {
                !(prayerEnabled && adhanEnabled)
            } else {
                true
            }

            if (showNotification) {
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_prayer_alert)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(clickPendingIntent)
                    .build()

                DebugLogger.info(LogCategory.NOTIFICATION, "Notification shown — prayer=$prayerName before=${minutesBefore}min id=$prayerId")
                DebugLogger.debug(LogCategory.ALARM, "onReceive — before notify id=$prayerId")

                if (!notificationManager.areNotificationsEnabled()) {
                    DebugLogger.warning(
                        LogCategory.NOTIFICATION,
                        "Notifications are disabled by the user. Prayer notification cannot be shown for prayer=$prayerName."
                    )
                    DiagnosticsDashboard.recordFailure(prayerKey, traceId, PipelineStageType.NOTIFICATION_BLOCKED_PERMISSION, "POST_NOTIFICATIONS denied or notifications disabled by user")
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = notificationManager.getNotificationChannel(channelId)
                        if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                            DebugLogger.warning(
                                LogCategory.NOTIFICATION,
                                "Prayer notification channel is disabled or blocked for prayer=$prayerName."
                            )
                        }
                    }

                    notificationManager.notify(prayerId, notification)
                    DebugLogger.debug(LogCategory.ALARM, "onReceive — after notify id=$prayerId")
                    DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.NOTIFICATION_SHOWN, "id=$prayerId")
                }
            } else {
                DebugLogger.info(LogCategory.NOTIFICATION, "Prayer notification suppressed — adhan foreground service will display instead, prayer=$prayerName")
                DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.NOTIFICATION_SUPPRESSED)
            }

            // Adhan (audio) — only at prayer time, only if both per-prayer and global adhan are enabled
            if (minutesBefore == 0) {
                DebugLogger.info(
                    LogCategory.ALARM,
                    "Alarm fired at prayer time — prayer=$prayerName key=$prayerKey prayerEnabled=$prayerEnabled adhanEnabled=$adhanEnabled"
                )

                if (prayerEnabled && adhanEnabled) {
                    val selectedAdhanKey = prefs.getString("selected_muezzin_key", AdhanAudioCatalog.defaultOption().key)
                        ?: AdhanAudioCatalog.defaultOption().key
                    val stateBeforeAdhan = PrayerStateMachine.state.value
                    DebugLogger.debug(LogCategory.ADHAN, "onReceive — PrayerState BEFORE markAdhanStarted: ${stateBeforeAdhan::class.simpleName} prayerKey=$prayerKey")
                    DebugLogger.info(LogCategory.ADHAN, "Adhan started — prayer=$prayerName key=$selectedAdhanKey")
                    DebugLogger.debug(LogCategory.ALARM, "onReceive — adhanCheck prayer=$prayerName enabled=$prayerEnabled adhanEnabled=$adhanEnabled")

                    PrayerStateMachine.markAdhanStarted(prayerKey)
                    val stateAfterAdhan = PrayerStateMachine.state.value
                    DebugLogger.debug(LogCategory.ADHAN, "onReceive — PrayerState AFTER markAdhanStarted: ${stateAfterAdhan::class.simpleName}")
                    DebugLogger.debug(LogCategory.SYNC, "onReceive — calling PrayerWidgetUpdater.refresh() from adhan path")
                    PrayerWidgetUpdater.refresh(context)

                    // Check POST_NOTIFICATIONS before starting foreground service — on Android 13+
                    // startForeground throws SecurityException if permission is denied, causing
                    // "Context.startForegroundService() did not then call Service.startForeground()"
                    val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

                    if (hasNotificationPermission) {
                        val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
                            putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
                            putExtra(AdhanPlaybackService.EXTRA_PRAYER_KEY, prayerKey)
                            putExtra(AdhanPlaybackService.EXTRA_ADHAN_KEY, selectedAdhanKey)
                            putExtra(AdhanPlaybackService.EXTRA_TRACE_ID, traceId)
                        }
                        DebugLogger.debug(LogCategory.ALARM, "onReceive — before startForegroundService prayer=$prayerName adhanKey=$selectedAdhanKey")
                        ContextCompat.startForegroundService(context, serviceIntent)
                        DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.FOREGROUND_SERVICE_REQUESTED, "prayer=$prayerName")
                    } else {
                        DebugLogger.warning(LogCategory.NOTIFICATION, "POST_NOTIFICATIONS denied — skipping adhan foreground service for $prayerName")
                        DiagnosticsDashboard.recordFailure(prayerKey, traceId, PipelineStageType.FOREGROUND_START_FAILED, "POST_NOTIFICATIONS denied")
                        PrayerStateMachine.markAdhanEnded()
                    }
                } else {
                    val stateBeforeSkip = PrayerStateMachine.state.value
                    DebugLogger.debug(LogCategory.ADHAN, "onReceive — PrayerState BEFORE markAdhanStarted (skip path): ${stateBeforeSkip::class.simpleName} prayerKey=$prayerKey")
                    DebugLogger.warning(LogCategory.ADHAN, "Adhan skipped — prayerTime reached for $prayerKey, entering PostPrayer")
                    PrayerStateMachine.markAdhanStarted(prayerKey)
                    val stateAfterStart = PrayerStateMachine.state.value
                    DebugLogger.debug(LogCategory.ADHAN, "onReceive — PrayerState AFTER markAdhanStarted: ${stateAfterStart::class.simpleName}")
                    PrayerStateMachine.markAdhanEnded()
                    val stateAfterEnd = PrayerStateMachine.state.value
                    DebugLogger.debug(LogCategory.ADHAN, "onReceive — PrayerState AFTER markAdhanEnded: ${stateAfterEnd::class.simpleName}")
                    DebugLogger.debug(LogCategory.SYNC, "onReceive — calling PrayerWidgetUpdater.refresh() from skip path")
                    PrayerWidgetUpdater.refresh(context)
                }
            }

            DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.PIPELINE_COMPLETED)
            DebugLogger.debug(LogCategory.ALARM, "PrayerNotificationReceiver.onReceive() EXIT — prayerKey=$prayerKey minutesBefore=$minutesBefore")
        } catch (e: Exception) {
            DebugLogger.error(LogCategory.ALARM, "PrayerNotificationReceiver — unhandled exception", e)
            DiagnosticsDashboard.recordFailure(prayerKey, traceId, PipelineStageType.PIPELINE_FAILED, "Receiver exception: ${e.message}")
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
        val alarmTime = java.time.LocalDateTime.now().plusMinutes(1)
        val epochMilli = alarmTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val traceId = DiagnosticsDashboard.generateTraceId("test", epochMilli, 0)
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_NOTIFICATION
            putExtra("extra_prayer_key", "test")
            putExtra("extra_prayer_name", "TEST")
            putExtra("extra_minutes_before", 0)
            putExtra("extra_trace_id", traceId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            888888,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            epochMilli,
            pendingIntent
        )
        DiagnosticsDashboard.startNewTrace("test", traceId)
        DiagnosticsDashboard.updateStage("test", traceId, PipelineStageType.ALARM_SCHEDULED, "test_alarm time=$alarmTime")
    }

    /**
     * Bug fix (Bug 7): reads the cache file only ONCE via the batch method
     * getPrayerTimesForDates(), then schedules all alarms from the in-memory map.
     * Previously this read the JSON file 15 separate times (one per day).
     */
    fun scheduleUpcomingAlarms(context: Context) {
        DebugLogger.info(LogCategory.ALARM, "scheduleUpcomingAlarms() started")
        DebugLogger.debug(LogCategory.ALARM, "scheduleUpcomingAlarms — entered ts=${System.currentTimeMillis()}")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val minutesBefore = prefs.getInt("pre_prayer_alarm_minutes", 5)
        val trackedCodes = mutableSetOf<String>()

        val upcomingDates = (0L..14L).map { LocalDate.now().plusDays(it) }
        DebugLogger.debug(LogCategory.ALARM, "scheduleUpcomingAlarms — today=${LocalDate.now()} prayerCount=5")

        // Step 1: read and validate the cache BEFORE touching any existing alarms.
        var allTimes: Map<LocalDate, PrayerTimes> = PrayerTimesCacheStore.getPrayerTimesForDates(context, upcomingDates)
        var daysWithData = allTimes.size
        val cityId = PrayerTimesCacheStore.getCachedCityId(context)

        // Fallback: if primary city lookup returned nothing, try any city in the cache
        if (daysWithData == 0) {
            val anyCityTimes = PrayerTimesCacheStore.getPrayerTimesForDatesAnyCity(context, upcomingDates)
            if (anyCityTimes.isNotEmpty()) {
                DebugLogger.warning(LogCategory.ALARM, "Primary city ($cityId) has no cached data — falling back to any available city data (${anyCityTimes.size} days)")
                allTimes = anyCityTimes
                daysWithData = anyCityTimes.size
            }
        }

        if (cityId == null) {
            DebugLogger.warning(LogCategory.ALARM, "No cached cityId found. Prayer alarms cannot be scheduled correctly.")
        }

        DebugLogger.debug(LogCategory.ALARM, "scheduleUpcomingAlarms — cityId=$cityId daysWithData=$daysWithData")
        DebugLogger.info(LogCategory.PRAYER, "Prayer times loaded — $daysWithData days cached, cityId=$cityId")

        if (daysWithData == 0) {
            DebugLogger.warning(LogCategory.ALARM, "scheduleUpcomingAlarms — cache empty, preserving existing alarms")
            DebugLogger.warning(LogCategory.ALARM, "Cache empty — preserving existing alarms, skipping reschedule")
            return
        }

        val alarmsPerDay = if (minutesBefore > 0) 10 else 5
        DebugLogger.debug(LogCategory.ALARM, "scheduleUpcomingAlarms — alarmsPerDay=$alarmsPerDay expected=${daysWithData * alarmsPerDay}")
        DebugLogger.info(LogCategory.ALARM, "Scheduling up to ${daysWithData * alarmsPerDay} alarms for $daysWithData days...")

        // Step 2: cache is valid — cancel old alarms and schedule new ones.
        cancelTrackedAlarms(context, alarmManager)

        upcomingDates.forEach { date ->
            val dayTimes = allTimes[date]
            if (dayTimes == null) {
                DebugLogger.warning(LogCategory.ALARM, "No prayer times found for date=$date")
                return@forEach
            }
            val prayers = listOf(
                Triple("fajr", "الفجر", dayTimes.fajr),
                Triple("dhuhr", "الظهر", dayTimes.dhuhr),
                Triple("asr", "العصر", dayTimes.asr),
                Triple("maghrib", "المغرب", dayTimes.maghrib),
                Triple("isha", "العشاء", dayTimes.isha)
            )

            prayers.forEach { (key, displayName, timeStr) ->
                val time = parseTimeSafely(timeStr) ?: return@forEach

                scheduleSingleAlarm(context, alarmManager, key, displayName, date, time, 0)?.let {
                    trackedCodes += it.toString()
                }
                if (minutesBefore > 0) {
                    scheduleSingleAlarm(context, alarmManager, key, displayName, date, time, minutesBefore)?.let {
                        trackedCodes += it.toString()
                    }
                }
            }
        }

        // Step 3: persist only after successful scheduling.
        context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(TRACKING_KEY, trackedCodes)
            .apply()

        if (trackedCodes.size == 0) {
            DebugLogger.warning(LogCategory.ALARM, "scheduleUpcomingAlarms — scheduled=0 alarms — no alarms will fire")
        } else {
            DebugLogger.debug(LogCategory.ALARM, "scheduleUpcomingAlarms — scheduled=${trackedCodes.size} alarms")
        }
        DebugLogger.info(LogCategory.ALARM, "Scheduling complete — ${trackedCodes.size} alarms tracked")
        DebugLogger.debug(LogCategory.ALARM, "scheduleUpcomingAlarms — finished ts=${System.currentTimeMillis()}")
        scheduleMidnightRefresh(context)
    }

    private fun cancelTrackedAlarms(context: Context, alarmManager: AlarmManager) {
        val storedCodes = context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .getStringSet(TRACKING_KEY, emptySet())
            .orEmpty()

        DebugLogger.debug(LogCategory.ALARM, "cancelTrackedAlarms() — cancelling ${storedCodes.size} tracked alarms")
        DebugLogger.debug(LogCategory.ALARM, "cancelTrackedAlarms — count=${storedCodes.size}")
        storedCodes.forEach { stored ->
            val requestCode = stored.toIntOrNull()
            if (requestCode == null) {
                DebugLogger.warning(LogCategory.ALARM, "cancelTrackedAlarms — invalid stored code '$stored' cannot be parsed as Int")
                return@forEach
            }
            DebugLogger.debug(LogCategory.ALARM, "cancelTrackedAlarms — cancelling rc=$requestCode")
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = ACTION_PRAYER_NOTIFICATION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        DebugLogger.debug(LogCategory.ALARM, "cancelTrackedAlarms — finished")
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        prayerKey: String,
        prayerName: String,
        date: LocalDate,
        time: LocalTime,
        minutesBefore: Int
    ): Int? {
        var alarmTime = LocalDateTime.of(date, time)
        if (minutesBefore > 0) {
            alarmTime = alarmTime.minusMinutes(minutesBefore.toLong())
        }

        if (alarmTime.isBefore(LocalDateTime.now())) {
            DebugLogger.warning(LogCategory.ALARM, "scheduleSingleAlarm — SKIPPED prayer=$prayerName date=$date time=$alarmTime minutesBefore=$minutesBefore reason=past_time")
            return null
        }

        // Deterministic request code to avoid hashCode() collisions.
        // Format: date * 1000 + prayerIndex * 100 + minutesBefore
    val prayerIndex = listOf("fajr", "dhuhr", "asr", "maghrib", "isha").indexOf(prayerKey).coerceAtLeast(0)
    val requestCode = date.toEpochDay().toInt() * 1000 + prayerIndex * 100 + minutesBefore

    val zonedDateTime = alarmTime.atZone(ZoneId.systemDefault())
    val epochMilli = zonedDateTime.toInstant().toEpochMilli()
    val traceId = DiagnosticsDashboard.generateTraceId(prayerKey, epochMilli, minutesBefore)

    val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
        action = ACTION_PRAYER_NOTIFICATION
        putExtra("extra_prayer_key", prayerKey)
        putExtra("extra_prayer_name", prayerName)
        putExtra("extra_minutes_before", minutesBefore)
        putExtra("extra_trace_id", traceId)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

        val alarmApi = if (minutesBefore == 0) "setAlarmClock" else "setExactAndAllowWhileIdle"
        DebugLogger.debug(LogCategory.ALARM, "scheduleSingleAlarm — BEFORE prayer=$prayerName key=$prayerKey date=$date triggerTime=$alarmTime minutesBefore=$minutesBefore rc=$requestCode alarmType=$alarmApi triggerMillis=$epochMilli")

        DebugLogger.info(
            LogCategory.ALARM,
            "Alarm scheduled — prayer=$prayerName date=$date time=$alarmTime before=${minutesBefore}min rc=$requestCode"
        )

        // Bug fix: always use setAlarmClock() for the main prayer (minutesBefore == 0)
        // because it has the highest priority on MIUI/Xiaomi and is guaranteed exact.
        // For pre-prayer reminders (minutesBefore > 0), fall back to inexact if exact not allowed.
        try {
            if (minutesBefore == 0) {
                val showIntent = Intent(context, MainActivity::class.java)
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                DebugLogger.debug(LogCategory.ALARM, "scheduleSingleAlarm — SUCCESS prayer=$prayerName rc=$requestCode api=setAlarmClock triggerMillis=$epochMilli")
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(epochMilli, showPendingIntent),
                    pendingIntent
                )
            } else {
                val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || alarmManager.canScheduleExactAlarms()
                if (canScheduleExact) {
                    DebugLogger.debug(LogCategory.ALARM, "scheduleSingleAlarm — SUCCESS prayer=$prayerName rc=$requestCode api=setExactAndAllowWhileIdle triggerMillis=$epochMilli")
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                } else {
                    DebugLogger.warning(LogCategory.ALARM, "scheduleSingleAlarm — INEXACT prayer=$prayerName rc=$requestCode api=setAndAllowWhileIdle triggerMillis=$epochMilli reason=no_exact_permission")
                    DebugLogger.warning(LogCategory.ALARM, "Exact alarm unavailable — falling back to inexact for $prayerName")
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                }
            }
        } catch (e: SecurityException) {
            DebugLogger.error(LogCategory.ALARM, "scheduleSingleAlarm — SecurityException for $prayerName rc=$requestCode triggerMillis=$epochMilli", e)
            DebugLogger.error(LogCategory.ALARM, "Scheduling exception for $prayerName — falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
        }
        val verifyFlags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val verifyIntent = PendingIntent.getBroadcast(context, requestCode, intent, verifyFlags)
        if (verifyIntent == null) {
            DebugLogger.warning(LogCategory.ALARM, "PendingIntent could not be retrieved using FLAG_NO_CREATE. Unable to confirm AlarmManager registration. requestCode=$requestCode")
        } else {
            DebugLogger.debug(LogCategory.ALARM, "PendingIntent verified successfully. requestCode=$requestCode")
        }

        DiagnosticsDashboard.startNewTrace(prayerKey, traceId)
        DiagnosticsDashboard.updateStage(prayerKey, traceId, PipelineStageType.ALARM_SCHEDULED, "time=$alarmTime before=${minutesBefore}min rc=$requestCode triggerEpoch=$epochMilli")
        return requestCode
    }

    private fun parseTimeSafely(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr.replace(" ", "").trim())
        } catch (e: Exception) {
            DebugLogger.error(LogCategory.ALARM, "parseTimeSafely — failed to parse timeStr=\"$timeStr\"", e)
            null
        }
    }

    fun scheduleMidnightRefresh(context: Context) {
        DebugLogger.info(LogCategory.ALARM, "scheduleMidnightRefresh() — scheduling next midnight refresh")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerMaintenanceReceiver::class.java).apply {
            action = "com.example.ACTION_MIDNIGHT_REFRESH"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(5) // 00:00:05
        val epochMilli = nextMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        DebugLogger.debug(LogCategory.ALARM, "scheduleMidnightRefresh — midnight=$nextMidnight triggerMillis=$epochMilli rc=9999")
        
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            } else {
                DebugLogger.warning(LogCategory.ALARM, "scheduleMidnightRefresh — exact alarm unavailable, using inexact")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            }
        } catch (e: SecurityException) {
            DebugLogger.error(LogCategory.ALARM, "scheduleMidnightRefresh — SecurityException rc=9999", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
        }
    }
}
