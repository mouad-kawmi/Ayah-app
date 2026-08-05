package com.example.quranapp.data.audio

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object AudioCacheManager {
    private const val MAX_CACHE_SIZE_BYTES = 500L * 1024L * 1024L // 500 MB
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDirectory = File(context.cacheDir, "quran_audio_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            simpleCache = SimpleCache(cacheDirectory, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    fun isSurahCached(context: Context, audioUrl: String): Boolean {
        try {
            val cache = getInstance(context)
            val cachedSpans = cache.getCachedSpans(audioUrl)
            var totalBytes = 0L
            for (span in cachedSpans) {
                totalBytes += span.length
            }
            return totalBytes > 1024
        } catch (e: Exception) {
            return false
        }
    }

    fun clearCache(context: Context) {
        try {
            getInstance(context).let { cache ->
                for (key in cache.keys) {
                    cache.removeResource(key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
