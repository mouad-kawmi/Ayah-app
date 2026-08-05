package com.example.quranapp.data.prayer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

data class PrayerTimes(
    val fajr: String = "--:--",
    val shuruq: String = "--:--",
    val dhuhr: String = "--:--",
    val asr: String = "--:--",
    val maghrib: String = "--:--",
    val isha: String = "--:--"
)

fun getDefaultCasablancaPrayerTimes(date: LocalDate = LocalDate.now()): PrayerTimes {
    return when (date.monthValue) {
        11, 12, 1 -> PrayerTimes(fajr = "06:15", shuruq = "07:45", dhuhr = "12:45", asr = "15:45", maghrib = "17:30", isha = "19:00")
        2, 3 -> PrayerTimes(fajr = "05:30", shuruq = "07:00", dhuhr = "13:00", asr = "16:15", maghrib = "18:30", isha = "20:00")
        4, 5 -> PrayerTimes(fajr = "04:30", shuruq = "06:15", dhuhr = "13:15", asr = "17:00", maghrib = "20:00", isha = "21:30")
        6, 7, 8 -> PrayerTimes(fajr = "04:15", shuruq = "06:00", dhuhr = "13:30", asr = "17:15", maghrib = "20:35", isha = "22:00")
        9, 10 -> PrayerTimes(fajr = "05:15", shuruq = "06:45", dhuhr = "13:15", asr = "16:30", maghrib = "19:15", isha = "20:45")
        else -> PrayerTimes(fajr = "04:30", shuruq = "06:15", dhuhr = "13:30", asr = "17:15", maghrib = "20:30", isha = "22:00")
    }
}

class PrayerTimesRepository(
    private val context: Context,
    private val providerSelector: ProviderSelector,
    private val countryDetector: CountryDetector
) {
    suspend fun fetchSchedule(
        lat: Double,
        lon: Double,
        cityName: String,
        dateRange: ClosedRange<LocalDate>,
        forceRefresh: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i("PrayerSync", "FetchSchedule lat=$lat lon=$lon city=$cityName dateRange=${dateRange.start}..${dateRange.endInclusive} forceRefresh=$forceRefresh")
            val countryCode = countryDetector.detectCountry(lat, lon)
            val provider = providerSelector.selectProvider(countryCode)
            val providerId = provider.providerId

            if (!forceRefresh && hasCompleteCachedRange(context, providerId, dateRange)) {
                Log.i("PrayerSync", "Country=$countryCode Provider=$providerId Source=Cache")
                PrayerTimesCacheStore.setActiveProvider(context, providerId)
                countryDetector.cacheCountry(countryCode)
                return@withContext Result.success(Unit)
            }

            if (!hasInternetConnection(context)) {
                val offlineLoaded = loadOfflineFallback(context)
                if (offlineLoaded) {
                    Log.i("PrayerSync", "Country=$countryCode Provider=habous Source=OfflineJson")
                    countryDetector.cacheCountry(countryCode)
                    return@withContext Result.success(Unit)
                }
                if (countryCode != "MA") {
                    val suncalcSchedule = calculateSunPositionSchedule(
                        lat, lon, dateRange
                    )
                    if (suncalcSchedule.isNotEmpty()) {
                        saveSchedule(context, ProviderIds.SUNCALC, suncalcSchedule)
                        PrayerTimesCacheStore.setActiveProvider(context, ProviderIds.SUNCALC)
                        countryDetector.cacheCountry(countryCode)
                        Log.i("PrayerSync", "Country=$countryCode Provider=SunCalc Source=OfflineFallback Approximate=true")
                        return@withContext Result.success(Unit)
                    }
                }
                DebugLogger.error(
                    LogCategory.SYNC,
                    Instrumentation.line("sync", Instrumentation.NO_TRACE, cityName, ErrorCode.FETCH_NO_DATA.prefix("No internet and no offline data"))
                )
                return@withContext Result.failure(IllegalStateException("No internet and no offline data"))
            }

            val request = PrayerTimeRequest(
                lat = lat,
                lon = lon,
                dateRange = dateRange,
                cityName = cityName,
                countryCode = countryCode
            )

            val result = provider.fetchSchedule(context, request)

            result.fold(
                onSuccess = { schedule ->
                    saveSchedule(context, providerId, schedule)
                    PrayerTimesCacheStore.setActiveProvider(context, providerId)
                    countryDetector.cacheCountry(countryCode)
                    PrayerTimesCacheStore.pruneOldMonths(context, LocalDate.now().minusMonths(6).withDayOfMonth(1))
                    Log.i("PrayerSync", "Country=$countryCode Provider=$providerId Source=Network Days=${schedule.size}")
                },
                onFailure = { e ->
                    val cachedAny = PrayerTimesCacheStore.getAnyAvailablePrayerTimesForDate(context, LocalDate.now())
                    if (cachedAny != null) {
                        Log.i("PrayerSync", "Country=$countryCode Provider=$providerId Source=Cache (fetch failed)")
                        return@withContext Result.success(Unit)
                    }
                    Log.e("PrayerSync", "Country=$countryCode Provider=$providerId Source=Error Error=${e.message}")
                    return@withContext Result.failure(e)
                }
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PRAYER", "fetchSchedule failed", e)
            DebugLogger.error(
                LogCategory.SYNC,
                Instrumentation.line("sync", Instrumentation.NO_TRACE, cityName, ErrorCode.FETCH_SCHEDULE_FAILED.prefix("fetchSchedule failed")),
                e
            )
            Result.failure(e)
        }
    }

    fun getCachedPrayerTimesForToday(
        latitude: Double,
        longitude: Double,
        cityName: String,
        context: Context
    ): PrayerTimes? {
        val today = LocalDate.now()
        val activeProvider = PrayerTimesCacheStore.getActiveProvider(context)

        // 1. Primary: active provider's cached city
        val providerKey = when (activeProvider) {
            ProviderIds.HABOUS -> {
                val habousProvider = providerSelector.selectProvider("MA") as? HabousProvider
                if (habousProvider != null) {
                    val city = habousProvider.resolveOfficialCity(latitude, longitude, cityName)
                    PrayerTimesCacheStore.getPrayerTimesForDate(context, city.habousId, today)
                } else null
            }
            else -> {
                PrayerTimesCacheStore.getPrayerTimesForDate(context, today)
            }
        }
        if (providerKey != null) return providerKey

        // 2. Fallback: any cached city
        val any = PrayerTimesCacheStore.getAnyAvailablePrayerTimesForDate(context, today)
        if (any != null) return any

        // 3. Default fallback: Casablanca seasonal
        return getDefaultCasablancaPrayerTimes(today)
    }

    private fun saveSchedule(
        context: Context,
        providerId: String,
        schedule: Map<LocalDate, PrayerTimes>
    ) {
        schedule.entries
            .groupBy { YearMonth.from(it.key) }
            .forEach { (yearMonth, entries) ->
                PrayerTimesCacheStore.saveMonthSchedule(
                    context = context,
                    providerId = providerId,
                    year = yearMonth.year,
                    month = yearMonth.monthValue,
                    days = entries.associate { it.key.dayOfMonth to it.value },
                    updateActiveCity = true
                )
            }
    }

    private fun hasCompleteCachedRange(
        context: Context,
        providerId: String,
        dateRange: ClosedRange<LocalDate>
    ): Boolean {
        val months = PrayerTimeUtils.getMonthsInRange(dateRange.start, dateRange.endInclusive)
        return months.all { (year, month) ->
            PrayerTimesCacheStore.hasCompleteMonthSchedule(
                context, providerId, year, month
            )
        }
    }

    private fun loadOfflineFallback(context: Context): Boolean {
        return try {
            val jsonString = context.assets.open("prayer_times_offline.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonString)
            val currentYear = java.time.Year.now().value
            var loaded = false
            val schedulesByCity = mutableMapOf<String, MutableMap<YearMonth, MutableMap<Int, PrayerTimes>>>()
            val cityIds = root.keys()
            while (cityIds.hasNext()) {
                val cityKey = cityIds.next()
                val cityData = root.optJSONObject(cityKey) ?: continue
                val yearData = cityData.optJSONObject(currentYear.toString()) ?: continue
                val cityMonths = mutableMapOf<YearMonth, MutableMap<Int, PrayerTimes>>()
                val months = yearData.keys()
                while (months.hasNext()) {
                    val monthKey = months.next()
                    val monthData = yearData.optJSONObject(monthKey) ?: continue
                    val month = monthKey.toIntOrNull() ?: continue
                    val days = mutableMapOf<Int, PrayerTimes>()
                    val dayKeys = monthData.keys()
                    while (dayKeys.hasNext()) {
                        val dayStr = dayKeys.next()
                        val dayData = monthData.optJSONObject(dayStr) ?: continue
                        val day = dayStr.toIntOrNull() ?: continue
                        days[day] = PrayerTimes(
                            fajr = dayData.optString("fajr", "--:--"),
                            shuruq = dayData.optString("sunrise", "--:--"),
                            dhuhr = dayData.optString("dohr", "--:--"),
                            asr = dayData.optString("asr", "--:--"),
                            maghrib = dayData.optString("maghreb", "--:--"),
                            isha = dayData.optString("ichaa", "--:--")
                        )
                    }
                    if (days.isNotEmpty()) {
                        cityMonths[YearMonth.of(currentYear, month)] = days
                        loaded = true
                    }
                }
                if (cityMonths.isNotEmpty()) {
                    schedulesByCity[cityKey] = cityMonths
                }
            }
            if (loaded) {
                // Build the whole cache structure in memory and write it once,
                // instead of one read-modify-write per city-month (which took
                // minutes on device for a large offline seed).
                PrayerTimesCacheStore.saveMonthSchedulesBulk(
                    context = context,
                    providerId = ProviderIds.HABOUS,
                    schedulesByCity = schedulesByCity
                )
                // If no city_id has been set yet, initialize it to the first available
                // city from the offline JSON so that getIdentifierForKey("habous")
                // returns a valid identifier for indexed cache lookups.
                val existingCityId = PrayerTimesCacheStore.getCachedCityId(context)
                if (existingCityId == null) {
                    val firstCityKey = try { root.keys().next() } catch (_: Exception) { null }
                    val firstCityId = firstCityKey?.toIntOrNull()
                    if (firstCityId != null) {
                        PrayerTimesCacheStore.setActiveCity(
                            context = context,
                            cityId = firstCityId,
                            cityName = "المغرب",
                            cityUniqueId = firstCityId
                        )
                    }
                }
                PrayerTimesCacheStore.setActiveProvider(context, ProviderIds.HABOUS)
            }
            loaded
        } catch (e: Exception) {
            Log.w("PRAYER", "Failed to load offline fallback", e)
            DebugLogger.warning(
                LogCategory.SYNC,
                Instrumentation.line("sync", Instrumentation.NO_TRACE, null, ErrorCode.OFFLINE_FALLBACK_FAILED.prefix("Failed to load offline fallback")),
                e
            )
            false
        }
    }

    private fun calculateSunPositionSchedule(
        lat: Double, lon: Double, dateRange: ClosedRange<LocalDate>
    ): Map<LocalDate, PrayerTimes> {
        val result = mutableMapOf<LocalDate, PrayerTimes>()
        var date = dateRange.start
        while (date <= dateRange.endInclusive) {
            result[date] = SunPositionCalculator.calculate(lat, lon, date)
            date = date.plusDays(1)
        }
        return result
    }

    fun hasInternetConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
