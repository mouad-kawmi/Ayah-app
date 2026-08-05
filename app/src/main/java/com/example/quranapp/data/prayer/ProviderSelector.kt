package com.example.quranapp.data.prayer

import android.util.Log

class ProviderSelector(
    private val habousProvider: HabousProvider,
    private val alAdhanProvider: AlAdhanProvider
) {
    fun selectProvider(countryCode: String): PrayerTimesProvider {
        val provider = when (countryCode) {
            "MA" -> habousProvider
            else -> alAdhanProvider
        }
        val providerId = when (provider) {
            is HabousProvider -> ProviderIds.HABOUS
            is AlAdhanProvider -> ProviderIds.ALADHAN
            else -> "unknown"
        }
        Log.i("PrayerSync", "Provider country=$countryCode provider=$providerId")
        return provider
    }
}
