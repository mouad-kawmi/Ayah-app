package com.example.quranapp.data.prayer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SunPositionCalculatorTest {

    @Test
    fun calculate_returnsValidTimeFormat() {
        val result = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 7, 15))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue("fajr=${result.fajr}", timeRegex.matches(result.fajr))
        assertTrue("shuruq=${result.shuruq}", timeRegex.matches(result.shuruq))
        assertTrue("dhuhr=${result.dhuhr}", timeRegex.matches(result.dhuhr))
        assertTrue("asr=${result.asr}", timeRegex.matches(result.asr))
        assertTrue("maghrib=${result.maghrib}", timeRegex.matches(result.maghrib))
        assertTrue("isha=${result.isha}", timeRegex.matches(result.isha))
    }

    @Test
    fun calculate_timesAreChronological() {
        val result = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 7, 15))
        val toMinutes = { s: String -> s.substringBefore(":").toInt() * 60 + s.substringAfter(":").toInt() }
        assertTrue(toMinutes(result.fajr) < toMinutes(result.shuruq))
        assertTrue(toMinutes(result.shuruq) < toMinutes(result.dhuhr))
        assertTrue(toMinutes(result.dhuhr) < toMinutes(result.asr))
        assertTrue(toMinutes(result.asr) < toMinutes(result.maghrib))
        assertTrue(toMinutes(result.maghrib) <= toMinutes(result.isha))
    }

    @Test
    fun calculate_differentLocationsDifferentResults() {
        val casablanca = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 7, 15))
        val rabat = SunPositionCalculator.calculate(34.0209, -6.8416, LocalDate.of(2026, 7, 15))
        assertTrue(casablanca.fajr != rabat.fajr || casablanca.dhuhr != rabat.dhuhr)
    }

    @Test
    fun calculate_differentDatesDifferentResults() {
        val july = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 7, 15))
        val december = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 12, 15))
        assertTrue(july.fajr != december.fajr)
    }

    @Test
    fun calculate_equatorDate() {
        val result = SunPositionCalculator.calculate(0.0, 0.0, LocalDate.of(2026, 3, 20))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue(timeRegex.matches(result.fajr))
        assertTrue(timeRegex.matches(result.isha))
    }

    @Test
    fun calculate_arcticCircleDoesNotCrash() {
        val result = SunPositionCalculator.calculate(69.6, 18.9, LocalDate.of(2026, 6, 21))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue(timeRegex.matches(result.fajr))
        assertTrue(timeRegex.matches(result.isha))
    }

    @Test
    fun calculate_antarcticDoesNotCrash() {
        val result = SunPositionCalculator.calculate(-53.0, -70.0, LocalDate.of(2026, 12, 21))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue(timeRegex.matches(result.fajr))
        assertTrue(timeRegex.matches(result.isha))
    }

    @Test
    fun calculate_sameInputConsistentOutput() {
        val r1 = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 7, 15))
        val r2 = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 7, 15))
        assertTrue(r1 == r2)
    }

    @Test
    fun calculate_dstTransitionSpring() {
        val before = SunPositionCalculator.calculate(48.8566, 2.3522, LocalDate.of(2026, 3, 29))
        val after = SunPositionCalculator.calculate(48.8566, 2.3522, LocalDate.of(2026, 3, 30))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue(timeRegex.matches(before.fajr))
        assertTrue(timeRegex.matches(after.fajr))
    }

    @Test
    fun calculate_dstTransitionAutumn() {
        val before = SunPositionCalculator.calculate(48.8566, 2.3522, LocalDate.of(2026, 10, 25))
        val after = SunPositionCalculator.calculate(48.8566, 2.3522, LocalDate.of(2026, 10, 26))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue(timeRegex.matches(before.fajr))
        assertTrue(timeRegex.matches(after.fajr))
    }

    @Test
    fun calculate_halfHourTimezoneValid() {
        val result = SunPositionCalculator.calculate(28.6139, 77.2090, LocalDate.of(2026, 7, 15))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue(timeRegex.matches(result.fajr))
        assertTrue(timeRegex.matches(result.dhuhr))
    }

    @Test
    fun calculate_yearBoundary() {
        val dec31 = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2026, 12, 31))
        val jan1 = SunPositionCalculator.calculate(33.5731, -7.5898, LocalDate.of(2027, 1, 1))
        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        assertTrue(timeRegex.matches(dec31.fajr))
        assertTrue(timeRegex.matches(jan1.fajr))
    }
}
