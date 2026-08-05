package com.example.quranapp.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import com.example.quranapp.core.debug.DeveloperMode
import com.example.quranapp.core.debug.DeveloperModeActivator

data class MoreItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPrayer: () -> Unit = {},
    onNavigateToAudio: () -> Unit = {},
    onNavigateToQibla: () -> Unit = {},
    onNavigateToAdhanSettings: () -> Unit = {},
    onNavigateToKhatma: () -> Unit = {},
    onNavigateToAsmaulHusna: () -> Unit = {},
    onNavigateToSunnahReminders: () -> Unit = {},
    onNavigateToBukhari: () -> Unit = {},
    onNavigateToDeveloperTools: () -> Unit = {},
    onNavigateToTafsirManager: () -> Unit = {},
    onNavigateToSourcesLicenses: () -> Unit = {}
) {
    val primaryGreen = Color(0xFF004d40)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)
    val iconBg = Color(0xFFE8F5E9)

    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    val developerMode = remember { DeveloperMode(context) }
    var developerModeEnabled by remember { mutableStateOf(developerMode.isEnabled) }
    val coroutineScope = rememberCoroutineScope()
    val developerModeActivator = remember {
        DeveloperModeActivator(
            developerMode = developerMode,
            scope = coroutineScope
        ) { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            developerModeEnabled = developerMode.isEnabled
        }
    }

    val religiousTools = listOf(
        MoreItem("مواقيت الصلاة", "جميع أوقات اليوم من الفجر إلى العشاء", Icons.Default.AccessTime, onNavigateToPrayer),
        MoreItem("القبلة", "تحديد اتجاه مكة المكرمة", Icons.Default.Explore, onNavigateToQibla),
        MoreItem("الختمة", "15 يوم، 30 يوم، أو 60 يوم مع تتبع الصفحات", Icons.Default.Flag, onNavigateToKhatma),
        MoreItem("الصوتيات المحملة", "السور المحملة للاستماع دون إنترنت", Icons.Default.MusicNote, onNavigateToAudio),
        MoreItem("أسماء الله الحسنى", "تصفح الأسماء التسعة والتسعين مع معانيها", Icons.Default.AutoAwesome, onNavigateToAsmaulHusna),
        MoreItem("تنبيهات السنن", "تذكيرات بسورة الكهف والملك والأذكار والصيام", Icons.Default.Notifications, onNavigateToSunnahReminders),
        MoreItem("صحيح البخاري", "الأحاديث النبوية الشريفة", Icons.Default.MenuBook, onNavigateToBukhari),
        MoreItem("التفاسير", "تحميل تفاسير إضافية وإدارتها", Icons.Default.LibraryBooks, onNavigateToTafsirManager)
    )

    val settingsItems = listOf(
        MoreItem("الأذان", "اختيار الصوت وتحديد الصلوات", Icons.Default.NotificationsActive, onNavigateToAdhanSettings)
    )

    val generalItems = buildList {
        add(
            MoreItem("حول التطبيق", "إصدار التطبيق ومعلومات المطور", Icons.Default.Info) {
                showAboutDialog = true
            }
        )
        add(
            MoreItem("المصادر والتراخيص", "مصادر وتراخيص التفاسير المقدمة", Icons.Default.Copyright, onNavigateToSourcesLicenses)
        )
        add(
            MoreItem("شارك التطبيق", "أرسل التطبيق لأصدقائك", Icons.Default.Share) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "تطبيق القرآن الكريم والأذان - رفيقك اليومي، حمل الآن: https://play.google.com/store/apps/details?id=com.example.quranapp")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            }
        )
        if (developerModeEnabled) {
            add(
                MoreItem("أدوات المطور", "أدوات التشخيص والتصحيح", Icons.Default.Build, onNavigateToDeveloperTools)
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "المزيد",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        },
        containerColor = lightBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "الأدوات الدينية",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen,
                            fontSize = 18.sp
                        )
                    )
                }
            }

            items(religiousTools) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Navigate",
                            tint = textGray,
                            modifier = Modifier.size(24.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark,
                                        fontSize = 16.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textGray,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = iconBg,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = primaryGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "الإعدادات",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen,
                            fontSize = 18.sp
                        )
                    )
                }
            }

            items(settingsItems) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Navigate",
                            tint = textGray,
                            modifier = Modifier.size(24.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark,
                                        fontSize = 16.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textGray,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = iconBg,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = primaryGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "عام",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen,
                            fontSize = 18.sp
                        )
                    )
                }
            }

            items(generalItems) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Navigate",
                            tint = textGray,
                            modifier = Modifier.size(24.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark,
                                        fontSize = 16.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textGray,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = iconBg,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = primaryGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(24.dp),
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "App Icon",
                        tint = primaryGreen,
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = "القرآن الكريم والأذان",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold, color = primaryGreen
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "الإصدار 1.0.0",
                        style = MaterialTheme.typography.bodyMedium.copy(color = textGray),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { developerModeActivator.onVersionTapped() }
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "تطبيق إسلامي شامل يضم القرآن الكريم، مواقيت الصلاة، الأذكار، أسماء الله الحسنى والمزيد. تم تصميمه بعناية ليقدم أفضل تجربة للمستخدم المسلم.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = textDark, lineHeight = 24.sp),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }
}
