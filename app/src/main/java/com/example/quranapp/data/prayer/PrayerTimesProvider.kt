package com.example.quranapp.data.prayer

import android.content.Context
import java.time.LocalDate

interface PrayerTimesProvider {
    val providerId: String

    suspend fun fetchSchedule(
        context: Context,
        request: PrayerTimeRequest
    ): Result<Map<LocalDate, PrayerTimes>>
}

data class PrayerTimeRequest(
    val lat: Double,
    val lon: Double,
    val dateRange: ClosedRange<LocalDate>,
    val cityName: String? = null,
    val countryCode: String = ""
)
