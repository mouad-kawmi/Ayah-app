package com.example.quranapp.core.debug

/**
 * Stable, release-stable error identifiers attached to log messages.
 *
 * Numbering scheme (by subsystem so codes stay grep-able across releases):
 * 1xxx App / crash
 * 2xxx Notification
 * 3xxx Adhan / audio
 * 4xxx Cache / data
 * 5xxx Location / country
 * 6xxx Sync / network
 * 7xxx Widget
 * 8xxx Alarm / maintenance
 * 9xxx Tafsir / content reader
 * 10xxx Download / resources (generic layer)
 *
 * Never reuse or renumber a code: existing codes must stay stable across releases.
 */
enum class ErrorCode(val number: Int) {
    UNCAUGHT_EXCEPTION(1001),
    NOTIFICATION_PERMISSION_DENIED(2001),
    FOREGROUND_SERVICE_FAILED(2002),
    ADHAN_PLAYBACK_START_FAILED(3001),
    ADHAN_AUDIO_MISSING(3002),
    ADHAN_PLAYBACK_ERROR(3003),
    OFFLINE_FALLBACK_FAILED(4002),
    FETCH_NO_DATA(4003),
    FETCH_SCHEDULE_FAILED(4004),
    PRAYER_CACHE_CORRUPTED(4005),
    GEOCODER_FAILED(5001),
    IP_LOOKUP_FAILED(5002),
    SYNC_FAILURE(6001),
    WIDGET_UPDATE_FAILED(7001),
    WIDGET_OPTIONS_FAILED(7002),
    WIDGET_RECEIVE_FAILED(7003),
    WIDGET_EXACT_ALARM_DENIED(7004),
    RECEIVER_EXCEPTION(8001),
    MAINTENANCE_INIT_FAILED(8002),
    MAINTENANCE_SCHEDULE_FAILED(8003),
    MAINTENANCE_SYNC_FAILED(8004),
    MAINTENANCE_CACHE_ALARM_FAILED(8005),
    MAINTENANCE_SUNNAH_FAILED(8006),
    TAFSIR_DB_UNAVAILABLE(9001),
    TAFSIR_DB_CORRUPTED(9002),
    TAFSIR_SCHEMA_UNSUPPORTED(9003),
    TAFSIR_READ_FAILED(9004),
    DOWNLOAD_FAILED(10001),
    DOWNLOAD_TIMEOUT(10002),
    DOWNLOAD_CANCELLED(10003),
    DOWNLOAD_INTEGRITY_MISMATCH(10004),
    DOWNLOAD_NO_SPACE(10005),
    DOWNLOAD_APP_VERSION_REQUIRED(10006),
    DOWNLOAD_SCHEMA_UNSUPPORTED(10007),
    DOWNLOAD_INVALID_CATALOG(10008),
    DOWNLOAD_INSTALL_FAILED(10009);

    /** Stable textual identifier, e.g. "ERR-2001". */
    val code: String get() = "ERR-${number.toString().padStart(4, '0')}"

    /** Prepends the stable code while preserving the original message, e.g. "ERR-2001 Notification permission denied". */
    fun prefix(message: String): String = "$code $message"
}
