package com.example.quranapp.presentation.home

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.quranapp.core.utils.PermissionHandler

fun openAutoStartSettings(context: android.content.Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    try {
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                PermissionHandler.openXiaomiBatterySettings(context)
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                val intent = Intent().apply {
                    setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalListActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") -> {
                val intent = Intent().apply {
                    setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                val intent = Intent().apply {
                    setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            manufacturer.contains("realme") -> {
                val intent = Intent().apply {
                    setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            else -> {
                PermissionHandler.openAppSettings(context)
            }
        }
    } catch (_: Exception) {
        PermissionHandler.openAppSettings(context)
    }
}

private fun getAutoStartHint(): String {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            "الإعدادات > التطبيقات > إدارة التطبيقات > هذا التطبيق > إذن التشغيل التلقائي > تشغيل"
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "الإعدادات > البطارية > تشغيل التطبيقات > إدارة يدوية > السماح بالتشغيل التلقائي + التشغيل في الخلفية"
        manufacturer.contains("oppo") || manufacturer.contains("oneplus") ->
            "الإعدادات > التطبيقات > إدارة التطبيقات > هذا التطبيق > التشغيل التلقائي > تشغيل"
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
            "الإعدادات > إدارة التطبيقات > هذا التطبيق > الإدارة التلقائية > السماح بالتشغيل التلقائي"
        manufacturer.contains("realme") ->
            "الإعدادات > التطبيقات > إدارة التطبيقات > هذا التطبيق > التشغيل التلقائي > تشغيل"
        manufacturer.contains("samsung") ->
            "الإعدادات > التطبيقات > هذا التطبيق > البطارية > عدم تقييد الاستخدام"
        manufacturer.contains("google") || manufacturer.contains("pixel") ->
            "الإعدادات > التطبيقات > هذا التطبيق > البطارية > غير محسّن"
        else ->
            "الإعدادات > التطبيقات > هذا التطبيق > ابحث عن خيار \"التشغيل التلقائي\" أو \"Auto-start\" وقم بتفعيله"
    }
}

@Composable
fun AutoStartReminderDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit = onDismiss
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تفعيل التشغيل التلقائي",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "بعض الهواتف تمنع التطبيقات من التشغيل التلقائي في الخلفية، مما يمنع وصول الأذان في الوقت المحدد."
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = "اتبع الخطوات التالية حسب جهازك:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = getAutoStartHint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = "بعد فتح الإعدادات، ابحث عن هذا التطبيق وقم بتفعيل خيار \"التشغيل التلقائي\" أو \"Auto-start\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "إذا واجهت صعوبة، يمكنك البحث على الإنترنت: \"" + getDeviceModel() + " auto-start app\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenSettings()
            }) {
                Text(
                    text = "فتح الإعدادات",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("لاحقاً")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

private fun getDeviceModel(): String {
    return "${Build.MANUFACTURER} ${Build.MODEL}"
}
