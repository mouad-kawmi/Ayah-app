package com.example

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aistudio.quran.mwkpqz.BuildConfig
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import com.example.quran.data.PageCoordinate
import com.example.quran.data.PageLayout
import com.example.quran.data.PageLayoutProvider
import com.example.quran.data.PageRect
import kotlin.math.max
import kotlin.math.min

private data class CanvasTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

private const val COVER_INTERPOLATION = 0.22f

@Composable
fun QuranPageCanvas(
    pageNumber: Int,
    pageLayoutProvider: PageLayoutProvider,
    selectedVerseKey: String?,
    onVerseTapped: (String) -> Unit,
    pageScaleMode: PageScaleMode = PageScaleMode.CONTAIN,
    tapId: Long = -1L,
    modifier: Modifier = Modifier
) {
    val layout by produceState<PageLayout?>(
        initialValue = pageLayoutProvider.getCachedPageLayout(pageNumber),
        key1 = pageNumber
    ) {
        if (value == null) {
            value = pageLayoutProvider.getPageLayoutAsync(pageNumber)
        }
    }

    val highlightColor = Color(0x40FFEB3B)
    val borderColor = Color(0xCCD4A017)
    val currentLayout = layout

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F3E7))
    ) {
        val density = LocalDensity.current
        val canvasSize = remember(density, maxWidth, maxHeight) {
            IntSize(
                with(density) { maxWidth.roundToPx() },
                with(density) { maxHeight.roundToPx() }
            )
        }

        val transform = remember(currentLayout, canvasSize, pageScaleMode) {
            computeTransform(currentLayout, canvasSize, pageScaleMode)
        }

        val selectedVerseRects = remember(selectedVerseKey, currentLayout, transform) {
            if (selectedVerseKey == null || currentLayout == null || transform == null) emptyList()
            else computeVerseScreenRectsForKey(currentLayout.verseCoordinates, selectedVerseKey, transform)
        }

        val containScale = remember(currentLayout, canvasSize) {
            if (currentLayout == null) 0f
            else min(
                canvasSize.width / currentLayout.viewBoxWidth,
                canvasSize.height / currentLayout.viewBoxHeight
            )
        }

        val imageScaleRatio = remember(transform, containScale) {
            if (transform == null || containScale <= 0f) 1f
            else transform.scale / containScale
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithHighlight(selectedVerseRects, highlightColor, borderColor)
                .pointerInput(currentLayout, transform) {
                    detectTapGestures { tapOffset ->
                        if (currentLayout == null || transform == null) return@detectTapGestures

                        val touchStartNs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L

                        if (BuildConfig.DEBUG && tapId >= 0L) {
                            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId Verse touch detection START")
                        }

                        val tapX = tapOffset.x
                        val tapY = tapOffset.y
                        var bestVerseKey: String? = null
                        var bestDistanceSquared = Float.MAX_VALUE

                        for (coordinate in currentLayout.verseCoordinates) {
                            for (rect in coordinate.rects) {
                                val screenRect = transformRect(rect, transform)
                                if (!screenRect.contains(Offset(tapX, tapY))) {
                                    continue
                                }

                                val centerX = screenRect.left + screenRect.width / 2f
                                val centerY = screenRect.top + screenRect.height / 2f
                                val distanceSquared = (tapX - centerX) * (tapX - centerX) +
                                    (tapY - centerY) * (tapY - centerY)

                                if (distanceSquared < bestDistanceSquared) {
                                    bestDistanceSquared = distanceSquared
                                    bestVerseKey = coordinate.verseKey
                                }
                            }
                        }

                        if (BuildConfig.DEBUG && tapId >= 0L) {
                            val touchEndNs = SystemClock.elapsedRealtime()
                            val touchDurationMs = touchEndNs - touchStartNs
                            DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId Verse touch detection END (${touchDurationMs}ms)")
                            if (bestVerseKey != null) {
                                DebugLogger.debug(LogCategory.PERFORMANCE, "[PERF] tap=$tapId Verse touch detected → $bestVerseKey")
                            }
                            val mainThreadMs = touchEndNs - touchStartNs
                            if (mainThreadMs > 16) {
                                DebugLogger.warning(LogCategory.PERFORMANCE, "[PERF] tap=$tapId WARNING Main thread blocked for ${mainThreadMs}ms (verse touch detection)")
                            }
                        }

                        bestVerseKey?.let(onVerseTapped)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = imageScaleRatio
                        scaleY = imageScaleRatio
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/mushafs/hafs/kfqc/webp/${String.format("%03d", pageNumber)}.webp")
                        .crossfade(true)
                        .build(),
                    contentDescription = "Quran Page $pageNumber",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun computeTransform(
    layout: PageLayout?,
    canvasSize: IntSize,
    scaleMode: PageScaleMode
): CanvasTransform? {
    if (layout == null || canvasSize.width <= 0 || canvasSize.height <= 0
        || layout.viewBoxWidth <= 0f || layout.viewBoxHeight <= 0f) {
        return null
    }

    val widthRatio = canvasSize.width / layout.viewBoxWidth
    val heightRatio = canvasSize.height / layout.viewBoxHeight
    val containScale = min(widthRatio, heightRatio)
    val coverScale = max(widthRatio, heightRatio)
    val scale = when (scaleMode) {
        PageScaleMode.CONTAIN -> containScale
        PageScaleMode.COVER -> containScale + (coverScale - containScale) * COVER_INTERPOLATION
    }
    val offsetX = (canvasSize.width - layout.viewBoxWidth * scale) / 2f
    val offsetY = (canvasSize.height - layout.viewBoxHeight * scale) / 2f
    return CanvasTransform(scale, offsetX, offsetY)
}

private fun computeVerseScreenRectsForKey(
    coordinates: List<PageCoordinate>,
    verseKey: String,
    transform: CanvasTransform
): List<Rect> {
    val target = coordinates.firstOrNull { it.verseKey == verseKey } ?: return emptyList()
    return target.rects.map { rect -> transformRect(rect, transform) }
}

private fun transformRect(rect: PageRect, transform: CanvasTransform): Rect {
    return Rect(
        rect.left * transform.scale + transform.offsetX,
        rect.top * transform.scale + transform.offsetY,
        rect.right * transform.scale + transform.offsetX,
        rect.bottom * transform.scale + transform.offsetY
    )
}

private fun Modifier.drawWithHighlight(
    verseScreenRects: List<Rect>,
    fillColor: Color,
    borderColor: Color
): Modifier = this.drawWithContent {
    drawContent()
    for (rect in verseScreenRects) {
        drawRect(fillColor, topLeft = Offset(rect.left, rect.top), size = Size(rect.width, rect.height))
        drawRect(
            borderColor.copy(alpha = 0.6f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(width = 2f)
        )
    }
}
