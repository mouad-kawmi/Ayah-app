package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import com.example.debug.PerformanceProfiler
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val geocoderCityName: String? // User-friendly UI display name only
)

/**
 * Handles interactions with FusedLocationProviderClient.
 * Exclusively responsible for obtaining raw coordinates safely.
 */
open class LocationService(context: Context) {

    // Store applicationContext to completely eliminate Activity leak risks
    private val appContext: Context = context.applicationContext

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    companion object {
        private const val TAG = "LocationService"
    }

    /**
     * Gets the location asynchronously. 
     * Safe to call on Main Thread since internal implementations handle IO bridging.
     */
    @SuppressLint("MissingPermission")
    open suspend fun getCurrentLocation(
        forceRefresh: Boolean = false,
        priority: Int = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        maxStaleAgeMs: Long = 30 * 60 * 1000,
        timeoutMs: Long = 10000L
    ): LocationResult? {
        val overallStart = System.currentTimeMillis()
        DebugLogger.info(LogCategory.LOCATION, "getCurrentLocation() started — forceRefresh=$forceRefresh priority=$priority")
        
        if (!LocationManager.isLocationEnabled(appContext)) {
            Log.d(TAG, "GPS is disabled.")
            DebugLogger.warning(LogCategory.GPS, "GPS is disabled — aborting location request")
            DebugLogger.info(LogCategory.LOCATION, "Returning null (GPS disabled)")
            PerformanceProfiler.logLocationOperation("GPS Disabled", System.currentTimeMillis() - overallStart)
            return null
        }

        try {
            // 1. Efficiency first: Try cached location if we don't strictly force it
            if (!forceRefresh) {
                val cachedCheckStart = System.currentTimeMillis()
                DebugLogger.info(LogCategory.LOCATION, "About to call fusedLocationClient.lastLocation.await() (cached check)")
                val lastLocation: Location? = fusedLocationClient.lastLocation.await()
                DebugLogger.info(LogCategory.LOCATION, "After fusedLocationClient.lastLocation.await() — lastLocation == null? ${lastLocation == null}")
                if (lastLocation != null) {
                    val now = System.currentTimeMillis()
                    val locationTime = lastLocation.time
                    val elapsedRealtimeAge = now - locationTime
                    DebugLogger.info(
                        LogCategory.LOCATION,
                        "Cached location details — latitude=${lastLocation.latitude} longitude=${lastLocation.longitude} accuracy=${lastLocation.accuracy} provider=${lastLocation.provider} elapsedRealtimeAge=${elapsedRealtimeAge}ms"
                    )
                    if ((now - locationTime) < maxStaleAgeMs) {
                        Log.d(TAG, "Using fresh lastLocation.")
                        DebugLogger.info(
                            LogCategory.LOCATION,
                            "Coordinates received (cached) — accuracy=${lastLocation.accuracy}m age=${now - locationTime}ms"
                        )
                        DebugLogger.info(LogCategory.LOCATION, "Returning cached location")
                        PerformanceProfiler.logLocationOperation("Return Cached Location", System.currentTimeMillis() - cachedCheckStart)
                        return mapToLocationResult(lastLocation)
                    }
                }
            }

            Log.d(TAG, "Requesting current location (GPS) with priority $priority...")
            DebugLogger.debug(LogCategory.LOCATION, "FusedLocationProvider request — priority=$priority forceRefresh=$forceRefresh")

            // 2. Fetch fresh fix with strict timeout and Cancellation propagation
            val freshLocationStart = System.currentTimeMillis()
            val cancellationTokenSource = CancellationTokenSource()
            DebugLogger.info(LogCategory.LOCATION, "About to call fusedLocationClient.getCurrentLocation().await() inside withTimeoutOrNull($timeoutMs ms)")
            val freshLocation = withTimeoutOrNull(timeoutMs) {
                try {
                    DebugLogger.info(LogCategory.LOCATION, "Inside withTimeoutOrNull — calling fusedLocationClient.getCurrentLocation() now")
                    val result = fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token).await()
                    DebugLogger.info(LogCategory.LOCATION, "After fusedLocationClient.getCurrentLocation().await() — result == null? ${result == null}")
                    result
                } catch (e: CancellationException) {
                    // Rethrow so coroutine cancellation propagates correctly — do NOT log as ERROR
                    throw e
                } catch (e: Exception) {
                    DebugLogger.error(LogCategory.LOCATION, "FailureListener: Exception caught inside withTimeoutOrNull", e)
                    null
                }
            }

            if (freshLocation != null) {
                val now = System.currentTimeMillis()
                val elapsedRealtimeAge = now - freshLocation.time
                DebugLogger.info(
                    LogCategory.LOCATION,
                    "Fresh location details — latitude=${freshLocation.latitude} longitude=${freshLocation.longitude} accuracy=${freshLocation.accuracy} provider=${freshLocation.provider} elapsedRealtimeAge=${elapsedRealtimeAge}ms"
                )
                DebugLogger.info(
                    LogCategory.LOCATION,
                    "Coordinates received (fresh GPS) — accuracy=${freshLocation.accuracy}m"
                )
                DebugLogger.info(LogCategory.LOCATION, "Returning fresh location")
                PerformanceProfiler.logLocationOperation("Return Fresh Location", System.currentTimeMillis() - freshLocationStart)
                return mapToLocationResult(freshLocation)
            } else {
                Log.d(TAG, "Fresh location fetch timed out or returned null. Cancelling request.")
                DebugLogger.warning(LogCategory.LOCATION, "getCurrentLocation timed out (${timeoutMs}ms) or returned null")
                DebugLogger.warning(LogCategory.LOCATION, "Fresh location fetch timed out (${timeoutMs}ms) — cancelling request")
                cancellationTokenSource.cancel()
                DebugLogger.info(LogCategory.LOCATION, "CancellationListener: Request cancelled")
                PerformanceProfiler.logLocationOperation("Fresh Location Timed Out", System.currentTimeMillis() - freshLocationStart)
            }

            // 3. Absolute fallback (timeout on fresh attempt, but stale cache available)
            val staleFallbackStart = System.currentTimeMillis()
            DebugLogger.info(LogCategory.LOCATION, "About to call fusedLocationClient.lastLocation.await() (stale fallback)")
            val staleLastLocation = fusedLocationClient.lastLocation.await()
            DebugLogger.info(LogCategory.LOCATION, "After fusedLocationClient.lastLocation.await() — staleLastLocation == null? ${staleLastLocation == null}")
            if (staleLastLocation != null) {
                val now = System.currentTimeMillis()
                val elapsedRealtimeAge = now - staleLastLocation.time
                DebugLogger.info(
                    LogCategory.LOCATION,
                    "Stale fallback location details — latitude=${staleLastLocation.latitude} longitude=${staleLastLocation.longitude} accuracy=${staleLastLocation.accuracy} provider=${staleLastLocation.provider} elapsedRealtimeAge=${elapsedRealtimeAge}ms"
                )
                Log.d(TAG, "Fallback to stale lastLocation.")
                DebugLogger.info(
                    LogCategory.LOCATION,
                    "Coordinates received (stale fallback) — accuracy=${staleLastLocation.accuracy}m"
                )
                DebugLogger.info(LogCategory.LOCATION, "Returning cached location")
                PerformanceProfiler.logLocationOperation("Return Stale Fallback Location", System.currentTimeMillis() - staleFallbackStart)
                return mapToLocationResult(staleLastLocation)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Location failure: ${e.message}", e)
            DebugLogger.error(LogCategory.LOCATION, "FailureListener: Exception caught in outer try-catch", e)
            PerformanceProfiler.logLocationOperation("Location Failed", System.currentTimeMillis() - overallStart)
        }

        DebugLogger.info(LogCategory.LOCATION, "Returning null")
        PerformanceProfiler.logLocationOperation("Location Returned Null", System.currentTimeMillis() - overallStart)
        return null
    }

    private suspend fun mapToLocationResult(location: Location): LocationResult {
        DebugLogger.info(LogCategory.LOCATION, "mapToLocationResult() called")
        DebugLogger.info(
            LogCategory.LOCATION,
            "Raw coordinates — lat=${location.latitude} lon=${location.longitude} provider=${location.provider}"
        )
        val cityName = getCityNameFromGeocoder(location.latitude, location.longitude)
        DebugLogger.info(LogCategory.LOCATION, "mapToLocationResult() completed — resolvedCityName=$cityName")
        return LocationResult(
            latitude = location.latitude,
            longitude = location.longitude,
            geocoderCityName = cityName
        )
    }

    /**
     * Reverses geocodes latitude & longitude into a readable city name safely.
     * Geocoder is strictly wrapped in Dispatchers.IO.
     */
    @Suppress("DEPRECATION")
    private suspend fun getCityNameFromGeocoder(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            DebugLogger.warning(LogCategory.LOCATION, "Geocoder not present")
            return@withContext null
        }

        return@withContext try {
            val geocoder = Geocoder(appContext, Locale("ar")) 

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val addr = addresses.firstOrNull()
                            DebugLogger.info(
                                LogCategory.LOCATION,
                                "Geocoder raw fields — locality=${addr?.locality} " +
                                "subAdminArea=${addr?.subAdminArea} " +
                                "adminArea=${addr?.adminArea} " +
                                "featureName=${addr?.featureName} " +
                                "countryName=${addr?.countryName}"
                            )
                            val city = extractCity(addr)
                            DebugLogger.info(LogCategory.LOCATION, "Geocoder resolved city=$city")
                            continuation.resume(city)
                        }
                        override fun onError(errorMessage: String?) {
                            Log.e(TAG, "Geocoder API 33+ Error: $errorMessage")
                            DebugLogger.warning(
                                LogCategory.LOCATION,
                                "Geocoder API 33+ error — message=$errorMessage"
                            )
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val addr = addresses?.firstOrNull()
                DebugLogger.info(
                    LogCategory.LOCATION,
                    "Geocoder raw fields (old API) — locality=${addr?.locality} " +
                    "subAdminArea=${addr?.subAdminArea} " +
                    "adminArea=${addr?.adminArea} " +
                    "featureName=${addr?.featureName} " +
                    "countryName=${addr?.countryName}"
                )
                val city = extractCity(addr)
                DebugLogger.info(LogCategory.LOCATION, "Geocoder resolved city (old API)=$city")
                city
            }
        } catch (e: IOException) {
            Log.e(TAG, "Geocoder network/IO failure: ${e.message}")
            DebugLogger.warning(LogCategory.LOCATION, "Geocoder IO failure", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Geocoder invalid coordinates: ${e.message}")
            DebugLogger.warning(LogCategory.LOCATION, "Geocoder invalid coordinates", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder unexpected failure: ${e.message}")
            DebugLogger.warning(LogCategory.LOCATION, "Geocoder unexpected failure", e)
            null
        }
    }

    private fun extractCity(address: Address?): String? {
        if (address == null) return null
        return address.locality ?: address.subAdminArea ?: address.adminArea ?: address.featureName
    }
}
