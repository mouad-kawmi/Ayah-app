package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.debug.DebugLogger
import com.example.debug.LogCategory

object PrayerWidgetUpdater {

    fun refresh(context: Context) {
        if (BuildConfig.DEBUG) {
            DebugLogger.debug(LogCategory.WIDGET, "PrayerWidgetUpdater.refresh() ENTER — ids=${getWidgetIds(context)}")
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, PrayerTimesWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) {
            DebugLogger.debug(LogCategory.WIDGET, "PrayerWidgetUpdater.refresh() EXIT — no widget instances")
            return
        }
        val intent = Intent(context, PrayerTimesWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
        DebugLogger.debug(LogCategory.WIDGET, "PrayerWidgetUpdater.refresh() EXIT — broadcast sent for ${ids.size} widget(s)")
    }

    private fun getWidgetIds(context: Context): String {
        return try {
            val mgr = AppWidgetManager.getInstance(context)
            val comp = ComponentName(context, PrayerTimesWidgetProvider::class.java)
            mgr.getAppWidgetIds(comp).contentToString()
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    fun refreshAll(context: Context) {
        refresh(context)
    }
}
