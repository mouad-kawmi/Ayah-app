package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun EnableGpsDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onEnableGpsClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = AppTranslation.translate("enable_gps_title", language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = AppTranslation.translate("enable_gps_desc", language) 
            )
        },
        icon = {
            Icon(Icons.Rounded.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onEnableGpsClick()
                }
            ) {
                Text(
                    text = AppTranslation.translate("enable_gps", language),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppTranslation.translate("not_now", language))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun PermissionDeniedDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = AppTranslation.translate("permission_denied_title", language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = AppTranslation.translate("permission_denied_desc", language) 
            )
        },
        icon = {
            Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenSettingsClick()
                }
            ) {
                Text(
                    text = AppTranslation.translate("open_settings", language),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppTranslation.translate("cancel", language))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun XiaomiBatteryFixDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = AppTranslation.translate("battery_fix_title", language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = AppTranslation.translate("battery_fix_desc", language)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenSettingsClick()
                }
            ) {
                Text(
                    text = AppTranslation.translate("open_settings", language),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppTranslation.translate("not_now", language))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
