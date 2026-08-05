package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onQiblaClick: () -> Unit,
    onTasbihClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlannerClick: () -> Unit,
    onAudioSettingsClick: () -> Unit = {},
    onDownloadManagerClick: () -> Unit = {},
    onNavigateToDeveloperTools: () -> Unit = {}
) {
    SideEffect {
        android.util.Log.d("NAV_TRACE", "MORE_SCREEN_READY")
        android.util.Log.d("NAV_TRACE", "MORE_RECOMPOSE")
    }
    LaunchedEffect(Unit) {
        android.util.Log.d("NAV_TRACE", "MORE_SCREEN_FIRST_COMPOSITION")
    }
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }
    val devPrefs = context.getSharedPreferences("developer_prefs", Context.MODE_PRIVATE)
    var devTapCount by remember { mutableIntStateOf(0) }
    var isDeveloperModeEnabled by remember { mutableStateOf(devPrefs.getBoolean("developer_mode", false)) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
    }

    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = AppTranslation.translate("more", language),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Beautiful App Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mosque,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        Text(
                            text = AppTranslation.translate("app_name", language),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        
                        Text(
                            text = AppTranslation.translate("about_desc", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "v$versionName",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                devTapCount++
                                val remaining = 7 - devTapCount
                                if (remaining > 0) {
                                    Toast.makeText(
                                        context,
                                        if (remaining == 1) "1 tap remaining to enable Developer Mode"
                                        else "$remaining taps remaining to enable Developer Mode",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    devPrefs.edit().putBoolean("developer_mode", true).apply()
                                    isDeveloperModeEnabled = true
                                    devTapCount = 0
                                    Toast.makeText(context, "Developer mode enabled.", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }

                // Grid/List of Options
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Language Selection Section
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = AppTranslation.translate("language_section", language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    Column {
                                        Text(
                                            text = AppTranslation.translate("change_language", language),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "العربية / English / Français",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppLanguage.entries.forEach { lang ->
                                    val isSelected = lang == language
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .clickable { onLanguageChange(lang) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = AppTranslation.translate("services_title", language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // 1. Qibla compass
                    MoreServiceItem(
                        title = AppTranslation.translate("qibla", language),
                        subtitle = AppTranslation.translate("qibla_desc", language),
                        icon = Icons.Rounded.Explore,
                        iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.primary,
                        language = language,
                        onClick = onQiblaClick
                    )

                    // 2. Electronic Tasbih
                    MoreServiceItem(
                        title = AppTranslation.translate("tasbih", language),
                        subtitle = AppTranslation.translate("tasbih_desc", language),
                        icon = Icons.Rounded.AddCircle,
                        iconBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.secondary,
                        language = language,
                        onClick = onTasbihClick
                    )

                    // 3. Khatma Planner
                    MoreServiceItem(
                        title = AppTranslation.translate("khatma_planner", language),
                        subtitle = AppTranslation.translate("khatma_planner_desc", language),
                        icon = Icons.Rounded.MenuBook,
                        iconBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        language = language,
                        onClick = onPlannerClick
                    )

                    // 4. Alarm settings
                    MoreServiceItem(
                        title = AppTranslation.translate("settings_title", language),
                        subtitle = AppTranslation.translate("about_item_sub", language),
                        icon = Icons.Rounded.Settings,
                        iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.primary,
                        language = language,
                        onClick = onSettingsClick
                    )

                    // Spacer between sections
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SunnanSection()
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Settings
                    MoreServiceItem(
                        title = AppTranslation.translate("audio_settings", language),
                        subtitle = AppTranslation.translate("audio_settings_desc", language),
                        icon = Icons.Rounded.VolumeUp,
                        iconBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.secondary,
                        language = language,
                        onClick = onAudioSettingsClick
                    )

                    // Download Manager
                    MoreServiceItem(
                        title = AppTranslation.translate("download_manager", language),
                        subtitle = AppTranslation.translate("download_manager_desc", language),
                        icon = Icons.Rounded.Download,
                        iconBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        language = language,
                        onClick = onDownloadManagerClick
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = AppTranslation.translate("general_options", language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // 4. Share App
                    val shareMsg = AppTranslation.translate("share_text", language)
                    val shareTitle = AppTranslation.translate("share_chooser", language)
                    MoreServiceItem(
                        title = AppTranslation.translate("share", language),
                        subtitle = AppTranslation.translate("share_sub", language),
                        icon = Icons.Rounded.Share,
                        iconBg = MaterialTheme.colorScheme.surfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        language = language,
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareMsg)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, shareTitle))
                        }
                    )

                    // 5. About App credits
                    MoreServiceItem(
                        title = AppTranslation.translate("about_item", language),
                        subtitle = AppTranslation.translate("about_item_sub", language),
                        icon = Icons.Rounded.Info,
                        iconBg = MaterialTheme.colorScheme.surfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        language = language,
                        onClick = { showAboutDialog = true }
                    )

                    if (isDeveloperModeEnabled) {
                        MoreServiceItem(
                            title = "Developer Tools",
                            subtitle = "Debugging and testing tools",
                            icon = Icons.Rounded.Build,
                            iconBg = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            iconColor = MaterialTheme.colorScheme.error,
                            language = language,
                            onClick = onNavigateToDeveloperTools
                        )
                    }
                }
            }
        }
    }

    // Beautiful About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(AppTranslation.translate("close_about", language), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mosque,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = AppTranslation.translate("about_title", language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = AppTranslation.translate("about_body1", language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = AppTranslation.translate("about_body2", language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = AppTranslation.translate("about_quote", language),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

@Composable
fun MoreServiceItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconColor: Color,
    language: AppLanguage,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            val arrowIcon = if (language.isRtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SunnanSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("sunnan_prefs", Context.MODE_PRIVATE)

    var fastingEnabled by remember { mutableStateOf(prefs.getBoolean("fasting", false)) }
    var kahfEnabled by remember { mutableStateOf(prefs.getBoolean("kahf", false)) }
    var duhaEnabled by remember { mutableStateOf(prefs.getBoolean("duha", false)) }
    var witrEnabled by remember { mutableStateOf(prefs.getBoolean("witr", false)) }
    var sabahAdhkarEnabled by remember { mutableStateOf(prefs.getBoolean("sabah", false)) }
    var masaaAdhkarEnabled by remember { mutableStateOf(prefs.getBoolean("masaa", false)) }

    fun updatePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        SunnanAlarmScheduler.updateSunnanAlarms(context)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "السنن والأذكار",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            Text(
                text = "فعل التنبيهات لتذكيرك بالسنن الرواتب والأذكار.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SunnahToggleItem(
                title = "أذكار الصباح",
                description = "تذكير يومي (7:00 صباحاً)",
                checked = sabahAdhkarEnabled,
                onCheckedChange = { 
                    sabahAdhkarEnabled = it
                    updatePref("sabah", it)
                }
            )

            SunnahToggleItem(
                title = "أذكار المساء",
                description = "تذكير يومي (5:00 مساءً)",
                checked = masaaAdhkarEnabled,
                onCheckedChange = { 
                    masaaAdhkarEnabled = it
                    updatePref("masaa", it)
                }
            )

            SunnahToggleItem(
                title = "صيام الإثنين والخميس",
                description = "تذكير يومي الأحد والأربعاء",
                checked = fastingEnabled,
                onCheckedChange = { 
                    fastingEnabled = it
                    updatePref("fasting", it)
                }
            )
            
            SunnahToggleItem(
                title = "سورة الكهف",
                description = "تذكير بقراءتها يوم الجمعة",
                checked = kahfEnabled,
                onCheckedChange = { 
                    kahfEnabled = it
                    updatePref("kahf", it)
                }
            )
            
            SunnahToggleItem(
                title = "صلاة الضحى",
                description = "تذكير يومي (9:00 صباحاً)",
                checked = duhaEnabled,
                onCheckedChange = { 
                    duhaEnabled = it
                    updatePref("duha", it)
                }
            )

            SunnahToggleItem(
                title = "صلاة الوتر",
                description = "تذكير قبل النوم (10:00 مساءً)",
                checked = witrEnabled,
                onCheckedChange = { 
                    witrEnabled = it
                    updatePref("witr", it)
                }
            )
        }
    }
}

@Composable
fun SunnahToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
