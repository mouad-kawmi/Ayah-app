package com.example.quranapp.presentation.prayer

import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.data.prayer.AlAdhanProvider
import com.example.quranapp.data.prayer.CountryDetector
import com.example.quranapp.data.prayer.HabousProvider
import com.example.quranapp.data.prayer.LocationProvider
import com.example.quranapp.data.prayer.OfficialPrayerCity
import com.example.quranapp.data.prayer.PrayerAlarmScheduler
import com.example.quranapp.data.prayer.PrayerStateMachine
import com.example.quranapp.data.prayer.PrayerTimesCacheStore
import com.example.quranapp.data.prayer.PrayerTimesRepository
import com.example.quranapp.data.prayer.PrayerTimesSyncScheduler
import com.example.quranapp.data.prayer.ProviderIds
import com.example.quranapp.data.prayer.ProviderSelector
import com.example.quranapp.data.prayer.officialPrayerCities
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.presentation.widget.PrayerWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class LocationState {
    data object Unknown : LocationState()
    data object Locating : LocationState()
    data object PermissionDenied : LocationState()
    data object LocationDisabled : LocationState()
    data object Located : LocationState()
}

data class PrayerTimesUiState(
    val cityName: String = "",
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val lastUpdated: String? = null,
    val locationState: LocationState = LocationState.Unknown,
    val showCityPicker: Boolean = false,
    val showLocationDialog: Boolean = false,
    val isMorocco: Boolean = true,
    val isApproximate: Boolean = false
)

class PrayerTimesViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val habousProvider = HabousProvider()
    private val alAdhanProvider = AlAdhanProvider()
    private val providerSelector = ProviderSelector(habousProvider, alAdhanProvider)
    private val countryDetector = CountryDetector(context)
    private val repository = PrayerTimesRepository(context, providerSelector, countryDetector)
    private val locationProvider = LocationProvider(context)

    private val _uiState = MutableStateFlow(PrayerTimesUiState())
    val uiState: StateFlow<PrayerTimesUiState> = _uiState

    private val _hasCachedData = MutableStateFlow(false)
    val hasCachedData: StateFlow<Boolean> = _hasCachedData

    private fun logUiState(tag: String) {
        val s = _uiState.value
        Log.d("PRAYER_TRACE", "$tag cityName=${s.cityName} showDialog=${s.showLocationDialog} locState=${s.locationState::class.simpleName} isSyncing=${s.isSyncing} isLoading=${s.isLoading}")
    }

    private fun updateApproximateFlag() {
        val provider = PrayerTimesCacheStore.getActiveProvider(context)
        _uiState.update { it.copy(isApproximate = provider == ProviderIds.SUNCALC) }
    }

    val availableCities: List<String> by lazy {
        officialPrayerCities.map { it.name }.distinct()
    }

    init {
        PrayerStateMachine.init(context)
        PrayerTimesSyncScheduler.ensureScheduled(context)
        loadFromCache()
        logUiState("after-loadFromCache")
        resolveLocationAndSync(showDialogOnDisabled = false)
        logUiState("after-resolveLocationAndSync")
    }

    private fun loadFromCache() {
        val activeProvider = PrayerTimesCacheStore.getActiveProvider(context)
        val today = LocalDate.now()

        if (activeProvider == ProviderIds.HABOUS) {
            val cachedUniqueId = PrayerTimesCacheStore.getCachedCityUniqueId(context)
            val cachedName = PrayerTimesCacheStore.getCachedCityName(context)

            val resolvedCity: OfficialPrayerCity? = when {
                cachedUniqueId != null -> {
                    habousProvider.resolveOfficialCityById(cachedUniqueId)
                        ?: if (cachedName != null) habousProvider.resolveOfficialCity(getSavedLat(), getSavedLon(), cachedName)
                        else null
                }
                cachedName != null -> {
                    habousProvider.resolveOfficialCity(getSavedLat(), getSavedLon(), cachedName)
                }
                else -> null
            }

            val displayName = resolvedCity?.name
                ?: habousProvider.resolveOfficialCity(33.5731, -7.5898, "الدار البيضاء").name

            PrayerStateMachine.loadToday(context)
            _hasCachedData.value = true
            _uiState.update {
                it.copy(
                    cityName = displayName,
                    isLoading = false
                )
            }
        } else {
            val savedLat = getSavedLat()
            val savedLon = getSavedLon()
            val cachedCity = PrayerTimesCacheStore.getCachedCityName(context)
            val displayCity = cachedCity ?: "موقعي الحالي"
            PrayerStateMachine.loadToday(context)
            _hasCachedData.value = true
            _uiState.update {
                it.copy(
                    cityName = displayCity,
                    isLoading = false
                )
            }
        }
        updateApproximateFlag()
    }

    fun onLocationPermissionGranted() {
        if (_uiState.value.locationState != LocationState.PermissionDenied) return
        resolveLocationAndSync()
    }

    fun retryLocationResolution() {
        resolveLocationAndSync()
    }

    fun dismissLocationDialog() {
        _uiState.update { it.copy(showLocationDialog = false) }
        logUiState("dismissLocationDialog")
    }

    fun refresh() {
        Log.i("PrayerSync", "ViewModel refresh triggered")
        resolveLocationAndSync(showDialogOnDisabled = true)
    }

    private fun resolveLocationAndSync(showDialogOnDisabled: Boolean = false) {
        _uiState.update { it.copy(locationState = LocationState.Locating) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!locationProvider.hasLocationPermission()) {
                _uiState.update { it.copy(locationState = LocationState.PermissionDenied, isSyncing = false) }
                trySyncLastCity()
                return@launch
            }

            if (!locationProvider.isLocationEnabled()) {
                val hasSavedCity = PrayerTimesCacheStore.getCachedCityId(context) != null
                if (showDialogOnDisabled || !hasSavedCity) {
                    _uiState.update {
                        it.copy(
                            locationState = LocationState.LocationDisabled,
                            showLocationDialog = true,
                            isSyncing = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            locationState = LocationState.LocationDisabled,
                            isSyncing = false
                        )
                    }
                }
                trySyncLastCity()
                return@launch
            }

            val location = locationProvider.getCurrentLocation()
            if (location == null) {
                _uiState.update { it.copy(locationState = LocationState.Located, isSyncing = false) }
                trySyncLastCity()
                return@launch
            }

            val (lat, lon) = location
            val countryCode = countryDetector.detectCountry(lat, lon)
            Log.i("PrayerSync", "ViewModel flow lat=$lat lon=$lon country=$countryCode")
            val isMorocco = countryCode == "MA"
            if (isMorocco) {
                val resolvedCity = habousProvider.resolveOfficialCity(lat, lon, "")
                val cachedCityUniqueId = PrayerTimesCacheStore.getCachedCityUniqueId(context)
                val cityChanged = cachedCityUniqueId == null || cachedCityUniqueId != resolvedCity.id
                if (cityChanged) {
                    PrayerTimesCacheStore.setActiveCity(
                        context = context,
                        cityId = resolvedCity.habousId,
                        cityName = resolvedCity.name,
                        cityUniqueId = resolvedCity.id,
                        latitude = lat,
                        longitude = lon
                    )
                    loadFromCache()
                    PrayerWidgetProvider.requestWidgetRefresh(context)
                    _uiState.update {
                        it.copy(
                            locationState = LocationState.Located
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            cityName = resolvedCity.name,
                            locationState = LocationState.Located
                        )
                    }
                }
                saveLocationPrefs(lat, lon)
                _uiState.update { it.copy(isMorocco = true) }
                doSync(lat, lon, resolvedCity.name)
            } else {
                val displayCity = geocoderCityName(lat, lon) ?: "موقعي الحالي"
                PrayerTimesCacheStore.setActiveCity(
                    context = context,
                    cityId = 0,
                    cityName = displayCity,
                    cityUniqueId = 0,
                    latitude = lat,
                    longitude = lon
                )
                _uiState.update {
                    it.copy(
                        cityName = displayCity,
                        locationState = LocationState.Located,
                        isMorocco = false
                    )
                }
                PrayerWidgetProvider.requestWidgetRefresh(context)
                saveLocationPrefs(lat, lon)
                doSync(lat, lon, displayCity)
            }
            countryDetector.cacheCountry(countryCode)
        }
    }

    fun showCityPicker() {
        _uiState.update { it.copy(showCityPicker = true) }
    }

    fun hideCityPicker() {
        _uiState.update { it.copy(showCityPicker = false) }
    }

    fun selectCity(cityName: String) {
        hideCityPicker()
        viewModelScope.launch(Dispatchers.IO) {
            val city = habousProvider.resolveOfficialCity(getSavedLat(), getSavedLon(), cityName)
            val cachedCityUniqueId = PrayerTimesCacheStore.getCachedCityUniqueId(context)
            val cityChanged = cachedCityUniqueId == null || cachedCityUniqueId != city.id

            _uiState.update { it.copy(cityName = city.name) }

            if (cityChanged) {
                PrayerTimesCacheStore.setActiveCity(
                    context = context,
                    cityId = city.habousId,
                    cityName = city.name,
                    cityUniqueId = city.id
                )
                loadFromCache()
                PrayerWidgetProvider.requestWidgetRefresh(context)
            }
            doSync(city.lat, city.lon, city.name)
        }
    }

    private fun trySyncLastCity() {
        val activeProvider = PrayerTimesCacheStore.getActiveProvider(context)
        if (activeProvider != null && activeProvider != ProviderIds.HABOUS) {
            val savedLat = getSavedLat()
            val savedLon = getSavedLon()
            if (savedLat != 0.0 || savedLon != 0.0) {
                val cachedCity = PrayerTimesCacheStore.getCachedCityName(context)
                doSync(savedLat, savedLon, cachedCity ?: "")
            }
            return
        }
        val cachedCityId = PrayerTimesCacheStore.getCachedCityId(context)
        val cachedCityName = PrayerTimesCacheStore.getCachedCityName(context)
        val cachedCityUniqueId = PrayerTimesCacheStore.getCachedCityUniqueId(context)
        if (cachedCityId != null && cachedCityName != null) {
            val hasMonth = PrayerTimesCacheStore.hasMonthSchedule(
                context, cachedCityId,
                LocalDate.now().year, LocalDate.now().monthValue
            )
            if (!hasMonth) {
                val city = if (cachedCityUniqueId != null) {
                    habousProvider.resolveOfficialCityById(cachedCityUniqueId)
                        ?: habousProvider.resolveOfficialCity(getSavedLat(), getSavedLon(), cachedCityName)
                } else {
                    habousProvider.resolveOfficialCity(getSavedLat(), getSavedLon(), cachedCityName)
                }
                if (city != null) doSync(city.lat, city.lon, city.name)
            }
        } else {
            Log.d("PRAYER_LOCATION", "No cached city available — syncing Casablanca as default")
            val defaultCity = habousProvider.resolveOfficialCity(33.5731, -7.5898, "الدار البيضاء")
            doSync(defaultCity.lat, defaultCity.lon, defaultCity.name)
        }
    }

    private fun doSync(lat: Double, lon: Double, cityName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncing = true, error = null) }

            val today = LocalDate.now()
            val endDate = today.plusDays(60)
            val dateRange = today..endDate

            val result = repository.fetchSchedule(
                lat = lat,
                lon = lon,
                cityName = cityName,
                dateRange = dateRange,
                forceRefresh = false
            )

            result.onSuccess {
                _uiState.update {
                    it.copy(
                        lastUpdated = today.toString()
                    )
                }
                loadFromCache()
                updateApproximateFlag()
                PrayerAlarmScheduler.scheduleUpcomingAlarms(context)
                PrayerWidgetProvider.requestWidgetRefresh(context)
            }.onFailure { e ->
                if (!_hasCachedData.value) {
                    _uiState.update {
                        it.copy(
                            error = "تعذر تحميل مواقيت الصلاة: ${e.message}"
                        )
                    }
                }
            }
            _uiState.update { it.copy(isSyncing = false) }
            Log.i("PrayerSync", "ViewModel doSync complete lat=$lat lon=$lon city=$cityName isApproximate=${_uiState.value.isApproximate} prayerCards=${PrayerStateMachine.prayerCards.value.size}")
        }
    }

    private fun geocoderCityName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, java.util.Locale.ENGLISH)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { addr ->
                addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.featureName
            }
        } catch (e: Exception) {
            Log.w("PrayerSync", "Geocoder city lookup failed", e)
            null
        }
    }

    private fun saveLocationPrefs(lat: Double, lon: Double) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putFloat("last_lat", lat.toFloat())
            .putFloat("last_lon", lon.toFloat())
            .putString("last_city", _uiState.value.cityName)
            .apply()
    }

    private fun getSavedLat(): Double {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getFloat("last_lat", 0f).toDouble()
    }

    private fun getSavedLon(): Double {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getFloat("last_lon", 0f).toDouble()
    }

    fun togglePrayerEnabled(name: String) {
        val key = PrayerStateMachine.prayerKeyForName(name) ?: return
        val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val enabled = !prefs.getBoolean("enable_$key", true)
        prefs.edit().putBoolean("enable_$key", enabled).apply()
        DebugLogger.info(
            LogCategory.PRAYER,
            Instrumentation.line(
                key, Instrumentation.NO_TRACE, null,
                if (enabled) "Prayer enabled by user" else "Prayer disabled by user"
            )
        )
        PrayerStateMachine.refreshPrayerCards(context)
        viewModelScope.launch(Dispatchers.IO) {
            PrayerAlarmScheduler.scheduleUpcomingAlarms(context)
        }
    }
}
