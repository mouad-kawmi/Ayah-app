package com.example.quranapp.data.tafsir

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.core.debug.Timings
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface TafsirTextSource {
    suspend fun getText(tafsirId: String, verseKey: String): String?
}

/**
 * Content reader over installed tafsir databases (architecture §3.2.6).
 *
 * - Bundled Muyassar ships in assets, is copied on first use and restored from
 *   the asset on corruption (never lost, never deletable).
 * - Downloaded tafsirs are read from `files/resources/tafsir/<id>.db`. A missing
 *   or unreadable downloaded database logs the failure and yields null; the
 *   selection layer falls back to Muyassar.
 * - One read-only handle per tafsir, cached; per-verse text cache (LRU).
 */
class TafsirReader private constructor(private val appContext: Context) : TafsirTextSource {

    companion object {
        const val BUNDLED_TAFSIR_ID = "muyassar"
        const val BUNDLED_TAFSIR_NAME = "التفسير الميسر"
        const val BUNDLED_TAFSIR_LANGUAGE = "ar"
        const val SUPPORTED_SCHEMA_VERSION = 1
        private const val BUNDLED_ASSET_PATH = "tafsir/muyassar.db"
        private const val CACHE_SIZE = 256
        private const val LEGACY_TAFSIR_FILE = "translation_ar.muyassar.json"

        @Volatile
        private var INSTANCE: TafsirReader? = null

        fun getInstance(context: Context): TafsirReader =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TafsirReader(context.applicationContext).also { INSTANCE = it }
            }
    }

    private enum class FileStatus { OK, MISSING, CORRUPTED, SCHEMA_UNSUPPORTED }

    private val textCache = object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CACHE_SIZE
    }

    private val openDatabases = object : LinkedHashMap<String, SQLiteDatabase>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SQLiteDatabase>?): Boolean =
            size > 4
    }

    private fun logLine(message: String): String =
        Instrumentation.line(BUNDLED_TAFSIR_ID, Instrumentation.NO_TRACE, null, message)

    private fun tafsirDir(): File = File(appContext.filesDir, "resources/tafsir")

    private fun tafsirFile(tafsirId: String): File = File(tafsirDir(), "$tafsirId.db")

    override suspend fun getText(tafsirId: String, verseKey: String): String? {
        val cacheKey = "$tafsirId:$verseKey"
        synchronized(textCache) { textCache[cacheKey]?.let { return it } }
        return withContext(Dispatchers.IO) {
            val db = openForReading(tafsirId) ?: return@withContext null
            try {
                val text = queryText(db, verseKey)
                if (text != null) synchronized(textCache) { textCache[cacheKey] = text }
                text
            } catch (e: SQLiteException) {
                DebugLogger.error(
                    LogCategory.TAFSIR,
                    logLine(ErrorCode.TAFSIR_READ_FAILED.prefix("Read failed for $tafsirId — self-healing")),
                    e
                )
                closeAndReset(tafsirId)
                val restored = openForReading(tafsirId)
                if (restored == null) {
                    null
                } else {
                    try {
                        queryText(restored, verseKey)
                    } catch (e2: SQLiteException) {
                        DebugLogger.error(
                            LogCategory.TAFSIR,
                            logLine(ErrorCode.TAFSIR_READ_FAILED.prefix("Read failed after recovery ($tafsirId)")),
                            e2
                        )
                        null
                    }
                }
            }
        }
    }

    private fun openForReading(tafsirId: String): SQLiteDatabase? = synchronized(this) {
        val current = openDatabases[tafsirId]
        if (current != null && current.isOpen) {
            return@synchronized current
        }
        val file = if (tafsirId == BUNDLED_TAFSIR_ID) {
            ensureValidBundledFile() ?: return@synchronized null
        } else {
            val f = tafsirFile(tafsirId)
            when (statusOf(f, tafsirId)) {
                FileStatus.OK -> f
                FileStatus.MISSING -> {
                    DebugLogger.warning(
                        LogCategory.TAFSIR,
                        logLine(ErrorCode.TAFSIR_DB_UNAVAILABLE.prefix("Downloaded tafsir $tafsirId missing — selection must fall back"))
                    )
                    return@synchronized null
                }
                FileStatus.CORRUPTED -> {
                    DebugLogger.warning(
                        LogCategory.TAFSIR,
                        logLine(ErrorCode.TAFSIR_DB_CORRUPTED.prefix("Downloaded tafsir $tafsirId corrupted — selection must fall back"))
                    )
                    return@synchronized null
                }
                FileStatus.SCHEMA_UNSUPPORTED -> {
                    DebugLogger.warning(
                        LogCategory.TAFSIR,
                        logLine(ErrorCode.TAFSIR_SCHEMA_UNSUPPORTED.prefix("Downloaded tafsir $tafsirId schema unsupported"))
                    )
                    return@synchronized null
                }
            }
        }
        try {
            val db = Timings.measure("Tafsir open ($tafsirId)") {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            }
            openDatabases[tafsirId] = db
            db
        } catch (e: SQLiteException) {
            DebugLogger.error(
                LogCategory.TAFSIR,
                logLine(ErrorCode.TAFSIR_DB_CORRUPTED.prefix("Open failed for $tafsirId")),
                e
            )
            if (tafsirId == BUNDLED_TAFSIR_ID) {
                // The bundled copy may have been damaged on disk: restore from asset.
                file.delete()
            }
            null
        }
    }

    private fun ensureValidBundledFile(): File? {
        val file = tafsirFile(BUNDLED_TAFSIR_ID)
        when (statusOf(file, BUNDLED_TAFSIR_ID)) {
            FileStatus.OK -> return file
            FileStatus.MISSING ->
                DebugLogger.info(LogCategory.TAFSIR, logLine("Bundled tafsir missing — copying from asset"))
            FileStatus.CORRUPTED ->
                DebugLogger.warning(
                    LogCategory.TAFSIR,
                    logLine(ErrorCode.TAFSIR_DB_CORRUPTED.prefix("Bundled tafsir corrupted — restoring from asset"))
                )
            FileStatus.SCHEMA_UNSUPPORTED ->
                DebugLogger.warning(
                    LogCategory.TAFSIR,
                    logLine(ErrorCode.TAFSIR_SCHEMA_UNSUPPORTED.prefix("Bundled tafsir schema unsupported — restoring from asset"))
                )
        }
        file.delete()
        val copied = Timings.measure("Bundled tafsir asset copy") { copyFromAsset() }
        if (copied != null && statusOf(copied, BUNDLED_TAFSIR_ID) == FileStatus.OK) return copied
        copied?.delete()
        DebugLogger.error(
            LogCategory.TAFSIR,
            logLine(ErrorCode.TAFSIR_DB_UNAVAILABLE.prefix("Bundled tafsir unavailable after restore attempt"))
        )
        return null
    }

    private fun statusOf(file: File, tafsirId: String): FileStatus {
        if (!file.exists() || file.length() == 0L) return FileStatus.MISSING
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val integrityOk = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0) == "ok"
                }
                if (!integrityOk) {
                    FileStatus.CORRUPTED
                } else {
                    val metaVersion = db.rawQuery("SELECT value FROM meta WHERE key = 'schema_version'", null).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() else null
                    }
                    if (metaVersion == SUPPORTED_SCHEMA_VERSION && db.version == SUPPORTED_SCHEMA_VERSION) {
                        FileStatus.OK
                    } else {
                        FileStatus.SCHEMA_UNSUPPORTED
                    }
                }
            }
        } catch (e: SQLiteException) {
            FileStatus.CORRUPTED
        }
    }

    private fun copyFromAsset(): File? {
        val target = tafsirFile(BUNDLED_TAFSIR_ID)
        return try {
            target.parentFile?.mkdirs()
            appContext.assets.open(BUNDLED_ASSET_PATH).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            DebugLogger.info(
                LogCategory.TAFSIR,
                logLine("Bundled tafsir copied to ${target.path} (${target.length()} bytes)")
            )
            cleanupLegacyJson()
            target
        } catch (e: Exception) {
            DebugLogger.error(
                LogCategory.TAFSIR,
                logLine(ErrorCode.TAFSIR_DB_UNAVAILABLE.prefix("Asset copy failed")),
                e
            )
            target.delete()
            null
        }
    }

    private fun cleanupLegacyJson() {
        val legacy = File(appContext.filesDir, LEGACY_TAFSIR_FILE)
        if (legacy.exists() && legacy.delete()) {
            DebugLogger.info(LogCategory.TAFSIR, logLine("Legacy $LEGACY_TAFSIR_FILE removed"))
        }
    }

    private fun closeAndReset(tafsirId: String) {
        synchronized(this) {
            openDatabases.remove(tafsirId)?.close()
        }
    }

    private fun queryText(db: SQLiteDatabase, verseKey: String): String? {
        db.rawQuery("SELECT text FROM tafsir WHERE verse_key = ?", arrayOf(verseKey)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
