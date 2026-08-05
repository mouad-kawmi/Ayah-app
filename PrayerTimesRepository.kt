package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import com.example.debug.PerformanceProfiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.annotations.VisibleForTesting
import org.jsoup.Jsoup
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt

data class PrayerTimes(
    val fajr: String = "--:--",
    val shuruq: String = "--:--",
    val dhuhr: String = "--:--",
    val asr: String = "--:--",
    val maghrib: String = "--:--",
    val isha: String = "--:--"
)

data class OfficialPrayerCity(
    val id: Int,
    val habousId: Int,
    val name: String,
    val lat: Double,
    val lon: Double
)

val officialPrayerCities = listOf(
    // Grand cities
    OfficialPrayerCity(80, 58, "الدار البيضاء", 33.5731, -7.5898),
    OfficialPrayerCity(69, 1, "الرباط", 34.0209, -6.8416),
    OfficialPrayerCity(69, 1, "الرباط سلا", 34.0209, -6.8416),
    OfficialPrayerCity(69, 1, "سلا", 34.0333, -6.8000),
    OfficialPrayerCity(32, 81, "فاس", 34.0331, -5.0003),
    OfficialPrayerCity(2, 31, "وجدة", 34.6814, -1.9086),
    OfficialPrayerCity(84, 104, "مراكش", 31.6295, -7.9811),
    OfficialPrayerCity(95, 117, "أكادير", 30.4202, -9.5982),
    OfficialPrayerCity(48, 14, "طنجة", 35.7595, -5.8340),
    OfficialPrayerCity(43, 99, "مكناس", 33.8935, -5.5473),
    OfficialPrayerCity(67, 7, "القنيطرة", 34.2610, -6.5802),
    OfficialPrayerCity(40, 15, "تطوان", 35.5785, -5.3684),
    // Casablanca region — important for GPS fallback near suburbs
    OfficialPrayerCity(75, 59, "المحمدية", 33.6972, -7.3830),
    OfficialPrayerCity(76, 60, "بن سليمان", 33.6553, -7.1173),
    OfficialPrayerCity(77, 61, "سطات", 32.9900, -7.6214),
    OfficialPrayerCity(78, 65, "برشيد", 33.2650, -7.5870),   // ~30km from Sidi Rahal
    OfficialPrayerCity(79, 64, "ابن أحمد", 33.0403, -7.2292),
    OfficialPrayerCity(81, 62, "الكارة", 33.1919, -7.4200),
    OfficialPrayerCity(82, 63, "البروج", 32.5003, -7.1936),
    // El Jadida region
    OfficialPrayerCity(88, 66, "الجديدة", 33.2316, -8.5007),
    OfficialPrayerCity(89, 67, "أزمور", 33.2944, -8.3434),
    OfficialPrayerCity(90, 68, "سيدي بنور", 32.6539, -8.4225),
    OfficialPrayerCity(91, 69, "خميس الزمامرة", 32.5833, -8.7000),
    // Beni Mellal
    OfficialPrayerCity(62, 73, "بني ملال", 32.3373, -6.3498),
    OfficialPrayerCity(63, 74, "أزيلال", 31.9653, -6.5693),
    OfficialPrayerCity(64, 75, "الفقيه بن صالح", 32.5025, -6.6869),
    // Khouribga
    OfficialPrayerCity(70, 79, "خريبكة", 32.8811, -6.9063),
    OfficialPrayerCity(71, 80, "وادي زم", 32.8620, -6.4670),
    // Fes region
    OfficialPrayerCity(32, 82, "صفرو", 33.8306, -4.8350),
    OfficialPrayerCity(33, 83, "مولاي يعقوب", 34.0833, -5.1667),
    // Meknes region
    OfficialPrayerCity(46, 8, "سيدي قاسم", 34.2264, -5.7033),
    OfficialPrayerCity(55, 2, "الخميسات", 33.8151, -6.0663),
    OfficialPrayerCity(45, 70, "خنيفرة", 32.9395, -5.6687),
    OfficialPrayerCity(35, 100, "إفران", 33.5228, -5.1051),
    OfficialPrayerCity(36, 103, "آزرو", 33.4350, -5.2190),
    // Taza
    OfficialPrayerCity(17, 89, "تازة", 34.2100, -4.0100),
    // Nador
    OfficialPrayerCity(8, 39, "الناظور", 35.1681, -2.9300),
    // Safi & Essaouira
    OfficialPrayerCity(97, 106, "الصويرة", 31.5085, -9.7595),
    OfficialPrayerCity(92, 111, "آسفي", 32.2994, -9.2372),
    // Marrakech region
    OfficialPrayerCity(85, 105, "قلعة السراغنة", 32.0500, -7.9500),
    OfficialPrayerCity(86, 108, "بنجرير", 32.2399, -7.9500),
    OfficialPrayerCity(87, 107, "شيشاوة", 31.5314, -8.7636),
    // Agadir region
    OfficialPrayerCity(90, 118, "تارودانت", 30.4703, -8.8770),
    OfficialPrayerCity(96, 119, "تزنيت", 29.6974, -9.7319),
    OfficialPrayerCity(94, 148, "سيدي إفني", 29.3792, -10.1731),
    // Ouarzazate / South
    OfficialPrayerCity(71, 138, "ورزازات", 30.9189, -6.8934),
    OfficialPrayerCity(72, 137, "زاكورة", 30.3617, -5.7336),
    OfficialPrayerCity(73, 139, "تنغير", 31.5217, -5.5296),
    // Rashidiya
    OfficialPrayerCity(23, 128, "الراشيدية", 31.9314, -4.4244),
    // North
    OfficialPrayerCity(15, 23, "الحسيمة", 35.2472, -3.9322),
    OfficialPrayerCity(50, 21, "القصر الكبير", 34.9965, -5.9017),
    OfficialPrayerCity(51, 16, "العرائش", 35.1990, -6.1573),
    // Deep south
    OfficialPrayerCity(98, 149, "كلميم", 28.9869, -10.0573),
    OfficialPrayerCity(103, 156, "العيون", 27.1253, -13.1625),
    OfficialPrayerCity(104, 157, "السمارة", 26.7417, -11.6806),
    OfficialPrayerCity(105, 165, "الداخلة", 23.6848, -15.9575)
)

fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p = 0.017453292519943295
    val a = 0.5 - cos((lat2 - lat1) * p) / 2 +
            cos(lat1 * p) * cos(lat2 * p) *
            (1 - cos((lon2 - lon1) * p)) / 2
    return 12742 * asin(sqrt(a))
}

class PrayerTimesRepository(private val context: Context? = null) {

    private data class HabousScheduleSnapshot(
        val availableMonths: Set<YearMonth>,
        val schedule: Map<LocalDate, PrayerTimes>
    )

    private val client: OkHttpClient = createUnsafeOkHttpClient()

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    @Suppress("TrustAllX509TrustManager")
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    @Suppress("TrustAllX509TrustManager")
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )
            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            DebugLogger.warning(LogCategory.NETWORK, "Failed to build SSL OkHttpClient — falling back to plain", e)
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Pure read-only cache lookup for today's prayer times.
     * Safe to call on the main thread — does NOT write to disk.
     * The active-city record is only updated during background sync.
     *
     * Fallback chain:
     * 1. Resolved city by GPS/name → habousId
     * 2. Cached city_id (survives migration)
     * 3. Any city that has today's month data (last resort)
     */
    fun getCachedPrayerTimesForToday(
        latitude: Double = 33.5731,
        longitude: Double = -7.5898,
        cityName: String = "الدار البيضاء",
        context: Context
    ): PrayerTimes? {
        Log.d("STARTUP_TRACE", "ENTRY")
        DebugLogger.debug(LogCategory.PERFORMANCE, "ENTRY — getCachedPrayerTimesForToday lat=$latitude lon=$longitude city=$cityName")
        PerformanceProfiler.recordRepositoryCall("PrayerTimesRepository.getCachedPrayerTimesForToday")
        val targetCity = resolveOfficialCity(latitude, longitude, cityName)
        val today = LocalDate.now()

        // 1. Primary: resolved city by habousId
        val t0 = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "CACHESTORE_GET_DATE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_DATE_START — target habousId=${targetCity.habousId}")
        val primary = PrayerTimesCacheStore.getPrayerTimesForDate(context, targetCity.habousId, today)
        val t1 = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "CACHESTORE_GET_DATE_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_DATE_END — hit=${primary != null} elapsedMs=${t1 - t0}")
        if (primary != null) {
            PerformanceProfiler.recordCacheResult("PrayerTimesRepository.prayerTimesCache", true)
            return primary
        }

        // 2. Secondary: whatever city_id is in the cache (may be old id before migration)
        val t2 = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "CACHESTORE_GET_CITY_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_CITY_START")
        val cachedId = PrayerTimesCacheStore.getCachedCityId(context)
        val t3 = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "CACHESTORE_GET_CITY_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_CITY_END — cachedId=$cachedId elapsedMs=${t3 - t2}")
        if (cachedId != null && cachedId != targetCity.habousId) {
            val t4 = System.currentTimeMillis()
            Log.d("STARTUP_TRACE", "CACHESTORE_GET_DATE_START")
            DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_DATE_START — cachedId=$cachedId")
            val cached = PrayerTimesCacheStore.getPrayerTimesForDate(context, cachedId, today)
            val t5 = System.currentTimeMillis()
            Log.d("STARTUP_TRACE", "CACHESTORE_GET_DATE_END")
            DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_DATE_END — hit=${cached != null} elapsedMs=${t5 - t4}")
            if (cached != null) {
                PerformanceProfiler.recordCacheResult("PrayerTimesRepository.prayerTimesCache", true)
                return cached
            }
        }

        // 3. Tertiary: any city in the cache (last resort)
        val t6 = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "CACHESTORE_GET_ANY_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_ANY_START")
        val any = PrayerTimesCacheStore.getAnyAvailablePrayerTimesForDate(context, today)
        val t7 = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "CACHESTORE_GET_ANY_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CACHESTORE_GET_ANY_END — hit=${any != null} elapsedMs=${t7 - t6}")
        PerformanceProfiler.recordCacheResult("PrayerTimesRepository.prayerTimesCache", any != null)
        return any
    }

    /**
     * Background-only synchronization with the Habous website.
     * Downloads only months absent from the cache.
     * Must be called from a background coroutine only.
     *
     * Safety guarantees:
     * - setActiveCity is only called AFTER at least one month saves successfully.
     * - If Habous fetch fails, existing cache is preserved (no write occurs).
     * - If offline, existing cache and alarms are preserved.
     */
    /**
     * Seeds the cache from the bundled offline JSON asset when no network
     * and no existing cache data are available. This ensures a fresh install
     * without internet still shows correct prayer times.
     * Returns true if any data was loaded.
     */
    private fun loadOfflineFallback(context: Context, targetCity: OfficialPrayerCity): Boolean {
        return try {
            val jsonString = context.assets.open("prayer_times_offline.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonString)
            val cityKey = targetCity.id.toString()
            val cityData = root.optJSONObject(cityKey) ?: return false
            val yearStr = java.time.Year.now().toString()
            val yearData = cityData.optJSONObject(yearStr) ?: return false

            var loaded = false
            val months = yearData.keys()
            while (months.hasNext()) {
                val monthStr = months.next()
                val monthData = yearData.optJSONObject(monthStr) ?: continue
                val month = monthStr.toIntOrNull() ?: continue
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
                    PrayerTimesCacheStore.saveMonthSchedule(
                        context = context,
                        cityId = targetCity.habousId,
                        cityName = targetCity.name,
                        year = java.time.Year.now().value,
                        month = month,
                        days = days,
                        updateActiveCity = false
                    )
                    loaded = true
                }
            }
            if (loaded) {
                PrayerTimesCacheStore.setActiveCity(context, targetCity.habousId, targetCity.name)
            }
            loaded
        } catch (e: Exception) {
            DebugLogger.warning(LogCategory.CACHE, "Failed to load offline fallback", e)
            false
        }
    }

    suspend fun syncUpcomingMonths(
        context: Context,
        latitude: Double,
        longitude: Double,
        cityName: String,
        forceRefresh: Boolean = false
    ): Result<OfficialPrayerCity> = withContext(Dispatchers.IO) {
        PerformanceProfiler.recordRepositoryCall("PrayerTimesRepository.syncUpcomingMonths")
        try {
            val targetCity = resolveOfficialCity(latitude, longitude, cityName)

            // One-time migration: rekey old id → habousId if needed
            val cachedCityName = PrayerTimesCacheStore.getCachedCityName(context)
            val idToHabous = officialPrayerCities
                .groupBy { it.id }
                .mapValues { (_, cities) ->
                    val cached = cachedCityName
                    if (cached != null) {
                        cities.firstOrNull { normalizeCityName(it.name) == normalizeCityName(cached) }?.habousId
                            ?: cities.first().habousId
                    } else {
                        cities.first().habousId
                    }
                }
            PrayerTimesCacheStore.migrateCityIds(context, idToHabous, cachedCityName)

            if (!hasInternetConnection(context)) {
                // No network — try the bundled offline asset as a bootstrap
                val cachedAny = PrayerTimesCacheStore.getAnyAvailablePrayerTimesForDate(context, LocalDate.now())
                if (cachedAny == null) {
                    loadOfflineFallback(context, targetCity)
                }
                return@withContext Result.success(targetCity)
            }

            val snapshot = fetchHabousScheduleSnapshot(targetCity.habousId)

            // If fetch returns nothing, check whether the cache already has usable data
            if (snapshot == null || snapshot.schedule.isEmpty()) {
                val cachedAny = PrayerTimesCacheStore.getAnyAvailablePrayerTimesForDate(context, LocalDate.now())
                if (cachedAny != null) {
                    Log.d("NOOR_DEBUG", "Habous fetch returned nothing but cache has data — preserving existing cache")
                    return@withContext Result.success(targetCity)
                }
                val msg = if (snapshot == null) "Unable to load Habous prayer times" else "Habous returned empty schedule"
                return@withContext Result.failure(IllegalStateException(msg))
            }

            var anyMonthSaved = false
            snapshot.availableMonths.forEach { month ->
                val alreadyCached = !forceRefresh &&
                        PrayerTimesCacheStore.hasMonthSchedule(
                            context, targetCity.habousId, month.year, month.monthValue
                        )
                PerformanceProfiler.recordCacheResult("PrayerTimesRepository.monthScheduleCache", alreadyCached)
                if (!alreadyCached) {
                    val monthSchedule = snapshot.schedule.filterKeys { YearMonth.from(it) == month }
                    if (monthSchedule.isNotEmpty()) {
                        saveDatedSchedule(context, targetCity, monthSchedule, updateActiveCity = false)
                        anyMonthSaved = true
                    }
                }
            }

            // Only set active city after at least one month was saved successfully
            if (anyMonthSaved || forceRefresh) {
                PrayerTimesCacheStore.setActiveCity(
                    context = context,
                    cityId = targetCity.habousId,
                    cityName = targetCity.name
                )
            }

            PrayerTimesCacheStore.pruneOldMonths(context, LocalDate.now().minusMonths(6).withDayOfMonth(1))
            Result.success(targetCity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resolveOfficialCity(latitude: Double, longitude: Double, cityName: String): OfficialPrayerCity {
        val normalizedInput = normalizeCityName(cityName)
        val nameMatch = officialPrayerCities.firstOrNull { normalizeCityName(it.name) == normalizedInput }
        if (nameMatch != null) {
            Log.d("NOOR_CITY", "City resolved by NAME match: input='$cityName' → '${nameMatch.name}' (habousId=${nameMatch.habousId})")
            return nameMatch
        }
        val nearest = officialPrayerCities.minByOrNull { distanceKm(latitude, longitude, it.lat, it.lon) }
            ?: officialPrayerCities.first()
        val distKm = distanceKm(latitude, longitude, nearest.lat, nearest.lon)
        Log.d("NOOR_CITY", "City resolved by DISTANCE fallback: input='$cityName' lat=$latitude lon=$longitude → '${nearest.name}' (habousId=${nearest.habousId}, dist=${String.format("%.1f", distKm)}km)")
        return nearest
    }

    private fun normalizeCityName(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("â", "a")
            .replace("î", "i")
            .replace("ô", "o")
            .replace("û", "u")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .replace("\\s+".toRegex(), "")
    }

    private fun saveDatedSchedule(
        context: Context,
        city: OfficialPrayerCity,
        schedule: Map<LocalDate, PrayerTimes>,
        updateActiveCity: Boolean = true
    ) {
        Log.d("NOOR_DEBUG", "Saving ${schedule.size} days")
        schedule.entries
            .groupBy { YearMonth.from(it.key) }
            .forEach { (yearMonth, entries) ->
                PrayerTimesCacheStore.saveMonthSchedule(
                    context = context,
                    cityId = city.habousId,
                    cityName = city.name,
                    year = yearMonth.year,
                    month = yearMonth.monthValue,
                    days = entries.associate { it.key.dayOfMonth to it.value },
                    updateActiveCity = updateActiveCity
                )
            }
    }

    private fun fetchHabousScheduleSnapshot(cityId: Int): HabousScheduleSnapshot? {
        val url = "https://www.habous.gov.ma/prieres/index.php?ville=$cityId"
        // Retry up to 2 times to handle transient network issues
        repeat(2) { attempt ->
            Log.d("NOOR_DEBUG", "Fetching Habous city=$cityId (attempt ${attempt + 1})")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    Log.d("NOOR_DEBUG", "HTTP CODE=${response.code}")
                    if (!response.isSuccessful) return@repeat
                    val body = response.body?.string().orEmpty()
                    Log.d("NOOR_DEBUG", "Content-Length=${body.length}")
                    val snapshot = parseHabousSchedule(body)
                    Log.d("NOOR_DEBUG", "Months=${snapshot.availableMonths.size} Days=${snapshot.schedule.size}")
                    if (snapshot.schedule.isNotEmpty()) return snapshot
                }
            } catch (e: Exception) {
                DebugLogger.error(LogCategory.NETWORK, "Habous API request failed (attempt ${attempt + 1})", e)
                if (attempt == 0) Thread.sleep(2000)
            }
        }
        return null
    }

    private fun parseHabousSchedule(html: String): HabousScheduleSnapshot {
        val document = Jsoup.parse(html)
        val tableGregorianHeader = document.select("#horaire tr").firstOrNull()
            ?.select("td, th")
            ?.getOrNull(2)
            ?.text()
            .orEmpty()
        val monthHeader = listOf(
            document.select(".priere-section-month p:not(.first)").firstOrNull()?.text().orEmpty(),
            document.select(".priere-section-month").text(),
            tableGregorianHeader
        ).filter { it.isNotBlank() }.joinToString(" ")
        val monthNumbers = extractGregorianMonths(monthHeader).ifEmpty { listOf(LocalDate.now().monthValue) }
        val years = Regex("\\d{4}")
            .findAll(toAsciiDigits(monthHeader))
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1900..2200 }
            .toList()
        var currentYear = years.firstOrNull() ?: LocalDate.now().year
        if (years.size == 1 && monthNumbers.size > 1 && monthNumbers.first() > monthNumbers.last()) {
            currentYear -= 1
        }
        var monthIndex = 0
        var currentMonth = monthNumbers[monthIndex]
        var previousGregorianDay: Int? = null
        val result = linkedMapOf<LocalDate, PrayerTimes>()

        Log.d("NOOR_DEBUG", "Tables count=${document.select("table").size}")
        val rows = document.select("#horaire tr").drop(1)
        Log.d("NOOR_DEBUG", "rows.size=${rows.size}")

        rows.forEach { row ->
            val cells = row.select("td")
            if (cells.size < 9) return@forEach

            val gregorianDay = extractNumber(cells[2].text()) ?: return@forEach
            previousGregorianDay?.let { previousDay ->
                if (gregorianDay < previousDay) {
                    val nextMonth = monthNumbers.getOrNull(monthIndex + 1)
                        ?: ((currentMonth % 12) + 1)
                    monthIndex = (monthIndex + 1).coerceAtMost(monthNumbers.lastIndex)
                    if (nextMonth < currentMonth) currentYear += 1
                    currentMonth = nextMonth
                }
            }
            previousGregorianDay = gregorianDay

            val date = runCatching { LocalDate.of(currentYear, currentMonth, gregorianDay) }
                .getOrNull() ?: return@forEach
            val fajr = extractTime(cells[3].text()) ?: return@forEach
            val shuruq = extractTime(cells[4].text()) ?: return@forEach
            val dhuhr = extractTime(cells[5].text()) ?: return@forEach
            val asr = extractTime(cells[6].text()) ?: return@forEach
            val maghrib = extractTime(cells[7].text()) ?: return@forEach
            val isha = extractTime(cells[8].text()) ?: return@forEach

            result[date] = PrayerTimes(
                fajr = fajr,
                shuruq = shuruq,
                dhuhr = dhuhr,
                asr = asr,
                maghrib = maghrib,
                isha = isha
            )
        }

        Log.d("NOOR_DEBUG", "Parsed days = ${result.size}")
        if (result.isNotEmpty()) {
            Log.d("NOOR_DEBUG", "First 3 dates: \n${result.keys.take(3).joinToString("\n")}")
        } else {
            Log.d("NOOR_DEBUG", "document.title() = ${document.title()}")
            val firstTable = document.select("table").firstOrNull()
            Log.d("NOOR_DEBUG", "First table id=${firstTable?.id()} class=${firstTable?.className()}")
        }

        return HabousScheduleSnapshot(
            availableMonths = result.keys.map { YearMonth.from(it) }.toSet(),
            schedule = result
        )
    }

    private fun extractGregorianMonths(header: String): List<Int> {
        val normalizedHeader = normalizeCityName(header)
        val monthNames = linkedMapOf(
            "يناير" to 1,
            "فبراير" to 2,
            "مارس" to 3,
            "ابريل" to 4,
            "ماي" to 5,
            "يونيو" to 6,
            "يوليوز" to 7,
            "يوليو" to 7,
            "غشت" to 8,
            "شتنبر" to 9,
            "اكتوبر" to 10,
            "نونبر" to 11,
            "دجنبر" to 12
        )

        return monthNames
            .mapNotNull { (name, month) ->
                val index = normalizedHeader.indexOf(normalizeCityName(name))
                if (index >= 0) index to month else null
            }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
    }

    private fun extractNumber(value: String): Int? {
        return Regex("\\d+").find(toAsciiDigits(value))?.value?.toIntOrNull()
    }

    private fun extractTime(value: String): String? {
        val match = Regex("\\d{1,2}:\\d{2}").find(toAsciiDigits(value))?.value ?: return null
        val hour = match.substringBefore(":").padStart(2, '0')
        val minute = match.substringAfter(":")
        return "$hour:$minute"
    }

    private fun toAsciiDigits(value: String): String {
        return value.map { char ->
            when (char) {
                in '٠'..'٩' -> '0' + (char.code - '٠'.code)
                in '۰'..'۹' -> '0' + (char.code - '۰'.code)
                else -> char
            }
        }.joinToString("")
    }

    fun hasInternetConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
