package com.example.quranapp.data.sunnah

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.quranapp.presentation.sunnah.SunnahRemindersViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class SunnahNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("EXTRA_REMINDER_ID") ?: return
        val reminderTitle = intent.getStringExtra("EXTRA_REMINDER_TITLE") ?: "تنبيه"
        val reminderDesc = intent.getStringExtra("EXTRA_REMINDER_DESC") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "SUNNAH_ALERTS_CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تنبيهات السنن",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة لإشعارات السنن والأذكار"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.quranapp.R.drawable.ic_monochrome)
            .setContentTitle(reminderTitle)
            .setContentText(reminderDesc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(clickPendingIntent)
            .build()
            
        val notificationId = reminderId.hashCode()
        notificationManager.notify(notificationId, notification)

        // Reschedule for next occurrence
        SunnahAlarmScheduler.scheduleNextOccurrence(context, reminderId)
    }
}

object SunnahAlarmScheduler {

    fun scheduleNextOccurrence(context: Context, reminderId: String) {
        val viewModel = SunnahRemindersViewModel(context)
        val reminder = viewModel.reminders.find { it.id == reminderId } ?: return
        
        // verify it is still enabled
        if (!viewModel.enabledReminders.value.contains(reminderId)) return

        val now = LocalDateTime.now()
        var targetTime = now.withHour(reminder.hour).withMinute(reminder.minute).withSecond(0).withNano(0)

        if (reminder.dayOfWeek != null) {
            targetTime = targetTime.with(TemporalAdjusters.nextOrSame(reminder.dayOfWeek))
            if (targetTime.isBefore(now) || targetTime.isEqual(now)) {
                targetTime = targetTime.plusWeeks(1)
            }
        } else {
            if (targetTime.isBefore(now) || targetTime.isEqual(now)) {
                targetTime = targetTime.plusDays(1)
            }
        }

        val epochMilli = targetTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val intent = Intent(context, SunnahNotificationReceiver::class.java).apply {
            putExtra("EXTRA_REMINDER_ID", reminder.id)
            putExtra("EXTRA_REMINDER_TITLE", reminder.title)
            putExtra("EXTRA_REMINDER_DESC", reminder.description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
        }
    }

    fun cancelReminder(context: Context, reminderId: String) {
        val intent = Intent(context, SunnahNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
