package com.example.quranapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Page(
    val page: Int,
    val font: String,
    val surahs: List<PageSurah> = emptyList(),
    val lines: List<Line>
)

@Serializable
data class PageSurah(
    val id: Int,
    val name: String,
    val name_arabic: String,
    val verse_start: Int,
    val verse_end: Int
)

@Serializable
data class Line(
    val line: Int,
    val words: List<Word>
)

@Serializable
data class Word(
    val char: String,
    val font: String,
    val text: String,
    val type: String,
    val verse_key: String? = null,
    val sura: Int? = null
)
