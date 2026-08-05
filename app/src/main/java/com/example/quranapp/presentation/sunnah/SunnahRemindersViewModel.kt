package com.example.quranapp.presentation.sunnah

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ReminderCategory(val title: String) {
    WEEKLY("أسبوعية \uD83D\uDCC5"),
    DAILY("يومية \uD83C\uDF19")
}

data class SunnahReminder(
    val id: String,
    val category: ReminderCategory,
    val title: String,
    val description: String,
    val timeText: String,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: java.time.DayOfWeek?, // null means Daily
    val iconBgColor: Color,
    val iconColor: Color,
    val iconType: IconType
)

enum class IconType {
    BOOK, HEART, FASTING, MOON, SUN, SUNSET, QURAN_BOOK
}

class SunnahRemindersViewModel(private val context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("sunnah_reminders_prefs", Context.MODE_PRIVATE)

    private val _enabledReminders = MutableStateFlow<Set<String>>(
        prefs.getStringSet("enabled_ids", emptySet()) ?: emptySet()
    )
    val enabledReminders: StateFlow<Set<String>> = _enabledReminders.asStateFlow()

    fun toggleReminder(id: String, isEnabled: Boolean) {
        _enabledReminders.update { current ->
            val updated = if (isEnabled) {
                current + id
            } else {
                current - id
            }
            prefs.edit().putStringSet("enabled_ids", updated).apply()
            
            // Schedule or cancel alarm
            if (isEnabled) {
                com.example.quranapp.data.sunnah.SunnahAlarmScheduler.scheduleNextOccurrence(context, id)
            } else {
                com.example.quranapp.data.sunnah.SunnahAlarmScheduler.cancelReminder(context, id)
            }
            
            updated
        }
    }

    fun setReminderTime(id: String, hour: Int, minute: Int) {
        _reminderTimes.update { it + (id to (hour to minute)) }
        prefs.edit()
            .putInt("time_hour_$id", hour)
            .putInt("time_minute_$id", minute)
            .apply()

        // When enabled, cancel the old alarm and schedule a new one with the new time.
        if (_enabledReminders.value.contains(id)) {
            com.example.quranapp.data.sunnah.SunnahAlarmScheduler.cancelReminder(context, id)
            com.example.quranapp.data.sunnah.SunnahAlarmScheduler.scheduleNextOccurrence(context, id)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /** Reminders as shown to the user / used by the scheduler, honoring any customized times. */
    val reminders: List<SunnahReminder>
        get() = defaultReminders.map { base ->
            val custom = _reminderTimes.value[base.id]
            val hour = custom?.first ?: base.hour
            val minute = custom?.second ?: base.minute
            base.copy(
                hour = hour,
                minute = minute,
                timeText = "${base.timeText.substringBefore("—").trim()} — ${formatTime(hour, minute)}"
            )
        }

    private val defaultReminders = listOf(
        SunnahReminder(
            id = "kahf",
            category = ReminderCategory.WEEKLY,
            title = "سورة الكهف \uD83D\uDCD6",
            description = "من قرأ سورة الكهف يوم الجمعة أضاء له النور ما بين الجمعتين.",
            timeText = "كل الجمعة — 08:00",
            hour = 8, minute = 0, dayOfWeek = java.time.DayOfWeek.FRIDAY,
            iconBgColor = Color(0xFFE8F5E9), 
            iconColor = Color(0xFF2E7D32), 
            iconType = IconType.BOOK
        ),
        SunnahReminder(
            id = "salawat",
            category = ReminderCategory.WEEKLY,
            title = "الصلاة على النبي ﷺ \uD83D\uDC9A",
            description = "قال ﷺ: أكثروا الصلاة علي يوم الجمعة وليلة الجمعة.",
            timeText = "كل الجمعة — 12:00",
            hour = 12, minute = 0, dayOfWeek = java.time.DayOfWeek.FRIDAY,
            iconBgColor = Color(0xFFF1F8E9), 
            iconColor = Color(0xFF558B2F),
            iconType = IconType.HEART
        ),
        SunnahReminder(
            id = "fasting_mon",
            category = ReminderCategory.WEEKLY,
            title = "صيام الاثنين \uD83E\uDD0D",
            description = "قال ﷺ: ذاك يوم ولدت فيه وأنزل علي فيه، فأحب أن أصوم فيه.",
            timeText = "كل الاثنين — 05:30",
            hour = 5, minute = 30, dayOfWeek = java.time.DayOfWeek.MONDAY,
            iconBgColor = Color(0xFFF3E5F5), 
            iconColor = Color(0xFF6A1B9A),
            iconType = IconType.FASTING
        ),
        SunnahReminder(
            id = "fasting_thu",
            category = ReminderCategory.WEEKLY,
            title = "صيام الخميس \uD83E\uDD0D",
            description = "قال ﷺ: تعرض الأعمال يوم الاثنين والخميس فأحب أن يُعرض عملي وأنا صائم.",
            timeText = "كل الخميس — 05:30",
            hour = 5, minute = 30, dayOfWeek = java.time.DayOfWeek.THURSDAY,
            iconBgColor = Color(0xFFF3E5F5), 
            iconColor = Color(0xFF6A1B9A),
            iconType = IconType.FASTING
        ),
        SunnahReminder(
            id = "mulk",
            category = ReminderCategory.DAILY,
            title = "سورة الملك \uD83C\uDF19",
            description = "عن جابر رضي الله عنه: كان النبي ﷺ لا ينام حتى يقرأ {تبارك الذي بيده الملك}.",
            timeText = "يومياً — 22:00",
            hour = 22, minute = 0, dayOfWeek = null,
            iconBgColor = Color(0xFFE8EAF6), 
            iconColor = Color(0xFF283593),
            iconType = IconType.MOON
        ),
        SunnahReminder(
            id = "morning_azkar",
            category = ReminderCategory.DAILY,
            title = "أذكار الصباح \uD83C\uDF04",
            description = "أذكار الصباح درع وحصن للمسلم طوال يومه من الشياطين والبلاء.",
            timeText = "يومياً — 06:30",
            hour = 6, minute = 30, dayOfWeek = null,
            iconBgColor = Color(0xFFFFF3E0), 
            iconColor = Color(0xFFE65100),
            iconType = IconType.SUN
        ),
        SunnahReminder(
            id = "evening_azkar",
            category = ReminderCategory.DAILY,
            title = "أذكار المساء \uD83C\uDF07",
            description = "أذكار المساء تحمي المسلم في ليله وتجدد صلته بربه.",
            timeText = "يومياً — 18:00",
            hour = 18, minute = 0, dayOfWeek = null,
            iconBgColor = Color(0xFFFBE9E7), 
            iconColor = Color(0xFFD84315),
            iconType = IconType.SUNSET
        ),
        SunnahReminder(
            id = "sleep_azkar",
            category = ReminderCategory.DAILY,
            title = "أذكار النوم \uD83D\uDE34",
            description = "أذكار النوم تحمي النائم وتجعل نومه عبادة.",
            timeText = "يومياً — 22:30",
            hour = 22, minute = 30, dayOfWeek = null,
            iconBgColor = Color(0xFFECEFF1), 
            iconColor = Color(0xFF37474F),
            iconType = IconType.MOON
        ),
        SunnahReminder(
            id = "quran_wird",
            category = ReminderCategory.DAILY,
            title = "ورد القرآن اليومي \uD83D\uDCDA",
            description = "قال ﷺ: اقرؤوا القرآن فإنه يأتي يوم القيامة شفيعاً لأصحابه.",
            timeText = "يومياً — 09:00",
            hour = 9, minute = 0, dayOfWeek = null,
            iconBgColor = Color(0xFFE0F2F1), 
            iconColor = Color(0xFF00695C),
            iconType = IconType.QURAN_BOOK
        )
    )

    private val _reminderTimes = MutableStateFlow<Map<String, Pair<Int, Int>>>(
        defaultReminders.mapNotNull { reminder ->
            val hour = prefs.getInt("time_hour_${reminder.id}", -1)
            val minute = prefs.getInt("time_minute_${reminder.id}", -1)
            if (hour >= 0 && minute >= 0) reminder.id to (hour to minute) else null
        }.toMap()
    )
    val reminderTimes: StateFlow<Map<String, Pair<Int, Int>>> = _reminderTimes.asStateFlow()
}
