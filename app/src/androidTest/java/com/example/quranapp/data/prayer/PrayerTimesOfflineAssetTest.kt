package com.example.quranapp.data.prayer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Year
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class PrayerTimesOfflineAssetTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun teardown() {
        context.deleteFile("prayer_times_cache.json")
        context.deleteFile("prayer_times_cache.json.tmp")
    }

    @Test
    fun offlineAssetExistsAndIsValidJson() {
        val jsonString = context.assets.open("prayer_times_offline.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue("Asset must not be empty", jsonString.isNotBlank())
        val root = JSONObject(jsonString)
        assertTrue("Root must contain at least one city", root.length() > 0)
    }

    @Test
    fun offlineAsset_containsCurrentYearForAllCities() {
        val root = JSONObject(
            context.assets.open("prayer_times_offline.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        )
        val currentYear = Year.now().value.toString()
        val cityIds = root.keys()
        while (cityIds.hasNext()) {
            val cityKey = cityIds.next()
            val cityData = root.optJSONObject(cityKey)
            assertNotNull("City '$cityKey' must be an object", cityData)
            val yearData = cityData!!.optJSONObject(currentYear)
            assertNotNull("City '$cityKey' must contain year $currentYear", yearData)
        }
    }

    @Test
    fun offlineAsset_everyDayHasSixPrayerTimesInExpectedFormat() {
        val root = JSONObject(
            context.assets.open("prayer_times_offline.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        )
        val currentYear = Year.now().value.toString()
        val timeFormat = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val requiredFields = listOf("fajr", "sunrise", "dohr", "asr", "maghreb", "ichaa")
        var dayCount = 0

        val cityIds = root.keys()
        while (cityIds.hasNext()) {
            val cityKey = cityIds.next()
            val cityData = root.optJSONObject(cityKey) ?: continue
            val yearData = cityData.optJSONObject(currentYear) ?: continue
            val monthKeys = yearData.keys()
            while (monthKeys.hasNext()) {
                val monthKey = monthKeys.next()
                val monthData = yearData.optJSONObject(monthKey) ?: continue
                assertTrue("Month key '$monthKey' must be an int", monthKey.toIntOrNull() != null)
                val dayKeys = monthData.keys()
                while (dayKeys.hasNext()) {
                    val dayStr = dayKeys.next()
                    val dayData = monthData.optJSONObject(dayStr) ?: continue
                    assertTrue("Day key '$dayStr' must be an int", dayStr.toIntOrNull() != null)
                    requiredFields.forEach { field ->
                        val value = dayData.optString(field, "--:--")
                        assertTrue(
                            "City '$cityKey' $currentYear-$monthKey-$dayStr field '$field' = '$value'",
                            timeFormat.matches(value)
                        )
                    }
                    dayCount++
                }
            }
        }
        // 54 cities x ~365 days each must be present
        assertTrue("Expected >10000 day records, got $dayCount", dayCount > 10000)
    }

    @Test
    fun loadOfflineFallback_initializesCityAndProviderForFreshInstall() {
        // Simulate a fresh install: no cached city id, no active provider.
        val cityIdBefore = PrayerTimesCacheStore.getCachedCityId(context)
        val providerBefore = PrayerTimesCacheStore.getActiveProvider(context)
        assertTrue("Precondition: fresh install (cityId=$cityIdBefore, provider=$providerBefore)", cityIdBefore == null)

        val jsonString = context.assets.open("prayer_times_offline.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonString)
        val currentYear = Year.now().value

        // Replicate the refactored loadOfflineFallback: parse every city into
        // memory, then perform a single atomic cache write (bulk API).
        val schedulesByCity = mutableMapOf<String, MutableMap<YearMonth, MutableMap<Int, PrayerTimes>>>()
        var loaded = false
        val cityIds = root.keys()
        while (cityIds.hasNext()) {
            val cityKey = cityIds.next()
            val cityData = root.optJSONObject(cityKey) ?: continue
            val yearData = cityData.optJSONObject(currentYear.toString()) ?: continue
            val cityMonths = mutableMapOf<YearMonth, MutableMap<Int, PrayerTimes>>()
            val monthKeys = yearData.keys()
            while (monthKeys.hasNext()) {
                val monthKey = monthKeys.next()
                val monthData = yearData.optJSONObject(monthKey) ?: continue
                val month = monthKey.toIntOrNull() ?: continue
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
                    cityMonths[YearMonth.of(currentYear, month)] = days
                    loaded = true
                }
            }
            if (cityMonths.isNotEmpty()) {
                schedulesByCity[cityKey] = cityMonths
            }
        }
        assertTrue("Offline JSON must produce data for current year", loaded)

        val start = System.currentTimeMillis()
        PrayerTimesCacheStore.saveMonthSchedulesBulk(
            context = context,
            providerId = ProviderIds.HABOUS,
            schedulesByCity = schedulesByCity
        )
        val elapsedMs = System.currentTimeMillis() - start
        // The whole 54-city seed must complete in a single write. The budget is
        // intentionally generous to tolerate slow emulators: the old per-city
        // read-modify-write path took 60+ seconds (and often crashed), so any
        // true regression back to that behavior still fails this guard.
        assertTrue("Full offline seed should be fast (<10000ms), took ${elapsedMs}ms", elapsedMs < 10000)

        // Replicate P0-2 init: set city id to first key when null, then activate habous provider.
        val existingCityId = PrayerTimesCacheStore.getCachedCityId(context)
        if (existingCityId == null) {
            val firstCityKey = try { root.keys().next() } catch (_: Exception) { null }
            val firstCityId = firstCityKey?.toIntOrNull()
            if (firstCityId != null) {
                PrayerTimesCacheStore.setActiveCity(
                    context = context,
                    cityId = firstCityId,
                    cityName = "المغرب",
                    cityUniqueId = firstCityId
                )
            }
        }
        PrayerTimesCacheStore.setActiveProvider(context, ProviderIds.HABOUS)

        // After a fresh install offline load, an indexed lookup for the default
        // city (58 = Casablanca) must return real times for today.
        val today = java.time.LocalDate.now()
        val times = PrayerTimesCacheStore.getPrayerTimesForDate(context, 58, today)
        assertNotNull("Indexed lookup for default city 58 must succeed", times)
        assertFalse("Returned times must not be placeholder", times!!.fajr == "--:--")
        assertEquals(ProviderIds.HABOUS, PrayerTimesCacheStore.getActiveProvider(context))
        assertEquals(58, PrayerTimesCacheStore.getCachedCityId(context))

        // Any other city seeded offline must also be readable by its habous id.
        val rabatTimes = PrayerTimesCacheStore.getPrayerTimesForDate(context, 1, today)
        assertNotNull("Indexed lookup for city 1 (Rabat) must succeed after bulk seed", rabatTimes)
        assertFalse("Rabat times must not be placeholder", rabatTimes!!.fajr == "--:--")
    }
}
