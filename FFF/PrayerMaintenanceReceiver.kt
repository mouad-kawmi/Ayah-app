package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.debug.DebugLogger
import com.example.debug.LogCategory

class PrayerMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action ?: "UNKNOWN"
        DebugLogger.debug(LogCategory.ALARM, "PrayerMaintenanceReceiver — onReceive ENTER action=$intentAction ts=${System.currentTimeMillis()}")

        // Log the specific trigger so we know exactly what woke the receiver
        when (intentAction) {
            Intent.ACTION_BOOT_COMPLETED ->
                DebugLogger.info(LogCategory.BOOT, "BOOT_COMPLETED received — rescheduling all alarms")
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                DebugLogger.info(LogCategory.BOOT, "PACKAGE_REPLACED received — rescheduling all alarms")
            Intent.ACTION_TIME_CHANGED ->
                DebugLogger.info(LogCategory.ALARM, "TIME_CHANGED received — rescheduling all alarms")
            Intent.ACTION_TIMEZONE_CHANGED ->
                DebugLogger.info(LogCategory.ALARM, "TIMEZONE_CHANGED received — rescheduling all alarms")
            Intent.ACTION_DATE_CHANGED ->
                DebugLogger.info(LogCategory.ALARM, "DATE_CHANGED received — rescheduling all alarms")
            "com.example.ACTION_MIDNIGHT_REFRESH" ->
                DebugLogger.info(LogCategory.ALARM, "MIDNIGHT_REFRESH received — rescheduling all alarms")
            else ->
                DebugLogger.info(LogCategory.BOOT, "MaintenanceReceiver fired — action=$intentAction")
        }

        DebugLogger.debug(LogCategory.ALARM, "PrayerMaintenanceReceiver — ensureScheduled called")
        PrayerTimesSyncScheduler.ensureScheduled(context)
        DebugLogger.debug(LogCategory.ALARM, "PrayerMaintenanceReceiver — triggerImmediateSync called")
        PrayerTimesSyncScheduler.triggerImmediateSync(context)
        DebugLogger.info(LogCategory.SYNC, "triggerImmediateSync() called from MaintenanceReceiver")
        DebugLogger.debug(LogCategory.ALARM, "PrayerMaintenanceReceiver — scheduleUpcomingAlarms called")
        PrayerAlarmScheduler.scheduleUpcomingAlarms(context)
        DebugLogger.info(LogCategory.ALARM, "scheduleUpcomingAlarms() called from MaintenanceReceiver")

        // Reschedule sunnan reminders (Android clears all AlarmManager alarms on boot/update).
        // Without this, sunnan toggles were lost after reboot until the user reopened the app.
        DebugLogger.info(LogCategory.BOOT, "Rescheduling sunnan alarms after $intentAction")
        SunnanAlarmScheduler.updateSunnanAlarms(context)
        DebugLogger.info(LogCategory.ALARM, "updateSunnanAlarms() called from MaintenanceReceiver")

        DebugLogger.debug(LogCategory.SYNC, "MaintenanceReceiver — calling PrayerWidgetUpdater.refresh()")
        PrayerWidgetUpdater.refresh(context)

        // Schedule next midnight
        DebugLogger.info(LogCategory.ALARM, "scheduleMidnightRefresh() called from MaintenanceReceiver")
        PrayerAlarmScheduler.scheduleMidnightRefresh(context)
        DebugLogger.debug(LogCategory.ALARM, "PrayerMaintenanceReceiver — onReceive EXIT action=$intentAction")
    }
}
