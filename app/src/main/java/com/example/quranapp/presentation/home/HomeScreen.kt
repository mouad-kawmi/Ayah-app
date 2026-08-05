package com.example.quranapp.presentation.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.core.utils.PermissionHandler
import com.example.quranapp.core.utils.QuranPreferences
import com.example.quranapp.data.prayer.PrayerCardInfo
import com.example.quranapp.data.prayer.PrayerState
import com.example.quranapp.data.prayer.PrayerStateMachine
import com.example.quranapp.presentation.prayer.LocationState
import com.example.quranapp.presentation.prayer.PrayerTimesViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class VerseOfTheDay(
    val text: String,
    val surahName: String,
    val verseNumber: Int
)

data class MainService(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

data class PrayerCountdownDisplay(
    val title: String,
    val displayPrayer: PrayerCardInfo?,
    val displayPrayerTime: String,
    val timerText: String,
    val timerLabel: String,
    val progress: Float,
    val nextPrayer: PrayerCardInfo?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onNavigateToQuran: () -> Unit,
    onNavigateToPrayer: () -> Unit,
    onNavigateToAzkar: () -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToKhatma: () -> Unit,
    onNavigateToLastRead: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val primaryGreen = Color(0xFF004d40)
    val lightGreenIndicator = Color(0xFFD8E7D8)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)
    val goldAccent = Color(0xFFC5A059)

    val prayerTimesViewModel: PrayerTimesViewModel = viewModel()
    val uiState by prayerTimesViewModel.uiState.collectAsState()

    val lastSurahId = QuranPreferences.getLastSurahId(context)
    val lastPage = QuranPreferences.getLastPage(context)
    val lastSurahName = QuranPreferences.getLastSurahName(context)

    val services = listOf(
        MainService("القرآن", "تلاوة وترجمة", Icons.Default.MenuBook, onNavigateToQuran),
        MainService("مواقيت الصلاة", "جميع أوقات اليوم من الفجر إلى العشاء", Icons.Default.AccessTime, onNavigateToPrayer),
        MainService("القبلة", "تحديد اتجاه مكة المكرمة", Icons.Default.Explore, onNavigateToQibla),
        MainService("الأذكار", "حصن المسلم", Icons.Default.Schedule, onNavigateToAzkar),
        MainService("الختمة", "متابعة وردك اليومي", Icons.Default.AutoStories, onNavigateToKhatma)
    )

    val dailyVerses = remember {
        listOf(
            VerseOfTheDay("\"فإن كذبوك فقل ربكم ذو رحمة واسعة ولا يرد بأسه عن القوم المجرمين\"", "سورة الأنعام", 147),
            VerseOfTheDay("\"إلا تنصروه فقد نصره الله إذ أخرجه الذين كفروا ثاني اثنين إذ هما في الغار\"", "سورة التوبة", 40),
            VerseOfTheDay("\"وقال ربكم ادعوني أستجيب لكم إن الذين يستكبرون عن عبادتي سيدخلون جهنم داخرين\"", "سورة غافر", 60),
            VerseOfTheDay("\"إنا نحن نزلنا الذكر وإنا له لحافظون\"", "سورة الحجر", 9),
            VerseOfTheDay("\"لا يكلف الله نفسا إلا وسعها لها ما كسبت وعليها ما اكتسبت\"", "سورة البقرة", 286)
        )
    }

    val todayVerse = remember {
        val dayOfYear = LocalDate.now().dayOfYear
        dailyVerses[abs(dayOfYear) % dailyVerses.size]
    }

    val todayGregorian = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", java.util.Locale("ar")))
        } catch (e: Exception) { "الجمعة، 24 يوليو 2026" }
    }

    val todayHijri = remember {
        try {
            val hijrahDate = java.time.chrono.HijrahDate.now()
            val day = hijrahDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
            val monthIndex = (hijrahDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1).coerceIn(0, 11)
            val year = hijrahDate.get(java.time.temporal.ChronoField.YEAR)
            val months = listOf("المحرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
            "$day ${months[monthIndex]} $year هـ"
        } catch (e: Exception) { "10 صفر 1448 هـ" }
    }

    val prayerState by PrayerStateMachine.state.collectAsState()
    val prayerCards by PrayerStateMachine.prayerCards.collectAsState()
    val prayers = prayerCards

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            prayerTimesViewModel.onLocationPermissionGranted()
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }

    val locationEnabledLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        prayerTimesViewModel.retryLocationResolution()
    }

    // Pure UI consumer: the countdown is never computed here, it is derived from the
    // single source of truth (PrayerStateMachine) and mapped to the local display model.
    val displayState = buildCountdownDisplay(prayerState, prayerCards)

    var showBatteryDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showAutoStartDialog by remember { mutableStateOf(false) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    val isChinesePhone = remember {
        val m = Build.MANUFACTURER.lowercase()
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
        m.contains("huawei") || m.contains("honor") ||
        m.contains("oppo") || m.contains("oneplus") ||
        m.contains("vivo") || m.contains("iqoo") ||
        m.contains("realme")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showBatteryDialog = !PermissionHandler.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        showBatteryDialog = !PermissionHandler.isIgnoringBatteryOptimizations(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!PermissionHandler.hasNotificationPermission(context)) {
            showNotificationDialog = true
        }
        if (isChinesePhone) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val dismissCount = prefs.getInt("autostart_dismiss_count", 0)
            if (dismissCount < 3) {
                showAutoStartDialog = true
            }
        }
        if (!PermissionHandler.canScheduleExactAlarms(context)) {
            showExactAlarmDialog = true
        }
    }

    if (showBatteryDialog) {
        BatteryOptimizationReminderDialog(
            onDismiss = { showBatteryDialog = false },
            onOpenSettings = {
                val intent = PermissionHandler.createBatteryOptimizationSettingsIntent(context)
                if (intent != null) {
                    settingsLauncher.launch(intent!!)
                } else {
                    PermissionHandler.openAppSettings(context)
                }
            },
            onLater = { showBatteryDialog = false }
        )
    }

    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = {
                Text("تفعيل الإشعارات", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("لضمان وصول الأذان والإشعارات في الوقت المحدد، يرجى السماح بالتطبيق بإرسال الإشعارات من الإعدادات.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationDialog = false
                    PermissionHandler.openAppSettings(context)
                }) {
                    Text("فتح الإعدادات", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDialog = false }) {
                    Text("لاحقاً")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showAutoStartDialog) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        AutoStartReminderDialog(
            onDismiss = {
                showAutoStartDialog = false
                prefs.edit().putInt("autostart_dismiss_count", prefs.getInt("autostart_dismiss_count", 0) + 1).apply()
            },
            onOpenSettings = {
                prefs.edit().putInt("autostart_dismiss_count", prefs.getInt("autostart_dismiss_count", 0) + 1).apply()
                openAutoStartSettings(context)
            },
            onLater = {
                showAutoStartDialog = false
                prefs.edit().putInt("autostart_dismiss_count", prefs.getInt("autostart_dismiss_count", 0) + 1).apply()
            }
        )
    }

    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = {
                Text("تفعيل الإنذار الدقيق", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "نظام Android 12 فأكثر يطلب إذن خاص لتشغيل الإنذار في الوقت المحدد بدقة.\n\n" +
                    "بدون هذا الإذن، قد يتأخر الأذان أو لا ينطلق إطلاقاً.\n\n" +
                    "يرجى النقر على \"فتح الإعدادات\" وتفعيل خيار \"السماح بالإنذار الدقيق\"."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmDialog = false
                    PermissionHandler.openExactAlarmSettings(context)
                }) {
                    Text("فتح الإعدادات", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) {
                    Text("لاحقاً")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isSyncing,
        onRefresh = { prayerTimesViewModel.refresh() }
    )

    Box(
        modifier = Modifier.fillMaxSize().background(lightBg).pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
                // Card 1: Next Prayer & Countdown
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            // Location chip + Dates row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LocationChip(
                                    locationState = uiState.locationState,
                                    cityName = uiState.cityName,
                                    isSyncing = uiState.isSyncing,
                                    primaryGreen = primaryGreen,
                                    textGray = textGray,
                                    onPermissionRequest = {
                                        locationPermissionLauncher.launch(
                                            buildList {
                                                add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                                add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            }.toTypedArray()
                                        )
                                    },
                                    onLocationSettings = {
                                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        locationEnabledLauncher.launch(intent)
                                    }
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = todayHijri,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold, color = goldAccent, fontSize = 18.sp,
                                            textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = todayGregorian,
                                        style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 13.sp)
                                    )
                                }
                            }

                            // Permission denied banner inside the card
                            if (uiState.locationState is LocationState.PermissionDenied) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFF8E1)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "للحصول على مواقيت الصلاة حسب موقعك الحالي، يرجى السماح للتطبيق باستخدام الموقع.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF795548)),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = { prayerTimesViewModel.showCityPicker() },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("اختيار مدينة يدوياً", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                            Button(
                                                onClick = {
                                                    locationPermissionLauncher.launch(
                                                        buildList {
                                                            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                                            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                                add(android.Manifest.permission.POST_NOTIFICATIONS)
                                                            }
                                                        }.toTypedArray()
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("تفعيل الموقع", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                }
                            }

                            // Error message when no cached data
                            val errorMsg = uiState.error
                            if (errorMsg != null && prayers.isEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB00020), fontSize = 13.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Next prayer row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = displayState.title,
                                        style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 14.sp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = displayState.displayPrayer?.name ?: if (uiState.isLoading) "جاري التحميل" else "--",
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Bold, color = primaryGreen, fontSize = 36.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = displayState.displayPrayerTime,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold, color = textDark, fontSize = 28.sp
                                        )
                                    )
                                }

                                Box(
                                    modifier = Modifier.size(130.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 10.dp.toPx()
                                        val diameter = size.minDimension - strokeWidth
                                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                                        val arcSize = Size(diameter, diameter)
                                        drawCircle(
                                            color = Color(0xFFF0F4F0),
                                            radius = diameter / 2f,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = primaryGreen,
                                            startAngle = -90f,
                                            sweepAngle = displayState.progress * 360f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = displayState.timerText,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold, color = textDark, fontSize = 16.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = displayState.timerLabel,
                                            style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 12.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section Header: أوقات الصلاة
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "أوقات الصلاة",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold, color = textDark, fontSize = 18.sp
                            )
                        )
                    }
                }

                // Prayer Times Grid
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val chunks = prayers.chunked(3)
                        chunks.forEachIndexed { index, rowPrayers ->
                            // RTL layout: Fajr on right, Dhuhr on left (Row 1), Asr on right, Isha on left (Row 2)
                            val displayPrayers = rowPrayers.reversed()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                displayPrayers.forEach { prayer ->
                                    val isNext = prayerState.prayerName == prayer.name
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                prayerTimesViewModel.togglePrayerEnabled(prayer.name)
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isNext) lightGreenIndicator else cardBg
                                        ),
                                        border = if (isNext) BorderStroke(1.5.dp, primaryGreen) else null,
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (prayer.isEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                                    contentDescription = "Adhan Toggle",
                                                    tint = if (prayer.isEnabled) primaryGreen else Color.LightGray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = prayer.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isNext) primaryGreen else textDark
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = prayer.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold, color = textDark, fontSize = 18.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Verse of the Day
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFDF7E7)
                            ) {
                                Text(
                                    text = "آية اليوم",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold, color = goldAccent, fontSize = 14.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = todayVerse.text,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold, color = textDark, fontSize = 20.sp, lineHeight = 32.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "${todayVerse.surahName} • آية ${todayVerse.verseNumber}",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium.copy(color = textGray, fontSize = 14.sp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Section Header: الخدمات الرئيسية
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "الخدمات الرئيسية",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold, color = textDark, fontSize = 18.sp
                            )
                        )
                    }
                }

                // Services Grid
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val serviceChunks = services.chunked(2)
                        serviceChunks.forEach { rowServices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowServices.forEach { service ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(150.dp)
                                            .clickable(onClick = service.onClick),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = lightGreenIndicator,
                                                modifier = Modifier.size(50.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = service.icon,
                                                        contentDescription = service.title,
                                                        tint = primaryGreen,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = service.title,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold, color = textDark, fontSize = 16.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = service.subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 11.sp),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Last Read Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToLastRead(lastSurahId, lastPage) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFDF7E7),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Continue Reading",
                                        tint = goldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "آخر قراءة • Récemment lu",
                                        style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 12.sp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "سُورَةُ $lastSurahName",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold, color = textDark, fontSize = 18.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "الصفحة $lastPage",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold, color = goldAccent, fontSize = 15.sp
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(44.dp)
                                        .background(
                                            brush = Brush.verticalGradient(colors = listOf(goldAccent, primaryGreen)),
                                            shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Bookmark",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    }
                }
                PullRefreshIndicator(
                    refreshing = uiState.isSyncing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

        // Location disabled dialog
        if (uiState.showLocationDialog) {
            LocationDisabledDialog(
                onEnableLocation = {
                    prayerTimesViewModel.dismissLocationDialog()
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    locationEnabledLauncher.launch(intent)
                },
                onDismiss = {
                    prayerTimesViewModel.dismissLocationDialog()
                },
                onManualCity = {
                    prayerTimesViewModel.dismissLocationDialog()
                    prayerTimesViewModel.showCityPicker()
                },
                primaryGreen = primaryGreen,
                goldAccent = goldAccent
            )
        }

        // City picker dialog
        if (uiState.showCityPicker) {
            AlertDialog(
                onDismissRequest = { prayerTimesViewModel.hideCityPicker() },
                title = {
                    Text(
                        text = "اختر مدينتك",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(prayerTimesViewModel.availableCities.size) { index ->
                            val city = prayerTimesViewModel.availableCities[index]
                            TextButton(
                                onClick = { prayerTimesViewModel.selectCity(city) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = city,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

private fun buildCountdownDisplay(
    state: PrayerState,
    cards: List<PrayerCardInfo>
): PrayerCountdownDisplay {
    val empty = PrayerCountdownDisplay("--", null, "--:--", "--:--:--", "--", 0f, null)
    if (cards.isEmpty() || state.prayerTime == "--:--") return empty
    val referenced = cards.firstOrNull { it.name == state.prayerName }
    return when (state) {
        is PrayerState.BeforePrayer -> PrayerCountdownDisplay(
            title = "الصلاة القادمة",
            displayPrayer = referenced,
            displayPrayerTime = state.prayerTime,
            timerText = formatCountdown(state.remainingSeconds),
            timerLabel = "متبقي",
            progress = state.progress,
            nextPrayer = referenced
        )
        is PrayerState.AdhanPlaying -> PrayerCountdownDisplay(
            title = "الصلاة الحالية",
            displayPrayer = referenced,
            displayPrayerTime = state.prayerTime,
            timerText = formatCountdown(state.elapsedSeconds),
            timerLabel = "مضى",
            progress = state.progress,
            nextPrayer = null
        )
        is PrayerState.PostPrayer -> PrayerCountdownDisplay(
            title = "الصلاة الحالية",
            displayPrayer = referenced,
            displayPrayerTime = state.prayerTime,
            timerText = formatCountdown(state.elapsedSeconds),
            timerLabel = "مضى",
            progress = state.progress,
            nextPrayer = null
        )
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

@Composable
private fun LocationChip(
    locationState: LocationState,
    cityName: String,
    isSyncing: Boolean,
    primaryGreen: Color,
    textGray: Color,
    onPermissionRequest: () -> Unit,
    onLocationSettings: () -> Unit
) {
    when (locationState) {
        is LocationState.Locating -> {
            Surface(shape = RoundedCornerShape(50), color = Color(0xFFF0F4F0)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primaryGreen
                    )
                    Text(
                        text = "جاري تحديد موقعك...",
                        style = MaterialTheme.typography.bodySmall.copy(color = textGray)
                    )
                }
            }
        }
        is LocationState.PermissionDenied -> {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFFFF3E0),
                onClick = onPermissionRequest
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = "Permission denied",
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "الموقع غير مفعل",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    )
                    if (cityName.isNotEmpty()) {
                        Text(
                            text = cityName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2C3E2D))
                        )
                    }
                }
            }
        }
        is LocationState.LocationDisabled -> {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFFFF3E0),
                onClick = onLocationSettings
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = "GPS disabled",
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "خدمات الموقع متوقفة",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    )
                    if (cityName.isNotEmpty()) {
                        Text(
                            text = cityName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2C3E2D))
                        )
                    }
                }
            }
        }
        else -> {
            Surface(shape = RoundedCornerShape(50), color = Color(0xFFF0F4F0)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = primaryGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (cityName.isNotEmpty()) cityName else "غير محدد",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2C3E2D))
                    )
                    if (isSyncing) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primaryGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationDisabledDialog(
    onEnableLocation: () -> Unit,
    onDismiss: () -> Unit,
    onManualCity: () -> Unit,
    primaryGreen: Color,
    goldAccent: Color
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = goldAccent,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "تفعيل الموقع",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = primaryGreen,
                        fontSize = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "لتحديد موقعك الحالي وتحديث مواقيت الصلاة بدقة، يرجى تفعيل خدمة الموقع. يمكنك استخدام التطبيق بدون تفعيل الموقع، ويمكنك تحديث موقعك في أي وقت من خلال سحب الصفحة.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF7F8C8D),
                        lineHeight = 24.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onEnableLocation,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                ) {
                    Text(
                        text = "تفعيل الموقع",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                OutlinedButton(
                    onClick = onManualCity,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryGreen),
                    border = BorderStroke(1.dp, primaryGreen)
                ) {
                    Text(
                        text = "اختيار مدينة يدوياً",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen
                        )
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "ليس الآن",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF7F8C8D),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
