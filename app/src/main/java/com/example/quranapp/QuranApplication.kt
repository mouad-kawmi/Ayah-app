package com.example.quranapp

import android.app.Application
import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.quranapp.core.debug.CrashHandler
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.DiagnosticsCollector
import com.example.quranapp.core.debug.LogCategory

class QuranApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // The debug foundation must be online before anything else runs so that
        // every subsequent startup step and runtime event is captured.
        DebugLogger.initialize(this)
        DiagnosticsCollector.initialize(this)
        CrashHandler.initialize(this)

        logStartup()
    }

    private fun logStartup() {
        DebugLogger.info(LogCategory.APP, "APP START")
        DebugLogger.info(LogCategory.APP, "Application created")
        DebugLogger.info(LogCategory.APP, "Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        DebugLogger.info(LogCategory.APP, "Device manufacturer: ${Build.MANUFACTURER}")
        DebugLogger.info(LogCategory.APP, "Device model: ${Build.MODEL}")
        DebugLogger.info(LogCategory.APP, "App version: ${appVersionName()}")
        DebugLogger.info(LogCategory.APP, "App version code: ${appVersionCode()}")
        DebugLogger.info(LogCategory.APP, "Build type: ${buildType()}")
        DebugLogger.info(LogCategory.APP, "Session ID: ${DiagnosticsCollector.sessionId}")
        DebugLogger.info(LogCategory.APP, "Process name: ${processName()}")
        DebugLogger.info(LogCategory.APP, "Crash handler installed")
        DebugLogger.info(LogCategory.APP, "Logger initialized")
        DebugLogger.info(LogCategory.APP, "Diagnostics initialized")
        DebugLogger.info(LogCategory.APP, "Application initialization completed")
    }

    private fun appVersionName(): String {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrElse { "unknown" } ?: "unknown"
    }

    private fun appVersionCode(): Long {
        return runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        }.getOrDefault(-1L)
    }

    private fun buildType(): String {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) "debug" else "release"
    }

    private fun processName(): String {
        return runCatching {
            val pid = Process.myPid()
            val am = getSystemService(Application.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }.getOrElse {
            Log.w(TAG, "Unable to resolve process name", it)
            null
        } ?: "unknown"
    }

    companion object {
        private const val TAG = "QuranApplication"
    }
}
