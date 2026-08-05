package com.example.quranapp.data.prayer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class PrayerTimesCacheStoreTest {

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

    private fun sampleTimes(day: Int): PrayerTimes = PrayerTimes(
        fajr = "05:00", shuruq = "06:30", dhuhr = "13:30",
        asr = "16:30", maghrib = "19:00", isha = "20:30"
    )

    @Test
    fun setAndGetCachedCityId() {
        PrayerTimesCacheStore.setActiveCity(
            context = context, cityId = 58, cityName = "Casablanca", cityUniqueId = 58
        )
        assertEquals(58, PrayerTimesCacheStore.getCachedCityId(context))
    }

    @Test
    fun getCachedCityId_returnsNullWhenNotSet() {
        assertNull(PrayerTimesCacheStore.getCachedCityId(context))
    }

    @Test
    fun saveAndHasCompleteMonthSchedule_30DayMonth() {
        PrayerTimesCacheStore.setActiveCity(
            context = context, cityId = 58, cityName = "Casablanca", cityUniqueId = 58
        )
        val days = (1..30).associateWith { sampleTimes(it) }
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.HABOUS,
            year = 2026, month = 6, days = days
        )
        assertTrue(
            PrayerTimesCacheStore.hasCompleteMonthSchedule(
                context = context, providerId = ProviderIds.HABOUS, year = 2026, month = 6
            )
        )
    }

    @Test
    fun hasCompleteMonthSchedule_missingDayReturnsFalse() {
        PrayerTimesCacheStore.setActiveCity(
            context = context, cityId = 58, cityName = "Casablanca", cityUniqueId = 58
        )
        val days = (1..15).associateWith { sampleTimes(it) }
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.HABOUS,
            year = 2026, month = 7, days = days
        )
        assertTrue(
            !PrayerTimesCacheStore.hasCompleteMonthSchedule(
                context = context, providerId = ProviderIds.HABOUS, year = 2026, month = 7
            )
        )
    }

    @Test
    fun getPrayerTimesForDate_returnsSavedTimes() {
        PrayerTimesCacheStore.setActiveCity(
            context = context, cityId = 58, cityName = "Casablanca", cityUniqueId = 58
        )
        PrayerTimesCacheStore.setActiveProvider(context, ProviderIds.HABOUS)
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.HABOUS,
            year = 2026, month = 7,
            days = mapOf(15 to PrayerTimes(
                fajr = "04:30", shuruq = "06:00", dhuhr = "13:30",
                asr = "17:00", maghrib = "20:00", isha = "21:30"
            ))
        )
        val result = PrayerTimesCacheStore.getPrayerTimesForDate(
            context = context, date = LocalDate.of(2026, 7, 15)
        )
        assertNotNull(result)
        assertEquals("04:30", result!!.fajr)
        assertEquals("13:30", result.dhuhr)
    }

    @Test
    fun getAnyAvailablePrayerTimesForDate() {
        PrayerTimesCacheStore.setActiveCity(
            context = context, cityId = 58, cityName = "Casablanca", cityUniqueId = 58
        )
        PrayerTimesCacheStore.setActiveProvider(context, ProviderIds.HABOUS)
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.HABOUS,
            year = 2026, month = 7, days = mapOf(15 to sampleTimes(15))
        )
        assertNotNull(
            PrayerTimesCacheStore.getAnyAvailablePrayerTimesForDate(
                context = context, date = LocalDate.of(2026, 7, 15)
            )
        )
    }

    @Test
    fun pruneOldMonths_removesOldData() {
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.HABOUS, identifier = "58",
            year = 2025, month = 1,
            days = mapOf(1 to sampleTimes(1))
        )
        PrayerTimesCacheStore.pruneOldMonths(
            context = context, minimumMonthToKeep = LocalDate.of(2026, 1, 1)
        )
        assertNull(
            PrayerTimesCacheStore.getPrayerTimesForDate(
                context = context, cityId = 58, date = LocalDate.of(2025, 1, 1)
            )
        )
    }

    @Test
    fun saveMonthSchedule_legacyOverload() {
        val days = (1..30).associateWith { sampleTimes(it) }
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, cityId = 58, cityName = "Casablanca",
            year = 2026, month = 8, days = days
        )
        assertTrue(PrayerTimesCacheStore.hasMonthSchedule(context, 58, 2026, 8))
    }

    @Test
    fun saveAndReadAcrossProviderSwitch() {
        PrayerTimesCacheStore.setActiveCity(
            context = context, cityId = 58, cityName = "Casablanca", cityUniqueId = 58,
            latitude = 33.5731, longitude = -7.5898
        )
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.HABOUS,
            year = 2026, month = 7, days = mapOf(15 to sampleTimes(15))
        )
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.ALADHAN,
            year = 2026, month = 7, days = mapOf(15 to sampleTimes(15))
        )
        PrayerTimesCacheStore.setActiveProvider(context, ProviderIds.ALADHAN)
        assertNotNull(
            PrayerTimesCacheStore.getPrayerTimesForDate(
                context = context, date = LocalDate.of(2026, 7, 15)
            )
        )
    }

    @Test
    fun migrateOldKeys_preservesData() {
        PrayerTimesCacheStore.setActiveCity(
            context = context, cityId = 58, cityName = "Casablanca", cityUniqueId = 58
        )
        PrayerTimesCacheStore.saveMonthSchedule(
            context = context, providerId = ProviderIds.HABOUS, identifier = "58",
            year = 2026, month = 7, days = mapOf(15 to sampleTimes(15))
        )
        PrayerTimesCacheStore.migrateOldKeys(context)
        assertNotNull(
            PrayerTimesCacheStore.getPrayerTimesForDate(
                context = context, cityId = 58, date = LocalDate.of(2026, 7, 15)
            )
        )
    }
}
