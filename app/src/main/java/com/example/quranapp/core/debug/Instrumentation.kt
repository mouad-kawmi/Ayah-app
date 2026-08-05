package com.example.quranapp.core.debug

import java.util.Locale

internal object Instrumentation {
    const val NO_TRACE = "-"
    const val NO_CITY = "-"

    /** Formats a duration in seconds as HH:MM:SS for snapshot logs (e.g. "00:18:12"). */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun source(): String {
        val frames = Throwable().stackTrace
        for (frame in frames) {
            val cls = frame.className
            if (cls.startsWith("com.example.quranapp.") &&
                !cls.startsWith("com.example.quranapp.core.debug.")
            ) {
                return "${cls.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
            }
        }
        return "unknown:0"
    }

    fun line(prayerKey: String, traceId: String, city: String?, detail: String): String {
        val trace = traceId.ifBlank { NO_TRACE }
        return "prayer=$prayerKey | trace=$trace | city=${city ?: NO_CITY} | ${source()} | $detail"
    }
}
