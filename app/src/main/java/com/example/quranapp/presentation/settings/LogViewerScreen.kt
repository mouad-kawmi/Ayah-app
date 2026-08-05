package com.example.quranapp.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.LogFilePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val primaryGreen = Color(0xFF004d40)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)

    var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Discover available log files once.
    LaunchedEffect(Unit) {
        val files = withContext(Dispatchers.IO) { discoverLogFiles(context) }
        logFiles = files
        if (selectedFileName == null || files.none { it.name == selectedFileName }) {
            selectedFileName = files.firstOrNull()?.name
        }
    }

    // Load the selected file, newest lines first.
    LaunchedEffect(selectedFileName) {
        val name = selectedFileName ?: return@LaunchedEffect
        isLoading = true
        lines = withContext(Dispatchers.IO) { readLogLines(context, name) }
        isLoading = false
        listState.scrollToItem(0)
    }

    val currentFile = logFiles.firstOrNull { it.name == selectedFileName }
    val filteredLines = if (searchQuery.isBlank()) {
        lines
    } else {
        lines.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "عارض السجلات",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── File switcher ──────────────────────────────────────
            if (logFiles.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lightBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logFiles.size) { index ->
                        val file = logFiles[index]
                        FilterChip(
                            selected = file.name == selectedFileName,
                            onClick = { selectedFileName = file.name },
                            label = { Text(file.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primaryGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // ── Search field ───────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("بحث في السجلات...", style = MaterialTheme.typography.bodySmall, color = textGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textGray) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح البحث", tint = textGray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = textDark, fontFamily = FontFamily.Monospace)
            )

            // ── Action row ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip(
                    icon = Icons.Default.ContentCopy,
                    label = "نسخ الكل",
                    onClick = {
                        val text = filteredLines.joinToString("\n")
                        copyToClipboard(context, text)
                        Toast.makeText(context, "تم نسخ السجلات", Toast.LENGTH_SHORT).show()
                    },
                    enabled = filteredLines.isNotEmpty()
                )
                ActionChip(
                    icon = Icons.Default.Share,
                    label = "مشاركة الملف",
                    onClick = {
                        val file = currentFile
                        if (file != null) {
                            DevToolsFileUtils.shareFile(context, file)
                        } else {
                            Toast.makeText(context, "لا يوجد ملف سجل", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = currentFile != null
                )
                ActionChip(
                    icon = Icons.Default.Delete,
                    label = "مسح الملف",
                    onClick = { showClearConfirm = true },
                    enabled = currentFile != null
                )
                ActionChip(
                    icon = Icons.Default.Refresh,
                    label = "تحديث",
                    onClick = {
                        val name = selectedFileName ?: return@ActionChip
                        scope.launch {
                            lines = withContext(Dispatchers.IO) { readLogLines(context, name) }
                            listState.scrollToItem(0)
                        }
                    },
                    enabled = selectedFileName != null
                )
            }

            // ── Log lines ──────────────────────────────────────────
            HorizontalDivider(color = primaryGreen.copy(alpha = 0.1f))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryGreen)
                }
            } else if (filteredLines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (lines.isEmpty()) "لا توجد سجلات" else "لا توجد نتائج للبحث",
                        style = MaterialTheme.typography.bodyMedium.copy(color = textGray)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(filteredLines, key = { index, _ -> index }) { index, line ->
                        LogLineItem(
                            text = line,
                            onClick = { copyToClipboard(context, line); Toast.makeText(context, "تم نسخ السطر", Toast.LENGTH_SHORT).show() },
                            textDark = textDark,
                            cardBg = cardBg
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(24.dp),
            title = { Text("مسح الملف؟", color = textDark, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حذف ${currentFile?.name ?: "الملف"}. هل تريد المتابعة؟", color = textGray) },
            confirmButton = {
                TextButton(onClick = {
                    currentFile?.let { file ->
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) { DebugLogger.deleteLogFile(context, file.name) }
                            if (deleted) {
                                Toast.makeText(context, "تم مسح الملف", Toast.LENGTH_SHORT).show()
                                logFiles = withContext(Dispatchers.IO) { discoverLogFiles(context) }
                                if (selectedFileName == file.name) {
                                    selectedFileName = logFiles.firstOrNull()?.name
                                }
                            }
                        }
                    }
                    showClearConfirm = false
                }) { Text("مسح", color = Color(0xFFC62828)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("إلغاء", color = textGray) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogLineItem(
    text: String,
    onClick: () -> Unit,
    textDark: Color,
    cardBg: Color
) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (text.startsWith("====") || text.startsWith("----")) Color(0xFFE8F5E9) else cardBg)
                .combinedClickable(onClick = onClick, onLongClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.White,
            labelColor = Color(0xFF004d40)
        )
    )
}

private fun discoverLogFiles(context: Context): List<File> {
    val dir = context.getExternalFilesDir("logs") ?: return emptyList()
    val now = System.currentTimeMillis()
    return dir.listFiles()
        ?.filter { LogFilePolicy.isLogFile(it) && !LogFilePolicy.isExpired(it, now) }
        ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
        ?: emptyList()
}

private fun readLogLines(context: Context, fileName: String): List<String> {
    val dir = context.getExternalFilesDir("logs") ?: return emptyList()
    val file = File(dir, fileName)
    if (!file.exists()) return emptyList()
    return runCatching {
        file.readLines()
            .takeLast(MAX_LOG_LINES)
            .asReversed()
    }.getOrDefault(emptyList())
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Log line", text))
}

private const val MAX_LOG_LINES = 500
