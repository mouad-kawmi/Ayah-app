package com.example.quranapp.presentation.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.core.utils.Qcf4FontManager
import com.example.quranapp.core.utils.QuranPreferences
import com.example.quranapp.data.model.Line
import com.example.quranapp.data.model.Page
import com.example.quranapp.data.model.Word
import com.example.quranapp.data.tafsir.TafsirReader
import com.example.quranapp.data.tafsir.TafsirSelectionStore
import com.example.quranapp.data.resource.ResourceCatalogRepository
import com.example.quranapp.data.resource.ResourceIndexStore
import com.example.quranapp.data.resource.ResourceType
import com.example.quranapp.domain.ReciterDisplayNames

private data class TafsirTab(val id: String, val label: String, val language: String)

private suspend fun loadInstalledTafsirTabs(context: android.content.Context): List<TafsirTab> {
    val catalog = ResourceCatalogRepository.getInstance(context).getCatalog()
    val index = ResourceIndexStore.getInstance(context).allEntries()
    return catalog.resources
        .filter { it.type == ResourceType.TAFSIR && !it.bundled }
        .filter { index.containsKey("tafsir:${it.id}") }
        .map { TafsirTab(it.id, it.name, it.language) }
}

@Composable
fun SurahDetailScreen(
    surahId: Int,
    onBack: () -> Unit,
    initialPageArg: Int? = null,
    isKhatmaMode: Boolean = false,
    viewModel: SurahDetailViewModel = viewModel(),
    audioViewModel: com.example.quranapp.presentation.audio.AudioPlayerViewModel = viewModel(),
    translationViewModel: com.example.quranapp.presentation.translation.TranslationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val audioState by audioViewModel.uiState.collectAsState()
    val activeVerseKey by audioViewModel.uiState
        .map { it.playerState.currentAyah?.verseKey }
        .collectAsState(initial = null)
    val selectedReaderReciter = audioState.selectedReciter
    val readerSupportsVerseTimings = selectedReaderReciter?.supportsVerseTimings == true
    val context = LocalContext.current

    val stablePagesMap = remember(uiState.pagesMap) { uiState.pagesMap }
    val stableFontNamesMap = remember(uiState.fontNamesMap) { uiState.fontNamesMap }

    LaunchedEffect(surahId, initialPageArg) {
        val repository =
            com.example.quranapp.data.quran.Qcf4Repository(context)

        val startPage = if (initialPageArg != null && initialPageArg in 1..604) {
            initialPageArg
        } else {
            val surah = repository.getSurahById(surahId)
            surah?.firstPage ?: 1
        }

        viewModel.loadQuran(startPage)
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        return
    }

    val totalPages = 604

    val initialPageIndex =
        (uiState.currentPageNumber - 1)
            .coerceIn(0, totalPages - 1)

    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { totalPages }
    )

    var selectedVerseKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState.currentPage) {

        selectedVerseKey = null
        val actualPageNumber =
            pagerState.currentPage + 1

        if (
            actualPageNumber !=
            uiState.currentPageNumber
        ) {
            viewModel.onPageChanged(
                actualPageNumber
            )
        }
    }

    // Track last auto-scrolled page to avoid scroll loops
    var lastAutoScrolledPage by remember { mutableStateOf(-1) }

    // Auto-scroll pager when audio reaches a verse on a different page
    LaunchedEffect(activeVerseKey, readerSupportsVerseTimings) {
        if (!readerSupportsVerseTimings) return@LaunchedEffect
        val verseKey = activeVerseKey ?: return@LaunchedEffect

        val targetPage = viewModel.findPageForVerseKey(verseKey)
        if (targetPage != null) {
            val currentPage = pagerState.currentPage + 1
            if (targetPage != currentPage && targetPage != lastAutoScrolledPage) {
                lastAutoScrolledPage = targetPage
                pagerState.animateScrollToPage(targetPage - 1)
            }
        } else {
            // Page not loaded yet — trigger preload
            viewModel.ensurePageLoadedForVerse(verseKey)
        }
    }

    val metadata =
        uiState.currentMetadata

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {

            QuranHeader(
                pageNumber =
                    uiState.currentPageNumber,

                surahName =
                    metadata?.surahNameArabic
                        ?: "",

                juzNumber =
                    metadata?.juzNumber
                        ?: 1,
                isKhatmaMode = isKhatmaMode
            )
        },

    ) { paddingValues ->

        LaunchedEffect(pagerState.currentPage) {
            val actualPage = pagerState.currentPage + 1
            android.util.Log.d("PERF_LOG", "UI: pagerState.currentPage changed to $actualPage")
            if (actualPage != uiState.currentPageNumber) {
                viewModel.onPageChanged(actualPage)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize(),
                reverseLayout = true
            ) { pageIndex ->
                val pageNumber = pageIndex + 1
                val page = stablePagesMap[pageNumber]
                val fontName = stableFontNamesMap[pageNumber]

                if (page != null && fontName != null) {
                    android.util.Log.d("PERF_LOG", "UI: Rendering Qcf4PageView for page $pageNumber")
                    Qcf4PageView(
                        page = page,
                        fontName = fontName,
                        selectedVerseKey = selectedVerseKey,
                        activeVerseKey = if (readerSupportsVerseTimings) activeVerseKey else null,
                        onVerseClick = { verseKey ->
                            selectedVerseKey = if (selectedVerseKey == verseKey) null else verseKey
                        }
                    )
                } else {
                    android.util.Log.d("PERF_LOG", "UI: Loading indicator for page $pageNumber")
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (selectedVerseKey != null) {
                val pageNumber = uiState.currentPageNumber
                val page = uiState.pagesMap[pageNumber]
                if (page != null) {
                    val downloadScope = rememberCoroutineScope()
                    VerseActionBottomSheet(
                        verseKey = selectedVerseKey!!,
                        page = page,
                        onDismiss = { selectedVerseKey = null },
                        onActionClick = { action ->
                            if (action == "download") {
                                val audioState = audioViewModel.uiState.value
                                val parts = selectedVerseKey!!.split(":")
                                val sId = parts.getOrNull(0)?.toIntOrNull() ?: surahId
                                val reciter = audioState.selectedReciter
                                if (reciter != null) {
                                    android.util.Log.d("TIMESTAMP_DEBUG", "UI click download surahId=$sId reciterId=${reciter.id} reciterName=${reciter.name}")
                                    downloadScope.launch {
                                        try {
                                            val repo = com.example.quranapp.data.audio.AudioRepository(context)
                                            val surahAudio = repo.getSurahAudio(reciter.id, sId)
                                            val surahObj = com.example.quranapp.data.quran.Qcf4Repository(context).getSurahById(sId)
                                            val surahName = surahObj?.nameArabic ?: "سورة $sId"
                                            val downloadManager = com.example.quranapp.data.audio.AudioDownloadManager.getInstance(context)
                                            downloadManager.downloadSurahAudio(
                                                reciterId = reciter.id,
                                                surahId = sId,
                                                audioUrl = surahAudio.audioUrl,
                                                surahName = surahName,
                                                reciterName = reciter.name
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.e("AudioDebug", "[Download] Failed: ${e.message}")
                                        }
                                    }
                                }
                            }
                            selectedVerseKey = null
                        },
                        audioViewModel = audioViewModel,
                        translationViewModel = translationViewModel
                    )
                }
            }
        }
    }
}


/*
|--------------------------------------------------------------------------
| HEADER
|--------------------------------------------------------------------------
*/
@Composable
fun QuranHeader(
    pageNumber: Int,
    surahName: String,
    juzNumber: Int,
    isKhatmaMode: Boolean = false
) {
    val context = LocalContext.current
    var khatmaReadPages by remember(pageNumber) { 
        mutableStateOf(QuranPreferences.getKhatmaReadPages(context)) 
    }
    val isReadInKhatma = khatmaReadPages.contains(pageNumber)
    val primaryGreen = Color(0xFF004d40)
    val textColor = Color(0xFF666666)

    CompositionLocalProviderRtl {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 4.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right Side (in RTL) - Juz and Khatma Checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Khatma Checkbox (Only show if in Khatma mode)
                if (isKhatmaMode) {
                    Icon(
                        imageVector = if (isReadInKhatma) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Mark as read in Khatma",
                        tint = if (isReadInKhatma) primaryGreen else textColor.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                val set = khatmaReadPages.toMutableSet()
                                if (isReadInKhatma) set.remove(pageNumber) else set.add(pageNumber)
                                com.example.quranapp.core.utils.QuranPreferences.saveKhatmaReadPages(context, set)
                                khatmaReadPages = set
                            }
                    )
                }
                Text(
                    text = "جزء $juzNumber",
                    fontSize = 12.sp,
                    color = textColor
                )
            }

            // Center - Surah Name
            Text(
                text = surahName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = primaryGreen
            )

            // Left Side (in RTL) - Page number
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "صفحة $pageNumber",
                    fontSize = 12.sp,
                    color = textColor
                )
            }
        }
    }
}


/*
|--------------------------------------------------------------------------
| QCF4 PAGE
|--------------------------------------------------------------------------
*/
@Composable
fun Qcf4PageView(
    page: Page,
    fontName: String,
    selectedVerseKey: String?,
    activeVerseKey: String?,
    onVerseClick: (String) -> Unit
) {
    val context = LocalContext.current
    val fontFamily = remember(fontName) { Qcf4FontManager.getFontFamily(context, fontName) }

    CompositionLocalProviderRtl {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            page.lines.forEach { line ->
                Qcf4LineView(
                    pageNumber = page.page,
                    line = line,
                    fontFamily = fontFamily,
                    selectedVerseKey = selectedVerseKey,
                    activeVerseKey = activeVerseKey,
                    onVerseClick = onVerseClick
                )
            }
        }
    }
}

/*
|--------------------------------------------------------------------------
| QCF4 LINE
|--------------------------------------------------------------------------
*/
@Composable
fun Qcf4LineView(
    pageNumber: Int,
    line: Line,
    fontFamily: FontFamily,
    selectedVerseKey: String?,
    activeVerseKey: String?,
    onVerseClick: (String) -> Unit
) {
    val context = LocalContext.current
    val selectedColor = Color(0xFFD8E7D8)
    val activeColor = Color(0xFFFFECB3)

    val groups = remember(line) {
        val result = mutableListOf<Pair<String?, List<Word>>>()
        var currentKey: String? = null
        var currentGroup = mutableListOf<Word>()
        for (word in line.words) {
            val key = word.verse_key
            if (key != currentKey && currentGroup.isNotEmpty()) {
                result.add(currentKey to currentGroup.toList())
                currentGroup = mutableListOf()
            }
            currentKey = key
            currentGroup.add(word)
        }
        if (currentGroup.isNotEmpty()) result.add(currentKey to currentGroup.toList())
        result
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        groups.forEach { (verseKey, words) ->
                val isActive = verseKey != null && verseKey == activeVerseKey
                val isSelected = verseKey != null && verseKey == selectedVerseKey
                val bgColor = when {
                    isActive -> activeColor
                    isSelected -> selectedColor
                    else -> Color.Transparent
                }

                val reversedWords = remember(words) { words.reversed() }
                val annotatedText = remember(reversedWords, fontFamily) {
                    buildAnnotatedString {
                        var currentFont: String? = null
                        var currentType: String? = null
                        
                        reversedWords.forEachIndexed { i, word ->
                            if (word.font != currentFont || word.type != currentType) {
                                if (currentFont != null) pop()
                                
                                val wff = try {
                                    Qcf4FontManager.getFontFamily(context, word.font)
                                } catch (e: Exception) { fontFamily }
                                
                                val fs = when {
                                    word.type == "surah_header" -> 36.sp
                                    word.type == "bismillah" -> 32.sp
                                    else -> 21.sp
                                }
                                pushStyle(SpanStyle(fontFamily = wff, fontSize = fs))
                                
                                currentFont = word.font
                                currentType = word.type
                            }
                            
                            append(word.char)
                            if (i < reversedWords.size - 1) append(" ")
                        }
                        if (currentFont != null) pop()
                    }
                }

                if (pageNumber == 2) {
                    android.util.Log.d("QURAN_ORDER_DEBUG", "  annotatedText='$annotatedText'")
                }

                Text(
                    text = annotatedText,
                    color = Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .then(
                            if (verseKey != null) Modifier.clickable { onVerseClick(verseKey) }
                            else Modifier
                        )
                        .padding(horizontal = 0.dp, vertical = 1.dp)
                        .drawBehind {
                            if (bgColor != Color.Transparent) {
                                drawRoundRect(color = bgColor, cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                            }
                        }
                )
            }
        }
    }


/*
|--------------------------------------------------------------------------
| RTL
|--------------------------------------------------------------------------
*/
@Composable
private fun CompositionLocalProviderRtl(
    content: @Composable () -> Unit
) {

    androidx.compose.runtime.CompositionLocalProvider(

        LocalLayoutDirection provides
                LayoutDirection.Rtl

    ) {

        content()
    }
}


/*
|--------------------------------------------------------------------------
| VERSE ACTION BOTTOM SHEET
|--------------------------------------------------------------------------
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseActionBottomSheet(
    verseKey: String,
    page: Page,
    onDismiss: () -> Unit,
    onActionClick: (String) -> Unit,
    audioViewModel: com.example.quranapp.presentation.audio.AudioPlayerViewModel = viewModel(),
    translationViewModel: com.example.quranapp.presentation.translation.TranslationViewModel = viewModel()
) {
    val goldColor = Color(0xFFC5A059)
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)
    val sheetBg = Color(0xFFFAF7F0)

    var showTafsir by remember { mutableStateOf(false) }
    var showReciterDialog by remember { mutableStateOf(false) }

    val audioState by audioViewModel.uiState.collectAsState()
    val selectedReciter = audioState.selectedReciter

    val parts = verseKey.split(":")
    val surahId = parts.getOrNull(0)?.toIntOrNull() ?: 1

    val allWords = remember(page) { page.lines.flatMap { pageLine -> pageLine.words } }
    val verseWords = remember(allWords, verseKey) { allWords.filter { it.verse_key == verseKey && it.type != "end" && !it.text.startsWith("V") } }
    val verseText = remember(verseWords) { verseWords.joinToString(" ") { it.text.ifEmpty { it.char } } }

    val pageSurah = page.surahs.find { it.id == surahId }
    val surahNameArabic = pageSurah?.name_arabic ?: ""

    // Use translation view model state
    val translationState by translationViewModel.uiState.collectAsState()
    
    // Clear old text when opening a new verse
    LaunchedEffect(verseKey) {
        translationViewModel.clearVerseTranslation()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFFD3C5B4), shape = RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "($surahId) سورة $surahNameArabic",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = goldColor,
                    fontSize = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = verseText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textDark,
                    fontSize = 20.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Reciter Selection Card inside BottomSheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showReciterDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF2ECE1),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2D6C5))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change Reciter",
                        tint = textGray
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = ReciterDisplayNames.displayName(selectedReciter) ?: "اختر القارئ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textDark,
                                fontSize = 16.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Reciter",
                            tint = goldColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            val playerState = audioState.playerState
            val isSameAyah = verseKey == playerState.currentPlayingVerseKey
            val isAudioPlaying = playerState.isPlaying
            val playIcon = if (isSameAyah && isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
            val playAction: () -> Unit = {
                if (isSameAyah) {
                    audioViewModel.togglePlayPause()
                } else {
                    val sId = verseKey.split(":").firstOrNull()?.toIntOrNull() ?: 1
                    audioViewModel.playSurahWithVerse(sId, verseKey)
                }
                onDismiss()
            }

            // If the selected reciter does not provide verse timings, tell the user
            // instead of faking synchronization.
            if (selectedReciter?.supportsVerseTimings == false) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF4E5),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6C99A))
                ) {
                    Text(
                        text = "هذا القارئ لا يوفر مزامنة الآيات",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF8D5B2C),
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            val ctx = androidx.compose.ui.platform.LocalContext.current
            val dlManager = remember { com.example.quranapp.data.audio.AudioDownloadManager.getInstance(ctx) }
            val dlProgress by dlManager.downloadProgress.collectAsState()
            val dlDownloads by dlManager.downloads.collectAsState()
            val reciterId = audioState.selectedReciter?.id
                ?: audioState.reciters.firstOrNull()?.id
                ?: 7
            val isDownloading = dlProgress.containsKey("${reciterId}_$surahId")
            val isDownloaded = dlDownloads.any { it.reciterId == reciterId && it.surahId == surahId }
            val downloadIcon = when {
                isDownloading -> Icons.Default.CloudUpload
                isDownloaded -> Icons.Default.CheckCircle
                else -> Icons.Default.Download
            }

            val selectionStore = remember { TafsirSelectionStore(ctx) }
            var selectedTab by remember { mutableStateOf(selectionStore.selectedTafsirId()) }
            val tafsirReader = remember { TafsirReader.getInstance(ctx) }
            var tafsirText by remember(verseKey, selectedTab) { mutableStateOf<String?>(null) }
            var installedTafsirs by remember { mutableStateOf<List<TafsirTab>>(emptyList()) }
            LaunchedEffect(Unit) {
                installedTafsirs = loadInstalledTafsirTabs(ctx)
            }
            val extraTafsirIds = remember(installedTafsirs) { installedTafsirs.map { it.id }.toSet() }
            val tafsirTabIds = remember(extraTafsirIds) {
                setOf(TafsirReader.BUNDLED_TAFSIR_ID) + extraTafsirIds
            }
            val contentTabs = remember(installedTafsirs) {
                listOf(
                    TafsirTab(TafsirReader.BUNDLED_TAFSIR_ID, TafsirReader.BUNDLED_TAFSIR_NAME, TafsirReader.BUNDLED_TAFSIR_LANGUAGE)
                ) + installedTafsirs + translationViewModel.supportedEditions.map { TafsirTab(it.identifier, it.name, it.language) }
            }
            val actions = listOf(
                Triple("استماع", playIcon, playAction),
                Triple("تفسير", Icons.Default.MenuBook, { 
                    if (showTafsir && selectedTab in tafsirTabIds) showTafsir = false 
                    else { showTafsir = true; selectedTab = selectionStore.selectedTafsirId() } 
                }),
                Triple("ترجمة", Icons.Default.Translate, { 
                    if (showTafsir && selectedTab !in tafsirTabIds) showTafsir = false 
                    else { showTafsir = true; selectedTab = "fr.hamidullah" } 
                }),
                Triple("مشاركة", Icons.Default.Share, { 
                    val verseNum = verseKey.split(":").getOrNull(1) ?: ""
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "قال تعالى: ﴿ $verseText ﴾\n[سورة $surahNameArabic - الآية $verseNum]\n\nتمت المشاركة من تطبيق القرآن الكريم")
                        type = "text/plain"
                    }
                    ctx.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة الآية"))
                })
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEach { (label, icon, onClick) ->
                    val isSelected = (label == "تفسير" && showTafsir && selectedTab in tafsirTabIds) || 
                                     (label == "ترجمة" && showTafsir && selectedTab !in tafsirTabIds)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF8D5B2C) else Color(0xFFF2ECE1),
                            modifier = Modifier.size(54.dp),
                            onClick = onClick
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color.White else goldColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            if (showTafsir) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Load translation if not loaded
                LaunchedEffect(selectedTab, verseKey, translationState.downloadedEditions) {
                    if (selectedTab !in tafsirTabIds && translationState.downloadedEditions.contains(selectedTab)) {
                        translationViewModel.loadVerseTranslation(selectedTab, verseKey)
                    }
                }
                LaunchedEffect(selectedTab, verseKey) {
                    if (selectedTab in tafsirTabIds) {
                        tafsirText = tafsirReader.getText(selectedTab, verseKey)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF4EEE2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2D6C5))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Tabs: installed tafsirs + translation editions
                        androidx.compose.material3.ScrollableTabRow(
                            selectedTabIndex = contentTabs.indexOfFirst { it.id == selectedTab }.coerceAtLeast(0),
                            containerColor = Color.Transparent,
                            contentColor = goldColor,
                            edgePadding = 0.dp,
                            divider = {}
                        ) {
                            contentTabs.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab.id,
                                    onClick = {
                                        selectedTab = tab.id
                                        if (tab.id in tafsirTabIds) {
                                            selectionStore.selectTafsir(tab.id)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = tab.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val isTafsirTab = selectedTab in tafsirTabIds
                        val isDownloaded = !isTafsirTab && translationState.downloadedEditions.contains(selectedTab)
                        val isDownloading = !isTafsirTab && translationState.downloadingEditions.contains(selectedTab)

                        val contentScrollStates = remember { mutableMapOf<String, ScrollState>() }
                        val contentScrollState = contentScrollStates.getOrPut(selectedTab) { ScrollState(0) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(contentScrollState)
                        ) {
                        if (isTafsirTab) {
                            val text = tafsirText
                            if (text != null) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textDark,
                                        fontSize = 16.sp,
                                        lineHeight = 26.sp
                                    ),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (isDownloaded) {
                            val text = translationState.currentVerseText[selectedTab]
                            if (text != null) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textDark,
                                        fontSize = 16.sp,
                                        lineHeight = 26.sp
                                    ),
                                    textAlign = if (selectedTab.startsWith("ar")) TextAlign.Right else TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "هذه النسخة غير محملة (تحتاج إنترنت للمرة الأولى فقط)",
                                    color = textGray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { translationViewModel.downloadEdition(selectedTab) },
                                    enabled = !isDownloading,
                                    colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري التحميل...")
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("تحميل النسخة (حوالي 1 ميغابايت)")
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Reciter Selection Dialog
    if (showReciterDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredReciters = remember(searchQuery, audioState.reciters) {
            if (searchQuery.isBlank()) audioState.reciters
            else audioState.reciters.filter { r ->
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
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredReciters.size) { index ->
                            val reciter = filteredReciters[index]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        audioViewModel.selectReciter(reciter)
                                        showReciterDialog = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (reciter.id == selectedReciter?.id) Color(0xFFE8F5E9) else Color.Transparent
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = ReciterDisplayNames.displayName(reciter),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF004d40)
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
                    Text("إغلاق", color = Color(0xFF004d40))
                }
            }
        )
    }
}