package com.example.quranapp.data.prayer

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.DiagnosticsDashboard
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.core.debug.Timings
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class PrayerTimesSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val habousProvider by lazy { HabousProvider() }
    private val alAdhanProvider by lazy { AlAdhanProvider() }
    private val providerSelector by lazy { ProviderSelector(habousProvider, alAdhanProvider) }
    private val countryDetector by lazy { CountryDetector(applicationContext) }
    private val repository by lazy { PrayerTimesRepository(applicationContext, providerSelector, countryDetector) }

    override suspend fun doWork(): Result {
        val traceId = DiagnosticsDashboard.generateTraceId()
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("last_lat", 0f).toDouble()
        val lon = prefs.getFloat("last_lon", 0f).toDouble()
        val city = prefs.getString("last_city", null)
        if (city == null) {
            Log.i("PrayerSync", "Worker skip — no saved city")
            DebugLogger.info(
                LogCategory.SYNC,
                Instrumentation.line("sync", traceId, null, "Worker skip — no saved city")
            )
            return Result.success()
        }

        val countryCode = countryDetector.detectCountry(lat, lon)
        val provider = providerSelector.selectProvider(countryCode)
        Log.i("PrayerSync", "Worker start lat=$lat lon=$lon city=$city country=$countryCode provider=${provider.providerId}")
        DebugLogger.info(
            LogCategory.SYNC,
            Instrumentation.line("sync", traceId, city, "Worker start lat=$lat lon=$lon country=$countryCode provider=${provider.providerId}")
        )

        val today = LocalDate.now()
        val endDate = today.plusDays(60)

        return Timings.measure("Prayer schedule fetch") {
            repository.fetchSchedule(
                lat = lat,
                lon = lon,
                cityName = city,
                dateRange = today..endDate,
                forceRefresh = false
            ).fold(
                onSuccess = {
                    PrayerAlarmScheduler.scheduleUpcomingAlarms(applicationContext)

                    val widgetIntent = android.content.Intent("com.example.ACTION_UPDATE_PRAYER_WIDGET").apply {
                        setPackage(applicationContext.packageName)
                    }
                    applicationContext.sendBroadcast(widgetIntent)

                    Log.i("PrayerSync", "Worker result=SUCCESS city=$city")
                    DebugLogger.info(
                        LogCategory.SYNC,
                        Instrumentation.line("sync", traceId, city, "Worker result=SUCCESS")
                    )
                    Result.success()
                },
                onFailure = {
                    Log.e("PrayerSync", "Worker result=FAILURE city=$city error=${it.message}", it)
                    DebugLogger.error(
                        LogCategory.SYNC,
                        Instrumentation.line("sync", traceId, city, ErrorCode.SYNC_FAILURE.prefix("Worker result=FAILURE error=${it.message}")),
                        it
                    )
                    Result.retry()
                }
            )
        }
    }
}

object PrayerTimesSyncScheduler {
    private const val PERIODIC_WORK_NAME = "prayer_times_daily_sync"
    private const val IMMEDIATE_WORK_NAME = "prayer_times_immediate_sync"

    fun ensureScheduled(context: Context) {
        DebugLogger.info(
            LogCategory.WORKMANAGER,
            Instrumentation.line("sync", Instrumentation.NO_TRACE, null, "Enqueue periodic sync $PERIODIC_WORK_NAME")
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<PrayerTimesSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    fun triggerImmediateSync(context: Context) {
        DebugLogger.info(
            LogCategory.WORKMANAGER,
            Instrumentation.line("sync", Instrumentation.NO_TRACE, null, "Trigger immediate sync $IMMEDIATE_WORK_NAME")
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<PrayerTimesSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }
}
