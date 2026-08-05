package com.example.quranapp.domain.model

data class Surah(
    val id: Int,
    val nameArabic: String,
    val nameTransliterated: String,
    val numberOfAyahs: Int,
    val revelationPlace: String,
    val firstPage: Int,
    val lastPage: Int
) {
    val allPages: List<Int> get() = (firstPage..lastPage).toList()
}
