package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aistudio.quran.mwkpqz.R
import com.example.debug.DashboardReport
import com.example.debug.DebugLogger
import com.example.debug.DiagnosticsCollector
import com.example.debug.DiagnosticsDashboard
import com.example.debug.LogCategory
import com.example.debug.LogExporter
import com.example.debug.PerformanceOverlay
import com.example.debug.PerformanceProfiler
import com.example.debug.PipelineFailure
import com.example.debug.PipelineStageType
import com.example.debug.PipelineWarning
import com.example.debug.PrayerPipelineState
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.graphics.Brush

@Composable
fun DeveloperToolsSection(
    mainViewModel: MainViewModel,
    onNavigateToLogViewer: () -> Unit,
    onDisableDeveloperMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ElevatedSurface)
    ) {
        // Red top accent line to signify dev mode
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Red.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Red.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Developer Tools",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                )
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            DevToolItem("View Logs", "Open LogViewerScreen", Icons.Rounded.List) {
                onNavigateToLogViewer()
            }
            DevToolItem("Export Logs", "Generate ZIP and share", Icons.Rounded.Share) {
                coroutineScope.launch {
                    val zipFile = LogExporter.exportLogs(context)
                    if (zipFile != null) {
                        shareFile(context, zipFile)
                    } else {
                        Toast.makeText(context, "Failed to export logs", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            DevToolItem("Clear Logs", "Delete all log files", Icons.Rounded.Delete) {
                showClearConfirm = true
            }
            DevToolItem("Test Notification", "Send immediate test alert", Icons.Rounded.Notifications) {
                sendTestNotification(context)
                Toast.makeText(context, "Notification sent", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Test Adhan", "Start AdhanPlaybackService", Icons.Rounded.VolumeUp) {
                testAdhan(context)
                Toast.makeText(context, "Adhan started", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Test Alarm", "Schedule alarm in 1min", Icons.Rounded.Alarm) {
                PrayerAlarmScheduler.scheduleTestPrayerAlarm(context)
                Toast.makeText(context, "Alarm scheduled in 1min", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Test Sunnan Alarm", "Schedule sunnan alarm in 1min", Icons.Rounded.Alarm) {
                SunnanAlarmScheduler.scheduleTestSunnanAlarm(context)
                Toast.makeText(context, "Sunnan alarm scheduled in 1min", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Test GPS", "Force refresh location", Icons.Rounded.MyLocation) {
                mainViewModel.fetchPrayerTimes(context = context)
                Toast.makeText(context, "GPS request sent", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Test Crash", "Trigger a runtime exception", Icons.Rounded.Warning) {
                showCrashConfirm = true
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            Text(
                text = "Performance",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9C27B0)
                )
            )

            DevToolItem("Enable Profiler", "Start collecting performance metrics", Icons.Rounded.Analytics) {
                PerformanceProfiler.enable()
                Toast.makeText(context, "Performance Profiler enabled", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Disable Profiler", "Stop collecting performance metrics", Icons.Rounded.Stop) {
                PerformanceProfiler.disable()
                Toast.makeText(context, "Performance Profiler disabled", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Enable Overlay", "Show floating performance overlay", Icons.Rounded.Visibility) {
                PerformanceProfiler.showOverlay()
                Toast.makeText(context, "Overlay enabled", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Disable Overlay", "Hide floating performance overlay", Icons.Rounded.VisibilityOff) {
                PerformanceProfiler.hideOverlay()
                Toast.makeText(context, "Overlay disabled", Toast.LENGTH_SHORT).show()
            }
            DevToolItem("Export Performance Report", "Save performance logs to file", Icons.Rounded.Download) {
                coroutineScope.launch {
                    val files = PerformanceProfiler.exportPerformanceReports(context)
                    if (files.isNotEmpty()) {
                        shareFile(context, files.first())
                    } else {
                        Toast.makeText(context, "Failed to export performance report", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            DevToolItem("Reset Statistics", "Clear all performance data", Icons.Rounded.Refresh) {
                PerformanceProfiler.resetStatistics()
                Toast.makeText(context, "Statistics reset", Toast.LENGTH_SHORT).show()
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            Text(
                text = "Diagnostics Dashboard",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF009688)
                )
            )

            DiagnosticsDashboardSection()

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            Text(
                text = "Device & Environment",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9C27B0)
                )
            )

            DeviceContextSection()

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            DevToolItem("Disable Developer Mode", "Turn off developer tools access", Icons.Rounded.Build) {
                onDisableDeveloperMode()
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    val dir = context.getExternalFilesDir("logs")
                    dir?.listFiles()?.forEach { it.delete() }
                    DebugLogger.info(LogCategory.APP, "Logs cleared by developer")
                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                    showClearConfirm = false
                }) { Text("Clear", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Clear Logs?") },
            text = { Text("This will delete all current log files. Continue?") }
        )
    }

    if (showCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showCrashConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showCrashConfirm = false
                    throw RuntimeException("Developer test crash")
                }) { Text("CRASH", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showCrashConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Test Crash") },
            text = { Text("Are you sure you want to crash the app to test the CrashHandler?") }
        )
    }
}

@Composable
fun DevToolItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        }
    }
}

@Composable
fun DiagnosticsDashboardSection() {
    val refreshTrigger = remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(2000)
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    val report = DiagnosticsDashboard.reportSnapshot()
    @Suppress("UNUSED")
    val forceRefresh = refreshTrigger.value

    var searchQuery by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showAllWarnings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Summary Card ──────────────────────────────────────────────
        val totalPipelines = report.pipelines.size
        val totalHistory = report.traceHistory.size
        val warningCount = report.warnings.size
        SummaryCard(
            completedCount = report.completedCount,
            failedCount = report.failedCount,
            warningCount = warningCount,
            activeCount = totalPipelines,
            historyCount = totalHistory
        )

        // ── Search Filter ─────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter by trace ID, prayer key, session...", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
        )

        // ── Live Pipelines ────────────────────────────────────────────
        val filteredPipelines = if (searchQuery.isBlank()) {
            report.pipelines
        } else {
            val q = searchQuery.lowercase()
            report.pipelines.filter { (k, v) ->
                k.lowercase().contains(q) || v.traceId.lowercase().contains(q) ||
                DiagnosticsCollector.sessionId.lowercase().contains(q)
            }
        }

        if (filteredPipelines.isEmpty() && report.pipelines.isNotEmpty()) {
            Text("No pipelines match filter.", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        }

        filteredPipelines.forEach { (key, state) ->
            val pipelineColor = pipelineColorCoding(state)
            val firstTimestamp = state.stages.firstOrNull()?.timestamp

            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = pipelineColor
                        )
                    )
                    if (state.isCompleted) {
                        Text(
                            text = "\u2713",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF4CAF50))
                        )
                    }
                    if (state.isFailed) {
                        Text(
                            text = "\u2716",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Red)
                        )
                    }
                }
                Text(
                    text = "trace: ${state.traceId}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )

                // ── Stage list ───────────────────────────────────────
                state.stages.forEachIndexed { index, stage ->
                    val stageColor = when {
                        stage.stage == PipelineStageType.PIPELINE_FAILED || stage.stage == PipelineStageType.FOREGROUND_START_FAILED || stage.stage == PipelineStageType.NOTIFICATION_BLOCKED_PERMISSION -> Color.Red
                        stage.stage == PipelineStageType.PIPELINE_COMPLETED -> Color(0xFF4CAF50)
                        else -> TextPrimary
                    }

                    if (index > 0) {
                        val gap = stage.timestamp - state.stages[index - 1].timestamp
                        val gapColor = when {
                            gap > 1000 -> Color.Red.copy(alpha = 0.7f)
                            gap > 500 -> Color.Yellow.copy(alpha = 0.8f)
                            else -> TextSecondary
                        }
                        Text(
                            text = "\u2193  +${gap}ms",
                            style = MaterialTheme.typography.labelSmall.copy(color = gapColor),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stage.stage.name,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = stageColor
                            )
                        )
                        // Show delta from previous stage on all stages except first
                        if (index > 0) {
                            val deltaMs = stage.timestamp - state.stages[index - 1].timestamp
                            Text(
                                text = "+${deltaMs}ms",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        } else {
                            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                            Text(
                                text = sdf.format(Date(stage.timestamp)),
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                        // Show total on last stage
                        if (index == state.stages.lastIndex && firstTimestamp != null) {
                            val totalMs = stage.timestamp - firstTimestamp
                            Text(
                                text = "Total: ${totalMs}ms",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (totalMs > 3000) Color.Red.copy(alpha = 0.8f) else Color(0xFF9C27B0)
                                )
                            )
                        }
                    }

                    // Show details if present
                    if (stage.details.isNotBlank()) {
                        val detailsColor = when {
                            stage.details.contains("triggerEpoch=") -> Color(0xFF4CAF50).copy(alpha = 0.7f)
                            stage.details.contains("delayMs=") -> Color.Red.copy(alpha = 0.7f)
                            else -> TextSecondary
                        }
                        Text(
                            text = stage.details,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = detailsColor,
                                fontWeight = FontWeight.Light
                            ),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                if (state.stages.isEmpty()) {
                    Text("PENDING", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                }

                // ── Receiver / Service timing hints ──────────────────
                val receiverMs = computeReceiverExecutionMs(state)
                if (receiverMs != null) {
                    Text(
                        text = "\u23F1 Receiver: ${receiverMs}ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (receiverMs > 1000) Color.Red.copy(alpha = 0.8f) else Color(0xFF4CAF50).copy(alpha = 0.7f)
                        )
                    )
                }
                val serviceMs = computeServiceStartupMs(state)
                if (serviceMs != null) {
                    Text(
                        text = "\u25B6 Service: ${serviceMs}ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (serviceMs > 500) Color.Red.copy(alpha = 0.8f) else Color(0xFF4CAF50).copy(alpha = 0.7f)
                        )
                    )
                }
                val driftMs = computeAlarmDriftMs(state)
                if (driftMs != null) {
                    val driftLabel = if (driftMs >= 0) "+${driftMs}ms" else "${driftMs}ms"
                    Text(
                        text = "\u23F0 Drift: $driftLabel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (kotlin.math.abs(driftMs) > 1000) Color.Red.copy(alpha = 0.8f) else Color(0xFF4CAF50).copy(alpha = 0.7f)
                        )
                    )
                }

                if (state.failure != null) {
                    Text(
                        text = "\u2716 ${state.failure.message}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Red)
                    )
                }
            }
            if (key != filteredPipelines.keys.last()) {
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
            }
        }

        if (report.pipelines.isEmpty()) {
            Text("No pipeline activity yet.", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        }

        // ── Warnings Section ──────────────────────────────────────────
        if (report.warnings.isNotEmpty()) {
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Warnings (${report.warnings.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC107)
                    )
                )
                if (report.warnings.size > 3) {
                    TextButton(onClick = { showAllWarnings = !showAllWarnings }) {
                        Text(if (showAllWarnings) "Less" else "All", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val shownWarnings = if (showAllWarnings) report.warnings else report.warnings.take(3)
            shownWarnings.forEach { w ->
                Text(
                    text = "\u26A0 ${w.prayerKey}: ${w.message}",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFFC107).copy(alpha = 0.8f))
                )
            }
        }

        // ── Trace History ─────────────────────────────────────────────
        if (report.traceHistory.isNotEmpty()) {
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Trace History (${report.traceHistory.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF009688)
                    )
                )
                if (report.traceHistory.size > 0) {
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Text(if (showHistory) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (showHistory) {
                val toShow = if (report.traceHistory.size > 10) {
                    report.traceHistory.takeLast(10)
                } else {
                    report.traceHistory
                }
                toShow.reversed().forEach { trace ->
                    val histPipelineColor = pipelineColorCoding(trace)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = trace.prayerKey,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = histPipelineColor,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = trace.traceId,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${trace.totalDurationMs}ms",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = if (trace.isFailed) "\u2716" else if (trace.isCompleted) "\u2713" else "",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (trace.isFailed) Color.Red else Color(0xFF4CAF50)
                            )
                        )
                    }
                }
            }
        }

        // ── Recent Failures ───────────────────────────────────────────
        if (report.failureHistory.isNotEmpty()) {
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
            Text(
                "Recent Failures (last ${report.failureHistory.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Red.copy(alpha = 0.8f)
                )
            )
            report.failureHistory.takeLast(3).forEach { failure ->
                Text(
                    text = "[${failure.stage.name}] ${failure.message}",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Red.copy(alpha = 0.6f))
                )
            }
        }
    }
}

// ── Composable Helpers ─────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    completedCount: Int,
    failedCount: Int,
    warningCount: Int,
    activeCount: Int,
    historyCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryBadge("\u2713", "$completedCount", Color(0xFF4CAF50))
        SummaryBadge("\u2716", "$failedCount", Color.Red)
        SummaryBadge("\u26A0", "$warningCount", Color(0xFFFFC107))
        SummaryBadge("\u25C9", "$activeCount", Color(0xFF009688))
        SummaryBadge("\u2630", "$historyCount", TextSecondary)
    }
}

@Composable
private fun SummaryBadge(prefix: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.labelSmall.copy(color = color)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

// ── Non-Composable Helpers ─────────────────────────────────────────────────

private fun pipelineColorCoding(state: PrayerPipelineState): Color {
    return when {
        state.isFailed || state.failure != null -> Color.Red
        state.isCompleted -> {
            if (state.totalDurationMs > 3000) Color(0xFFFFC107) else Color(0xFF4CAF50)
        }
        state.stages.isEmpty() -> Color.Gray
        else -> TextPrimary
    }
}

/**
 * Computes the time between ALARM_RECEIVED (first stage that is NOT
 * ALARM_SCHEDULED) and PIPELINE_COMPLETED — an approximation of
 * receiver execution time.
 */
private fun computeReceiverExecutionMs(state: PrayerPipelineState): Long? {
    val stages = state.stages
    if (stages.size < 2) return null
    // Find the first stage after ALARM_SCHEDULED or the very first stage
    val startIdx = when {
        stages.first().stage == PipelineStageType.ALARM_SCHEDULED -> 1
        else -> 0
    }
    if (startIdx >= stages.size) return null
    val endIdx = stages.indexOfLast { it.stage == PipelineStageType.PIPELINE_COMPLETED }
    if (endIdx < startIdx) return null
    return stages[endIdx].timestamp - stages[startIdx].timestamp
}

/**
 * Computes the time between [PipelineStageType.FOREGROUND_SERVICE_REQUESTED]
 * and [PipelineStageType.FOREGROUND_SERVICE_STARTED].
 */
private fun computeServiceStartupMs(state: PrayerPipelineState): Long? {
    val stages = state.stages
    val reqIdx = stages.indexOfFirst { it.stage == PipelineStageType.FOREGROUND_SERVICE_REQUESTED }
    val startedIdx = stages.indexOfFirst { it.stage == PipelineStageType.FOREGROUND_SERVICE_STARTED }
    if (reqIdx < 0 || startedIdx < 0 || startedIdx <= reqIdx) return null
    return stages[startedIdx].timestamp - stages[reqIdx].timestamp
}

/**
 * Computes alarm drift: the difference between the actual ALARM_RECEIVED
 * timestamp and the scheduled triggerEpoch (parsed from ALARM_SCHEDULED details).
 */
private fun computeAlarmDriftMs(state: PrayerPipelineState): Long? {
    val stages = state.stages
    val scheduledStage = stages.find { it.stage == PipelineStageType.ALARM_SCHEDULED } ?: return null
    // Parse triggerEpoch=N from details
    val epoch = parseLongDetail(scheduledStage.details, "triggerEpoch=") ?: return null
    val receivedStage = stages.find { it.stage == PipelineStageType.RECEIVER_STARTED }
        ?: stages.find { it.stage == PipelineStageType.NOTIFICATION_SHOWN }
    val receivedTime = receivedStage?.timestamp ?: return null
    return receivedTime - epoch
}

/**
 * Parses a [Long] value from a details string after [prefix].
 * Returns null if prefix not found or value is not a valid long.
 * Example: parseLongDetail("time=12:00 triggerEpoch=1712345678000", "triggerEpoch=") -> 1712345678000L
 */
private fun parseLongDetail(details: String, prefix: String): Long? {
    val idx = details.indexOf(prefix)
    if (idx < 0) return null
    val start = idx + prefix.length
    val end = details.indexOf(' ', start).let { if (it < 0) details.length else it }
    return if (start < end) details.substring(start, end).toLongOrNull() else null
}

@Composable
fun DeviceContextSection() {
    val context = LocalContext.current
    val devPrefs = context.getSharedPreferences("developer_prefs", Context.MODE_PRIVATE)
    val isDevMode = devPrefs.getBoolean("developer_mode", false)

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val appVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) { "unknown" }

    val notificationsEnabled = notificationManager.areNotificationsEnabled()
    val ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else null

    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    val tz = TimeZone.getDefault().displayName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Android", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text("${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Device", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("App", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text("v$appVersion", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Notifications", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(if (notificationsEnabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodySmall.copy(color = if (notificationsEnabled) Color(0xFF4CAF50) else Color.Red))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Battery Opt", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(if (ignoringBattery) "Exempt" else "NOT exempt",
                style = MaterialTheme.typography.bodySmall.copy(color = if (ignoringBattery) Color(0xFF4CAF50) else Color.Red))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Exact Alarm", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(when (canScheduleExact) { true -> "Granted"; false -> "Denied"; null -> "N/A (pre-12)" },
                style = MaterialTheme.typography.bodySmall.copy(color = if (canScheduleExact != false) Color(0xFF4CAF50) else Color.Red))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dev Mode", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(if (isDevMode) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Time", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(now, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Timezone", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(tz, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
        }
    }
}

private fun shareFile(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Logs"))
}

private fun sendTestNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "DEV_TEST_CHANNEL"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Test", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_prayer_alert)
        .setContentTitle("Test Notification")
        .setContentText("This is a developer test.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    notificationManager.notify(8888, notification)
}

private fun testAdhan(context: Context) {
    val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
    val selectedAdhanKey = prefs.getString("selected_muezzin_key", AdhanAudioCatalog.defaultOption().key)
        ?: AdhanAudioCatalog.defaultOption().key
    val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
        putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, "Test Prayer")
        putExtra(AdhanPlaybackService.EXTRA_ADHAN_KEY, selectedAdhanKey)
    }
    ContextCompat.startForegroundService(context, serviceIntent)
}

private fun scheduleTestAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
        action = "com.example.ACTION_PRAYER_NOTIFICATION"
        putExtra("extra_prayer_name", "Test Alarm")
        putExtra("extra_minutes_before", 0)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        7777,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val epochMilli = System.currentTimeMillis() + 30_000 // 30s from now
    val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    try {
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
        }
    } catch (e: SecurityException) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
    }
}
