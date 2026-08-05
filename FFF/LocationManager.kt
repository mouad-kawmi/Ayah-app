package com.example

import android.content.Context
import android.location.LocationManager as AndroidLocationManager

object LocationManager {

    /**
     * Checks if GPS or Network Location is enabled on the device safely.
     * Returns true if at least one provider (GPS or Network) is enabled.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        
        com.example.debug.DebugLogger.info(com.example.debug.LogCategory.GPS, "About to check GPS provider")
        val gpsEnabled = try {
            locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            com.example.debug.DebugLogger.error(com.example.debug.LogCategory.GPS, "Error checking GPS provider status", e)
            false
        }
        com.example.debug.DebugLogger.info(com.example.debug.LogCategory.GPS, "gpsEnabled=$gpsEnabled")
        
        com.example.debug.DebugLogger.info(com.example.debug.LogCategory.GPS, "About to check Network provider")
        val networkEnabled = try {
            locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            com.example.debug.DebugLogger.error(com.example.debug.LogCategory.GPS, "Error checking Network provider status", e)
            false
        }
        com.example.debug.DebugLogger.info(com.example.debug.LogCategory.GPS, "networkEnabled=$networkEnabled")

        com.example.debug.DebugLogger.info(
            com.example.debug.LogCategory.GPS,
            "Final check: gpsEnabled=$gpsEnabled networkEnabled=$networkEnabled"
        )
        return gpsEnabled || networkEnabled
    }

    /**
     * Helper to determine if the city changed based strictly on Habous ID.
     */
    fun hasCityChanged(context: Context, newLat: Double, newLon: Double, newCityName: String?): Boolean {
        val cachedCityId = PrayerTimesCacheStore.getCachedCityId(context) ?: return true
        val newCity = PrayerTimesRepository().resolveOfficialCity(newLat, newLon, newCityName ?: "")
        return newCity.habousId != cachedCityId
    }
}
