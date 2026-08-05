package com.example.quranapp.presentation.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.quranapp.R
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.core.debug.Timings
import com.example.quranapp.data.prayer.PrayerCardInfo
import com.example.quranapp.data.prayer.PrayerStateMachine
import com.example.quranapp.data.prayer.PrayerState
import com.example.quranapp.data.prayer.PrayerTimesCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DebugLogger.info(
            LogCategory.WIDGET,
            Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Widget onUpdate ids=${appWidgetIds.size}")
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
                DebugLogger.info(
                    LogCategory.WIDGET,
                    Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Widget onUpdate completed ids=${appWidgetIds.size}")
                )
            } catch (e: Exception) {
                Log.e("PrayerWidget", "Error updating widgets", e)
                DebugLogger.error(
                    LogCategory.WIDGET,
                    Instrumentation.line("widget", Instrumentation.NO_TRACE, null, ErrorCode.WIDGET_UPDATE_FAILED.prefix("Error updating widgets")),
                    e
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        DebugLogger.info(
            LogCategory.WIDGET,
            Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Widget options changed id=$appWidgetId")
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e("PrayerWidget", "Error in onAppWidgetOptionsChanged for widget $appWidgetId", e)
                DebugLogger.error(
                    LogCategory.WIDGET,
                    Instrumentation.line("widget", Instrumentation.NO_TRACE, null, ErrorCode.WIDGET_OPTIONS_FAILED.prefix("Error in onAppWidgetOptionsChanged id=$appWidgetId")),
                    e
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == ACTION_UPDATE_WIDGET
        ) {
            val category = when (action) {
                Intent.ACTION_BOOT_COMPLETED -> LogCategory.BOOT
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED -> LogCategory.WIDGET
                else -> LogCategory.WIDGET
            }
            DebugLogger.info(
                category,
                Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Widget onReceive action=$action")
            )
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, PrayerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                    DebugLogger.info(
                        LogCategory.WIDGET,
                        Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Widget onReceive updated ids=${appWidgetIds.size} action=$action")
                    )
                } catch (e: Exception) {
                    Log.e("PrayerWidget", "Error in onReceive for action $action", e)
                    DebugLogger.error(
                        LogCategory.WIDGET,
                        Instrumentation.line("widget", Instrumentation.NO_TRACE, null, ErrorCode.WIDGET_RECEIVE_FAILED.prefix("Error in onReceive action=$action")),
                        e
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.ACTION_UPDATE_PRAYER_WIDGET"

        fun requestWidgetRefresh(context: Context) {
            val intent = Intent(ACTION_UPDATE_WIDGET).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
        
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                Timings.measure("Widget render") { updateAppWidgetInternal(context, appWidgetManager, appWidgetId) }
            } catch (e: Exception) {
                Log.e("PrayerWidget", "Error updating widget $appWidgetId", e)
                DebugLogger.error(
                    LogCategory.WIDGET,
                    Instrumentation.line("widget", Instrumentation.NO_TRACE, null, ErrorCode.WIDGET_UPDATE_FAILED.prefix("Error updating widget id=$appWidgetId")),
                    e
                )
            }
        }

        private fun updateAppWidgetInternal(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val city = runCatching { PrayerTimesCacheStore.getCachedCityName(context) }.getOrNull()
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            // Determine layout based on resize dimensions
            val layoutId = when {
                minHeight >= 200 && minWidth >= 200 -> R.layout.widget_prayer_large
                minHeight >= 110 -> R.layout.widget_prayer_medium
                else -> R.layout.widget_prayer_small
            }

            DebugLogger.info(
                LogCategory.WIDGET,
                Instrumentation.line("widget", Instrumentation.NO_TRACE, city, "Updating widget id=$appWidgetId size=${minWidth}x$minHeight layout=$layoutId")
            )

            val views = RemoteViews(context.packageName, layoutId)

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = if (intent != null) {
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else null
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Single source of truth: ensure the state machine holds today's data (loaded
            // through the same repository pipeline as the app, with the same fallback chain)
            // and read the shared state. The widget never computes the countdown itself.
            val cachedTimes = PrayerStateMachine.ensureConfigured(context)
            
            // Set City
            val cityName = PrayerTimesCacheStore.getCachedCityName(context) ?: "المغرب"
            views.setTextViewText(R.id.widget_tv_city, cityName)

            // Set Dates (only present in medium/large layouts)
            if (layoutId == R.layout.widget_prayer_medium || layoutId == R.layout.widget_prayer_large) {
                views.setTextViewText(R.id.widget_tv_hijri, getHijriDate())
                if (layoutId == R.layout.widget_prayer_large) {
                    views.setTextViewText(R.id.widget_tv_gregorian, getGregorianDate())
                }
            }

            if (cachedTimes != null) {
                val state = PrayerStateMachine.state.value
                val cards = PrayerStateMachine.prayerCards.value
                val prayers = cards.filter { it.name != "الشروق" }

                // If Medium/Large layout, update the prayers list
                if (layoutId == R.layout.widget_prayer_medium || layoutId == R.layout.widget_prayer_large) {
                    val bgs = listOf(R.id.widget_prayer1_bg, R.id.widget_prayer2_bg, R.id.widget_prayer3_bg, R.id.widget_prayer4_bg, R.id.widget_prayer5_bg)
                    val names = listOf(R.id.widget_prayer1_name, R.id.widget_prayer2_name, R.id.widget_prayer3_name, R.id.widget_prayer4_name, R.id.widget_prayer5_name)
                    val times = listOf(R.id.widget_prayer1_time, R.id.widget_prayer2_time, R.id.widget_prayer3_time, R.id.widget_prayer4_time, R.id.widget_prayer5_time)

                    val activeName = when (state) {
                        is PrayerState.BeforePrayer -> state.prayerName
                        is PrayerState.AdhanPlaying -> state.prayerName
                        is PrayerState.PostPrayer -> state.prayerName
                    }

                    for (i in 0 until 5) {
                        views.setTextViewText(names[i], prayers[i].name)
                        views.setTextViewText(times[i], prayers[i].time.format(DateTimeFormatter.ofPattern("HH:mm")))
                        
                        if (prayers[i].name == activeName) {
                            views.setInt(bgs[i], "setBackgroundResource", R.drawable.widget_prayer_bg_active)
                        } else {
                            views.setInt(bgs[i], "setBackgroundResource", R.drawable.widget_prayer_bg_normal)
                        }
                    }
                }

                when (state) {
                    is PrayerState.BeforePrayer -> {
                        views.setTextViewText(R.id.widget_tv_prayer_label, "الصلاة القادمة")
                        views.setTextViewText(R.id.widget_tv_prayer_name, state.prayerName)
                        
                        views.setTextViewText(R.id.widget_tv_timer_label, "متبقي")
                        
                        views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_tv_now, View.GONE)
                        
                        val baseTime = SystemClock.elapsedRealtime() + (state.remainingSeconds * 1000L)
                        views.setLong(R.id.widget_chronometer, "setBase", baseTime)
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            views.setBoolean(R.id.widget_chronometer, "setCountDown", true)
                        }
                        // Start the chronometer
                        views.setChronometer(R.id.widget_chronometer, baseTime, "%s", true)
                        
                        // Schedule exact alarm for when the prayer arrives
                        scheduleExactAlarm(context, System.currentTimeMillis() + (state.remainingSeconds * 1000L))
                    }
                    is PrayerState.AdhanPlaying, is PrayerState.PostPrayer -> {
                        val prayerName = if (state is PrayerState.AdhanPlaying) state.prayerName else (state as PrayerState.PostPrayer).prayerName
                        val elapsed = if (state is PrayerState.AdhanPlaying) state.elapsedSeconds else (state as PrayerState.PostPrayer).elapsedSeconds
                        
                        views.setTextViewText(R.id.widget_tv_prayer_label, "الصلاة الحالية")
                        views.setTextViewText(R.id.widget_tv_prayer_name, prayerName)
                        
                        // Post-prayer window: mirror the app exactly. Show "مضى" with a native
                        // Chronometer counting UP from the adhan time.
                        views.setTextViewText(R.id.widget_tv_timer_label, "مضى")
                        
                        views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_tv_now, View.GONE)
                        
                        val windowSeconds = PrayerStateMachine.postPrayerDurationMinutes * 60L
                        val remaining = (windowSeconds - elapsed).coerceAtLeast(0L)
                        val baseTime = SystemClock.elapsedRealtime() - (elapsed * 1000L)
                        views.setLong(R.id.widget_chronometer, "setBase", baseTime)
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            views.setBoolean(R.id.widget_chronometer, "setCountDown", false)
                        }
                        views.setChronometer(R.id.widget_chronometer, baseTime, "%s", true)
                        
                        // Schedule exact alarm for when the post-prayer window ends.
                        // The state machine stays the single source of truth and flips to the
                        // next prayer countdown once the window elapses.
                        if (remaining > 0) {
                            scheduleExactAlarm(context, System.currentTimeMillis() + (remaining * 1000L))
                        } else {
                            // If we somehow missed the transition, force a manual state machine evaluation and update again shortly
                            scheduleExactAlarm(context, System.currentTimeMillis() + 5000L)
                        }
                    }
                }

                DebugLogger.info(
                    LogCategory.WIDGET,
                    Instrumentation.line("widget", Instrumentation.NO_TRACE, cityName, widgetSnapshot(state, prayers))
                )
            } else {
                DebugLogger.warning(
                    LogCategory.WIDGET,
                    Instrumentation.line("widget", Instrumentation.NO_TRACE, city, "No cached prayer times for widget id=$appWidgetId")
                )
                views.setTextViewText(R.id.widget_tv_prayer_name, "--")
                views.setViewVisibility(R.id.widget_chronometer, View.GONE)
                views.setViewVisibility(R.id.widget_tv_now, View.VISIBLE)
                views.setTextViewText(R.id.widget_tv_now, "--:--")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun scheduleExactAlarm(context: Context, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PrayerWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1005, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        DebugLogger.info(
                            LogCategory.WIDGET,
                            Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Exact alarm scheduled at $triggerAtMillis")
                        )
                    } else {
                        // User requirement: "NO fallback to inexact alarms... keep Chronometer running normally"
                        Log.w("PrayerWidget", "Exact alarms denied. Keeping Chronometer running normally without fallback.")
                        DebugLogger.warning(
                            LogCategory.WIDGET,
                            Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Exact alarms denied — keeping Chronometer without fallback")
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    DebugLogger.info(
                        LogCategory.WIDGET,
                        Instrumentation.line("widget", Instrumentation.NO_TRACE, null, "Exact alarm scheduled at $triggerAtMillis")
                    )
                }
            } catch (e: SecurityException) {
                Log.e("PrayerWidget", "Security exception for exact alarm. No inexact fallback applied as per requirements.", e)
                DebugLogger.error(
                    LogCategory.WIDGET,
                    Instrumentation.line("widget", Instrumentation.NO_TRACE, null, ErrorCode.WIDGET_EXACT_ALARM_DENIED.prefix("Security exception for exact alarm")),
                    e
                )
            }
        }

        private fun widgetSnapshot(state: PrayerState, prayers: List<PrayerCardInfo>): String {
            val names = prayers.map { it.name }
            val activeIndex = names.indexOf(state.prayerName)
            val current: String
            val next: String
            when (state) {
                is PrayerState.BeforePrayer -> {
                    next = state.prayerName
                    current = if (activeIndex > 0) names[activeIndex - 1] else names.lastOrNull() ?: "-"
                }
                is PrayerState.AdhanPlaying, is PrayerState.PostPrayer -> {
                    current = state.prayerName
                    next = if (activeIndex in 0 until names.lastIndex) names[activeIndex + 1] else names.firstOrNull() ?: "-"
                }
            }
            val timing = when (state) {
                is PrayerState.BeforePrayer -> "Remaining=${Instrumentation.clock(state.remainingSeconds)}"
                is PrayerState.AdhanPlaying -> "Elapsed=${Instrumentation.clock(state.elapsedSeconds)}"
                is PrayerState.PostPrayer -> "Elapsed=${Instrumentation.clock(state.elapsedSeconds)}"
            }
            return "Widget rendered Current=$current Next=$next $timing Display=${state::class.simpleName}"
        }

        private fun getHijriDate(): String {
            return try {
                val hijrahDate = java.time.chrono.HijrahDate.now()
                val day = hijrahDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                val monthIndex = (hijrahDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1).coerceIn(0, 11)
                val year = hijrahDate.get(java.time.temporal.ChronoField.YEAR)
                val months = listOf("المحرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
                "$day ${months[monthIndex]} $year هـ"
            } catch (e: Exception) { "" }
        }

        private fun getGregorianDate(): String {
            return try {
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", java.util.Locale("ar")))
            } catch (e: Exception) { "" }
        }
    }
}
