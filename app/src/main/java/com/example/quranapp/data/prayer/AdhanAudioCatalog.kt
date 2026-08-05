package com.example.quranapp.data.prayer

data class AdhanCatalogEntry(
    val key: String,
    val label: String,
    val assetPath: String
)

object AdhanAudioCatalog {
    private val options = listOf(
        AdhanCatalogEntry("makkah", "أذان مكة", "adhan/Adhan-Makkah.mp3"),
        AdhanCatalogEntry("madinah", "أذان المدينة", "adhan/Adhan-Madinah.mp3"),
        AdhanCatalogEntry("abdulbasit", "عبد الباسط", "adhan/Abdul-Basit.mp3"),
        AdhanCatalogEntry("minshawi", "المنشاوي", "adhan/Minshawi.mp3"),
        AdhanCatalogEntry("mishary", "مشاري راشد العفاسي", "adhan/Mishary Rashid Alafasy.mp3"),
        AdhanCatalogEntry("nasser", "ناصر القطامي", "adhan/Nasser AL Qatami.mp3"),
        AdhanCatalogEntry("yusuf", "يوسف إسلام", "adhan/Yusuf-Islam.mp3")
    )

    fun findByKey(key: String?): AdhanCatalogEntry {
        return options.firstOrNull { it.key == key } ?: defaultOption()
    }

    fun defaultOption(): AdhanCatalogEntry = options.firstOrNull { it.key == "nasser" } ?: options.first()
}
