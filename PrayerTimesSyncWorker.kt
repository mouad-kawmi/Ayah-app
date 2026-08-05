package com.example

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import java.util.concurrent.TimeUnit

class PrayerTimesSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repository = PrayerTimesRepository()

    override suspend fun doWork(): Result {
        val workerStart = System.currentTimeMillis()
        DebugLogger.info(LogCategory.WORKMANAGER, "PrayerTimesSyncWorker started")
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("last_lat", 33.5731f).toDouble()
        val lon = prefs.getFloat("last_lon", -7.5898f).toDouble()
        val city = prefs.getString("last_city", "الدار البيضاء") ?: "الدار البيضاء"

        DebugLogger.info(LogCategory.NETWORK, "Network sync started — city=$city")

        // Only runs when network is available (enforced by WorkManager constraints).
        // Checks Habous website, downloads only missing months, saves to cache.
        val resolvedCity = repository.resolveOfficialCity(lat, lon, city)
        DebugLogger.debug(LogCategory.SYNC, "SyncWorker — resolved city: id=${resolvedCity.id} habousId=${resolvedCity.habousId} name=${resolvedCity.name}")
        val currentYm = java.time.YearMonth.now()
        val preAnyMonthSaved = PrayerTimesCacheStore.hasMonthSchedule(applicationContext, currentYm.year, currentYm.monthValue)
        DebugLogger.debug(LogCategory.SYNC, "SyncWorker — before sync, current month cached=$preAnyMonthSaved forceRefresh=false")

        return repository.syncUpcomingMonths(
            context = applicationContext,
            latitude = lat,
            longitude = lon,
            cityName = city,
            forceRefresh = false
        ).fold(
            onSuccess = {
                DebugLogger.info(LogCategory.NETWORK, "Network sync success — city=$city")
                DebugLogger.info(LogCategory.CACHE, "Prayer cache updated — city=$city")
                val postAnyMonthSaved = PrayerTimesCacheStore.hasMonthSchedule(applicationContext, currentYm.year, currentYm.monthValue)
                val cachedCityId = PrayerTimesCacheStore.getCachedCityId(applicationContext)
                val cachedCityName = PrayerTimesCacheStore.getCachedCityName(applicationContext)
                DebugLogger.debug(LogCategory.SYNC, "SyncWorker — onSuccess anyMonthSaved=$postAnyMonthSaved cachedCityId=$cachedCityId cachedCityName=$cachedCityName setActiveCity=(anyMonthSaved=$postAnyMonthSaved || forceRefresh=false)")
                PrayerAlarmScheduler.scheduleUpcomingAlarms(applicationContext)
                val duration = System.currentTimeMillis() - workerStart
                DebugLogger.info(LogCategory.WORKMANAGER, "Worker finished successfully — duration=${duration}ms")
                DebugLogger.debug(LogCategory.SYNC, "SyncWorker — calling PrayerWidgetUpdater.refresh()")
                PrayerWidgetUpdater.refresh(applicationContext)
                Result.success()
            },
            onFailure = {
                val duration = System.currentTimeMillis() - workerStart
                DebugLogger.error(LogCategory.NETWORK, "Network sync failed — city=$city duration=${duration}ms", it)
                DebugLogger.warning(LogCategory.WORKMANAGER, "Worker retry scheduled — ran for ${duration}ms")
                Result.retry()
            }
        )
    }
}

object PrayerTimesSyncScheduler {
    private const val PERIODIC_WORK_NAME = "prayer_times_daily_sync"
    private const val IMMEDIATE_WORK_NAME = "prayer_times_immediate_sync"

    fun ensureScheduled(context: Context) {
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
