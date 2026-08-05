package com.example

import com.aistudio.quran.mwkpqz.R
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.os.Build
import android.widget.RemoteViews
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PrayerTimesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DebugLogger.debug(LogCategory.WIDGET, "PrayerTimesWidgetProvider.onUpdate() called for ${appWidgetIds.size} widget(s)")
        for (appWidgetId in appWidgetIds) {
            try {
                DebugLogger.debug(LogCategory.WIDGET, "Updating widget id=$appWidgetId")
                updateAppWidget(context, appWidgetManager, appWidgetId)
                DebugLogger.debug(LogCategory.WIDGET, "Widget updated id=$appWidgetId")
            } catch (e: Exception) {
                DebugLogger.error(LogCategory.WIDGET, "CRITICAL: updateAppWidget failed for id $appWidgetId", e)
            } finally {
                scheduleNextUpdate(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DebugLogger.debug(LogCategory.WIDGET, "PrayerTimesWidgetProvider.onEnabled() called")
        scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DebugLogger.debug(LogCategory.WIDGET, "PrayerTimesWidgetProvider.onDisabled() called")
        cancelAlarm(context)
    }

    companion object {
        private const val ACTION_UPDATE_WIDGET = "com.example.ACTION_UPDATE_PRAYER_WIDGET"
        private const val ALARM_REQUEST_CODE = 1001

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val nowDateTime = java.time.LocalDateTime.now()
            val todayDate = LocalDate.now()
            val cachedTimes = PrayerTimesCacheStore.getPrayerTimesForDate(context, todayDate)
            
            DebugLogger.debug(LogCategory.WIDGET, "RUNTIME_LOG: nowDateTime=$nowDateTime, today=$todayDate, maghrib=${cachedTimes?.maghrib}, isha=${cachedTimes?.isha}")
             
             val now = nowDateTime.toLocalTime()
             DebugLogger.debug(LogCategory.WIDGET, "RUNTIME_LOG: nowTime=$now, nowDate=${nowDateTime.toLocalDate()}")

            val tomorrowTimes = PrayerTimesCacheStore.getPrayerTimesForDate(context, LocalDate.now().plusDays(1))
            val city = PrayerTimesCacheStore.getCachedCityName(context) ?: "المغرب"
            
            val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setTextViewText(R.id.widget_city, city)

            if (cachedTimes != null) {
                val prayerTimesLocal = PrayerTimesLocal(
                    fajr = parseTimeSafely(cachedTimes.fajr) ?: LocalTime.of(4, 30),
                    dhuhr = parseTimeSafely(cachedTimes.dhuhr) ?: LocalTime.of(13, 30),
                    asr = parseTimeSafely(cachedTimes.asr) ?: LocalTime.of(17, 0),
                    maghrib = parseTimeSafely(cachedTimes.maghrib) ?: LocalTime.of(20, 30),
                    isha = parseTimeSafely(cachedTimes.isha) ?: LocalTime.of(22, 0)
                )

                val tomorrowLocal = tomorrowTimes?.let { 
                    PrayerTimesLocal(
                        parseTimeSafely(it.fajr) ?: LocalTime.of(4,30),
                        parseTimeSafely(it.dhuhr) ?: LocalTime.of(13,30),
                        parseTimeSafely(it.asr) ?: LocalTime.of(17,0),
                        parseTimeSafely(it.maghrib) ?: LocalTime.of(20,30),
                        parseTimeSafely(it.isha) ?: LocalTime.of(22,0)
                    )
                }
val displayState = resolveDisplayState(prayerTimesLocal, tomorrowLocal, nowDateTime)
                 DebugLogger.debug(LogCategory.WIDGET, "RUNTIME_LOG: displayResult=$displayState")
                 
                 when (val state = displayState) {
                    is WidgetDisplayState.Countdown -> {
                        DebugLogger.debug(LogCategory.WIDGET, "RUNTIME_LOG: State=Countdown, nextName=${state.nextName}, secondsRemaining=${state.secondsRemaining}")
                        val hours = state.secondsRemaining / 3600
                        val minutes = (state.secondsRemaining % 3600) / 60
                        val seconds = state.secondsRemaining % 60
                        val countdownFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                        views.setTextViewText(R.id.widget_title, "الصلاة القادمة: ${state.nextName}")
                        views.setTextViewText(R.id.widget_next_prayer_name, state.nextName)
                        views.setTextViewText(R.id.widget_next_prayer_time, state.nextTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                        views.setTextViewText(R.id.widget_countdown, countdownFormatted)

                        val progressBitmap = createCircularProgressBitmap(state.progressPercent)
                        views.setImageViewBitmap(R.id.widget_progress_image, progressBitmap)
                    }
                }

            } else {
                DebugLogger.debug(LogCategory.WIDGET, "updateAppWidget() — no cached times available")
                views.setTextViewText(R.id.widget_title, "الصلاة القادمة")
                views.setTextViewText(R.id.widget_next_prayer_name, "الفجر")
                views.setTextViewText(R.id.widget_next_prayer_time, "--:--")
                views.setTextViewText(R.id.widget_countdown, "00:00:00")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            DebugLogger.debug(LogCategory.WIDGET, "updateAppWidget() EXIT — widgetId=$appWidgetId")
        }

        private fun resolveDisplayState(
            todayTimes: PrayerTimesLocal,
            tomorrowTimes: PrayerTimesLocal?,
            nowDateTime: java.time.LocalDateTime
        ): WidgetDisplayState {
            val todayDate = nowDateTime.toLocalDate()
            val nowTime = nowDateTime.toLocalTime()

            val todayPrayers = listOf(
                "الفجر" to todayDate.atTime(todayTimes.fajr),
                "الظهر" to todayDate.atTime(todayTimes.dhuhr),
                "العصر" to todayDate.atTime(todayTimes.asr),
                "المغرب" to todayDate.atTime(todayTimes.maghrib),
                "العشاء" to todayDate.atTime(todayTimes.isha)
            )

            val tomorrowPrayers = tomorrowTimes?.let {
                val tDate = todayDate.plusDays(1)
                listOf(
                    "الفجر" to tDate.atTime(it.fajr),
                    "الظهر" to tDate.atTime(it.dhuhr),
                    "العصر" to tDate.atTime(it.asr),
                    "المغرب" to tDate.atTime(it.maghrib),
                    "العشاء" to tDate.atTime(it.isha)
                )
            } ?: emptyList()

            val allPrayers = todayPrayers + tomorrowPrayers

            val currentPrayer = allPrayers
                .filter { !it.second.isAfter(nowDateTime) }
                .maxByOrNull { it.second }
            DebugLogger.debug(LogCategory.WIDGET, "RUNTIME_LOG: resolveDisplayState nowTime=$nowTime currentPrayer=${currentPrayer?.first} currentTime=${currentPrayer?.second}")

            val fallbackTomorrowFajr = tomorrowTimes?.fajr
            val nextPrayerInfo = getNextPrayer(todayTimes, nowTime, fallbackTomorrowFajr)
            DebugLogger.debug(LogCategory.WIDGET, "RUNTIME_LOG: nextPrayer=${nextPrayerInfo.name} nextTime=${nextPrayerInfo.time} secondsRemaining=${nextPrayerInfo.secondsRemaining} progress=${nextPrayerInfo.progressPercent}")
            return WidgetDisplayState.Countdown(
                nextName = nextPrayerInfo.name,
                nextTime = nextPrayerInfo.time,
                secondsRemaining = nextPrayerInfo.secondsRemaining,
                progressPercent = nextPrayerInfo.progressPercent
            )
        }

        fun getNextPrayer(prayerTimes: PrayerTimesLocal, now: LocalTime, tomorrowFajr: LocalTime?): NextPrayerInfoLocal {
            val prayers = listOf(
                "الفجر" to prayerTimes.fajr,
                "الظهر" to prayerTimes.dhuhr,
                "العصر" to prayerTimes.asr,
                "المغرب" to prayerTimes.maghrib,
                "العشاء" to prayerTimes.isha
            )

            var nextPair: Pair<String, LocalTime>? = null
            var prevPair: Pair<String, LocalTime>? = null

            for (i in prayers.indices) {
                val p = prayers[i]
                if (now.isBefore(p.second)) {
                    nextPair = p
                    prevPair = if (i > 0) prayers[i - 1] else null
                    break
                } else {
                    prevPair = p
                }
            }
            DebugLogger.debug(LogCategory.WIDGET, "RUNTIME_LOG: getNextPrayer now=$now nextPair=${nextPair?.first} prevPair=${prevPair?.first}")

            return if (nextPair != null) {
                val secondsRemaining = Duration.between(now, nextPair.second).seconds
                val totalDuration = if (prevPair != null) {
                    Duration.between(prevPair.second, nextPair.second).seconds
                } else {
                    Duration.between(LocalTime.MIDNIGHT, nextPair.second).seconds
                }
                val elapsed = totalDuration - secondsRemaining
                val percent = if (totalDuration > 0) (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

                NextPrayerInfoLocal(
                    name = nextPair.first,
                    time = nextPair.second,
                    secondsRemaining = secondsRemaining,
                    totalDuration = totalDuration,
                    progressPercent = percent
                )
            } else {
                val fajrTime = tomorrowFajr ?: prayerTimes.fajr
                val secondsUntilMidnight = Duration.between(now, LocalTime.MAX).seconds + 1
                val secondsFromMidnightToFajr = Duration.between(LocalTime.MIDNIGHT, fajrTime).seconds
                val secondsRemaining = secondsUntilMidnight + secondsFromMidnightToFajr

                val totalDuration = Duration.between(prayerTimes.isha, LocalTime.MIDNIGHT).seconds +
                                    Duration.between(LocalTime.MIDNIGHT, fajrTime).seconds
                val elapsed = totalDuration - secondsRemaining
                val percent = if (totalDuration > 0) (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

                NextPrayerInfoLocal(
                    name = "الفجر",
                    time = fajrTime,
                    secondsRemaining = secondsRemaining,
                    totalDuration = totalDuration,
                    progressPercent = percent
                )
            }
        }

        private fun parseTimeSafely(timeStr: String): LocalTime? {
            return try {
                LocalTime.parse(timeStr.replace(" ", ""))
            } catch (e: Exception) {
                null
            }
        }

        private fun createCircularProgressBitmap(progress: Float): Bitmap {
            val width = 120
            val height = 120
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val strokeWidth = 12f
            val padding = strokeWidth
            val rectF = android.graphics.RectF(padding, padding, width - padding, height - padding)

            val backgroundPaint = Paint().apply {
                color = 0x33FFFFFF
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)

            val progressPaint = Paint().apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
                shader = SweepGradient(
                    width / 2f, height / 2f,
                    intArrayOf(0xFF4CAF50.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF9800.toInt(), 0xFF4CAF50.toInt()),
                    floatArrayOf(0f, 0.5f, 0.9f, 1f)
                )
            }

            canvas.save()
            canvas.rotate(-90f, width / 2f, height / 2f)
            canvas.drawArc(rectF, 0f, progress * 360f, false, progressPaint)
            canvas.restore()

            return bitmap
        }

        fun scheduleNextUpdate(context: Context) {
            DebugLogger.debug(LogCategory.WIDGET, "scheduleNextUpdate() called")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PrayerTimesWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerMillis = System.currentTimeMillis() + 60_000
            val triggerTimeFormatted = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(triggerMillis), 
                java.time.ZoneId.systemDefault()
            ).format(DateTimeFormatter.ofPattern("HH:mm:ss"))

            DebugLogger.debug(LogCategory.WIDGET, "Programming alarm for $triggerTimeFormatted")

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
                DebugLogger.debug(LogCategory.WIDGET, "SUCCESS: Alarme programmée pour le $triggerTimeFormatted (+60s)")
            } catch (e: Exception) {
                DebugLogger.error(LogCategory.WIDGET, "Échec alarme exacte, tentative en mode inexact...", e)
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    DebugLogger.debug(LogCategory.WIDGET, "SUCCESS (Fallback): Alarme inexacte programmée pour le $triggerTimeFormatted")
                } catch (fallbackEx: Exception) {
                    DebugLogger.error(LogCategory.WIDGET, "CRITICAL: Impossible de programmer l'alarme", fallbackEx)
                }
            }
        }

        private fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PrayerTimesWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            DebugLogger.debug(LogCategory.WIDGET, "cancelAlarm() — widget alarm cancelled")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        DebugLogger.debug(LogCategory.WIDGET, "onReceive() action=${intent.action}")
        super.onReceive(context, intent)
        val action = intent.action
        DebugLogger.debug(LogCategory.WIDGET, "PrayerTimesWidgetProvider.onReceive() triggered with action=$action")

        if (action == ACTION_UPDATE_WIDGET || 
            action == Intent.ACTION_TIME_TICK || 
            action == Intent.ACTION_DATE_CHANGED || 
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED) {
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, PrayerTimesWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            DebugLogger.debug(LogCategory.WIDGET, "onReceive() updating ${ids.size} widget instance(s)")
            for (id in ids) {
                try {
                    DebugLogger.debug(LogCategory.WIDGET, "Updating widget id=$id")
                    updateAppWidget(context, appWidgetManager, id)
                    DebugLogger.debug(LogCategory.WIDGET, "Widget updated id=$id")
                } catch (e: Exception) {
                    DebugLogger.error(LogCategory.WIDGET, "Error in onReceive updateAppWidget for id $id", e)
                }
            }
            scheduleNextUpdate(context)
        }
    }
}

class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DebugLogger.debug(LogCategory.WIDGET, "WidgetBootReceiver triggered — rescheduling widget alarms after reboot")
            PrayerTimesWidgetProvider.scheduleNextUpdate(context)
        }
    }
}

sealed class WidgetDisplayState {
    data class Countdown(
        val nextName: String,
        val nextTime: LocalTime,
        val secondsRemaining: Long,
        val progressPercent: Float
    ) : WidgetDisplayState()
}

data class PrayerTimesLocal(
    val fajr: LocalTime,
    val dhuhr: LocalTime,
    val asr: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime
)

data class NextPrayerInfoLocal(
    val name: String,
    val time: LocalTime,
    val secondsRemaining: Long,
    val totalDuration: Long,
    val progressPercent: Float
)
