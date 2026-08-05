package com.example.quranapp.core.debug

import android.content.Context
import java.util.UUID

object DiagnosticsCollector {
    private val lock = Any()
    private var appContext: Context? = null
    private var sessionIdValue: String? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (appContext == null) {
                appContext = context.applicationContext
                sessionIdValue = UUID.randomUUID().toString()
            }
        }
    }

    val sessionId: String
        get() {
            synchronized(lock) {
                return sessionIdValue ?: UUID.randomUUID().toString().also { sessionIdValue = it }
            }
        }
}
