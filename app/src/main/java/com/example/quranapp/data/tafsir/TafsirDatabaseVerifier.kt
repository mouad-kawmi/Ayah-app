package com.example.quranapp.data.tafsir

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.ErrorCode
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Install pipeline for tafsir databases (architecture §3.2.5).
 *
 * Checks run in order — each one must pass before the resource is considered
 * installable. Failure at any step aborts the install; the previously installed
 * database is left untouched.
 *
 * 1. SHA-256 integrity (done by the download manager, file hash vs catalog)
 * 2. schema_version supported (done by the download manager)
 * 3. SQLite structural integrity (PRAGMA quick_check)
 * 4. Required tables exist (tafsir, meta) — the contract the reader implements
 * 5. Required metadata present (id, name, schema_version), id matches the catalog
 * 6. PRAGMA user_version matches schema_version (same check as the reader)
 */
object TafsirDatabaseVerifier {

    private const val SCHEMA_VERSION_COLUMN = "schema_version"
    private const val SQLITE_PRAGMA_QUICK_CHECK = "PRAGMA quick_check"

    private fun logLine(detail: String): String =
        Instrumentation.line("tafsir_verify", Instrumentation.NO_TRACE, null, detail)

    /**
     * Verifies the resource at [dbFile] against the expected [meta]. Returns
     * the failure message, or null when every check passed.
     */
    fun verify(dbFile: File, meta: TafsirResourceMeta): String? {
        if (!dbFile.isFile || dbFile.length() == 0L) {
            return ErrorCode.TAFSIR_DB_UNAVAILABLE.prefix("Database file missing or empty")
        }
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            val integrity = db.rawQuery(SQLITE_PRAGMA_QUICK_CHECK, null).use { cursor ->
                cursor.moveToFirst()
                val value = if (cursor.count > 0) cursor.getString(0) else "error: no result"
                cursor.moveToFirst()
                cursor.getString(0)
            }
            if (!integrity.equals("ok", ignoreCase = true)) {
                return ErrorCode.TAFSIR_DB_CORRUPTED.prefix("SQLite integrity check failed: $integrity")
            }

            val tables = db.query(
                "sqlite_master",
                arrayOf("name"),
                "type = ?",
                arrayOf("table"),
                null, null, null
            ).use { cursor ->
                val names = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { names.add(it) }
                }
                names
            }
            val requiredTables = listOf("tafsir", "meta")
            val missing = requiredTables.filter { it !in tables }
            if (missing.isNotEmpty()) {
                return ErrorCode.TAFSIR_DB_CORRUPTED.prefix("Missing required tables: $missing")
            }

            val metaValues = readMeta(db)
            val requiredKeys = listOf("id", "name", SCHEMA_VERSION_COLUMN)
            val missingMeta = requiredKeys.filter { !metaValues.containsKey(it) }
            if (missingMeta.isNotEmpty()) {
                return ErrorCode.TAFSIR_DB_CORRUPTED.prefix("Missing required metadata: $missingMeta")
            }
            val schemaVersion = metaValues[SCHEMA_VERSION_COLUMN]?.toIntOrNull()
            if (schemaVersion == null || schemaVersion > TafsirReader.SUPPORTED_SCHEMA_VERSION) {
                return ErrorCode.TAFSIR_SCHEMA_UNSUPPORTED.prefix(
                    "Schema version $schemaVersion (supported: ${TafsirReader.SUPPORTED_SCHEMA_VERSION})"
                )
            }
            if (db.version != schemaVersion) {
                return ErrorCode.TAFSIR_DB_CORRUPTED.prefix(
                    "PRAGMA user_version ${db.version} does not match schema_version $schemaVersion"
                )
            }
            if (meta != TafsirResourceMeta.NONE && metaValues["id"] != meta.id) {
                return ErrorCode.TAFSIR_DB_CORRUPTED.prefix(
                    "Metadata id '${metaValues["id"]}' does not match expected '${meta.id}'"
                )
            }
            return null
        } catch (e: Exception) {
            DebugLogger.warning(
                LogCategory.TAFSIR,
                logLine(ErrorCode.TAFSIR_DB_CORRUPTED.prefix("Verification failed for ${dbFile.name}")),
                e
            )
            return ErrorCode.TAFSIR_DB_CORRUPTED.prefix(e.message ?: "Database unreadable")
        } finally {
            runCatching { db?.close() }
        }
    }

    suspend fun verifyAsync(dbFile: File, meta: TafsirResourceMeta): String? =
        withContext(Dispatchers.IO) { verify(dbFile, meta) }

    private fun readMeta(db: SQLiteDatabase): Map<String, String> = runCatching {
        val map = mutableMapOf<String, String>()
        db.rawQuery("SELECT key, value FROM meta", null).use { cursor ->
            while (cursor.moveToNext()) {
                map[cursor.getString(0)] = cursor.getString(1)
            }
        }
        map
    }.getOrDefault(emptyMap())
}
