package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class MainUiState(
    val isLoading: Boolean = true,
    val prayerTimes: PrayerTimes? = null,
    val error: String? = null,
    val dateGregorian: String = "",
    val nextPrayerName: String = "",
    val nextPrayerTimeFormat: String = "",
    val remainingSeconds: Long = 0L,
    val cityName: String = "جاري تحديد الموقع...",
    val latitude: Double = 33.5731,
    val longitude: Double = -7.5898,
    val language: AppLanguage = AppLanguage.AR,
    val hasFallbackData: Boolean = false
)

class MainViewModel(
    private val repository: PrayerTimesRepository = PrayerTimesRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        updateDate()
    }

    fun loadSettings(context: Context) {
        Log.d("STARTUP_TRACE", "LOAD_SETTINGS_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "LOAD_SETTINGS_START")
        loadLanguage(context)
        loadLocation(context)
        Log.d("STARTUP_TRACE", "LOAD_SETTINGS_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "LOAD_SETTINGS_END")
    }

    private fun loadLocation(context: Context) {
        Log.d("STARTUP_TRACE", "LOAD_LOCATION_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "LOAD_LOCATION_START")
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("last_lat", 33.5731f).toDouble()
        val lon = prefs.getFloat("last_lon", -7.5898f).toDouble()
        val city = prefs.getString("last_city", "الدار البيضاء") ?: "الدار البيضاء"
        
        Log.d("STARTUP_TRACE", "LOAD_LOCATION_AFTER_PREFS_READ")
        DebugLogger.debug(LogCategory.PERFORMANCE, "LOAD_LOCATION_AFTER_PREFS_READ — lat=$lat lon=$lon city=$city")
        
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[latitude, longitude, cityName]")
        _uiState.update { it.copy(
            latitude = lat,
            longitude = lon,
            cityName = city
        ) }
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")
        
        fetchPrayerTimes(lat, lon, city, context)
        Log.d("STARTUP_TRACE", "LOAD_LOCATION_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "LOAD_LOCATION_END")
    }

    fun loadLanguage(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "ar") ?: "ar"
        val cachedLang = AppLanguage.fromCode(langCode)
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[language]")
        _uiState.update { it.copy(language = cachedLang) }
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")
        updateDate()
        _uiState.value.prayerTimes?.let { calculateNextPrayer(it) }
    }

    fun setLanguage(language: AppLanguage, context: Context) {
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[language]")
        _uiState.update { it.copy(language = language) }
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", language.code).apply()
        updateDate()
        _uiState.value.prayerTimes?.let { calculateNextPrayer(it) }
    }

    private fun updateDate() {
        val today = LocalDate.now()
        val locale = java.util.Locale(_uiState.value.language.code)
        val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", locale)
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[dateGregorian]")
        _uiState.update { it.copy(dateGregorian = today.format(formatter)) }
        Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")
    }

    fun fetchPrayerTimes(
        latitude: Double = _uiState.value.latitude,
        longitude: Double = _uiState.value.longitude,
        cityName: String = _uiState.value.cityName,
        context: Context? = null
    ) {
        Log.d("STARTUP_TRACE", "FETCH_PRAYERTIMES_CALLED")
        DebugLogger.debug(LogCategory.PERFORMANCE, "FETCH_PRAYERTIMES_CALLED — lat=$latitude lon=$longitude city=$cityName")
        Log.d("STARTUP_TRACE", "FETCH_PRAYERTIMES_BEFORE_LAUNCH")
        DebugLogger.debug(LogCategory.PERFORMANCE, "FETCH_PRAYERTIMES_BEFORE_LAUNCH")
        viewModelScope.launch {
            Log.d("STARTUP_TRACE", "FETCH_PRAYERTIMES_LAUNCH_STARTED")
            DebugLogger.debug(LogCategory.PERFORMANCE, "FETCH_PRAYERTIMES_LAUNCH_STARTED")
            Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
            DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[isLoading, error, cityName, latitude, longitude]")
            _uiState.update { it.copy(isLoading = true, error = null, cityName = cityName, latitude = latitude, longitude = longitude) }
            Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
            DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")

            context?.let { ctx ->
                val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putFloat("last_lat", latitude.toFloat())
                    putFloat("last_lon", longitude.toFloat())
                    putString("last_city", cityName)
                    apply()
                }

                Log.d("STARTUP_TRACE", "CACHE_LOOKUP_START")
                DebugLogger.debug(LogCategory.PERFORMANCE, "CACHE_LOOKUP_START — city=$cityName lat=$latitude lon=$longitude")
                val times = repository.getCachedPrayerTimesForToday(latitude, longitude, cityName, ctx)
                Log.d("STARTUP_TRACE", "CACHE_LOOKUP_END")
                DebugLogger.debug(LogCategory.PERFORMANCE, "CACHE_LOOKUP_END — hit=${times != null} city=$cityName")
                if (times != null) {
                    Log.d("STARTUP_TRACE", "UISTATE prayerTimes assigned=true")
                    Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[isLoading, prayerTimes, error, hasFallbackData](hit)")
                    _uiState.update { it.copy(isLoading = false, prayerTimes = times, error = null, hasFallbackData = false, latitude = latitude, longitude = longitude) }
                    Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")
                    Log.d("STARTUP_TRACE", "CALCULATE_NEXT_PRAYER_START")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "CALCULATE_NEXT_PRAYER_START")
                    calculateNextPrayer(times)
                    Log.d("STARTUP_TRACE", "CALCULATE_NEXT_PRAYER_END")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "CALCULATE_NEXT_PRAYER_END")
                    Log.d("STARTUP_TRACE", "PREFILL_CONFIG")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "PREFILL_CONFIG — seeding PrayerStateMachine early")
                    PrayerStateMachine.configure(times, ctx)
                    Log.d("STARTUP_TRACE", "PREFILL_CONFIG_DONE")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "PREFILL_CONFIG_DONE — PrayerStateMachine seeded early")
                } else {
                    Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[isLoading, error, prayerTimes(fallback), hasFallbackData](miss)")
                    _uiState.update { it.copy(isLoading = false, error = null, prayerTimes = it.prayerTimes ?: PrayerTimes("04:30", "06:00", "13:30", "17:00", "20:30", "22:00"), hasFallbackData = true, latitude = latitude, longitude = longitude) }
                    Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")
                    _uiState.value.prayerTimes?.let {
                        Log.d("STARTUP_TRACE", "CALCULATE_NEXT_PRAYER_START")
                        DebugLogger.debug(LogCategory.PERFORMANCE, "CALCULATE_NEXT_PRAYER_START")
                        calculateNextPrayer(it)
                        Log.d("STARTUP_TRACE", "CALCULATE_NEXT_PRAYER_END")
                        DebugLogger.debug(LogCategory.PERFORMANCE, "CALCULATE_NEXT_PRAYER_END")
                    }
                }

                repository.syncUpcomingMonths(context = ctx, latitude = latitude, longitude = longitude, cityName = cityName, forceRefresh = false).fold(
                    onSuccess = {
                        val updated = repository.getCachedPrayerTimesForToday(latitude, longitude, cityName, ctx)
                        if (updated != null) {
                            Log.d("STARTUP_TRACE", "UISTATE_UPDATE_START")
                            DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_START — fields=[prayerTimes, hasFallbackData](post-sync)")
                            _uiState.update { it.copy(prayerTimes = updated, hasFallbackData = false) }
                            Log.d("STARTUP_TRACE", "UISTATE_UPDATE_END")
                            DebugLogger.debug(LogCategory.PERFORMANCE, "UISTATE_UPDATE_END")
                            Log.d("STARTUP_TRACE", "CALCULATE_NEXT_PRAYER_START")
                            DebugLogger.debug(LogCategory.PERFORMANCE, "CALCULATE_NEXT_PRAYER_START")
                            calculateNextPrayer(updated)
                            Log.d("STARTUP_TRACE", "CALCULATE_NEXT_PRAYER_END")
                            DebugLogger.debug(LogCategory.PERFORMANCE, "CALCULATE_NEXT_PRAYER_END")
                        }
                    },
                    onFailure = { }
                )
            }
        }
    }

    fun updateWidgetCache(context: Context) {
        val state = _uiState.value
        val resolvedCity = repository.resolveOfficialCity(state.latitude, state.longitude, state.cityName)
        val oldCityId = PrayerTimesCacheStore.getCachedCityId(context)
        val oldCityName = PrayerTimesCacheStore.getCachedCityName(context)
        DebugLogger.debug(LogCategory.CACHE, "updateWidgetCache() — old cityId=$oldCityId oldName=$oldCityName new habousId=${resolvedCity.habousId} newName=${resolvedCity.name} lat=${state.latitude} lon=${state.longitude}")
        PrayerTimesCacheStore.setActiveCity(context, resolvedCity.habousId, resolvedCity.name)
        DebugLogger.debug(LogCategory.CACHE, "updateWidgetCache() EXIT — city updated")
    }

    private fun calculateNextPrayer(times: PrayerTimes) {
        val now = LocalTime.now()

        val parsedTimes = listOf(
            "fajr" to parseTimeSafely(times.fajr),
            "dhuhr" to parseTimeSafely(times.dhuhr),
            "asr" to parseTimeSafely(times.asr),
            "maghrib" to parseTimeSafely(times.maghrib),
            "isha" to parseTimeSafely(times.isha)
        )

        var nextPrayer: Pair<String, LocalTime?>? = null
        for (pt in parsedTimes) {
            if (pt.second != null && now.isBefore(pt.second)) {
                nextPrayer = pt
                break
            }
        }

        if (nextPrayer == null) {
            nextPrayer = parsedTimes.first()
            val secondsUntilMidnight = ChronoUnit.SECONDS.between(now, LocalTime.MAX)
            val secondsFromMidnightToFajr = nextPrayer.second?.toSecondOfDay()?.toLong() ?: 0L
            val newRemaining = secondsUntilMidnight + secondsFromMidnightToFajr
            android.util.Log.d("UISTATE_WRITE", "calculateNextPrayer(wrapped) remaining=$newRemaining now=$now\n${Throwable().stackTraceToString()}")
            _uiState.update {
                it.copy(
                    nextPrayerName = nextPrayer.first,
                    nextPrayerTimeFormat = nextPrayer.second?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",
                    remainingSeconds = newRemaining
                )
            }
        } else {
            val secondsRemaining = ChronoUnit.SECONDS.between(now, nextPrayer.second)
            android.util.Log.d("UISTATE_WRITE", "calculateNextPrayer(unwrapped) remaining=$secondsRemaining now=$now\n${Throwable().stackTraceToString()}")
            _uiState.update {
                it.copy(
                    nextPrayerName = nextPrayer.first,
                    nextPrayerTimeFormat = nextPrayer.second?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",
                    remainingSeconds = secondsRemaining
                )
            }
        }
    }
    
    private fun parseTimeSafely(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr.replace(" ", ""))
        } catch (e: Exception) {
            null
        }
    }
}
