package com.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.debug.PerformanceProfiler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranListScreen(
    language: AppLanguage,
    onNavigateUp: (() -> Unit)? = null,
    onChapterClick: (Int) -> Unit,
    viewModel: QuranViewModel = viewModel()
) {
    SideEffect {
        android.util.Log.d("NAV_TRACE", "QURAN_SCREEN_READY")
    }
    LaunchedEffect(Unit) {
        android.util.Log.d("NAV_TRACE", "QURAN_SCREEN_FIRST_COMPOSITION")
    }
    if (BuildConfig.DEBUG) {
        SideEffect {
            PerformanceProfiler.recordScreenRecomposition("QuranListScreen")
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    SideEffect {
        android.util.Log.d("NAV_TRACE", "QURAN_RECOMPOSE isLoading=${uiState.isLoading} chapters=${uiState.chapters.size}")
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredChapters by remember {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            if (query.isEmpty()) {
                uiState.chapters
            } else {
                uiState.chapters.filter { chapter ->
                    val englishName = SURAH_NAMES_EN.getOrNull(chapter.id - 1) ?: ""
                    chapter.id.toString() == query ||
                    chapter.name.contains(query, ignoreCase = true) ||
                    englishName.contains(query, ignoreCase = true)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppTranslation.translate("index", language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateUp != null) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(AppTranslation.translate("search_surah_hint", language)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.error != null -> {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    filteredChapters.isEmpty() && searchQuery.isNotBlank() -> {
                        Text(
                            text = AppTranslation.translate("no_surah_found", language),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = filteredChapters,
                                key = { it.id },
                                contentType = { "chapter_item" }
                            ) { chapter ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            android.util.Log.d("QuranPerf", "[onChapterClick] page=${chapter.startingPage} at ${System.currentTimeMillis()}ms")
                                            onChapterClick(chapter.startingPage)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${AppTranslation.translate("page_label", language)} ${chapter.startingPage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "${AppTranslation.translate("surah_label", language)} ${chapter.name}",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
