package com.example.quranapp.data.translation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Edition(
    val identifier: String,
    val language: String,
    val name: String,
    val englishName: String,
    val type: String
)

@Serializable
data class TranslationResponse(
    val code: Int,
    val status: String,
    val data: TranslationData
)

@Serializable
data class TranslationData(
    val surahs: List<SurahTranslation>
)

@Serializable
data class SurahTranslation(
    val number: Int,
    val ayahs: List<AyahTranslation>
)

@Serializable
data class AyahTranslation(
    val numberInSurah: Int,
    val text: String
)
