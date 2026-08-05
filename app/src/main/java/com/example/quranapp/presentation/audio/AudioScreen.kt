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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.domain.model.Reciter
import com.example.quranapp.domain.model.Surah
import com.example.quranapp.domain.ReciterDisplayNames
import com.example.quranapp.data.audio.AudioDownloadManager
import com.example.quranapp.data.audio.AudioRepository
import com.example.quranapp.data.audio.DownloadEntry
import com.example.quranapp.data.audio.PlayerState
import com.example.quranapp.data.audio.RepeatMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    onNavigateToQuranDetail: (Int, Int) -> Unit,
    onNavigateToDownloads: (() -> Unit)? = null,
    viewModel: AudioPlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val primaryGreen = Color(0xFF004d40)
    val lightGreenBg = Color(0xFFF4F7F4)
    val goldColor = Color(0xFFC5A059)

    var showReciterDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val downloadManager = remember { AudioDownloadManager.getInstance(context) }
    val downloadProgress by downloadManager.downloadProgress.collectAsState()
    val downloadedEntries by downloadManager.downloads.collectAsState()
    val downloadScope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "الاستماع للقرآن الكريم",
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
        containerColor = lightGreenBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Reciter Selector Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { showReciterDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Reciter",
                        tint = primaryGreen
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = ReciterDisplayNames.displayName(uiState.selectedAudioReciter) ?: "اختر القارئ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = primaryGreen
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.selectedAudioReciter?.style ?: "المقرئ",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = primaryGreen.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Reciter",
                                tint = primaryGreen
                            )
                        }
                    }
                }
            }

            // Downloads section
            if (onNavigateToDownloads != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onNavigateToDownloads() },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFAF7F0),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "الصوتيات المحملة",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E2D)
                                )
                            )
                            val downloadsCountLabel = when {
                                downloadedEntries.isEmpty() -> "لا توجد تسجيلات"
                                downloadedEntries.size == 1 -> "تسجيل واحد"
                                else -> "${downloadedEntries.size} تسجيلات"
                            }
                            Text(
                                text = downloadsCountLabel,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF7F8C8D)
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "فتح المحملة",
                            tint = Color(0xFFC5A059)
                        )
                    }
                }
            }

            // Surahs List for Audio
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.surahs) { surah ->
                    val reciterId = uiState.selectedAudioReciter?.id
                        ?: uiState.allReciters.firstOrNull()?.id
                        ?: 7
                    val key = "${reciterId}_${surah.id}"
                    val isDownloading = downloadProgress.containsKey(key)
                    val currentProgress = downloadProgress[key] ?: 0f
                    val isDownloaded = downloadedEntries.any { it.reciterId == reciterId && it.surahId == surah.id }
                    val isCurrentSurah = uiState.playerState.currentSurahAudio?.surahId == surah.id
                    val isPlaying = isCurrentSurah && uiState.playerState.isPlaying

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.playAudioSurah(surah.id)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentSurah) Color(0xFFE8F5E9) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isPlaying) primaryGreen else Color(0xFFF0F4F0),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = if (isPlaying) Color.White else primaryGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isDownloaded -> Color(0xFFE8F5E9)
                                        isDownloading -> Color(0xFFFFF8E1)
                                        else -> Color(0xFFF0F4F0)
                                    },
                                    modifier = Modifier.size(38.dp),
                                    onClick = {
                                        if (!isDownloaded && !isDownloading) {
                                            val reciter = uiState.selectedAudioReciter
                                            if (reciter != null) {
                                                downloadScope.launch {
                                                    try {
                                                        val repo = AudioRepository(context)
                                                        val surahAudio = repo.getSurahAudio(reciter.id, surah.id)
                                                        downloadManager.downloadSurahAudio(
                                                            reciterId = reciter.id,
                                                            surahId = surah.id,
                                                            audioUrl = surahAudio.audioUrl,
                                                            surahName = surah.nameArabic,
                                                            reciterName = reciter.name
                                                        )
                                                        android.widget.Toast.makeText(context, "تم تحميل ${surah.nameArabic}", android.widget.Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(context, "فشل التحميل: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                progress = { currentProgress },
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = goldColor
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                                contentDescription = "Download",
                                                tint = if (isDownloaded) primaryGreen else goldColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(onClick = {
                                    onNavigateToQuranDetail(surah.id, surah.firstPage)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = "Open Mushaf",
                                        tint = goldColor
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = surah.nameArabic,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.DarkGray
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${surah.numberOfAyahs} آية",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                )
                            }
                        }
                    }
                }
            }

            // Active Player Bottom Bar if playing or selected
            if (uiState.playerState.currentSurahAudio != null) {
                ActiveAudioPlayerBar(
                    playerState = uiState.playerState,
                    playbackPosition = playbackPosition,
                    selectedSurah = uiState.surahs.find { it.id == uiState.playerState.currentSurahAudio?.surahId },
                    selectedReciter = uiState.selectedAudioReciter,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onSeek = { viewModel.seekTo(it) },
                    onRepeatToggle = {
                        val nextMode = when (uiState.playerState.repeatMode) {
                            RepeatMode.OFF -> RepeatMode.AYAH
                            RepeatMode.AYAH -> RepeatMode.SURAH
                            RepeatMode.SURAH -> RepeatMode.OFF
                        }
                        viewModel.setRepeatMode(nextMode)
                    }
                )
            }
        }
    }

    // Reciter Selection Dialog
    if (showReciterDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredReciters = remember(searchQuery, uiState.allReciters) {
            if (searchQuery.isBlank()) uiState.allReciters
            else uiState.allReciters.filter { r ->
                r.name.contains(searchQuery, ignoreCase = true) ||
                    (r.translatedName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        AlertDialog(
            onDismissRequest = { showReciterDialog = false },
            title = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "اختر القارئ", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("بحث عن قارئ...", textAlign = TextAlign.Right) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        }
                    )
                }
            },
            text = {
                if (filteredReciters.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد نتائج", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredReciters) { reciter ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectAudioReciter(reciter)
                                        showReciterDialog = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (reciter.id == uiState.selectedAudioReciter?.id) Color(0xFFE8F5E9) else Color.Transparent
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = ReciterDisplayNames.displayName(reciter),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryGreen
                                        )
                                    )
                                    if (!reciter.style.isNullOrEmpty()) {
                                        Text(
                                            text = reciter.style,
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                        )
                                    }
                                    if (reciter.supportsVerseTimings == false) {
                                        Text(
                                            text = "بدون مزامنة الآيات",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFFC0892F),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReciterDialog = false }) {
                    Text("إغلاق", color = primaryGreen)
                }
            }
        )
    }
}

@Composable
fun ActiveAudioPlayerBar(
    playerState: PlayerState,
    playbackPosition: Long,
    selectedSurah: Surah?,
    selectedReciter: Reciter?,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onRepeatToggle: () -> Unit
) {
    val primaryGreen = Color(0xFF004d40)
    val goldColor = Color(0xFFC5A059)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedReciter?.supportsVerseTimings == false) {
                Text(
                    text = "هذا القارئ لا يوفر مزامنة الآيات",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFC0892F),
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Slider
            val duration = playerState.durationMs.toFloat().coerceAtLeast(1f)
            val position = playbackPosition.toFloat().coerceIn(0f, duration)

            Slider(
                value = position,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = primaryGreen,
                    activeTrackColor = primaryGreen
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatMillis(playerState.durationMs), fontSize = 12.sp, color = Color.Gray)
                Text(
                    text = playerState.currentAyah?.let { "آية ${it.ayahNumber}" } ?: "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = goldColor
                )
                Text(text = formatMillis(playbackPosition), fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat Mode Button
                IconButton(onClick = onRepeatToggle) {
                    val icon = when (playerState.repeatMode) {
                        RepeatMode.OFF -> Icons.Default.Repeat
                        RepeatMode.AYAH -> Icons.Default.RepeatOne
                        RepeatMode.SURAH -> Icons.Default.AllInclusive
                    }
                    val tint = if (playerState.repeatMode != RepeatMode.OFF) primaryGreen else Color.Gray
                    Icon(imageVector = icon, contentDescription = "Repeat", tint = tint)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selectedSurah?.nameArabic ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen
                        )
                    )
                    Text(
                        text = ReciterDisplayNames.displayName(selectedReciter) ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }

                // Play / Pause Button
                Surface(
                    shape = CircleShape,
                    color = primaryGreen,
                    modifier = Modifier.size(50.dp),
                    onClick = onPlayPauseClick
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
