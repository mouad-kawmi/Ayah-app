package com.example.quranapp.presentation.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.data.audio.AudioDownloadManager
import com.example.quranapp.data.audio.DownloadEntry
import com.example.quranapp.domain.ReciterDisplayNames

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedAudioScreen(
    onBack: () -> Unit,
    onNavigateToQuranDetail: (Int, Int) -> Unit,
    audioViewModel: AudioPlayerViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadManager = remember { AudioDownloadManager.getInstance(context) }
    val downloads by downloadManager.downloads.collectAsState()
    val playerState by audioViewModel.uiState.collectAsState()
    val primaryGreen = Color(0xFF004d40)
    val lightGreenBg = Color(0xFFF4F7F4)
    val goldColor = Color(0xFFC5A059)
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)
    val cardBg = Color(0xFFFAF7F0)

    var showDeleteDialog by remember { mutableStateOf<DownloadEntry?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "الصوتيات المحملة",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        },
        containerColor = lightGreenBg
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = textGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لا توجد تسجيلات محملة بعد",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = textGray,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يمكنك تحميل التسجيلات من أي آية في المصحف",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textGray
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "التسجيلات المتاحة بدون إنترنت",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(downloads, key = { "${it.reciterId}_${it.surahId}" }) { entry ->
                    val isCurrentAudio = playerState.playerState.currentSurahAudio?.surahId == entry.surahId
                    val isPlaying = isCurrentAudio && playerState.playerState.isPlaying

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.surahName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ReciterDisplayNames.displayName(entry.reciterId, entry.reciterName),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = goldColor
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = primaryGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "متاح بدون إنترنت",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = primaryGreen
                                        )
                                    )
                                    if (entry.fileSize > 0) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = formatFileSize(entry.fileSize),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = textGray
                                            )
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (isPlaying) primaryGreen else goldColor,
                                modifier = Modifier.size(44.dp),
                                onClick = {
                                    if (isPlaying) {
                                        audioViewModel.togglePlayPause()
                                    } else {
                                        audioViewModel.playSurah(entry.surahId)
                                    }
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "تشغيل",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE8E0D0),
                                modifier = Modifier.size(44.dp),
                                onClick = { showDeleteDialog = entry }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف",
                                        tint = Color(0xFF8D5B2C),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = cardBg,
            title = {
                Text(
                    text = "حذف التسجيل",
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )
            },
            text = {
                Text(
                    text = "هل تريد حذف هذا التسجيل؟",
                    color = textDark
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadManager.deleteDownload(entry.reciterId, entry.surahId)
                    showDeleteDialog = null
                }) {
                    Text("نعم", color = Color(0xFF8D5B2C))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("إلغاء", color = textGray)
                }
            }
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    }
}
