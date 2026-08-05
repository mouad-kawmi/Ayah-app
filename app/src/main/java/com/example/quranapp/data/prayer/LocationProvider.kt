package com.example.quranapp.data.prayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isMock = Build.VERSION.SDK_INT >= 18 &&
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.isFromMockProvider == true

        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
            Log.i("PrayerSync", "Location lat=${it.latitude} lon=${it.longitude} source=GPS_LAST isMock=$isMock")
            return Pair(it.latitude, it.longitude)
        }
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
            Log.i("PrayerSync", "Location lat=${it.latitude} lon=${it.longitude} source=NETWORK_LAST isMock=$isMock")
            return Pair(it.latitude, it.longitude)
        }

        Log.i("PrayerSync", "Location source=FRESH (no last known)")
        return requestFreshLocation(locationManager)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(locationManager: LocationManager): Pair<Double, Double>? {
        val providers = locationManager.getProviders(true).filter {
            it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER
        }
        val provider = providers.firstOrNull() ?: return null

        return suspendCancellableCoroutine { continuation ->
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) {
                        locationManager.removeUpdates(this)
                        val isMock = Build.VERSION.SDK_INT >= 18 && location.isFromMockProvider
                        Log.i("PrayerSync", "Location lat=${location.latitude} lon=${location.longitude} source=FRESH_${location.provider} isMock=$isMock")
                        continuation.resume(Pair(location.latitude, location.longitude))
                    }
                }
                @Deprecated("Deprecated in API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    if (continuation.isActive) {
                        locationManager.removeUpdates(this)
                        continuation.resume(null)
                    }
                }
            }

            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (continuation.isActive) {
                    locationManager.removeUpdates(listener)
                    continuation.resume(null)
                }
            }, 15_000L)
        }
    }
}
