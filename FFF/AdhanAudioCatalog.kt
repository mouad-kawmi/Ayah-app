package com.example

import com.aistudio.quran.mwkpqz.R

data class AdhanAudioOption(
    val key: String,
    val label: String,
    val rawResId: Int
)

object AdhanAudioCatalog {
    val options = listOf(
        AdhanAudioOption("makkah", "أذان مكة", R.raw.adhan_makkah),
        AdhanAudioOption("madinah", "أذان المدينة", R.raw.adhan_madinah),
        AdhanAudioOption("abdul_basit", "عبد الباسط", R.raw.adhan_abdul_basit),
        AdhanAudioOption("minshawi", "المنشاوي", R.raw.adhan_minshawi),
        AdhanAudioOption("alafasy", "العفاسي", R.raw.adhan_alafasy),
        AdhanAudioOption("qatami", "ناصر القطامي", R.raw.adhan_qatami),
        AdhanAudioOption("yusuf_islam", "يوسف إسلام", R.raw.adhan_yusuf_islam)
    )

    fun defaultOption(): AdhanAudioOption = options.first()

    fun findByKey(key: String?): AdhanAudioOption {
        return options.firstOrNull { it.key == key } ?: defaultOption()
    }
}
