package com.example.quranapp.data.prayer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class AlAdhanProvider : PrayerTimesProvider {
    override val providerId: String = ProviderIds.ALADHAN

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchSchedule(
        context: Context,
        request: PrayerTimeRequest
    ): Result<Map<LocalDate, PrayerTimes>> = withContext(Dispatchers.IO) {
        try {
            val countryCode = request.countryCode
            val calcMethod = CalculationMethodSelector.methodFor(countryCode)
            Log.i("PrayerSync", "Country=$countryCode Provider=$providerId Method=${calcMethod.method} school=${calcMethod.school} latitudeAdjustment=${calcMethod.latitudeAdjustmentMethod} midnightMode=${calcMethod.midnightMode}")

            val months = getMonthsInRange(request.dateRange.start, request.dateRange.endInclusive)
            val deferreds = months.map { (year, month) ->
                async {
                    fetchMonth(request.lat, request.lon, year, month, calcMethod)
                }
            }
            val monthResults = deferreds.awaitAll()
            val combined = mutableMapOf<LocalDate, PrayerTimes>()
            for (result in monthResults) {
                if (result != null) {
                    combined.putAll(result)
                }
            }
            if (combined.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("AlAdhan returned empty schedule"))
            }
            Result.success(combined.filterKeys { it in request.dateRange })
        } catch (e: Exception) {
            Log.e("ALADHAN", "AlAdhan fetch failed", e)
            return@withContext Result.failure(e)
        }
    }

    private fun getMonthsInRange(start: LocalDate, end: LocalDate): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var current = YearMonth.from(start)
        val endMonth = YearMonth.from(end)
        while (current <= endMonth) {
            result.add(current.year to current.monthValue)
            current = current.plusMonths(1)
        }
        return result
    }

    private fun fetchMonth(lat: Double, lon: Double, year: Int, month: Int, calcMethod: CalculationMethod): Map<LocalDate, PrayerTimes>? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.aladhan.com")
            .addPathSegments("v1/calendar")
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("method", calcMethod.method.toString())
            .addQueryParameter("school", calcMethod.school.toString())
            .addQueryParameter("latitudeAdjustmentMethod", calcMethod.latitudeAdjustmentMethod.toString())
            .addQueryParameter("midnightMode", calcMethod.midnightMode.toString())
            .addQueryParameter("month", month.toString())
            .addQueryParameter("year", year.toString())
            .build()
        Log.i("PrayerSync", "AlAdhan URL=$url")
        return try {
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w("ALADHAN", "HTTP ${response.code} for $url")
                return null
            }
            val body = response.body?.string() ?: return null
            parseMonthResponse(body)
        } catch (e: Exception) {
            Log.e("ALADHAN", "Failed to fetch month=$month year=$year", e)
            null
        }
    }

    private fun parseMonthResponse(json: String): Map<LocalDate, PrayerTimes> {
        val root = JSONObject(json)
        val data = root.optJSONArray("data") ?: return emptyMap()
        val result = mutableMapOf<LocalDate, PrayerTimes>()

        // Log meta and first-day raw timings before any parsing
        if (data.length() > 0) {
            val firstDay = data.optJSONObject(0)
            if (firstDay != null) {
                val meta = firstDay.optJSONObject("meta")
                val timings = firstDay.optJSONObject("timings")
                if (meta != null) {
                    val methodObj = meta.optJSONObject("method")
                    val methodId = methodObj?.optInt("id", -1) ?: -1
                    val methodName = methodObj?.optString("name", "") ?: ""
                    val methodParams = methodObj?.optJSONObject("params")
                    val fajrAngle = methodParams?.optString("Fajr", "") ?: ""
                    val ishaAngle = methodParams?.optString("Isha", "") ?: ""
                    val latitudeAdjustment = meta.optString("latitudeAdjustmentMethod", "")
                    val midnightMode = meta.optString("midnightMode", "")
                    val school = meta.optString("school", "")
                    val timezone = meta.optString("timezone", "")
                    val metaLat = meta.optDouble("latitude", 0.0)
                    val metaLon = meta.optDouble("longitude", 0.0)
                    val offsetObj = meta.optJSONObject("offset")
                    val offsetStr = if (offsetObj != null) offsetObj.toString() else "none"

                    Log.i("PrayerSync", "AlAdhan response meta methodId=$methodId methodName=\"$methodName\" fajrAngle=$fajrAngle ishaAngle=$ishaAngle latitudeAdjustment=$latitudeAdjustment midnightMode=$midnightMode school=$school timezone=$timezone metaLat=$metaLat metaLon=$metaLon offset=$offsetStr")
                }
                if (timings != null) {
                    Log.i("PrayerSync", "AlAdhan raw timings Fajr=\"${timings.optString("Fajr")}\" Sunrise=\"${timings.optString("Sunrise")}\" Dhuhr=\"${timings.optString("Dhuhr")}\" Asr=\"${timings.optString("Asr")}\" Maghrib=\"${timings.optString("Maghrib")}\" Isha=\"${timings.optString("Isha")}\"")
                }
            }
        }

        for (i in 0 until data.length()) {
            val dayObj = data.optJSONObject(i) ?: continue
            val timings = dayObj.optJSONObject("timings") ?: continue
            val dateObj = dayObj.optJSONObject("date")?.optJSONObject("gregorian") ?: continue
            val dateStr = dateObj.optString("date", "")
            val dateParts = dateStr.split("-")
            if (dateParts.size != 3) continue
            val year = dateParts[2].toIntOrNull() ?: continue
            val month = dateParts[1].toIntOrNull() ?: continue
            val day = dateParts[0].toIntOrNull() ?: continue
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue

            val cleaned = PrayerTimes(
                fajr = cleanTime(timings.optString("Fajr", "")),
                shuruq = cleanTime(timings.optString("Sunrise", "")),
                dhuhr = cleanTime(timings.optString("Dhuhr", "")),
                asr = cleanTime(timings.optString("Asr", "")),
                maghrib = cleanTime(timings.optString("Maghrib", "")),
                isha = cleanTime(timings.optString("Isha", ""))
            )

            // Log cleaned times for the first day only
            if (i == 0) {
                Log.i("PrayerSync", "AlAdhan cleaned timings date=$date Fajr=${cleaned.fajr} Sunrise=${cleaned.shuruq} Dhuhr=${cleaned.dhuhr} Asr=${cleaned.asr} Maghrib=${cleaned.maghrib} Isha=${cleaned.isha}")
            }

            result[date] = cleaned
        }
        return result
    }

    private fun cleanTime(raw: String): String {
        val match = Regex("\\d{1,2}:\\d{2}").find(raw)?.value ?: return "--:--"
        val hour = match.substringBefore(":").padStart(2, '0')
        val minute = match.substringAfter(":")
        return "$hour:$minute"
    }
}
