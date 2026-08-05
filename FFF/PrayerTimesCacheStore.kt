package com.example

import android.content.Context
import android.util.Log
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

object PrayerTimesCacheStore {
    private const val CACHE_FILE_NAME = "prayer_times_cache.json"

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE_NAME)

    @Synchronized
    private fun readRoot(context: Context): JSONObject {
        val readRootStartMs = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "READROOT_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "READROOT_START")
        val file = cacheFile(context)
        if (!file.exists()) {
            Log.d("STARTUP_TRACE", "READROOT_END")
            DebugLogger.debug(LogCategory.PERFORMANCE, "READROOT_END — fileNotFound=true totalMs=${System.currentTimeMillis() - readRootStartMs}")
            return JSONObject().apply {
                put("months", JSONObject())
            }
        }

        return try {
            val fileSize = file.length()
            val fileReadStartMs = System.currentTimeMillis()
            Log.d("STARTUP_TRACE", "FILE_READ_START")
            DebugLogger.debug(LogCategory.PERFORMANCE, "FILE_READ_START")
            val content = file.readText()
            Log.d("STARTUP_TRACE", "FILE_READ_END")
            DebugLogger.debug(LogCategory.PERFORMANCE, "FILE_READ_END — elapsedMs=${System.currentTimeMillis() - fileReadStartMs} fileSize=$fileSize")
            if (content.isBlank()) {
                Log.d("STARTUP_TRACE", "READROOT_END")
                DebugLogger.debug(LogCategory.PERFORMANCE, "READROOT_END — blankFile=true totalMs=${System.currentTimeMillis() - readRootStartMs}")
                JSONObject().apply { put("months", JSONObject()) }
            } else {
                val jsonParseStartMs = System.currentTimeMillis()
                Log.d("STARTUP_TRACE", "JSON_PARSE_START")
                DebugLogger.debug(LogCategory.PERFORMANCE, "JSON_PARSE_START")
                val result = JSONObject(content)
                Log.d("STARTUP_TRACE", "JSON_PARSE_END")
                DebugLogger.debug(LogCategory.PERFORMANCE, "JSON_PARSE_END — elapsedMs=${System.currentTimeMillis() - jsonParseStartMs}")
                Log.d("STARTUP_TRACE", "READROOT_END")
                DebugLogger.debug(LogCategory.PERFORMANCE, "READROOT_END — success=true totalMs=${System.currentTimeMillis() - readRootStartMs}")
                result
            }
        } catch (e: Exception) {
            DebugLogger.warning(LogCategory.CACHE, "Failed to read prayer times cache — returning empty", e)
            Log.d("STARTUP_TRACE", "READROOT_END")
            DebugLogger.debug(LogCategory.PERFORMANCE, "READROOT_END — exception=true totalMs=${System.currentTimeMillis() - readRootStartMs}")
            JSONObject().apply { put("months", JSONObject()) }
        }
    }

    @Synchronized
    private fun writeRoot(context: Context, root: JSONObject) {
        if (!root.has("months")) {
            root.put("months", JSONObject())
        }
        val file = cacheFile(context)
        val tempFile = File(file.parentFile, "$CACHE_FILE_NAME.tmp")
        tempFile.writeText(root.toString())
        if (!tempFile.renameTo(file)) {
            file.writeText(root.toString())
        }
        tempFile.delete()
    }

    private fun toLegacyMonthKey(year: Int, month: Int): String = "%04d-%02d".format(year, month)

    private fun toMonthKey(cityId: Int, year: Int, month: Int): String =
        "$cityId:${toLegacyMonthKey(year, month)}"

    private fun prayerTimesToJson(prayerTimes: PrayerTimes): JSONObject {
        return JSONObject().apply {
            put("fajr", prayerTimes.fajr)
            put("shuruq", prayerTimes.shuruq)
            put("dhuhr", prayerTimes.dhuhr)
            put("asr", prayerTimes.asr)
            put("maghrib", prayerTimes.maghrib)
            put("isha", prayerTimes.isha)
        }
    }

    private fun jsonToPrayerTimes(json: JSONObject): PrayerTimes {
        return PrayerTimes(
            fajr = json.optString("fajr", "--:--"),
            shuruq = json.optString("shuruq", "--:--"),
            dhuhr = json.optString("dhuhr", "--:--"),
            asr = json.optString("asr", "--:--"),
            maghrib = json.optString("maghrib", "--:--"),
            isha = json.optString("isha", "--:--")
        )
    }

    @Synchronized
    fun setActiveCity(
        context: Context,
        cityId: Int,
        cityName: String,
        fallbackCityId: Int? = null
    ) {
        val root = readRoot(context)
        val oldId = if (root.has("city_id")) root.optInt("city_id") else null
        val oldName = root.optString("city_name").takeIf { it.isNotBlank() }
        DebugLogger.debug(LogCategory.CACHE, "setActiveCity() — old cityId=$oldId oldName=$oldName new cityId=$cityId newName=$cityName")
        root.put("city_id", cityId)
        root.put("city_name", cityName)
        if (fallbackCityId != null) {
            root.put("fallback_city_id", fallbackCityId)
        } else {
            root.remove("fallback_city_id")
        }
        writeRoot(context, root)
        DebugLogger.debug(LogCategory.CACHE, "setActiveCity() EXIT — written cityId=$cityId cityName=$cityName")
    }

    @Synchronized
    fun saveMonthSchedule(
        context: Context,
        cityId: Int,
        cityName: String,
        year: Int,
        month: Int,
        days: Map<Int, PrayerTimes>,
        updateActiveCity: Boolean = true,
        fallbackCityId: Int? = null
    ) {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: JSONObject()
        val monthKey = toMonthKey(cityId, year, month)
        val monthJson = months.optJSONObject(monthKey) ?: JSONObject()

        days.toSortedMap().forEach { (day, prayerTimes) ->
            monthJson.put(day.toString(), prayerTimesToJson(prayerTimes))
        }

        months.put(monthKey, monthJson)
        root.put("months", months)
        if (updateActiveCity) {
            root.put("city_id", cityId)
        }
        root.put("city_name", cityName)
        if (fallbackCityId != null) {
            root.put("fallback_city_id", fallbackCityId)
        } else {
            root.remove("fallback_city_id")
        }
        root.put("updated_at", System.currentTimeMillis())
        writeRoot(context, root)
    }

    fun hasMonthSchedule(context: Context, cityId: Int, year: Int, month: Int): Boolean {
        val months = readRoot(context).optJSONObject("months") ?: return false
        return months.has(toMonthKey(cityId, year, month))
    }

    fun hasCompleteMonthSchedule(context: Context, cityId: Int, year: Int, month: Int): Boolean {
        val months = readRoot(context).optJSONObject("months") ?: return false
        val monthJson = months.optJSONObject(toMonthKey(cityId, year, month)) ?: return false
        val daysInMonth = runCatching { YearMonth.of(year, month).lengthOfMonth() }.getOrNull() ?: return false
        return (1..daysInMonth).all { day -> monthJson.has(day.toString()) }
    }

    fun hasMonthSchedule(context: Context, year: Int, month: Int): Boolean {
        val cityId = getCachedCityId(context) ?: return false
        return hasMonthSchedule(context, cityId, year, month)
    }

    fun getPrayerTimesForDate(context: Context, cityId: Int, date: LocalDate): PrayerTimes? {
        val months = readRoot(context).optJSONObject("months") ?: return null
        val monthJson = months.optJSONObject(toMonthKey(cityId, date.year, date.monthValue)) ?: return null
        val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: return null
        val result = jsonToPrayerTimes(dayJson)
        DebugLogger.debug(LogCategory.CACHE, "getPrayerTimesForDate(cityId=$cityId, date=$date) — found=${result != null}")
        return result
    }

    fun getPrayerTimesForDate(context: Context, date: LocalDate): PrayerTimes? {
        val cityId = getCachedCityId(context) ?: return null
        val result = getPrayerTimesForDate(context, cityId, date)
        DebugLogger.debug(LogCategory.CACHE, "getPrayerTimesForDate(date=$date) — cityId=$cityId found=${result != null}")
        return result
    }

    /**
     * Batch read for multiple dates on the same day range — reads the JSON root
     * only once and returns a map, avoiding 15 separate file reads.
     */
    fun getPrayerTimesForDates(context: Context, dates: List<LocalDate>): Map<LocalDate, PrayerTimes> {
        if (dates.isEmpty()) return emptyMap()
        val root = readRoot(context)
        val cityId = if (root.has("city_id")) root.optInt("city_id") else return emptyMap()
        val months = root.optJSONObject("months") ?: return emptyMap()
        val result = mutableMapOf<LocalDate, PrayerTimes>()
        dates.forEach { date ->
            val monthJson = months.optJSONObject(toMonthKey(cityId, date.year, date.monthValue))
                ?: return@forEach
            val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: return@forEach
            result[date] = jsonToPrayerTimes(dayJson)
        }
        return result
    }

    fun getCachedCityId(context: Context): Int? {
        val root = readRoot(context)
        return if (root.has("city_id")) root.optInt("city_id") else null
    }

    /**
     * One-time migration: rekeys all month entries stored under the old city ID
     * to the new habous ID. The [oldToNewMapping] maps previous [OfficialPrayerCity.id]
     * to current [OfficialPrayerCity.habousId]. The cached [cityName] is used to
     * disambiguate when multiple cities shared the same old id.
     * Returns true if any entries were rekeyed.
     */
    @Synchronized
    fun migrateCityIds(
        context: Context,
        oldToNewMapping: Map<Int, Int>,
        cityName: String?
    ): Boolean {
        val root = readRoot(context)
        val oldCityId = if (root.has("city_id")) root.optInt("city_id") else return false
        val newCityId = oldToNewMapping[oldCityId] ?: return false
        if (oldCityId == newCityId) return false

        val months = root.optJSONObject("months") ?: return false
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }

        var changed = false
        allKeys.forEach { key ->
            if (key.startsWith("$oldCityId:")) {
                val datePart = key.substringAfter(":")
                months.put("$newCityId:$datePart", months.get(key))
                months.remove(key)
                changed = true
            }
        }

        if (changed) {
            root.put("city_id", newCityId)
            root.put("months", months)
            writeRoot(context, root)
        }
        return changed
    }

    /**
     * Scans every city's months in the cache and returns prayer times for [date]
     * from the first city that has data for that day.
     * Used as a last-resort fallback when the primary city lookup misses.
     */
    fun getAnyAvailablePrayerTimesForDate(context: Context, date: LocalDate): PrayerTimes? {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: return null
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }
        for (key in allKeys) {
            val monthJson = months.optJSONObject(key) ?: continue
            val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: continue
            return jsonToPrayerTimes(dayJson)
        }
        return null
    }

    /**
     * Batch read for multiple dates across ALL city IDs in the cache.
     * Used by [PrayerAlarmScheduler] as a fallback when the primary city returns nothing.
     */
    fun getPrayerTimesForDatesAnyCity(context: Context, dates: List<LocalDate>): Map<LocalDate, PrayerTimes> {
        if (dates.isEmpty()) return emptyMap()
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: return emptyMap()
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }
        if (allKeys.isEmpty()) return emptyMap()

        val result = mutableMapOf<LocalDate, PrayerTimes>()
        dates.forEach { date ->
            for (key in allKeys) {
                val monthJson = months.optJSONObject(key) ?: continue
                val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: continue
                result[date] = jsonToPrayerTimes(dayJson)
                break
            }
        }
        return result
    }

    fun getFallbackCityId(context: Context): Int? {
        val root = readRoot(context)
        return if (root.has("fallback_city_id")) root.optInt("fallback_city_id") else null
    }

    fun getCachedCityName(context: Context): String? {
        val result = readRoot(context).optString("city_name").takeIf { it.isNotBlank() }
        DebugLogger.debug(LogCategory.CACHE, "getCachedCityName() — returned=$result")
        return result
    }

    /**
     * Bug fix: collect all keys into a list before removal to avoid
     * concurrent modification of the JSONObject's internal key set during iteration.
     */
    @Synchronized
    fun pruneOldMonths(context: Context, minimumMonthToKeep: LocalDate) {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: return

        // Collect all keys first — avoid mutating while iterating.
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }

        val keysToRemove = allKeys.filter { key ->
            val datePart = key.substringAfter(":", key)
            val year = datePart.substringBefore("-").toIntOrNull() ?: return@filter false
            val month = datePart.substringAfter("-").toIntOrNull() ?: return@filter false
            year < minimumMonthToKeep.year ||
                    (year == minimumMonthToKeep.year && month < minimumMonthToKeep.monthValue)
        }

        keysToRemove.forEach(months::remove)
        root.put("months", months)
        writeRoot(context, root)
    }
}
