package com.example.quranapp.domain.model

data class Reciter(
    val id: Int,
    val name: String,
    val style: String?,
    val translatedName: String?,
    val supportsVerseTimings: Boolean? = null
)
