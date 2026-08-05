package com.example

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHandler {

    /**
     * Recursively traverses contexts to extract the root Activity (handles Hilt, ThemeWrapper, etc.)
     */
    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Returns true if ACCESS_FINE_LOCATION is explicitly granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the OS recommends showing a rationale before requesting again.
     */
    fun shouldShowRationale(context: Context): Boolean {
        val activity = context.findActivity() ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun hasRequestedLocationBefore(context: Context): Boolean {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("has_requested_location_before", false)
    }

    fun markLocationRequested(context: Context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_requested_location_before", true)
            .apply()
    }

    /**
     * Helper to safely launch App Settings intent from anywhere.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            if (context.findActivity() == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Helper to safely launch GPS Settings intent from anywhere.
     */
    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            if (context.findActivity() == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Helper to open Xiaomi-specific battery optimization settings.
     */
    /**
     * Tries to return the best available battery optimization settings intent
     * using a tiered fallback chain:
     *   1. ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (app-specific)
     *   2. ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS (global list)
     * Returns null if neither is available — caller should fall back to
     * ACTION_APPLICATION_DETAILS_SETTINGS.
     */
    fun createBatteryOptimizationSettingsIntent(context: Context): Intent? {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                DebugLogger.debug(LogCategory.BATTERY, "Using ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                return intent
            }
        } catch (_: Exception) {
            DebugLogger.debug(LogCategory.BATTERY, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS not available")
        }

        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (intent.resolveActivity(context.packageManager) != null) {
                DebugLogger.debug(LogCategory.BATTERY, "Using ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
                return intent
            }
        } catch (_: Exception) {
            DebugLogger.debug(LogCategory.BATTERY, "ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS not available")
        }

        DebugLogger.warning(LogCategory.BATTERY, "No battery optimization settings activity available")
        return null
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun shouldShowBatteryDialog(context: Context, currentVersion: Int = BuildConfig.VERSION_CODE): Boolean {
        if (isIgnoringBatteryOptimizations(context)) return false
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val laterVersion = prefs.getInt("battery_opt_later_version", -1)
        return laterVersion != currentVersion
    }

    fun markLater(context: Context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("battery_opt_later_version", BuildConfig.VERSION_CODE)
            .apply()
    }

    fun clearLater(context: Context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .remove("battery_opt_later_version")
            .apply()
    }

    fun openXiaomiBatterySettings(context: Context) {
        try {
            val intent = Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                if (context.findActivity() == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent().apply {
                    setClassName("com.miui.securitycenter", "com.miui.powercenter.PowerSettingsActivity")
                    if (context.findActivity() == null) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                openAppSettings(context)
            }
        }
    }
}

/**
 * A headless Composable to seamlessly request runtime permissions within Compose navigation.
 * 
 * @param requestTrigger When this boolean toggles (true), the system permission dialog is launched.
 * @param onPermissionGranted Triggered immediately upon success.
 * @param onPermissionDenied Triggered when user selects "Deny". Passes [isPermanentlyDenied]=true correctly analyzed.
 */
@Composable
fun LocationPermissionRequest(
    requestTrigger: Boolean,
    onPermissionGranted: () -> Unit,
    onPermissionDenied: (isPermanentlyDenied: Boolean) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            val shouldShowRationale = PermissionHandler.shouldShowRationale(context)
            val hasRequestedBefore = PermissionHandler.hasRequestedLocationBefore(context)
            
            // Permanent denial logic: If we asked before, and the OS now blocks rationale.
            val isPermanentlyDenied = hasRequestedBefore && !shouldShowRationale
            
            onPermissionDenied(isPermanentlyDenied)
        }
        
        // At the end of the request callback, mark that we've requested it at least once.
        PermissionHandler.markLocationRequested(context)
    }

    LaunchedEffect(requestTrigger) {
        if (requestTrigger) {
            if (PermissionHandler.hasLocationPermission(context)) {
                onPermissionGranted()
            } else {
                launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}
