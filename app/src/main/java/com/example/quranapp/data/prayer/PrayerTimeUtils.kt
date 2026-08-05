package com.example.quranapp.data.prayer

import java.time.LocalDate
import java.time.YearMonth

object PrayerTimeUtils {
    fun getMonthsInRange(start: LocalDate, end: LocalDate): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var current = YearMonth.from(start)
        val endMonth = YearMonth.from(end)
        while (current <= endMonth) {
            result.add(current.year to current.monthValue)
            current = current.plusMonths(1)
        }
        return result
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p = 0.017453292519943295
        val a = 0.5 - kotlin.math.cos((lat2 - lat1) * p) / 2 +
                kotlin.math.cos(lat1 * p) * kotlin.math.cos(lat2 * p) *
                (1 - kotlin.math.cos((lon2 - lon1) * p)) / 2
        return 12742 * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    fun isInMoroccoBoundingBox(lat: Double, lon: Double): Boolean {
        return lat in 21.0..36.0 && lon in -17.0..-1.0
    }
}
