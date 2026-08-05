package com.example.quranapp.presentation.quran

import com.example.quranapp.domain.model.Surah

data class SurahUiState(
    val surahs: List<Surah> = emptyList(),
    val isLoading: Boolean = false
)
