package com.example.quranapp.data.prayer

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.*

object SunPositionCalculator {
    private const val FAJR_ANGLE = 18.0
    private const val ISHA_ANGLE = 17.0
    private const val SUNRISE_ANGLE = 0.833
    private const val METHOD = 3 // Muslim World League

    fun calculate(lat: Double, lon: Double, date: LocalDate): PrayerTimes {
        val julianDay = julianDay(date)
        val t = (julianDay - 2451545.0) / 36525.0

        val meanAnomaly = radians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val equationOfTime = equationOfTime(t, meanAnomaly)
        val declination = declination(t, meanAnomaly)

        val dhuhr = 12.0 + (0.0 - lon) / 15.0 - equationOfTime

        val sunrise = dhuhr - hourAngle(lat, declination, -SUNRISE_ANGLE) / 15.0
        val sunset = dhuhr + hourAngle(lat, declination, -SUNRISE_ANGLE) / 15.0
        val fajr = dhuhr - hourAngle(lat, declination, -FAJR_ANGLE) / 15.0
        val isha = dhuhr + hourAngle(lat, declination, -ISHA_ANGLE) / 15.0
        val asr = dhuhr + asrHourAngle(lat, declination) / 15.0
        val maghrib = sunset

        return PrayerTimes(
            fajr = fmt(fajr, date, lon),
            shuruq = fmt(sunrise, date, lon),
            dhuhr = fmt(dhuhr, date, lon),
            asr = fmt(asr, date, lon),
            maghrib = fmt(maghrib, date, lon),
            isha = fmt(isha, date, lon)
        )
    }

    private fun julianDay(date: LocalDate): Double {
        val y = date.year.toDouble()
        val m = date.monthValue.toDouble()
        val d = date.dayOfMonth.toDouble()
        val a = floor((14.0 - m) / 12.0)
        val yy = y + 4800.0 - a
        val mm = m + 12.0 * a - 3.0
        return d + floor((153.0 * mm + 2.0) / 5.0) + 365.0 * yy + floor(yy / 4.0) - floor(yy / 100.0) + floor(yy / 400.0) - 32045.0
    }

    private fun equationOfTime(t: Double, meanAnomaly: Double): Double {
        val center = sin(meanAnomaly) * (1.914602 - 0.004817 * t - 0.000014 * t * t)
                + sin(2.0 * meanAnomaly) * (0.019993 - 0.000101 * t)
                + sin(3.0 * meanAnomaly) * 0.000289
        val apparentLongitude = meanAnomaly + center + radians(102.93735 + 1.71946 * t + 0.00046 * t * t)
        val obliquity = radians(23.439291 - 0.0130042 * t)
        val y = tan(obliquity / 2.0).pow(2)
        return degrees(
            y * sin(2.0 * apparentLongitude)
                    - 2.0 * 0.016708 * y * sin(meanAnomaly) * cos(2.0 * apparentLongitude)
                    + 2.0 * 0.016708 * center * cos(2.0 * apparentLongitude) * cos(meanAnomaly)
                    - 4.0 * y * center * sin(2.0 * apparentLongitude) / 2.0
        ) / 15.0
    }

    private fun declination(t: Double, meanAnomaly: Double): Double {
        val center = sin(meanAnomaly) * (1.914602 - 0.004817 * t - 0.000014 * t * t)
                + sin(2.0 * meanAnomaly) * (0.019993 - 0.000101 * t)
                + sin(3.0 * meanAnomaly) * 0.000289
        val apparentLongitude = meanAnomaly + center + radians(102.93735 + 1.71946 * t + 0.00046 * t * t)
        val obliquity = radians(23.439291 - 0.0130042 * t)
        return asin(sin(obliquity) * sin(apparentLongitude))
    }

    private fun hourAngle(lat: Double, declination: Double, altitude: Double): Double {
        val numerator = sin(radians(altitude)) - sin(radians(lat)) * sin(declination)
        val denominator = cos(radians(lat)) * cos(declination)
        val cosHourAngle = numerator / denominator
        return if (cosHourAngle < -1.0 || cosHourAngle > 1.0) {
            if (altitude < -18.0) 0.0 else 12.0 * 15.0
        } else {
            degrees(acos(cosHourAngle))
        }
    }

    private fun asrHourAngle(lat: Double, declination: Double): Double {
        val asrAltitude = when (METHOD) {
            3 -> {
                val arctanVal = atan(1.0 / (1.0 + tan(radians(abs(lat - degrees(declination))))))
                degrees(arctanVal)
            }
            else -> {
                val arctanVal = atan(1.0 / (2.0 + tan(radians(abs(lat - degrees(declination))))))
                degrees(arctanVal)
            }
        }
        return hourAngle(lat, declination, asrAltitude)
    }

    private fun fmt(hours: Double, date: LocalDate, lon: Double): String {
        val timeZone = timezoneOffset(lon, date)
        var localHours = hours + timeZone
        if (localHours < 0.0) localHours += 24.0
        if (localHours >= 24.0) localHours -= 24.0
        val totalMinutes = (localHours * 60.0).roundToInt()
        val h = (totalMinutes / 60).coerceIn(0, 23)
        val m = (totalMinutes % 60).coerceIn(0, 59)
        return "%02d:%02d".format(h, m)
    }

    private fun timezoneOffset(lon: Double, date: LocalDate): Double {
        return try {
            val zone = ZoneId.systemDefault()
            val instant = date.atStartOfDay(zone).toInstant()
            zone.getRules().getOffset(instant).totalSeconds / 3600.0
        } catch (_: Exception) {
            (lon / 15.0).roundToInt().toDouble()
        }
    }

    private fun radians(deg: Double): Double = Math.toRadians(deg)
    private fun degrees(rad: Double): Double = Math.toDegrees(rad)
}
