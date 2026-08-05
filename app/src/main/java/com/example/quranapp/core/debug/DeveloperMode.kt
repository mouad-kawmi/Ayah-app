package com.example.quranapp.core.debug

import android.content.Context
import android.content.SharedPreferences

class DeveloperMode(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    fun enable() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        DebugLogger.info(LogCategory.APP, "Developer mode enabled")
    }

    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        DebugLogger.info(LogCategory.APP, "Developer mode disabled")
    }

    companion object {
        const val PREFS_NAME = "developer_prefs"
        const val KEY_ENABLED = "developer_mode"
    }
}
