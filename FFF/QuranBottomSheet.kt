package com.example

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
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
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.audio.AudioViewModel
import com.example.audio.PlaybackState
import com.example.audio.di.AudioModule
import com.example.audio.model.DownloadState
import com.example.audio.model.ReaderType
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import kotlinx.coroutines.launch

enum class SheetTab {
    NONE, TAFSIR, TRANSLATION
}

@Composable
fun BottomSheetActionButton(
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    bgColor: Color,
    textColor: Color,
    showLoading: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(bgColor, CircleShape)
                .clip(CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = tint,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = iconVector,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranBottomSheet(
    verse: Verse,
    viewModel: QuranViewModel,
    audioViewModel: AudioViewModel,
    bgPageColor: Color,
    textPageColor: Color,
    topHeaderColor: Color,
    borderColor: Color,
    headerBgColor: Color,
    tapId: Long = -1L,
    onDismissRequest: () -> Unit,
    onNavigateToDownloadManager: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedSheetTab by remember { mutableStateOf(SheetTab.NONE) }
    
    var translationText by remember { mutableStateOf("") }
    var translationEnText by remember { mutableStateOf("") }
    var tafsirText by remember { mutableStateOf("") }
    var isFetchingTranslation by remember { mutableStateOf(true) }
    var fetchTranslationError by remember { mutableStateOf<String?>(null) }

    val readers = remember { AudioModule.readerCatalog.allReaders() }
    val sessionReaderId by audioViewModel.currentReaderId.collectAsState()
    var selectedReaderId by remember(sessionReaderId) {
        mutableStateOf(sessionReaderId ?: AudioModule.readerCatalog.getDefault(context).id)
    }
    val selectedReader = readers.find { it.id == selectedReaderId }
    var showReaderPicker by remember { mutableStateOf(false) }

    val bookmarkLookupStart = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    var bookmarkedVerseIds by remember {
        mutableStateOf(
            context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
                .getStringSet("bookmarked_verses", emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        )
    }
    if (BuildConfig.DEBUG && tapId >= 0L) {
        val bookmarkLookupMs = SystemClock.elapsedRealtime() - bookmarkLookupStart
        DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId Bookmark lookup END (${bookmarkLookupMs}ms)")
        if (bookmarkLookupMs > 16) {
            DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] tap=$tapId WARNING Main thread blocked for ${bookmarkLookupMs}ms (bookmark lookup)")
        }
    }

    fun toggleBookmark(verseId: Int) {
        val prefs = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet("bookmarked_verses", emptySet()) ?: emptySet()
        val stringId = verseId.toString()
        val newSet = if (currentSet.contains(stringId)) {
            currentSet - stringId
        } else {
            currentSet + stringId
        }
        prefs.edit().putStringSet("bookmarked_verses", newSet).apply()
        bookmarkedVerseIds = newSet.mapNotNull { it.toIntOrNull() }.toSet()
    }

    val audioState by audioViewModel.state.collectAsState()
    val currentVerseKey by audioViewModel.currentVerseKey.collectAsState()

    LaunchedEffect(verse) {
        isFetchingTranslation = true
        fetchTranslationError = null
        if (BuildConfig.DEBUG && tapId >= 0L) {
            val translationStart = SystemClock.elapsedRealtime()
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId Translation load START")
            viewModel.fetchVerseTranslationAndTafsir(verse.id).fold(
                onSuccess = { (translationFr, translationEn, tafsir) ->
                    val translationEnd = SystemClock.elapsedRealtime()
                    val translationMs = translationEnd - translationStart
                    translationText = translationFr
                    translationEnText = translationEn
                    tafsirText = tafsir
                    isFetchingTranslation = false
                    DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId Translation load END (${translationMs}ms)")
                    if (translationMs > 16) {
                        DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] tap=$tapId WARNING Main thread blocked for ${translationMs}ms (translation load)")
                    }
                },
                onFailure = { err ->
                    val translationEnd = SystemClock.elapsedRealtime()
                    val translationMs = translationEnd - translationStart
                    fetchTranslationError = "تعذر تحميل الترجمة والتفسير. تأكد من الاتصال بالإنترنت."
                    isFetchingTranslation = false
                    DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId Translation load FAIL (${translationMs}ms)")
                }
            )
        } else {
            viewModel.fetchVerseTranslationAndTafsir(verse.id).fold(
                onSuccess = { (translationFr, translationEn, tafsir) ->
                    translationText = translationFr
                    translationEnText = translationEn
                    tafsirText = tafsir
                    isFetchingTranslation = false
                },
                onFailure = { err ->
                    fetchTranslationError = "تعذر تحميل الترجمة والتفسير. تأكد من الاتصال بالإنترنت."
                    isFetchingTranslation = false
                }
            )
        }
    }

    fun Int.toArabicNumber(): String {
        val arabicNumbers = listOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return this.toString().map { char ->
            if (char.isDigit()) arabicNumbers[char.toString().toInt()] else char
        }.joinToString("")
    }

    val sheetComposeStart = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = bgPageColor,
        contentColor = textPageColor,
        tonalElevation = 6.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = topHeaderColor.copy(alpha = 0.4f)
            )
        }
    ) {
        if (BuildConfig.DEBUG && tapId >= 0L) {
            val sheetComposeMs = SystemClock.elapsedRealtime() - sheetComposeStart
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId BottomSheet composition END (${sheetComposeMs}ms)")
            if (sheetComposeMs > 16) {
                DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] tap=$tapId WARNING Main thread blocked for ${sheetComposeMs}ms (BottomSheet composition)")
            }
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId BottomSheet fully interactive (${sheetComposeMs}ms)")
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "(${verse.numberInSurah.toArabicNumber()}) سورة ${verse.surahName}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = topHeaderColor
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Original verse text
                Text(
                    text = verse.text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    color = textPageColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

                // Reader selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(headerBgColor.copy(alpha = 0.08f))
                        .clickable { showReaderPicker = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = topHeaderColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedReader?.displayName ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = textPageColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "تغيير القارئ",
                        tint = topHeaderColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Track download state for post-row actions
                val downloadStates by audioViewModel.downloadStates.collectAsState()
                val downloadKey = "${selectedReader?.id ?: ""}:${verse.surahNumber}"
                val surahState = if (selectedReader?.type == ReaderType.QURAN) downloadStates[downloadKey] else null

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share
                    BottomSheetActionButton(
                        iconVector = Icons.Rounded.Share,
                        label = "مشاركة",
                        tint = topHeaderColor,
                        bgColor = headerBgColor.copy(alpha = 0.5f),
                        textColor = textPageColor
                    ) {
                        val shareText = """
                            📖 *سورة ${verse.surahName} - الآية ${verse.numberInSurah}* 📖
                            
                            قال الله تعالى:
                            «${verse.text}»
                            
                            📝 *التفسير الميسر:*
                            ${tafsirText.ifEmpty { "المحتوى قيد التحميل من الإنترنت..." }}
                            
                            🌍 *Traduction:*
                            ${translationText.ifEmpty { "Chargement de la traduction..." }}
                            
                            تمت المشاركة من تطبيق نور الإيمان.
                        """.trimIndent()
                        
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة الآية الكريمة"))
                    }

                    // Bookmark
                    val isBookmarked = bookmarkedVerseIds.contains(verse.id)
                    BottomSheetActionButton(
                        iconVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        label = if (isBookmarked) "محفوظ" else "حفظ",
                        tint = if (isBookmarked) bgPageColor else topHeaderColor,
                        bgColor = if (isBookmarked) topHeaderColor else headerBgColor.copy(alpha = 0.5f),
                        textColor = textPageColor
                    ) {
                        toggleBookmark(verse.id)
                    }

                    // Translation
                    BottomSheetActionButton(
                        iconVector = Icons.Rounded.Translate,
                        label = "ترجمة",
                        tint = if (selectedSheetTab == SheetTab.TRANSLATION) bgPageColor else topHeaderColor,
                        bgColor = if (selectedSheetTab == SheetTab.TRANSLATION) topHeaderColor else headerBgColor.copy(alpha = 0.5f),
                        textColor = textPageColor
                    ) {
                        selectedSheetTab = if (selectedSheetTab == SheetTab.TRANSLATION) SheetTab.NONE else SheetTab.TRANSLATION
                    }

                    // Tafsir
                    BottomSheetActionButton(
                        iconVector = Icons.Rounded.MenuBook,
                        label = "تفسير",
                        tint = if (selectedSheetTab == SheetTab.TAFSIR) bgPageColor else topHeaderColor,
                        bgColor = if (selectedSheetTab == SheetTab.TAFSIR) topHeaderColor else headerBgColor.copy(alpha = 0.5f),
                        textColor = textPageColor
                    ) {
                        selectedSheetTab = if (selectedSheetTab == SheetTab.TAFSIR) SheetTab.NONE else SheetTab.TAFSIR
                    }

                    // Download
                    if (selectedReader?.type == ReaderType.QURAN) {
                        when (surahState) {
                            is DownloadState.Idle, null -> {
                                BottomSheetActionButton(
                                    iconVector = Icons.Rounded.Download,
                                    label = "تحميل",
                                    tint = topHeaderColor,
                                    bgColor = headerBgColor.copy(alpha = 0.5f),
                                    textColor = textPageColor
                                ) {
                                    audioViewModel.downloadSurah(verse.surahNumber, selectedReaderId)
                                }
                            }
                            is DownloadState.Queued -> {
                                BottomSheetActionButton(
                                    iconVector = Icons.Rounded.Cancel,
                                    label = "إلغاء",
                                    tint = bgPageColor,
                                    bgColor = Color(0xFFE53935),
                                    textColor = textPageColor
                                ) {
                                    audioViewModel.cancelDownloadSurah(verse.surahNumber, selectedReaderId)
                                }
                            }
                            is DownloadState.Downloading -> {
                                BottomSheetActionButton(
                                    iconVector = Icons.Rounded.Cancel,
                                    label = "${surahState.progress}%",
                                    tint = bgPageColor,
                                    bgColor = Color(0xFFE53935),
                                    textColor = textPageColor
                                ) {
                                    audioViewModel.cancelDownloadSurah(verse.surahNumber, selectedReaderId)
                                }
                            }
                            is DownloadState.Completed -> {
                                BottomSheetActionButton(
                                    iconVector = Icons.Rounded.CheckCircle,
                                    label = "✓ تم",
                                    tint = bgPageColor,
                                    bgColor = Color(0xFF4CAF50),
                                    textColor = bgPageColor
                                ) {}
                            }
                            is DownloadState.Failed -> {
                                BottomSheetActionButton(
                                    iconVector = Icons.Rounded.Refresh,
                                    label = "إعادة",
                                    tint = topHeaderColor,
                                    bgColor = headerBgColor.copy(alpha = 0.5f),
                                    textColor = textPageColor
                                ) {
                                    audioViewModel.downloadSurah(verse.surahNumber, selectedReaderId)
                                }
                            }
                        }
                    }

                    // Listen
                    val isPlaybackPlaying = audioState is PlaybackState.Playing
                    val isPlaybackBuffering = audioState is PlaybackState.Buffering
                    BottomSheetActionButton(
                        iconVector = if (isPlaybackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        label = if (isPlaybackPlaying) "إيقاف" else "استماع",
                        showLoading = isPlaybackBuffering,
                        tint = if (isPlaybackPlaying) bgPageColor else topHeaderColor,
                        bgColor = if (isPlaybackPlaying) topHeaderColor else headerBgColor.copy(alpha = 0.5f),
                        textColor = textPageColor
                    ) {
                        audioViewModel.playOrResume(
                            surah = verse.surahNumber,
                            ayah = verse.numberInSurah,
                            readerId = selectedReaderId
                        )
                    }
                }

                // Manage Downloads link when surah is downloaded
                if (surahState is DownloadState.Completed && selectedReader?.type == ReaderType.QURAN) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onNavigateToDownloadManager()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = topHeaderColor)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إدارة التحميلات", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Dynamic sub-sections inside the sheet for Tafsir or Translation
                AnimatedVisibility(
                    visible = selectedSheetTab != SheetTab.NONE,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        if (isFetchingTranslation) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = topHeaderColor)
                                    Text(
                                        text = "جاري جلب البيانات...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textPageColor.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else if (fetchTranslationError != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = fetchTranslationError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = headerBgColor.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 250.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (selectedSheetTab == SheetTab.TAFSIR) {
                                        Text(
                                            text = "التفسير الميسر:",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = topHeaderColor,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Right
                                        )
                                        Text(
                                            text = tafsirText.ifEmpty { "لا يوجد تفسير متاح لهذه الآية" },
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                            color = textPageColor,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Right
                                        )
                                    } else if (selectedSheetTab == SheetTab.TRANSLATION) {
                                        Text(
                                            text = "Translation (English):",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = topHeaderColor,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Left
                                        )
                                        Text(
                                            text = translationEnText.ifEmpty { "No translation available for this verse" },
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                            color = textPageColor.copy(alpha = 0.8f),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Left
                                        )
                                        
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = borderColor.copy(alpha = 0.3f))
                                        
                                        Text(
                                            text = "Traduction (French):",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = topHeaderColor,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Left
                                        )
                                        Text(
                                            text = translationText.ifEmpty { "Aucune traduction disponible pour ce verset" },
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                            color = textPageColor.copy(alpha = 0.8f),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Left
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showReaderPicker) {
        ModalBottomSheet(
            onDismissRequest = { showReaderPicker = false },
            containerColor = bgPageColor,
            contentColor = textPageColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = "اختر القارئ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = topHeaderColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                readers.forEach { reader ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedReaderId = reader.id
                                showReaderPicker = false
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reader.id == selectedReaderId,
                            onClick = {
                                selectedReaderId = reader.id
                                showReaderPicker = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = topHeaderColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reader.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textPageColor
                        )
                    }
                    if (reader != readers.last()) {
                        HorizontalDivider(
                            color = borderColor.copy(alpha = 0.15f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
