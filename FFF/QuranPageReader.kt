package com.example

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.example.quran.data.PageLayoutProvider
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuranPageReader(
    pagerState: PagerState,
    pageLayoutProvider: PageLayoutProvider,
    selectedVerseKey: String?,
    onVerseTapped: (String) -> Unit,
    tapId: Long = -1L,
    pageScaleMode: PageScaleMode = PageScaleMode.CONTAIN,
    onPageChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                onPageChanged?.invoke(pageIndex + 1)
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it + 1 }
    ) { pageIndex ->
        val pageNumber = pageIndex + 1
        QuranPageCanvas(
            pageNumber = pageNumber,
            pageLayoutProvider = pageLayoutProvider,
            selectedVerseKey = selectedVerseKey,
            onVerseTapped = onVerseTapped,
            tapId = tapId,
            pageScaleMode = pageScaleMode,
            modifier = Modifier.fillMaxSize()
        )
    }
}
