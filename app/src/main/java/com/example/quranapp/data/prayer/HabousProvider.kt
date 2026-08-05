package com.example.quranapp.data.prayer

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt

data class OfficialPrayerCity(
    val id: Int,
    val habousId: Int,
    val name: String,
    val lat: Double,
    val lon: Double
)

val officialPrayerCities = listOf(
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
    OfficialPrayerCity(75, 59, "المحمدية", 33.6972, -7.3830),
    OfficialPrayerCity(76, 60, "بن سليمان", 33.6553, -7.1173),
    OfficialPrayerCity(77, 61, "سطات", 32.9900, -7.6214),
    OfficialPrayerCity(78, 65, "برشيد", 33.2650, -7.5870),
    OfficialPrayerCity(79, 64, "ابن أحمد", 33.0403, -7.2292),
    OfficialPrayerCity(81, 62, "الكارة", 33.1919, -7.4200),
    OfficialPrayerCity(82, 63, "البروج", 32.5003, -7.1936),
    OfficialPrayerCity(106, 58, "سيدي رحال", 33.4560, -7.9570),
    OfficialPrayerCity(88, 66, "الجديدة", 33.2316, -8.5007),
    OfficialPrayerCity(89, 67, "أزمور", 33.2944, -8.3434),
    OfficialPrayerCity(90, 68, "سيدي بنور", 32.6539, -8.4225),
    OfficialPrayerCity(91, 69, "خميس الزمامرة", 32.5833, -8.7000),
    OfficialPrayerCity(62, 73, "بني ملال", 32.3373, -6.3498),
    OfficialPrayerCity(63, 74, "أزيلال", 31.9653, -6.5693),
    OfficialPrayerCity(64, 75, "الفقيه بن صالح", 32.5025, -6.6869),
    OfficialPrayerCity(70, 79, "خريبكة", 32.8811, -6.9063),
    OfficialPrayerCity(71, 80, "وادي زم", 32.8620, -6.4670),
    OfficialPrayerCity(32, 82, "صفرو", 33.8306, -4.8350),
    OfficialPrayerCity(33, 83, "مولاي يعقوب", 34.0833, -5.1667),
    OfficialPrayerCity(46, 8, "سيدي قاسم", 34.2264, -5.7033),
    OfficialPrayerCity(55, 2, "الخميسات", 33.8151, -6.0663),
    OfficialPrayerCity(45, 70, "خنيفرة", 32.9395, -5.6687),
    OfficialPrayerCity(35, 100, "إفران", 33.5228, -5.1051),
    OfficialPrayerCity(36, 103, "آزرو", 33.4350, -5.2190),
    OfficialPrayerCity(17, 89, "تازة", 34.2100, -4.0100),
    OfficialPrayerCity(8, 39, "الناظور", 35.1681, -2.9300),
    OfficialPrayerCity(97, 106, "الصويرة", 31.5085, -9.7595),
    OfficialPrayerCity(92, 111, "آسفي", 32.2994, -9.2372),
    OfficialPrayerCity(85, 105, "قلعة السراغنة", 32.0500, -7.9500),
    OfficialPrayerCity(86, 108, "بنجرير", 32.2399, -7.9500),
    OfficialPrayerCity(87, 107, "شيشاوة", 31.5314, -8.7636),
    OfficialPrayerCity(90, 118, "تارودانت", 30.4703, -8.8770),
    OfficialPrayerCity(96, 119, "تزنيت", 29.6974, -9.7319),
    OfficialPrayerCity(94, 148, "سيدي إفني", 29.3792, -10.1731),
    OfficialPrayerCity(71, 138, "ورزازات", 30.9189, -6.8934),
    OfficialPrayerCity(72, 137, "زاكورة", 30.3617, -5.7336),
    OfficialPrayerCity(73, 139, "تنغير", 31.5217, -5.5296),
    OfficialPrayerCity(23, 128, "الراشيدية", 31.9314, -4.4244),
    OfficialPrayerCity(15, 23, "الحسيمة", 35.2472, -3.9322),
    OfficialPrayerCity(50, 21, "القصر الكبير", 34.9965, -5.9017),
    OfficialPrayerCity(51, 16, "العرائش", 35.1990, -6.1573),
    OfficialPrayerCity(98, 149, "كلميم", 28.9869, -10.0573),
    OfficialPrayerCity(103, 156, "العيون", 27.1253, -13.1625),
    OfficialPrayerCity(104, 157, "السمارة", 26.7417, -11.6806),
    OfficialPrayerCity(105, 165, "الداخلة", 23.6848, -15.9575)
)

class HabousProvider : PrayerTimesProvider {
    override val providerId: String = ProviderIds.HABOUS

    private data class HabousScheduleSnapshot(
        val availableMonths: Set<YearMonth>,
        val schedule: Map<LocalDate, PrayerTimes>
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchSchedule(
        context: Context,
        request: PrayerTimeRequest
    ): Result<Map<LocalDate, PrayerTimes>> {
        val city = resolveOfficialCity(request.lat, request.lon, request.cityName ?: "")
        val snapshot = fetchHabousScheduleSnapshot(city.habousId)
            ?: return Result.failure(IllegalStateException("Habous fetch returned null for cityId=${city.habousId}"))

        val filtered = snapshot.schedule.filter { (date) ->
            date >= request.dateRange.start && date <= request.dateRange.endInclusive
        }
        if (filtered.isEmpty()) {
            return Result.failure(IllegalStateException("Habous returned empty schedule for cityId=${city.habousId}"))
        }
        return Result.success(filtered)
    }

    private data class FetchResult(
        val snapshot: HabousScheduleSnapshot?,
        val exception: Exception?,
        val httpCode: Int?,
        val bodyPreview: String?
    )

    fun getOfficialCities(): List<OfficialPrayerCity> = officialPrayerCities

    fun resolveOfficialCity(latitude: Double, longitude: Double, cityName: String): OfficialPrayerCity {
        val normalizedInput = normalizeCityName(cityName)
        val nameMatch = officialPrayerCities.firstOrNull { normalizeCityName(it.name) == normalizedInput }
        if (nameMatch != null) {
            Log.d("PRAYER", "City resolved by NAME match: input='$cityName' -> '${nameMatch.name}' (habousId=${nameMatch.habousId})")
            return nameMatch
        }
        val nearest = officialPrayerCities.minByOrNull { distanceKm(latitude, longitude, it.lat, it.lon) }
            ?: officialPrayerCities.first()
        val distKm = distanceKm(latitude, longitude, nearest.lat, nearest.lon)
        Log.d("PRAYER", "City resolved by DISTANCE: input='$cityName' lat=$latitude lon=$longitude -> '${nearest.name}' (habousId=${nearest.habousId}, dist=${"%.1f".format(distKm)}km)")
        return nearest
    }

    fun resolveOfficialCityById(cityUniqueId: Int): OfficialPrayerCity? {
        return officialPrayerCities.firstOrNull { it.id == cityUniqueId }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p = 0.017453292519943295
        val a = 0.5 - cos((lat2 - lat1) * p) / 2 +
                cos(lat1 * p) * cos(lat2 * p) *
                (1 - cos((lon2 - lon1) * p)) / 2
        return 12742 * asin(sqrt(a))
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

    private fun fetchHabousScheduleSnapshot(cityId: Int): HabousScheduleSnapshot? {
        val url = "https://www.habous.gov.ma/prieres/index.php?ville=$cityId"
        var lastResult: FetchResult? = null
        repeat(2) { attempt ->
            Log.d("PRAYER_SYNC", "Fetching Habous cityId=$cityId url=$url (attempt ${attempt + 1})")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    val httpCode = response.code
                    Log.d("PRAYER_SYNC", "HTTP CODE=$httpCode message=${response.message} finalUrl=${response.request.url}")
                    val body = response.body?.string().orEmpty()
                    Log.d("PRAYER_SYNC", "Body length=${body.length}")
                    if (!response.isSuccessful) {
                        Log.w("PRAYER_SYNC", "HTTP $httpCode for cityId=$cityId")
                        lastResult = FetchResult(null, null, httpCode, body.take(500))
                        return@repeat
                    }
                    val snapshot = parseHabousSchedule(body)
                    Log.d("PRAYER_SYNC", "Months=${snapshot.availableMonths.size} Days=${snapshot.schedule.size}")
                    if (snapshot.availableMonths.isNotEmpty()) {
                        Log.d("PRAYER_SYNC", "FirstMonth=${snapshot.availableMonths.first()} LastMonth=${snapshot.availableMonths.last()}")
                    }
                    if (snapshot.schedule.isNotEmpty()) return snapshot
                    Log.w("PRAYER_SYNC", "Schedule empty after parse")
                    lastResult = FetchResult(snapshot, null, httpCode, body.take(1000))
                }
            } catch (e: Exception) {
                Log.e("PRAYER_SYNC", "Habous request failed (attempt ${attempt + 1})", e)
                lastResult = FetchResult(null, e, null, null)
                if (attempt == 0) Thread.sleep(2000)
            }
        }
        val r = lastResult
        if (r != null) {
            if (r.exception != null) {
                Log.e("PRAYER_DEBUG", "Habous fetch FAILED — exception=${r.exception::class.simpleName} msg=\"${r.exception.message}\" cityId=$cityId url=$url", r.exception)
            } else {
                Log.e("PRAYER_DEBUG", "Habous fetch FAILED — httpCode=${r.httpCode} cityId=$cityId url=$url")
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

        val rows = document.select("#horaire tr").drop(1)

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

            result[date] = PrayerTimes(fajr, shuruq, dhuhr, asr, maghrib, isha)
        }

        Log.d("PRAYER_SYNC", "Parsed days = ${result.size}")
        if (result.isEmpty()) {
            Log.d("PRAYER_SYNC", "document.title() = ${document.title()}")
            Log.d("PRAYER_SYNC", "URL = ${document.location()}")
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
}
