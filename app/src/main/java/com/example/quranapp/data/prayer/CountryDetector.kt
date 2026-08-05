package com.example.quranapp.data.prayer

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class CountryDetector(private val context: Context) {

    companion object {
        private const val PREFS_KEY_COUNTRY = "detected_country"
        private const val MOROCCO_CODE = "MA"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val CACHE_MAX_DISTANCE_KM = 20.0
    }

    private data class CountryCacheEntry(
        val code: String,
        val lat: Double,
        val lon: Double,
        val detectionTime: Long
    )

    @Volatile private var cacheEntry: CountryCacheEntry? = null

    suspend fun detectCountry(lat: Double, lon: Double): String {
        val start = System.nanoTime()
        val code = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cacheEntry?.let { entry ->
                val dist = PrayerTimeUtils.distanceKm(lat, lon, entry.lat, entry.lon)
                if (now - entry.detectionTime < CACHE_TTL_MS && dist <= CACHE_MAX_DISTANCE_KM) {
                    Log.i("PrayerSync", "Country cache HIT country=${entry.code} distance=${"%.1f".format(dist)}km")
                    return@withContext entry.code
                }
                if (dist > CACHE_MAX_DISTANCE_KM) {
                    Log.i("PrayerSync", "Country cache INVALID country=${entry.code} distance=${"%.1f".format(dist)}km")
                }
            }
            geocoderLookup(lat, lon)?.let { country -> Log.i("PrayerSync", "Country cache REFRESH country=$country Source=Geocoder"); cache(country, lat, lon); return@withContext country }
            Log.i("PrayerSync", "Country-Source=Geocoder MISS")
            cachedCountry()?.let { country -> Log.i("PrayerSync", "Country cache REFRESH country=$country Source=SharedPrefs"); cache(country, lat, lon); return@withContext country }
            Log.i("PrayerSync", "Country-Source=SharedPrefs MISS")
            if (PrayerTimeUtils.isInMoroccoBoundingBox(lat, lon)) {
                Log.i("PrayerSync", "Country cache REFRESH country=MA Source=BoundingBox")
                cache(MOROCCO_CODE, lat, lon); return@withContext MOROCCO_CODE
            }
            Log.i("PrayerSync", "Country-Source=BoundingBox MISS")
            ipLookup()?.let { country -> Log.i("PrayerSync", "Country cache REFRESH country=$country Source=IpApi"); cache(country, lat, lon); return@withContext country }
            Log.i("PrayerSync", "Country-Source=IpApi MISS")
            val locale = localeFallback()
            Log.i("PrayerSync", "Country cache REFRESH country=$locale Source=LocaleFallback")
            cache(locale, lat, lon)
            locale
        }
        val ms = (System.nanoTime() - start) / 1_000_000L
        DebugLogger.info(
            LogCategory.PERFORMANCE,
            Instrumentation.line("location", Instrumentation.NO_TRACE, null, "Location resolution in $ms ms")
        )
        return code
    }

    private fun cache(code: String, lat: Double, lon: Double) {
        cacheEntry = CountryCacheEntry(code, lat, lon, System.currentTimeMillis())
    }

    fun cacheCountry(countryCode: String) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY_COUNTRY, countryCode)
            .apply()
    }

    private fun geocoderLookup(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.ENGLISH)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.countryCode
        } catch (e: Exception) {
            Log.w("COUNTRY", "Geocoder failed", e)
            DebugLogger.warning(
                LogCategory.LOCATION,
                Instrumentation.line("location", Instrumentation.NO_TRACE, null, ErrorCode.GEOCODER_FAILED.prefix("Geocoder failed")),
                e
            )
            null
        }
    }

    private fun cachedCountry(): String? {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString(PREFS_KEY_COUNTRY, null)
    }

    private fun ipLookup(): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        return try {
            val request = Request.Builder()
                .url("http://ip-api.com/json/")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            json.optString("countryCode").takeIf { it.length == 2 }
        } catch (e: Exception) {
            Log.w("COUNTRY", "IP lookup failed", e)
            DebugLogger.warning(
                LogCategory.LOCATION,
                Instrumentation.line("location", Instrumentation.NO_TRACE, null, ErrorCode.IP_LOOKUP_FAILED.prefix("IP lookup failed")),
                e
            )
            null
        }
    }

    private fun localeFallback(): String {
        return Locale.getDefault().country.ifBlank { MOROCCO_CODE }
    }

}
