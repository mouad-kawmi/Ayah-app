package com.example

import android.os.SystemClock
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.debug.PerformanceProfiler

private val SEARCH_FILTER_OPTIONS = listOf("الكل", "السور", "الآيات")

data class MatchRange(val start: Int, val end: Int)

fun findNormalizedMatches(original: String, normalizedQuery: String): List<MatchRange> {
    if (normalizedQuery.isEmpty()) return emptyList()
    
    val originalToNormalizedMap = mutableListOf<Int>()
    val normalizedBuilder = java.lang.StringBuilder()
    
    val diacritics = setOf(
        '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651', '\u0652', '\u0653', '\u0654', '\u0670', '\u0656', 'ـ'
    )
    
    for (i in original.indices) {
        val c = original[i]
        if (diacritics.contains(c)) {
            continue
        }
        
        val normC = when (c) {
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ى' -> 'ي'
            'ة' -> 'ه'
            else -> c
        }
        
        normalizedBuilder.append(normC)
        originalToNormalizedMap.add(i)
    }
    
    var normText = normalizedBuilder.toString()
    val replacements = listOf(
        "صلوه" to "صلاه",
        "زكوه" to "زكاه",
        "حيوه" to "حياه",
        "نجوه" to "نجاه",
        "غدوه" to "غداه",
        "منوه" to "مناه"
    )
    
    for ((from, to) in replacements) {
        var index = normText.indexOf(from)
        while (index >= 0) {
            normText = normText.substring(0, index) + to + normText.substring(index + from.length)
            index = normText.indexOf(from, index + 1)
        }
    }
    
    val matches = mutableListOf<MatchRange>()
    var startIndex = normText.indexOf(normalizedQuery, ignoreCase = true)
    while (startIndex >= 0) {
        val endIndex = startIndex + normalizedQuery.length
        
        if (startIndex < originalToNormalizedMap.size && (endIndex - 1) < originalToNormalizedMap.size) {
            val origStart = originalToNormalizedMap[startIndex]
            val lastMappedOrigIndex = originalToNormalizedMap[endIndex - 1]
            var origEnd = lastMappedOrigIndex + 1
            while (origEnd < original.length && diacritics.contains(original[origEnd])) {
                origEnd++
            }
            matches.add(MatchRange(origStart, origEnd))
        }
        
        startIndex = normText.indexOf(normalizedQuery, startIndex + 1, ignoreCase = true)
    }
    
    return matches
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    language: AppLanguage,
    onChapterClick: (Int) -> Unit,
    viewModel: QuranViewModel
) {
    SideEffect {
        android.util.Log.d("NAV_TRACE", "SEARCH_SCREEN_READY")
    }
    LaunchedEffect(Unit) {
        android.util.Log.d("NAV_TRACE", "SEARCH_SCREEN_FIRST_COMPOSITION")
    }
    SideEffect {
        PerformanceProfiler.recordScreenRecomposition("SearchScreen")
    }
    val uiState by viewModel.uiState.collectAsState()
    SideEffect {
        android.util.Log.d("NAV_TRACE", "SEARCH_RECOMPOSE isLoading=${uiState.isLoading} results=${uiState.searchResults.size}")
    }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("الكل") } // "الكل", "السور", "الآيات"
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Bottom Sheet / Dialog properties for verse detail
    var selectedVerseForTafsir by remember { mutableStateOf<Verse?>(null) }
    var translationText by remember { mutableStateOf("") }
    var translationEnText by remember { mutableStateOf("") }
    var tafsirText by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    
    // Trigger loading translation and tafsir when a verse is selected
    LaunchedEffect(selectedVerseForTafsir) {
        selectedVerseForTafsir?.let { verse ->
            isFetching = true
            translationText = ""
            translationEnText = ""
            tafsirText = ""
            viewModel.fetchVerseTranslationAndTafsir(verse.id).fold(
                onSuccess = { (translationFr, translationEn, tafsir) ->
                    translationText = translationFr
                    translationEnText = translationEn
                    tafsirText = tafsir
                    isFetching = false
                },
                onFailure = {
                    translationText = "..."
                    translationEnText = "..."
                    tafsirText = "..."
                    isFetching = false
                }
            )
        }
    }

    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = AppTranslation.translate("search_in_quran", language),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(AppTranslation.translate("search_hint", language)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "بحث",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "مسح",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )

                // Trigger search when query changes — debounced 300ms to avoid
                // firing a full 604-page scan on every keystroke.
                LaunchedEffect(query) {
                    if (query.isBlank()) {
                        viewModel.search(query)
                    } else {
                        delay(300L)
                        viewModel.search(query)
                    }
                }

                if (query.isEmpty()) {
                    // Suggestions View when search is empty
                    SuggestionsList(language = language, onSuggestionClick = { query = it })
                } else {
                    // Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = SEARCH_FILTER_OPTIONS
                        filters.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // Searching local Mushaf in-memory data
                    val normQuery = remember(query) { query.trim().normalizeArabic() }

                    // Filter chapters — only re-filter when query or chapters change
                    val filteredChapters = remember(query, selectedFilter, uiState.chapters) {
                        if (selectedFilter == "الكل" || selectedFilter == "السور") {
                            uiState.chapters.filter { it.name.normalizeArabic().contains(normQuery, ignoreCase = true) }
                        } else emptyList()
                    }

                    // Filtered verses come from viewModel search
                    val filteredVerses = if (selectedFilter == "الكل" || selectedFilter == "الآيات") {
                        uiState.searchResults
                    } else emptyList()

                    if (filteredChapters.isEmpty() && filteredVerses.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "${AppTranslation.translate("no_results_for", language)} \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            // Surah results
                            if (filteredChapters.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "${AppTranslation.translate("matching_surahs", language)} (${filteredChapters.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredChapters, key = { it.id }) { chapter ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onChapterClick(chapter.startingPage) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${chapter.id}",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = "${AppTranslation.translate("surah_label", language)} ${chapter.name}",
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${AppTranslation.translate("start_page", language)} ${chapter.startingPage}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronLeft,
                                                contentDescription = "عرض السورة",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Verse results
                            if (filteredVerses.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${AppTranslation.translate("matching_verses", language)} (${filteredVerses.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredVerses, key = { it.verse.id }) { searchItem ->
                                    val verse = searchItem.verse
                                    val matchedPage = verse.pageNumber
                                    
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Header containing Surah, Ayah index and Page index
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${AppTranslation.translate("surah_label", language)} ${verse.surahName} • ${AppTranslation.translate("ayah_label", language)} ${verse.numberInSurah}",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "${AppTranslation.translate("page_label", language)} $matchedPage",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Text with query highlighted
                                            val annotateStart = SystemClock.elapsedRealtime()
                                            val highlightColor = MaterialTheme.colorScheme.primary
                                            val annotatedText = remember(verse.text, searchItem.matches, highlightColor) {
                                                buildAnnotatedString {
                                                    val originalText = verse.text
                                                    val matches = searchItem.matches
                                                    if (matches.isEmpty()) {
                                                        append(originalText)
                                                    } else {
                                                        var lastIndex = 0
                                                        for (match in matches) {
                                                            if (match.start >= lastIndex && match.start <= originalText.length && match.end <= originalText.length) {
                                                                append(originalText.substring(lastIndex, match.start))
                                                                withStyle(style = SpanStyle(
                                                                    color = highlightColor,
                                                                    fontWeight = FontWeight.Bold,
                                                                    background = highlightColor.copy(alpha = 0.15f)
                                                                )) {
                                                                    append(originalText.substring(match.start, match.end))
                                                                }
                                                                lastIndex = match.end
                                                            }
                                                        }
                                                        if (lastIndex < originalText.length) {
                                                            append(originalText.substring(lastIndex, originalText.length))
                                                        }
                                                    }
                                                }
                                            }
                                            PerformanceProfiler.recordCompositionHotspot(
                                                "SearchScreen.annotatedVerseText",
                                                SystemClock.elapsedRealtime() - annotateStart
                                            )
                                            PerformanceProfiler.recordAllocationHotspot(
                                                "SearchScreen.annotatedVerseText",
                                                1
                                            )

                                            Text(
                                                text = annotatedText,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    lineHeight = 28.sp,
                                                    textAlign = TextAlign.Right
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Read Page action
                                                Button(
                                                    onClick = { onChapterClick(matchedPage) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.MenuBook,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("افتح الصفحة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }

                                                // Tafsir action
                                                OutlinedButton(
                                                    onClick = { selectedVerseForTafsir = verse },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.secondary
                                                    ),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Spa,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("التفسير والترجمة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Gorgeous Dialog with Translation and Tafsir
    if (selectedVerseForTafsir != null) {
        val verse = selectedVerseForTafsir!!
        AlertDialog(
            onDismissRequest = { selectedVerseForTafsir = null },
            confirmButton = {
                TextButton(onClick = { selectedVerseForTafsir = null }) {
                    Text("إغلاق", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = "${AppTranslation.translate("surah_label", language)} ${verse.surahName} • ${AppTranslation.translate("ayah_label", language)} ${verse.numberInSurah}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = verse.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            lineHeight = 32.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(16.dp)
                        )
                    } else {
                        // Translation Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Translation (English)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = translationEnText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                Text(
                                    text = AppTranslation.translate("translation_fr", language),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = translationText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Tafsir Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = AppTranslation.translate("tafsir_muyassar", language),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = tafsirText,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionsList(
    language: AppLanguage,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf(
        "يسر", "الجنة", "الصبر", "الرحمة", "الصلاة", "التقوى"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = AppTranslation.translate("quick_search_suggestions", language),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    modifier = Modifier.clickable { onSuggestionClick(suggestion) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
