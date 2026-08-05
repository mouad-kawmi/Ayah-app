package com.example.quranapp.presentation.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
fun BatteryOptimizationReminderDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val manufacturerHint = getManufacturerHint()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "حافظ على الأذان في الوقت",
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
                    text = "التطبيق يحتاج إلى تعطيل تحسين البطارية لضمان وصول الأذان في الوقت المحدد، خاصة إذا كان الهاتف في وضع السكون."
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = manufacturerHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = "بعد فتح الإعدادات، ابحث عن هذا التطبيق واختر \"غير محسن\" أو \"بدون قيود\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenSettings()
            }) {
                Text(
                    text = "تعطيل تحسين البطارية",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("لاحقاً")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun getManufacturerHint(): String {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("samsung") ->
            "الإعدادات > العناية بالجهاز > البطارية > حدود الاستخدام الخلفي > التطبيقات غير المحسّنة > إضافة هذا التطبيق"
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            "الإعدادات > البطارية > توفير الطاقة > اختيار التطبيقات > إزالة القيود عن هذا التطبيق"
        manufacturer.contains("oppo") || manufacturer.contains("oneplus") ->
            "الإعدادات > البطارية > إدارة التطبيقات > هذا التطبيق > تعطيل التوفير التلقائي"
        manufacturer.contains("realme") ->
            "الإعدادات > البطارية > إدارة التطبيقات > هذا التطبيق > تعطيل الإدارة التلقائية"
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "الإعدادات > البطارية > تشغيل التطبيقات > إدارة يدوية > السماح بالتشغيل في الخلفية"
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
            "الإعدادات > البطارية > إدارة التطبيقات > هذا التطبيق > تعطيل تحسين البطارية"
        manufacturer.contains("google") || manufacturer.contains("pixel") ->
            "الإعدادات > التطبيقات > هذا التطبيق > البطارية > غير محسّن"
        else ->
            "الإعدادات > التطبيقات > هذا التطبيق > البطارية > اختيار \"غير محسّن\" أو \"بدون قيود\""
    }
}
