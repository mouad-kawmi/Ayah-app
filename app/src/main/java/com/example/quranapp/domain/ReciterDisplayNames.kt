package com.example.quranapp.domain

import com.example.quranapp.domain.model.Reciter

/**
 * Best-effort Arabic display names for reciters.
 *
 * Resolution order:
 * 1. Arabic translated_name already provided by the API (future-proofing: QDC
 *    may serve Arabic translations later).
 * 2. Local mapping by English name (case-insensitive, so name variants and
 *    cached/downloaded entries are covered).
 * 3. Local mapping by reciter ID.
 * 4. Fall back to the original English name.
 *
 * Reciter IDs and API names are never modified — only the displayed label
 * changes.
 */
object ReciterDisplayNames {

    private val arabicCharRange = '\u0600'..'\u06FF'

    fun displayName(reciter: Reciter): String {
        reciter.translatedName?.takeIf { it.containsArabic() }?.let { return it }
        byName[reciter.name.trim().lowercase()]?.let { return it }
        byId[reciter.id]?.let { return it }
        return reciter.name
    }

    @JvmName("displayNameOrNull")
    fun displayName(reciter: Reciter?): String? = reciter?.let { displayName(it) }

    /** For entries that only store an id and the original English name (e.g. downloads). */
    fun displayName(reciterId: Int, storedName: String): String {
        if (storedName.containsArabic()) return storedName
        byName[storedName.trim().lowercase()]?.let { return it }
        byId[reciterId]?.let { return it }
        return storedName
    }

    private fun String.containsArabic(): Boolean = any { it in arabicCharRange }

    private val byName: Map<String, String> = buildMap {
        put("yasser ad dussary", "ياسر الدوسري")
        put("yasser al-dosari", "ياسر الدوسري")
        put("abdulbaset abdulsamad", "عبد الباسط عبد الصمد")
        put("abdul basit abdul samad", "عبد الباسط عبد الصمد")
        put("abdur-rahman as-sudais", "عبد الرحمن السديس")
        put("abdul rahman al sudais", "عبد الرحمن السديس")
        put("abu bakr al-shatri", "أبو بكر الشاطري")
        put("abu bakr as-shatri", "أبو بكر الشاطري")
        put("hani ar-rifai", "هاني الرفاعي")
        put("mahmoud khalil al-husary", "محمود خليل الحصري")
        put("sa'ud ash-shuraim", "سعود الشريم")
        put("saud al shuraim", "سعود الشريم")
        put("khalifah al tunaiji", "خليفة الطنيجي")
        put("mishari rashid al-`afasy", "مشاري راشد العفاسي")
        put("mishary rashid alafasy", "مشاري راشد العفاسي")
        put("mohamed siddiq al-minshawi", "محمد صديق المنشاوي")
        put("maher al muaiqly", "ماهر المعيقلي")
        put("saad al ghamdi", "سعد الغامدي")
        put("salah al budair", "صلاح البدير")
        put("abdullah basfar", "عبد الله بصفر")
        put("ahmed al ajmy", "أحمد العجمي")
        put("ali al-hudhaifi", "علي الحذيفي")
        put("sahl yasin", "سهل ياسين")
        put("ayman suwaid", "أيمن سويد")
        put("nasser al qatami", "ناصر القطامي")
        put("hazza al balushi", "هزاع البلوشي")
        put("abdullah al juhany", "عبد الله الجهني")
        put("adel al kalbani", "عادل الكلباني")
        put("bandar baleela", "بندر بليلة")
        put("islam sobhi", "إسلام صبحي")
        put("fares abbad", "فارس عباد")
        put("karim mansouri", "كريم منصوري")
        put("ibrahim al akhdar", "إبراهيم الأخضر")
        put("abdulmohsen alqasim", "عبد المحسن القاسم")
        put("mohamed al barak", "محمد البراك")
        put("muhammad al luhaidan", "محمد اللحيدان")
        put("muhammad jibreel", "محمد جبريل")
        put("muhammad ayyub", "محمد أيوب")
        put("abdulhadi kanakeri", "عبد الهادي أحمد كناكري")
        put("saeed noor", "سعيد نور")
        put("muhammad al-muhaisni", "محمد المهيسني")
        put("khalid al jalil", "خالد الجليل")
        put("omar alqazabri", "عمر القزابري")
    }

    private val byId: Map<Int, String> = mapOf(
        1 to "عبد الباسط عبد الصمد",
        2 to "عبد الباسط عبد الصمد",
        3 to "عبد الرحمن السديس",
        4 to "أبو بكر الشاطري",
        5 to "هاني الرفاعي",
        6 to "محمود خليل الحصري",
        7 to "مشاري راشد العفاسي",
        9 to "محمد صديق المنشاوي",
        10 to "سعود الشريم",
        12 to "محمود خليل الحصري",
        97 to "ياسر الدوسري",
        161 to "خليفة الطنيجي",
        168 to "محمد صديق المنشاوي",
        173 to "مشاري راشد العفاسي"
    )
}
