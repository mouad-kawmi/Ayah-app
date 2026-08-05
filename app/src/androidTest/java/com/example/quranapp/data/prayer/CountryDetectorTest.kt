package com.example.quranapp.data.prayer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class CountryDetectorTest {

    private lateinit var context: Context
    private lateinit var detector: CountryDetector

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        detector = CountryDetector(context)
    }

    @Test
    fun detectCountry_casablancaReturnsMorocco() = runBlocking {
        val country = detector.detectCountry(33.5731, -7.5898)
        assertEquals("MA", country)
    }

    @Test
    fun detectCountry_cacheHitWithinDistance() = runBlocking {
        val first = detector.detectCountry(33.0, -7.0)
        assertEquals("MA", first)
        val second = detector.detectCountry(33.1, -7.1)
        assertEquals("MA", second)
    }

    @Test
    fun detectCountry_cacheInvalidatedWhenTooFar() = runBlocking {
        val first = detector.detectCountry(33.0, -7.0)
        assertEquals("MA", first)
        val second = detector.detectCountry(35.0, -7.0)
        assertEquals("MA", second)
    }

    @Test
    fun cacheCountry_storesValue() {
        detector.cacheCountry("MA")
        detector.cacheCountry("FR")
        detector.cacheCountry("MA")
    }
}
