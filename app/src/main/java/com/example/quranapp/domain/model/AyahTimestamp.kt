package com.example.quranapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AyahTimestamp(
    val verseKey: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
) {
    val surahId: Int get() = verseKey.split(":").firstOrNull()?.toIntOrNull() ?: 1
    val ayahNumber: Int get() = verseKey.split(":").lastOrNull()?.toIntOrNull() ?: 1
}
