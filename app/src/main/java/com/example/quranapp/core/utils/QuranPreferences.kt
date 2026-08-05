package com.example.quranapp.core.utils

import android.content.Context
import android.content.SharedPreferences

object QuranPreferences {
    private const val PREF_NAME = "quran_prefs"
    private const val KEY_LAST_SURAH_ID = "last_surah_id"
    private const val KEY_LAST_PAGE = "last_page"
    private const val KEY_LAST_SURAH_NAME = "last_surah_name"
    private const val KEY_ADHAN_VOLUME = "adhan_volume"
    private const val KEY_SELECTED_ADHAN_ID = "selected_adhan_id"
    private const val KEY_CUSTOM_ADHAN_URI = "custom_adhan_uri"
    private const val KEY_CUSTOM_ADHAN_NAME = "custom_adhan_name"
    private const val KEY_KHATMA_DURATION = "khatma_duration"
    private const val KEY_KHATMA_READ_PAGES = "khatma_read_pages"
    private const val KEY_SELECTED_RECITER_ID = "selected_reciter_id"
    private const val KEY_SELECTED_AUDIO_RECITER_ID = "selected_audio_reciter_id"
    private const val KEY_SELECTED_TAFSIR_ID = "selected_tafsir_id"
    private const val DEFAULT_TAFSIR_ID = "muyassar"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSelectedReciterId(context: Context, reciterId: Int) {
        getPrefs(context).edit().putInt(KEY_SELECTED_RECITER_ID, reciterId).apply()
    }

    fun getSelectedReciterId(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED_RECITER_ID, 7)
    }

    fun saveSelectedAudioReciterId(context: Context, reciterId: Int) {
        getPrefs(context).edit().putInt(KEY_SELECTED_AUDIO_RECITER_ID, reciterId).apply()
    }

    fun saveSelectedTafsirId(context: Context, tafsirId: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_TAFSIR_ID, tafsirId).apply()
    }

    fun getSelectedTafsirId(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_TAFSIR_ID, DEFAULT_TAFSIR_ID) ?: DEFAULT_TAFSIR_ID
    }

    fun getSelectedAudioReciterId(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED_AUDIO_RECITER_ID, 7)
    }

    fun saveLastRead(context: Context, surahId: Int, pageNumber: Int, surahName: String) {
        getPrefs(context).edit().apply {
            putInt(KEY_LAST_SURAH_ID, surahId)
            putInt(KEY_LAST_PAGE, pageNumber)
            putString(KEY_LAST_SURAH_NAME, surahName)
            apply()
        }
    }

    fun getLastSurahId(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_SURAH_ID, 1)
    }

    fun getLastPage(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_PAGE, 1)
    }

    fun getLastSurahName(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_SURAH_NAME, "الفاتحة") ?: "الفاتحة"
    }

    fun saveAdhanVolume(context: Context, volume: Float) {
        getPrefs(context).edit().putFloat(KEY_ADHAN_VOLUME, volume).apply()
    }

    fun getAdhanVolume(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ADHAN_VOLUME, 60f)
    }

    fun saveSelectedAdhanId(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_ADHAN_ID, id).apply()
    }

    fun getSelectedAdhanId(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_ADHAN_ID, "nasser") ?: "nasser"
    }

    fun saveCustomAdhan(context: Context, uri: String, name: String) {
        getPrefs(context).edit().apply {
            putString(KEY_CUSTOM_ADHAN_URI, uri)
            putString(KEY_CUSTOM_ADHAN_NAME, name)
            apply()
        }
    }

    fun getCustomAdhanUri(context: Context): String? {
        return getPrefs(context).getString(KEY_CUSTOM_ADHAN_URI, null)
    }

    fun getCustomAdhanName(context: Context): String? {
        return getPrefs(context).getString(KEY_CUSTOM_ADHAN_NAME, null)
    }

    fun saveKhatmaDuration(context: Context, days: Int) {
        getPrefs(context).edit().putInt(KEY_KHATMA_DURATION, days).apply()
    }

    fun getKhatmaDuration(context: Context): Int {
        return getPrefs(context).getInt(KEY_KHATMA_DURATION, 60)
    }

    fun saveKhatmaReadPages(context: Context, pages: Set<Int>) {
        val strSet = pages.map { it.toString() }.toSet()
        getPrefs(context).edit().putStringSet(KEY_KHATMA_READ_PAGES, strSet).apply()
    }

    fun getKhatmaReadPages(context: Context): Set<Int> {
        val strSet = getPrefs(context).getStringSet(KEY_KHATMA_READ_PAGES, emptySet()) ?: emptySet()
        return strSet.mapNotNull { it.toIntOrNull() }.toSet()
    }
}
