package com.example.quranapp.domain.model

data class SurahAudio(
    val surahId: Int,
    val reciterId: Int,
    val audioUrl: String,
    val durationMs: Long,
    val ayahTimestamps: List<AyahTimestamp>,
    val verseTimingsReliable: Boolean = false
)
