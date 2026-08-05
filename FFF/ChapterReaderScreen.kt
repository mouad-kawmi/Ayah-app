package com.example

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planner.KhatmaRepository
import com.example.planner.KhatmaViewModel
import com.squareup.moshi.Moshi
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.audio.AudioEvent
import com.example.audio.AudioViewModel
import com.example.audio.PlaybackState
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import com.example.quran.data.PageLayoutProvider
import com.example.quran.data.QuranData
import com.example.quran.data.QuranInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private val tapIdCounter = AtomicLong(0)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChapterReaderScreen(
    viewModel: QuranViewModel,
    initialPage: Int = 1,
    fromPlanner: Boolean = false,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateToDownloadManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pageLayoutProvider = viewModel.pageLayoutProvider

    var isSheetVisible by remember { mutableStateOf(false) }
    var selectedVerseKey by remember { mutableStateOf<String?>(null) }
    var selectedVerse by remember { mutableStateOf<Verse?>(null) }
    val currentTapId = remember { mutableStateOf(-1L) }
    val audioViewModel: AudioViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val audioState by audioViewModel.state.collectAsState()
    val currentVerseKey by audioViewModel.currentVerseKey.collectAsState()
    val isPlaybackActive = audioState is PlaybackState.Playing
            || audioState is PlaybackState.Paused
            || audioState is PlaybackState.Buffering
    val highlightVerse = if (isPlaybackActive) currentVerseKey?.toString()
        else selectedVerseKey
    val quranInfo = remember { QuranInfo() }

    val pageCount = QuranData.NUMBER_OF_PAGES
    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )

    val prefs = remember {
        context.getSharedPreferences("planner_prefs", Context.MODE_PRIVATE)
    }
    val moshi = remember { Moshi.Builder().build() }
    val repository = remember { KhatmaRepository(prefs, moshi) }
    Log.d("KHATMA_TRACE", "ChapterReaderScreen: creating KhatmaViewModel (new instance)")
    val plannerViewModel: KhatmaViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                Log.d("KHATMA_TRACE", "ChapterReaderScreen: KhatmaViewModel factory.create() called — NEW INSTANCE")
                @Suppress("UNCHECKED_CAST")
                return KhatmaViewModel(repository) as T
            }
        }
    )
    val plannerUiState by plannerViewModel.uiState.collectAsState()

    var isCurrentPageCompleted by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val composeStartTime = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L

    fun onVerseTapped(verseKey: String) {
        if (verseKey == selectedVerseKey) {
            selectedVerseKey = null
            isSheetVisible = false
        } else {
            if (BuildConfig.DEBUG) {
                val tapId = tapIdCounter.incrementAndGet()
                currentTapId.value = tapId
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId onVerseTapped START → verseKey=$verseKey")
            }
            selectedVerseKey = verseKey
            isSheetVisible = true
        }
    }

    val exitTimeline = remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = true) {
        if (BuildConfig.DEBUG) {
            exitTimeline.value = SystemClock.elapsedRealtime()
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] Back pressed")
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] Navigation starts")
        }
        onNavigateUp?.invoke()
    }

    fun onDismissSheet() {
        selectedVerseKey = null
        isSheetVisible = false
    }

    DisposableEffect(Unit) {
        if (BuildConfig.DEBUG) {
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] ChapterReaderScreen composed")
        }
        onDispose {
            if (BuildConfig.DEBUG) {
                val exitStart = exitTimeline.value
                val disposeStart = SystemClock.elapsedRealtime()
                if (exitStart != null) {
                    val navToDisposeMs = disposeStart - exitStart
                    DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] Navigation → Compose disposal (${navToDisposeMs}ms)")
                }
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] Compose disposal START")
                val hPagerDisposeMs = SystemClock.elapsedRealtime() - disposeStart
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] HorizontalPager disposed (${hPagerDisposeMs}ms)")
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] ChapterReaderScreen disposed (${hPagerDisposeMs}ms)")
                val prevScreenMs = SystemClock.elapsedRealtime() - disposeStart
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] Previous screen interactive (${prevScreenMs}ms)")
                if (prevScreenMs > 16) {
                    DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] WARNING Main thread blocked for ${prevScreenMs}ms (screen exit)")
                }
            }
        }
    }

    LaunchedEffect(selectedVerseKey) {
        if (selectedVerseKey != null) {
            if (BuildConfig.DEBUG && currentTapId.value >= 0L) {
                val tapId = currentTapId.value
                val resolveStart = SystemClock.elapsedRealtime()
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId resolveVerseByKey START")
                selectedVerse = viewModel.resolveVerseByKey(selectedVerseKey!!)
                val resolveEnd = SystemClock.elapsedRealtime()
                val resolveMs = resolveEnd - resolveStart
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId resolveVerseByKey END (${resolveMs}ms)")
                if (resolveMs > 16) {
                    DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] tap=$tapId WARNING Main thread blocked for ${resolveMs}ms (resolveVerseByKey)")
                }
                val sheetVisibleMs = SystemClock.elapsedRealtime() - resolveStart
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId BottomSheet visible (${sheetVisibleMs}ms)")
            } else {
                selectedVerse = viewModel.resolveVerseByKey(selectedVerseKey!!)
            }
        } else {
            selectedVerse = null
        }
    }

    LaunchedEffect(audioState) {
        if (audioState is PlaybackState.Error) {
            snackbarHostState.showSnackbar(
                message = (audioState as PlaybackState.Error).message
            )
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val currentPage = pagerState.currentPage + 1
        val completed = repository.isPageCompleted(currentPage)
        Log.d("KHATMA_TRACE", "ReaderScreen LaunchedEffect: page=$currentPage, isPageCompleted=$completed")
        isCurrentPageCompleted = completed
    }

    LaunchedEffect(Unit) {
        audioViewModel.events.collect { event ->
            when (event) {
                is AudioEvent.PlayNext -> {
                    val nextVerse = event.verseKey
                    val nextPage = quranInfo.getPageFromSuraAyah(nextVerse.surah, nextVerse.ayah)
                    val currentPage = pagerState.currentPage + 1
                    if (nextPage != currentPage) {
                        try {
                            pagerState.animateScrollToPage(nextPage - 1)
                            audioViewModel.play(nextVerse.surah, nextVerse.ayah)
                        } catch (_: CancellationException) {
                        }
                    } else {
                        audioViewModel.play(nextVerse.surah, nextVerse.ayah)
                    }
                }
                is AudioEvent.EndOfQuran -> {
                    snackbarHostState.showSnackbar("\u0627\u0646\u062A\u0647\u0649 \u0627\u0644\u0642\u0631\u0622\u0646")
                }
            }
        }
    }

    val currentPageNumber by remember {
        derivedStateOf { pagerState.currentPage + 1 }
    }
    val surahName by remember {
        derivedStateOf {
            val suraNumber = quranInfo.getSuraOnPage(currentPageNumber)
            SURAH_NAMES_AR.getOrNull(suraNumber - 1) ?: ""
        }
    }
    val juzDisplay by remember {
        derivedStateOf {
            val juzNumber = quranInfo.getJuzForDisplayFromPage(currentPageNumber)
            "الجزء ${arabicNumerals(juzNumber)}"
        }
    }
    val pageNumberStr by remember {
        derivedStateOf { arabicNumerals(currentPageNumber) }
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8F3E7))) {
        // Top header (surah name + juz)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, top = 4.dp, end = 20.dp)
        ) {
            Text(
                text = surahName,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF7B5B3A)
            )
            Text(
                text = juzDisplay,
                textAlign = TextAlign.End,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF9C7B57)
            )
        }

        // Quran page — fills remaining space
        Box(modifier = Modifier.weight(1f)) {
            QuranPageReader(
                pagerState = pagerState,
                pageLayoutProvider = pageLayoutProvider,
                selectedVerseKey = highlightVerse,
                onVerseTapped = ::onVerseTapped,
                tapId = currentTapId.value,
                pageScaleMode = PageScaleMode.CONTAIN,
                onPageChanged = { page ->
                    context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("last_read_page", page)
                        .apply()
                }
            )

            if (isSheetVisible && selectedVerse != null) {
                QuranBottomSheet(
                    verse = selectedVerse!!,
                    viewModel = viewModel,
                    audioViewModel = audioViewModel,
                    tapId = currentTapId.value,
                    bgPageColor = Color(0xFFF8F3E7),
                    textPageColor = Color.Black,
                    topHeaderColor = Color(0xFF8B4513),
                    borderColor = Color(0xFFD4B872),
                    headerBgColor = Color(0xFFF3E5AB),
                    onDismissRequest = ::onDismissSheet,
                    onNavigateToDownloadManager = onNavigateToDownloadManager
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Page number badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .shadow(1.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF7F1E0))
                    .border(0.5.dp, Color(0xFFD4C4A0), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Text(
                    text = pageNumberStr,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF7B5B3A)
                )
            }
        }

        // Khatma Card — only visible when reading inside an active Khatma session
        if (fromPlanner && plannerUiState.hasActivePlan) {
            if (isCurrentPageCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F3E7))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF5C8A3C),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تم احتساب هذه الصفحة ضمن ختمتك",
                        color = Color(0xFF5C8A3C),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F3E7))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = {
                            Log.d("KHATMA_TRACE", "=== ReaderScreen: Button clicked for page=$currentPageNumber ===")
                            Log.d("KHATMA_TRACE", "ReaderScreen: isCurrentPageCompleted BEFORE click=$isCurrentPageCompleted")
                            plannerViewModel.markPageCompleted(currentPageNumber)
                            isCurrentPageCompleted = true
                            Log.d("KHATMA_TRACE", "ReaderScreen: isCurrentPageCompleted AFTER click=$isCurrentPageCompleted")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB8860B)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "✓ احتساب الصفحة ضمن الختمة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // Empty space for navigation bar when no plan
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(0.dp)
            )
        }
    }
}

private fun arabicNumerals(number: Int): String {
    return number.toString().map { ('\u0660' + (it - '0')).toChar() }.joinToString("")
}
