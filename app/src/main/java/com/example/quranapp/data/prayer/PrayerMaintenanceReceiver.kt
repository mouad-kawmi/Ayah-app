package com.example.quranapp.data.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.DiagnosticsDashboard
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory

class PrayerMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "UNKNOWN"
        val traceId = DiagnosticsDashboard.generateTraceId()
        val city = runCatching { PrayerTimesCacheStore.getCachedCityName(context) }.getOrNull()
        Log.d("PRAYER", "MaintenanceReceiver — action=$action")
        DebugLogger.info(
            LogCategory.PRAYER,
            Instrumentation.line("maintenance", traceId, city, "MaintenanceReceiver action=$action")
        )

        try {
            PrayerStateMachine.init(context)
        } catch (e: Exception) {
            Log.e("PRAYER", "MaintenanceReceiver — init failed", e)
            DebugLogger.error(
                LogCategory.PRAYER,
                Instrumentation.line("maintenance", traceId, city, ErrorCode.MAINTENANCE_INIT_FAILED.prefix("State machine init failed")),
                e
            )
        }

        try {
            PrayerTimesSyncScheduler.ensureScheduled(context)
            DebugLogger.info(
                LogCategory.SYNC,
                Instrumentation.line("maintenance", traceId, city, "Periodic sync ensured")
            )
        } catch (e: Exception) {
            Log.e("PRAYER", "MaintenanceReceiver — ensureScheduled failed", e)
            DebugLogger.error(
                LogCategory.SYNC,
                Instrumentation.line("maintenance", traceId, city, ErrorCode.MAINTENANCE_SCHEDULE_FAILED.prefix("ensureScheduled failed")),
                e
            )
        }

        try {
            PrayerTimesSyncScheduler.triggerImmediateSync(context)
            DebugLogger.info(
                LogCategory.SYNC,
                Instrumentation.line("maintenance", traceId, city, "Immediate sync triggered")
            )
        } catch (e: Exception) {
            Log.e("PRAYER", "MaintenanceReceiver — triggerImmediateSync failed", e)
            DebugLogger.error(
                LogCategory.SYNC,
                Instrumentation.line("maintenance", traceId, city, ErrorCode.MAINTENANCE_SYNC_FAILED.prefix("triggerImmediateSync failed")),
                e
            )
        }

        try {
            val today = PrayerStateMachine.ensureConfigured(context)
            if (today != null) {
                PrayerAlarmScheduler.scheduleUpcomingAlarms(context)
                DebugLogger.info(
                    LogCategory.ALARM,
                    Instrumentation.line("maintenance", traceId, city, "Alarms rescheduled from shared state")
                )
            } else {
                DebugLogger.warning(
                    LogCategory.ALARM,
                    Instrumentation.line("maintenance", traceId, city, "No cached prayer times — alarms not rescheduled")
                )
            }
        } catch (e: Exception) {
            Log.e("PRAYER", "MaintenanceReceiver — cache/alarm setup failed", e)
            DebugLogger.error(
                LogCategory.ALARM,
                Instrumentation.line("maintenance", traceId, city, ErrorCode.MAINTENANCE_CACHE_ALARM_FAILED.prefix("Cache/alarm setup failed")),
                e
            )
        }

        try {
            val sunnahPrefs = context.getSharedPreferences("sunnah_reminders_prefs", Context.MODE_PRIVATE)
            val enabledSunnahIds = sunnahPrefs.getStringSet("enabled_ids", emptySet()) ?: emptySet()
            enabledSunnahIds.forEach { id ->
                com.example.quranapp.data.sunnah.SunnahAlarmScheduler.scheduleNextOccurrence(context, id)
            }
            DebugLogger.info(
                LogCategory.ALARM,
                Instrumentation.line("maintenance", traceId, city, "Sunnah reminders rescheduled (${enabledSunnahIds.size})")
            )
        } catch (e: Exception) {
            Log.e("PRAYER", "MaintenanceReceiver — Sunnah reschedule failed", e)
            DebugLogger.error(
                LogCategory.ALARM,
                Instrumentation.line("maintenance", traceId, city, ErrorCode.MAINTENANCE_SUNNAH_FAILED.prefix("Sunnah reschedule failed")),
                e
            )
        }
    }
}
