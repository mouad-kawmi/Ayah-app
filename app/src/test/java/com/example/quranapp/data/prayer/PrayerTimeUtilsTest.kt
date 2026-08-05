package com.example.quranapp.data.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrayerTimeUtilsTest {

    // --- getMonthsInRange ---

    @Test
    fun getMonthsInRange_sameMonth() {
        val result = PrayerTimeUtils.getMonthsInRange(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        )
        assertEquals(listOf(2026 to 7), result)
    }

    @Test
    fun getMonthsInRange_twoMonths() {
        val result = PrayerTimeUtils.getMonthsInRange(
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 8, 15)
        )
        assertEquals(listOf(2026 to 7, 2026 to 8), result)
    }

    @Test
    fun getMonthsInRange_acrossYearBoundary() {
        val result = PrayerTimeUtils.getMonthsInRange(
            LocalDate.of(2026, 11, 15),
            LocalDate.of(2027, 2, 10)
        )
        assertEquals(
            listOf(2026 to 11, 2026 to 12, 2027 to 1, 2027 to 2),
            result
        )
    }

    @Test
    fun getMonthsInRange_sixtyDaysRange() {
        val result = PrayerTimeUtils.getMonthsInRange(
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 9, 13)
        )
        assertEquals(
            listOf(2026 to 7, 2026 to 8, 2026 to 9),
            result
        )
    }

    @Test
    fun getMonthsInRange_sixtyDaysSpanningYear() {
        val result = PrayerTimeUtils.getMonthsInRange(
            LocalDate.of(2026, 12, 15),
            LocalDate.of(2027, 2, 13)
        )
        assertEquals(
            listOf(2026 to 12, 2027 to 1, 2027 to 2),
            result
        )
    }

    @Test
    fun getMonthsInRange_startEqualsEnd() {
        val d = LocalDate.of(2026, 7, 15)
        val result = PrayerTimeUtils.getMonthsInRange(d, d)
        assertEquals(listOf(2026 to 7), result)
    }

    // --- distanceKm ---

    @Test
    fun distanceKm_zeroDistance() {
        val d = PrayerTimeUtils.distanceKm(33.5731, -7.5898, 33.5731, -7.5898)
        assertTrue(d < 0.001)
    }

    @Test
    fun distanceKm_casablancaToRabat() {
        val d = PrayerTimeUtils.distanceKm(33.5731, -7.5898, 34.0209, -6.8416)
        assertTrue("distance=$d", d in 60.0..100.0)
    }

    @Test
    fun distanceKm_casablancaToMakkah() {
        val d = PrayerTimeUtils.distanceKm(33.5731, -7.5898, 21.4225, 39.8262)
        assertTrue("distance=$d", d in 4500.0..5500.0)
    }

    @Test
    fun distanceKm_symmetric() {
        val d1 = PrayerTimeUtils.distanceKm(30.0, 0.0, -30.0, 180.0)
        val d2 = PrayerTimeUtils.distanceKm(-30.0, 180.0, 30.0, 0.0)
        assertEquals(d1, d2, 0.001)
    }

    @Test
    fun distanceKm_antipodal() {
        val d = PrayerTimeUtils.distanceKm(0.0, 0.0, 0.0, 180.0)
        assertTrue("distance=$d", d in 19900.0..20100.0)
    }

    // --- isInMoroccoBoundingBox ---

    @Test
    fun isInMoroccoBoundingBox_casablanca() {
        assertTrue(PrayerTimeUtils.isInMoroccoBoundingBox(33.5731, -7.5898))
    }

    @Test
    fun isInMoroccoBoundingBox_rabat() {
        assertTrue(PrayerTimeUtils.isInMoroccoBoundingBox(34.0209, -6.8416))
    }

    @Test
    fun isInMoroccoBoundingBox_marrakech() {
        assertTrue(PrayerTimeUtils.isInMoroccoBoundingBox(31.6295, -8.0))
    }

    @Test
    fun isInMoroccoBoundingBox_parisOutside() {
        assertFalse(PrayerTimeUtils.isInMoroccoBoundingBox(48.8566, 2.3522))
    }

    @Test
    fun isInMoroccoBoundingBox_londonOutside() {
        assertFalse(PrayerTimeUtils.isInMoroccoBoundingBox(51.5074, -0.1278))
    }

    @Test
    fun isInMoroccoBoundingBox_cairoOutside() {
        assertFalse(PrayerTimeUtils.isInMoroccoBoundingBox(30.0444, 31.2357))
    }

    @Test
    fun isInMoroccoBoundingBox_makkahOutside() {
        assertFalse(PrayerTimeUtils.isInMoroccoBoundingBox(21.4225, 39.8262))
    }

    @Test
    fun isInMoroccoBoundingBox_boundaryPoints() {
        assertTrue(PrayerTimeUtils.isInMoroccoBoundingBox(21.0, -10.0))
        assertTrue(PrayerTimeUtils.isInMoroccoBoundingBox(36.0, -5.0))
        assertTrue(PrayerTimeUtils.isInMoroccoBoundingBox(30.0, -1.0))
        assertTrue(PrayerTimeUtils.isInMoroccoBoundingBox(30.0, -17.0))
    }
}
