package com.example.quranapp.data.prayer

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerMaintenanceReceiverTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun onReceive_bootCompleted_runsWithoutCrash() {
        val receiver = PrayerMaintenanceReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun onReceive_myPackageReplaced_runsWithoutCrash() {
        val receiver = PrayerMaintenanceReceiver()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun onReceive_timezoneChanged_runsWithoutCrash() {
        val receiver = PrayerMaintenanceReceiver()
        val intent = Intent(Intent.ACTION_TIMEZONE_CHANGED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun onReceive_nullAction_runsWithoutCrash() {
        val receiver = PrayerMaintenanceReceiver()
        val intent = Intent()
        receiver.onReceive(context, intent)
    }
}
