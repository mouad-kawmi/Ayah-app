package com.example.quranapp.data.prayer

import android.content.Context
import android.util.Log
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

object PrayerTimesCacheStore {
    private const val CACHE_FILE_NAME = "prayer_times_cache.json"
    private const val TAG = "PRAYER_CACHE"

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE_NAME)

    @Synchronized
    private fun readRoot(context: Context): JSONObject {
        val file = cacheFile(context)
        if (!file.exists()) {
            return JSONObject().apply {
                put("months", JSONObject())
                put("active_provider", "")
            }
        }
        return try {
            val content = file.readText()
            if (content.isBlank()) {
                JSONObject().apply {
                    put("months", JSONObject())
                    put("active_provider", "")
                }
            } else {
                JSONObject(content).apply {
                    if (!has("active_provider")) put("active_provider", "")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read prayer times cache — returning empty", e)
            DebugLogger.warning(
                LogCategory.CACHE,
                Instrumentation.line("cache", Instrumentation.NO_TRACE, null, ErrorCode.PRAYER_CACHE_CORRUPTED.prefix("Failed to read prayer times cache — returning empty")),
                e
            )
            JSONObject().apply {
                put("months", JSONObject())
                put("active_provider", "")
            }
        }
    }

    @Synchronized
    private fun writeRoot(context: Context, root: JSONObject) {
        if (!root.has("months")) {
            root.put("months", JSONObject())
        }
        if (!root.has("active_provider")) {
            root.put("active_provider", "")
        }
        val file = cacheFile(context)
        val tempFile = File(file.parentFile, "$CACHE_FILE_NAME.tmp")
        tempFile.writeText(root.toString())
        if (!tempFile.renameTo(file)) {
            file.writeText(root.toString())
        }
        tempFile.delete()
    }

    private fun toMonthKey(providerId: String, identifier: String, year: Int, month: Int): String =
        "$providerId:$identifier:$year-${month.toString().padStart(2, '0')}"

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

    // --- Provider management ---

    fun setActiveProvider(context: Context, providerId: String) {
        val root = readRoot(context)
        root.put("active_provider", providerId)
        writeRoot(context, root)
    }

    fun getActiveProvider(context: Context): String? {
        val root = readRoot(context)
        val provider = root.optString("active_provider", "")
        return provider.takeIf { it.isNotBlank() }
    }

    // --- City metadata (shared across providers) ---

    @Synchronized
    fun setActiveCity(
        context: Context,
        cityId: Int,
        cityName: String,
        cityUniqueId: Int,
        latitude: Double? = null,
        longitude: Double? = null,
        fallbackCityId: Int? = null
    ) {
        val root = readRoot(context)
        root.put("city_id", cityId)
        root.put("city_name", cityName)
        root.put("city_unique_id", cityUniqueId)
        if (latitude != null) root.put("latitude", latitude)
        if (longitude != null) root.put("longitude", longitude)
        if (fallbackCityId != null) {
            root.put("fallback_city_id", fallbackCityId)
        } else {
            root.remove("fallback_city_id")
        }
        writeRoot(context, root)
    }

    fun getCachedCityId(context: Context): Int? {
        val root = readRoot(context)
        return if (root.has("city_id")) root.optInt("city_id") else null
    }

    fun getCachedCityName(context: Context): String? {
        return readRoot(context).optString("city_name").takeIf { it.isNotBlank() }
    }

    fun getCachedCityUniqueId(context: Context): Int? {
        val root = readRoot(context)
        return if (root.has("city_unique_id")) root.optInt("city_unique_id") else null
    }

    fun getFallbackCityId(context: Context): Int? {
        val root = readRoot(context)
        return if (root.has("fallback_city_id")) root.optInt("fallback_city_id") else null
    }

    fun getCachedLatitude(context: Context): Double? {
        val root = readRoot(context)
        return if (root.has("latitude")) root.optDouble("latitude") else null
    }

    fun getCachedLongitude(context: Context): Double? {
        val root = readRoot(context)
        return if (root.has("longitude")) root.optDouble("longitude") else null
    }

    // --- Month schedule save (new provider-aware API) ---

    @Synchronized
    fun saveMonthSchedule(
        context: Context,
        providerId: String,
        year: Int,
        month: Int,
        days: Map<Int, PrayerTimes>,
        identifier: String = "",
        updateActiveCity: Boolean = true
    ) {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: JSONObject()
        val effectiveId = identifier.ifBlank {
            getIdentifierForKey(context, providerId) ?: root.optInt("city_id", 0).toString()
        }
        val monthKey = toMonthKey(providerId, effectiveId, year, month)
        val monthJson = months.optJSONObject(monthKey) ?: JSONObject()

        days.toSortedMap().forEach { (day, prayerTimes) ->
            monthJson.put(day.toString(), prayerTimesToJson(prayerTimes))
        }

        months.put(monthKey, monthJson)
        root.put("months", months)
        if (updateActiveCity) {
            root.put("active_provider", providerId)
        }
        root.put("updated_at", System.currentTimeMillis())
        writeRoot(context, root)
        Log.i("PrayerSync", "CacheWrite key=$monthKey days=${days.size}")
    }

    // --- Month schedule read (provider-aware) ---

    /**
     * Persists many month schedules in a single read/merge/write cycle.
     * Unlike calling [saveMonthSchedule] repeatedly (which re-reads and re-writes
     * the entire cache file every time), this builds the complete structure in
     * memory and performs one atomic write. Used by offline first-launch seeding
     * where hundreds of months are loaded at once.
     *
     * @param schedulesByCity identifier (e.g. habous city key) -> YearMonth -> day -> times
     */
    @Synchronized
    fun saveMonthSchedulesBulk(
        context: Context,
        providerId: String,
        schedulesByCity: Map<String, Map<YearMonth, Map<Int, PrayerTimes>>>,
        updateActiveCity: Boolean = false
    ) {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: JSONObject().also { root.put("months", it) }

        schedulesByCity.forEach { (identifier, byMonth) ->
            byMonth.forEach { (yearMonth, days) ->
                val monthKey = toMonthKey(providerId, identifier, yearMonth.year, yearMonth.monthValue)
                val monthJson = months.optJSONObject(monthKey) ?: JSONObject()
                days.toSortedMap().forEach { (day, prayerTimes) ->
                    monthJson.put(day.toString(), prayerTimesToJson(prayerTimes))
                }
                months.put(monthKey, monthJson)
            }
        }

        if (updateActiveCity) {
            root.put("active_provider", providerId)
        }
        root.put("updated_at", System.currentTimeMillis())
        writeRoot(context, root)
        Log.i(TAG, "CacheBulkWrite provider=$providerId cities=${schedulesByCity.size} months=${schedulesByCity.values.sumOf { it.size }}")
    }

    fun hasCompleteMonthSchedule(context: Context, providerId: String, year: Int, month: Int): Boolean {
        val months = readRoot(context).optJSONObject("months") ?: return false
        val root = readRoot(context)
        val identifier = getIdentifierForKey(context, providerId) ?: return false
        val monthKey = toMonthKey(providerId, identifier, year, month)
        val monthJson = months.optJSONObject(monthKey) ?: return false
        val daysInMonth = runCatching { YearMonth.of(year, month).lengthOfMonth() }.getOrNull() ?: return false
        val complete = (1..daysInMonth).all { day -> monthJson.has(day.toString()) }
        Log.i("PrayerSync", "CacheHasComplete key=$monthKey result=$complete")
        return complete
    }

    fun getPrayerTimesForDate(context: Context, date: LocalDate): PrayerTimes? {
        val providerId = getActiveProvider(context) ?: return null
        val identifier = getIdentifierForKey(context, providerId) ?: return null
        val months = readRoot(context).optJSONObject("months") ?: return null
        val monthKey = toMonthKey(providerId, identifier, date.year, date.monthValue)
        val monthJson = months.optJSONObject(monthKey) ?: run { Log.i("PrayerSync", "CacheRead key=$monthKey result=MISS"); return null }
        val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: run { Log.i("PrayerSync", "CacheRead key=$monthKey day=${date.dayOfMonth} result=MISS"); return null }
        Log.i("PrayerSync", "CacheRead key=$monthKey day=${date.dayOfMonth} result=HIT")
        return jsonToPrayerTimes(dayJson)
    }

    fun getPrayerTimesForDate(context: Context, cityId: Int, date: LocalDate): PrayerTimes? {
        val months = readRoot(context).optJSONObject("months") ?: return null
        val monthKey = toMonthKey("habous", cityId.toString(), date.year, date.monthValue)
        val monthJson = months.optJSONObject(monthKey) ?: run { Log.i("PrayerSync", "CacheRead key=$monthKey result=MISS"); return null }
        val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: run { Log.i("PrayerSync", "CacheRead key=$monthKey day=${date.dayOfMonth} result=MISS"); return null }
        Log.i("PrayerSync", "CacheRead key=$monthKey day=${date.dayOfMonth} result=HIT")
        return jsonToPrayerTimes(dayJson)
    }

    fun getAnyAvailablePrayerTimesForDate(context: Context, date: LocalDate): PrayerTimes? {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: return null
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }
        val activeProvider = getActiveProvider(context)
        for (key in allKeys) {
            if (activeProvider != null && !key.startsWith("$activeProvider:")) continue
            val monthJson = months.optJSONObject(key) ?: continue
            val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: continue
            Log.i("PrayerSync", "CacheReadAny key=$key day=${date.dayOfMonth} result=HIT")
            return jsonToPrayerTimes(dayJson)
        }
        Log.i("PrayerSync", "CacheReadAny result=MISS")
        return null
    }

    // --- Helpers ---

    private fun getIdentifierForKey(context: Context, providerId: String): String? {
        return when (providerId) {
            ProviderIds.HABOUS -> getCachedCityId(context)?.toString()
            ProviderIds.ALADHAN, ProviderIds.SUNCALC -> {
                val lat = getCachedLatitude(context)
                val lon = getCachedLongitude(context)
                if (lat != null && lon != null) "${"%.4f".format(lat)}|${"%.4f".format(lon)}" else null
            }
            else -> getCachedCityId(context)?.toString()
        }
    }

    fun migrateOldKeys(context: Context) {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: return
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }
        var changed = false
        val oldCityId = root.optInt("city_id", -1)
        val providerId = getActiveProvider(context) ?: ProviderIds.HABOUS

        allKeys.forEach { key ->
            if (!key.contains(":")) {
                // Old format: "cityId:year-month" or just "year-month"
                val datePart = key.substringAfter(":")
                if (datePart.matches(Regex("\\d{4}-\\d{2}"))) {
                    months.put("$providerId:$key", months.get(key))
                    months.remove(key)
                    changed = true
                }
            } else if (key.startsWith("$oldCityId:")) {
                val rest = key.substringAfter(":")
                val newKey = "$providerId:$oldCityId:$rest"
                if (newKey != key) {
                    months.put(newKey, months.get(key))
                    months.remove(key)
                    changed = true
                }
            }
        }
        if (changed) {
            root.put("months", months)
            writeRoot(context, root)
        }
    }

    // --- Legacy: still used by ViewModel cityId-based lookups ---

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
        val monthKey = toMonthKey("habous", cityId.toString(), year, month)
        val monthJson = months.optJSONObject(monthKey) ?: JSONObject()

        days.toSortedMap().forEach { (day, prayerTimes) ->
            monthJson.put(day.toString(), prayerTimesToJson(prayerTimes))
        }

        months.put(monthKey, monthJson)
        root.put("months", months)
        if (updateActiveCity) {
            root.put("city_id", cityId)
            root.put("city_name", cityName)
            root.put("active_provider", ProviderIds.HABOUS)
            if (fallbackCityId != null) {
                root.put("fallback_city_id", fallbackCityId)
            } else {
                root.remove("fallback_city_id")
            }
        }
        root.put("updated_at", System.currentTimeMillis())
        writeRoot(context, root)
        Log.i("PrayerSync", "CacheWriteLegacy key=$monthKey days=${days.size}")
    }

    fun hasMonthSchedule(context: Context, cityId: Int, year: Int, month: Int): Boolean {
        val months = readRoot(context).optJSONObject("months") ?: return false
        return months.has(toMonthKey("habous", cityId.toString(), year, month))
    }

    fun hasMonthSchedule(context: Context, year: Int, month: Int): Boolean {
        val cityId = getCachedCityId(context) ?: return false
        return hasMonthSchedule(context, cityId, year, month)
    }

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
        val oldPrefix = "habous:$oldCityId:"
        val newPrefix = "habous:$newCityId:"
        allKeys.forEach { key ->
            if (key.startsWith(oldPrefix)) {
                val rest = key.substring(oldPrefix.length)
                months.put("$newPrefix$rest", months.get(key))
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

    fun getPrayerTimesForDates(context: Context, dates: List<LocalDate>): Map<LocalDate, PrayerTimes> {
        if (dates.isEmpty()) return emptyMap()
        val root = readRoot(context)
        val providerId = getActiveProvider(context) ?: return emptyMap()
        val identifier = getIdentifierForKey(context, providerId) ?: return emptyMap()
        val months = root.optJSONObject("months") ?: return emptyMap()
        val result = mutableMapOf<LocalDate, PrayerTimes>()
        dates.forEach { date ->
            val monthKey = toMonthKey(providerId, identifier, date.year, date.monthValue)
            val monthJson = months.optJSONObject(monthKey) ?: return@forEach
            val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: return@forEach
            result[date] = jsonToPrayerTimes(dayJson)
        }
        return result
    }

    fun getPrayerTimesForDatesAnyCity(context: Context, dates: List<LocalDate>): Map<LocalDate, PrayerTimes> {
        if (dates.isEmpty()) return emptyMap()
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: return emptyMap()
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }
        val activeProvider = getActiveProvider(context)
        if (activeProvider != null) {
            val filtered = allKeys.filter { it.startsWith("$activeProvider:") }
            if (filtered.isEmpty()) return emptyMap()
            val result = mutableMapOf<LocalDate, PrayerTimes>()
            dates.forEach { date ->
                for (key in filtered) {
                    val monthJson = months.optJSONObject(key) ?: continue
                    val dayJson = monthJson.optJSONObject(date.dayOfMonth.toString()) ?: continue
                    result[date] = jsonToPrayerTimes(dayJson)
                    break
                }
            }
            return result
        }
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

    @Synchronized
    fun pruneOldMonths(context: Context, minimumMonthToKeep: LocalDate) {
        val root = readRoot(context)
        val months = root.optJSONObject("months") ?: return
        val allKeys = buildList {
            val iter = months.keys()
            while (iter.hasNext()) add(iter.next())
        }
        val keysToRemove = allKeys.filter { key ->
            val parts = key.split(":")
            val datePart = parts.lastOrNull() ?: return@filter false
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
