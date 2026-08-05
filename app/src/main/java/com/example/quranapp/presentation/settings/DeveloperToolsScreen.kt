package com.example.quranapp.presentation.settings

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranapp.core.debug.DashboardReport
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.DeveloperMode
import com.example.quranapp.core.debug.DiagnosticsCollector
import com.example.quranapp.core.debug.DiagnosticsDashboard
import com.example.quranapp.core.debug.LogExporter
import com.example.quranapp.core.debug.PipelineStageType
import com.example.quranapp.core.debug.PrayerPipelineState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperToolsScreen(
    onBack: () -> Unit,
    onNavigateToLogViewer: () -> Unit
) {
    val context = LocalContext.current
    val primaryGreen = Color(0xFF004d40)
    val goldColor = Color(0xFFC5A059)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)
    val iconBg = Color(0xFFE8F5E9)

    val developerMode = remember { DeveloperMode(context) }
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf(DiagnosticsDashboard.reportSnapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            report = DiagnosticsDashboard.reportSnapshot()
            delay(2000)
        }
    }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf(false) }

    val appInfo = remember { AppInfo.read(context) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "أدوات المطور",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
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
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(
                    title = "التطبيق",
                    icon = Icons.Default.Info,
                    primaryGreen = primaryGreen,
                    textDark = textDark,
                    textGray = textGray,
                    iconBg = iconBg,
                    cardBg = cardBg
                ) {
                    AppInfoRow("إصدار التطبيق", appInfo.versionName, textDark, textGray)
                    AppInfoRow("نوع البناء", appInfo.buildType, textDark, textGray)
                    AppInfoRow("معرّف الجلسة", appInfo.sessionId, textDark, textGray)
                    AppInfoRow("إصدار أندرويد", appInfo.androidVersion, textDark, textGray)
                    AppInfoRow("الشركة المصنعة", appInfo.manufacturer, textDark, textGray)
                    AppInfoRow("الطراز", appInfo.model, textDark, textGray)
                    AppInfoRow("اسم العملية", appInfo.processName, textDark, textGray)
                }
            }

            item {
                SectionCard(
                    title = "لوحة التشخيص",
                    icon = Icons.Default.MonitorHeart,
                    primaryGreen = primaryGreen,
                    textDark = textDark,
                    textGray = textGray,
                    iconBg = iconBg,
                    cardBg = cardBg
                ) {
                    DashboardSection(
                        report = report,
                        textDark = textDark,
                        textGray = textGray,
                        primaryGreen = primaryGreen,
                        goldColor = goldColor,
                        cardBg = cardBg
                    )
                }
            }

            item {
                SectionCard(
                    title = "السجلات",
                    icon = Icons.Default.Article,
                    primaryGreen = primaryGreen,
                    textDark = textDark,
                    textGray = textGray,
                    iconBg = iconBg,
                    cardBg = cardBg
                ) {
                    DevToolButton(
                        title = "عرض السجلات",
                        subtitle = "فتح عارض السجلات",
                        icon = Icons.Default.Visibility,
                        primaryGreen = primaryGreen,
                        textDark = textDark,
                        textGray = textGray,
                        onClick = onNavigateToLogViewer
                    )
                    DevToolButton(
                        title = "تصدير السجلات",
                        subtitle = "إنشاء ملف ZIP ومشاركته",
                        icon = Icons.Default.Share,
                        primaryGreen = primaryGreen,
                        textDark = textDark,
                        textGray = textGray,
                        onClick = {
                            scope.launch {
                                val zip = LogExporter.exportLogs(context)
                                if (zip != null) {
                                    DevToolsFileUtils.shareFile(context, zip)
                                } else {
                                    Toast.makeText(context, "فشل تصدير السجلات", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    DevToolButton(
                        title = "مسح السجلات",
                        subtitle = "حذف جميع ملفات السجلات",
                        icon = Icons.Default.Delete,
                        primaryGreen = primaryGreen,
                        textDark = textDark,
                        textGray = textGray,
                        onClick = { showClearConfirm = true }
                    )
                }
            }

            if (appInfo.isDebugBuild) {
                item {
                    SectionCard(
                        title = "اختبار الانهيار",
                        icon = Icons.Default.BugReport,
                        primaryGreen = primaryGreen,
                        textDark = textDark,
                        textGray = textGray,
                        iconBg = iconBg,
                        cardBg = cardBg
                    ) {
                        DevToolButton(
                            title = "إطلاق انهيار تجريبي",
                            subtitle = "إلقاء استثناء للتحقق من CrashHandler",
                            icon = Icons.Default.Warning,
                            primaryGreen = Color(0xFFC62828),
                            textDark = textDark,
                            textGray = textGray,
                            onClick = { showCrashConfirm = true }
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "وضع المطور",
                    icon = Icons.Default.Build,
                    primaryGreen = primaryGreen,
                    textDark = textDark,
                    textGray = textGray,
                    iconBg = iconBg,
                    cardBg = cardBg
                ) {
                    DevToolButton(
                        title = "تعطيل وضع المطور",
                        subtitle = "إيقاف الوصول إلى أدوات المطور",
                        icon = Icons.Default.PowerSettingsNew,
                        primaryGreen = primaryGreen,
                        textDark = textDark,
                        textGray = textGray,
                        onClick = {
                            developerMode.disable()
                            onBack()
                        }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(24.dp),
            title = { Text("مسح السجلات؟", color = textDark, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حذف جميع ملفات السجلات الحالية. هل تريد المتابعة؟", color = textGray) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        val ok = DebugLogger.clearAllLogs(context)
                        Toast.makeText(context, if (ok) "تم مسح السجلات" else "فشل مسح السجلات", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("مسح", color = Color(0xFFC62828)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("إلغاء", color = textGray) }
            }
        )
    }

    if (showCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showCrashConfirm = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(24.dp),
            title = { Text("اختبار الانهيار", color = textDark, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من إطلاق انهيار التطبيق للتحقق من CrashHandler؟", color = textGray) },
            confirmButton = {
                TextButton(onClick = {
                    showCrashConfirm = false
                    throw RuntimeException("Developer test crash")
                }) { Text("إطلاق", color = Color(0xFFC62828)) }
            },
            dismissButton = {
                TextButton(onClick = { showCrashConfirm = false }) { Text("إلغاء", color = textGray) }
            }
        )
    }
}

private data class AppInfo(
    val versionName: String,
    val buildType: String,
    val sessionId: String,
    val androidVersion: String,
    val manufacturer: String,
    val model: String,
    val processName: String,
    val isDebugBuild: Boolean
) {
    companion object {
        fun read(context: Context): AppInfo {
            val packageManager = context.packageManager
            val info = runCatching {
                packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()

            val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            return AppInfo(
                versionName = info?.versionName ?: "غير معروف",
                buildType = if (isDebug) "debug" else "release",
                sessionId = DiagnosticsCollector.sessionId,
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                processName = readProcessName(context),
                isDebugBuild = isDebug
            )
        }

        private fun readProcessName(context: Context): String {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return am?.runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }
                ?.processName
                ?: "غير معروف"
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    primaryGreen: Color,
    textDark: Color,
    textGray: Color,
    iconBg: Color,
    cardBg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = iconBg, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = primaryGreen)
                )
            }
            HorizontalDivider(color = primaryGreen.copy(alpha = 0.1f))
            content()
        }
    }
}

@Composable
private fun AppInfoRow(label: String, value: String, textDark: Color, textGray: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = textGray))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = textDark,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 12.dp)
        )
    }
}

@Composable
private fun DevToolButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primaryGreen: Color,
    textDark: Color,
    textGray: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F9F8))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = textDark))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = textGray))
        }
        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null, tint = textGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DashboardSection(
    report: DashboardReport,
    textDark: Color,
    textGray: Color,
    primaryGreen: Color,
    goldColor: Color,
    cardBg: Color
) {
    // ── Summary row ─────────────────────────────────────────────
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBadge("نشطة", report.pipelines.size.toString(), primaryGreen, textDark, textGray)
        StatBadge("مكتملة", report.completedCount.toString(), Color(0xFF2E7D32), textDark, textGray)
        StatBadge("فاشلة", report.failedCount.toString(), Color(0xFFC62828), textDark, textGray)
        StatBadge("تحذيرات", report.warnings.size.toString(), goldColor, textDark, textGray)
    }

    if (report.pipelines.isNotEmpty()) {
        Text(
            "العمليات النشطة",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textDark)
        )
        report.pipelines.forEach { (_, state) ->
            PipelineStateCard(state, textDark, textGray, primaryGreen, cardBg)
        }
    }

    if (report.failureHistory.isNotEmpty()) {
        Text(
            "آخر الإخفاقات",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textDark)
        )
        report.failureHistory.takeLast(5).forEach { failure ->
            FailureCard(failure.prayerKey, failure.stage, failure.message, textDark, textGray, cardBg)
        }
    }

    if (report.warnings.isNotEmpty()) {
        Text(
            "التحذيرات",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textDark)
        )
        report.warnings.takeLast(5).forEach { warning ->
            WarningRow(warning.prayerKey, warning.message, textDark, textGray)
        }
    }

    if (report.pipelines.isEmpty() && report.failureHistory.isEmpty() && report.warnings.isEmpty()) {
        Text(
            "لا يوجد نشاط تشخيصي بعد.",
            style = MaterialTheme.typography.bodySmall.copy(color = textGray)
        )
    }
}

@Composable
private fun RowScope.StatBadge(label: String, value: String, color: Color, textDark: Color, textGray: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = textGray))
    }
}

@Composable
private fun PipelineStateCard(
    state: PrayerPipelineState,
    textDark: Color,
    textGray: Color,
    primaryGreen: Color,
    cardBg: Color
) {
    val currentStage = state.stages.lastOrNull()?.stage ?: PipelineStageType.RECEIVER_STARTED
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9F8))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.prayerKey,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = primaryGreen)
                )
                Text(
                    "معرّف: ${state.traceId}",
                    style = MaterialTheme.typography.labelSmall.copy(color = textGray, fontFamily = FontFamily.Monospace)
                )
            }
            Text(
                "المرحلة الحالية: ${currentStage.name}",
                style = MaterialTheme.typography.bodySmall.copy(color = textDark)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "البداية: ${formatTimestamp(state.startedAtMs)}",
                    style = MaterialTheme.typography.labelSmall.copy(color = textGray)
                )
                Text(
                    "المدة: ${state.totalDurationMs}ms",
                    style = MaterialTheme.typography.labelSmall.copy(color = textGray)
                )
            }
        }
    }
}

@Composable
private fun FailureCard(
    prayerKey: String,
    stage: PipelineStageType,
    message: String,
    textDark: Color,
    textGray: Color,
    cardBg: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFDECEA))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
            Text(
                prayerKey,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
            )
            Text(
                stage.name,
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC62828), fontFamily = FontFamily.Monospace)
            )
        }
        Text(message, style = MaterialTheme.typography.bodySmall.copy(color = textDark), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WarningRow(prayerKey: String, message: String, textDark: Color, textGray: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(16.dp))
        Column {
            Text(
                prayerKey,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textDark)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall.copy(color = textGray),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
}
