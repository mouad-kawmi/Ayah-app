package com.example

import android.os.Build
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun getBatteryOptimizationHintKey(): String {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("samsung") -> "battery_opt_hint_samsung"
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            "battery_opt_hint_xiaomi"
        manufacturer.contains("oppo") || manufacturer.contains("oneplus") ->
            "battery_opt_hint_oppo"
        manufacturer.contains("realme") ->
            "battery_opt_hint_realme"
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "battery_opt_hint_huawei"
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
            "battery_opt_hint_vivo"
        manufacturer.contains("google") || manufacturer.contains("pixel") ->
            "battery_opt_hint_pixel"
        else -> "battery_opt_hint_unknown"
    }
}

@Composable
fun BatteryOptimizationReminderDialog(
    language: AppLanguage,
    manufacturerHintKey: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit = onDismiss
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = AppTranslation.translate("battery_opt_title", language),
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
                    text = AppTranslation.translate("battery_opt_desc", language)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = AppTranslation.translate(manufacturerHintKey, language),
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
                    text = AppTranslation.translate("disable_battery_optimization", language),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(AppTranslation.translate("battery_opt_later", language))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
