package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeScreen(
    uiState: MainUiState,
    prayerStateFlow: StateFlow<PrayerState> = PrayerStateMachine.state,
    lastReadPage: Int,
    lastReadSurah: String,
    lastReadVerse: Int,
    lastReadText: String,
    onLastReadClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onQuranClick: () -> Unit,
    onAdhkarClick: () -> Unit,
    onQiblaClick: () -> Unit,
    onTasbihClick: () -> Unit
) {
    SideEffect {
        android.util.Log.d("NAV_TRACE", "HOME_RECOMPOSE remaining=${uiState.remainingSeconds} city=${uiState.cityName} prayer=${uiState.prayerTimes != null} isLoading=${uiState.isLoading}")
    }
    DisposableEffect(Unit) {
        Log.d("STARTUP_TRACE", "HOME_FIRST_ENTER")
        android.util.Log.d("NAV_TRACE", "HOME_SCREEN_FIRST_COMPOSITION")
        DebugLogger.debug(LogCategory.PERFORMANCE, "HOME_FIRST_ENTER")
        onDispose { }
    }
    SideEffect {
        android.util.Log.d("NAV_TRACE", "HOME_SCREEN_READY")
    }
    SideEffect {
        val pt = uiState.prayerTimes
        Log.d("STARTUP_TRACE", "HOME_SIDEEFFECT cityName=${uiState.cityName} prayerTimesNull=${pt == null} remainingSeconds=${uiState.remainingSeconds}")
        DebugLogger.debug(LogCategory.PERFORMANCE, "HOME_SIDEEFFECT — cityName=${uiState.cityName} prayerTimesNull=${pt == null} remainingSeconds=${uiState.remainingSeconds}")
    }

    val language = uiState.language
    val prayerTimes = uiState.prayerTimes ?: PrayerTimes()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Bar & Hero Section
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = uiState.cityName.ifBlank { AppTranslation.translate("app_name", language) },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = uiState.dateGregorian,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant ?: Color.LightGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Hero Banner
            HeroBanner(uiState = uiState, prayerStateFlow = prayerStateFlow, language = language)
        }

        // Last Read Ticket
        LastReadTicket(
            lastReadPage = lastReadPage,
            lastReadSurah = lastReadSurah,
            lastReadVerse = lastReadVerse,
            lastReadText = lastReadText,
            language = language,
            onClick = { onLastReadClick(lastReadPage) }
        )

        // Prayer Timeline — uses uiState.nextPrayerName (not reactive to PrayerStateMachine)
        PrayerTimeline(
            prayerTimes = prayerTimes,
            nextPrayerName = if (uiState.nextPrayerName.isNotBlank()) AppTranslation.translate(uiState.nextPrayerName, language) else "",
            language = language
        )

        // Services Grid
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = AppTranslation.translate("services_title", language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumServiceTile(
                    modifier = Modifier.weight(1f),
                    title = AppTranslation.translate("quran", language),
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = onQuranClick
                )
                PremiumServiceTile(
                    modifier = Modifier.weight(1f),
                    title = AppTranslation.translate("adhkar", language),
                    icon = Icons.Rounded.Spa,
                    onClick = onAdhkarClick
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumServiceTile(
                    modifier = Modifier.weight(1f),
                    title = AppTranslation.translate("qibla", language),
                    icon = Icons.Rounded.Explore,
                    onClick = onQiblaClick
                )
                PremiumServiceTile(
                    modifier = Modifier.weight(1f),
                    title = AppTranslation.translate("tasbih", language),
                    icon = Icons.Rounded.Fingerprint,
                    onClick = onTasbihClick
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun HeroBanner(
    uiState: MainUiState,
    prayerStateFlow: StateFlow<PrayerState>,
    language: AppLanguage
) {
    val state by prayerStateFlow.collectAsState()
    LaunchedEffect(state) {
        Log.d("STARTUP_TRACE", "HERO_STATE_CHANGED type=${state::class.simpleName}")
    }
    val prayerTimes = uiState.prayerTimes ?: PrayerTimes()
    SideEffect {
        val s = state
        val remaining = when (s) {
            is PrayerState.BeforePrayer -> s.remainingSeconds
            is PrayerState.AdhanPlaying -> s.elapsedSeconds
            is PrayerState.PostPrayer -> s.elapsedSeconds
        }
        val prayerName = when (s) {
            is PrayerState.BeforePrayer -> s.prayerName
            is PrayerState.AdhanPlaying -> s.prayerName
            is PrayerState.PostPrayer -> s.prayerName
        }
        val prayerTime = when (s) {
            is PrayerState.BeforePrayer -> s.prayerTime
            is PrayerState.AdhanPlaying -> ""
            is PrayerState.PostPrayer -> ""
        }
        Log.d("STARTUP_TRACE", "HERO_RECOMPOSE remainingSeconds=$remaining")
        android.util.Log.d("NAV_TRACE", "HERO_RECOMPOSE remaining=$remaining")
        DebugLogger.debug(LogCategory.PERFORMANCE, "HERO_RECOMPOSE — remainingSeconds=$remaining prayerName=$prayerName prayerTime=$prayerTime")
    }

    // ─── Compute progress = elapsed / totalInterval (pure UI, no new timer) ───
    // For BeforePrayer: elapsed = totalInterval - remaining
    //   totalInterval = gap between previous prayer and next prayer (in seconds)
    // For AdhanPlaying / PostPrayer: fills over the post-prayer window
    val progressFraction: Float = remember(state, prayerTimes) {
        computePrayerProgressFraction(state, prayerTimes)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 900),
        label = "prayerRingProgress"
    )

    val trackColor  = MaterialTheme.colorScheme.outlineVariant
    val arcColor    = MaterialTheme.colorScheme.primary
    val arcColorEnd = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
    ) {
        // Subtle ambient glow behind the circle side
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.CenterStart)
                .offset(x = (-40).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ── LEFT: prayer info ───────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                when (val s = state) {
                    is PrayerState.BeforePrayer -> {
                        // "الصلاة القادمة" label
                        Text(
                            text  = AppTranslation.translate("next_prayer", language),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        // Prayer name – large prominent
                        Text(
                            text  = s.prayerName,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.sp,
                                lineHeight = 44.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        val displayTime = when (s.prayerKey) {
                            "fajr" -> prayerTimes.fajr
                            "dhuhr" -> prayerTimes.dhuhr
                            "asr" -> prayerTimes.asr
                            "maghrib" -> prayerTimes.maghrib
                            "isha" -> prayerTimes.isha
                            else -> s.prayerTime
                        }
                        // Time + clock icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector  = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                            SideEffect {
                                Log.d("STARTUP_TRACE", "PRAYER_TIME_TEXT stateMachine=${s.prayerTime} computed=$displayTime")
                            }
                            Text(
                                text  = displayTime,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    is PrayerState.AdhanPlaying -> {
                        Text(
                            text  = AppTranslation.translate("next_prayer", language),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = s.prayerName,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.sp,
                                lineHeight = 44.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text  = "جاري الأذان",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    is PrayerState.PostPrayer -> {
                        Text(
                            text  = "مر على أذان",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = s.prayerName,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.sp,
                                lineHeight = 44.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text  = "في انتظار الصلاة القادمة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error (if any) below the left column
                uiState.error?.takeIf { it.isNotBlank() }?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // ── RIGHT: circular progress ring ──────────────────────────────
            PrayerCountdownRing(
                progress    = animatedProgress,
                state       = state,
                trackColor  = trackColor,
                arcColor    = arcColor,
                arcColorEnd = arcColorEnd
            )
        }
    }
}

/**
 * Computes the visual progress fraction (0f → 1f) for the ring.
 *
 * For [PrayerState.BeforePrayer]:
 *   progress = elapsed / totalInterval
 *   where totalInterval = gap from previous prayer → next prayer (seconds)
 *   and   elapsed       = totalInterval − remainingSeconds
 *
 * For [PrayerState.AdhanPlaying] / [PrayerState.PostPrayer]:
 *   progress fills over the post-prayer window (same as before).
 *
 * This is pure display logic — no timers, no business state changes.
 */
private fun computePrayerProgressFraction(state: PrayerState, times: PrayerTimes): Float {
    return when (state) {
        is PrayerState.BeforePrayer -> {
            val ordered = listOf(
                "fajr"    to parseLocalTime(times.fajr),
                "dhuhr"   to parseLocalTime(times.dhuhr),
                "asr"     to parseLocalTime(times.asr),
                "maghrib" to parseLocalTime(times.maghrib),
                "isha"    to parseLocalTime(times.isha)
            ).mapNotNull { (k, t) -> if (t != null) k to t else null }

            // Find the index of the next prayer (matching by prayerKey)
            val nextIdx = ordered.indexOfFirst { it.first == state.prayerKey }
            if (nextIdx < 0) return 0f

            val nextTimeSec  = ordered[nextIdx].second.toSecondOfDay().toLong()
            val prevTimeSec  = if (nextIdx == 0) {
                // Previous prayer was isha from "yesterday" → shift by -24h
                ordered.last().second.toSecondOfDay().toLong() - 86400L
            } else {
                ordered[nextIdx - 1].second.toSecondOfDay().toLong()
            }

            val totalInterval = (nextTimeSec - prevTimeSec).coerceAtLeast(1L)
            val elapsed = (totalInterval - state.remainingSeconds).coerceAtLeast(0L)
            (elapsed.toFloat() / totalInterval.toFloat()).coerceIn(0f, 1f)
        }

        is PrayerState.AdhanPlaying -> {
            val window = (PrayerStateMachine.postPrayerDurationMinutes * 60L).coerceAtLeast(1L)
            (state.elapsedSeconds.toFloat() / window.toFloat()).coerceIn(0f, 1f)
        }

        is PrayerState.PostPrayer -> {
            val window = (PrayerStateMachine.postPrayerDurationMinutes * 60L).coerceAtLeast(1L)
            (state.elapsedSeconds.toFloat() / window.toFloat()).coerceIn(0f, 1f)
        }
    }
}

/** Parses "HH:mm" safely; returns null on failure. */
private fun parseLocalTime(timeStr: String): java.time.LocalTime? = try {
    java.time.LocalTime.parse(timeStr.trim())
} catch (_: Exception) { null }

/**
 * A 360° circular progress ring.
 * The arc starts at the top (−90°) and sweeps clockwise as [progress] grows from 0 → 1.
 * The countdown / elapsed text sits centred inside the ring.
 * No business logic here — values come directly from [PrayerState].
 */
@Composable
private fun PrayerCountdownRing(
    progress:    Float,
    state:       PrayerState,
    trackColor:  Color,
    arcColor:    Color,
    arcColorEnd: Color
) {
    val ringSize    = 130.dp
    val strokeWidth = 9.dp
    val startAngle  = -90f          // top of circle
    val maxSweep    = 360f          // full circle

    val countdownText = when (val s = state) {
        is PrayerState.BeforePrayer -> formatRemainingDuration(s.remainingSeconds)
        is PrayerState.AdhanPlaying -> formatElapsedDuration(s.elapsedSeconds)
        is PrayerState.PostPrayer   -> formatElapsedDuration(s.elapsedSeconds)
    }
    val subLabel = when (state) {
        is PrayerState.BeforePrayer -> "متبقي"
        is PrayerState.AdhanPlaying -> "مضى"
        is PrayerState.PostPrayer   -> "مضى"
    }
    LaunchedEffect(state) {
        Log.d("STARTUP_TRACE", "COUNTDOWN_STATE_CHANGED remaining=${
            when (state) {
                is PrayerState.BeforePrayer -> state.remainingSeconds
                is PrayerState.AdhanPlaying -> state.elapsedSeconds
                is PrayerState.PostPrayer -> state.elapsedSeconds
            }
        }")
    }
    SideEffect {
        val remaining = when (val s = state) {
            is PrayerState.BeforePrayer -> s.remainingSeconds
            is PrayerState.AdhanPlaying -> s.elapsedSeconds
            is PrayerState.PostPrayer -> s.elapsedSeconds
        }
        Log.d("STARTUP_TRACE", "COUNTDOWN_RECOMPOSE remainingSeconds=$remaining")
        DebugLogger.debug(LogCategory.PERFORMANCE, "COUNTDOWN_RECOMPOSE — remainingSeconds=$remaining formatted=\"$countdownText\"")
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ringSize)
            .drawBehind {
                val strokePx  = strokeWidth.toPx()
                val halfStroke = strokePx / 2f
                val arcSize   = Size(size.width - strokePx, size.height - strokePx)
                val topLeft   = Offset(halfStroke, halfStroke)

                // Track ring (full circle, muted)
                drawArc(
                    color      = trackColor,
                    startAngle = startAngle,
                    sweepAngle = maxSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Progress arc — sweeps clockwise, from empty → full
                val sweep = maxSweep * progress
                if (sweep > 0.5f) {
                    drawArc(
                        brush      = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0.0f to arcColor,
                                0.5f to arcColorEnd,
                                1.0f to arcColor
                            ),
                            center = Offset(size.width / 2f, size.height / 2f)
                        ),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text  = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = countdownText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatElapsedDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}





@Composable
private fun LastReadTicket(
    lastReadPage: Int,
    lastReadSurah: String,
    lastReadVerse: Int,
    lastReadText: String,
    language: AppLanguage,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant ?: Color.LightGray, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        // Bookmark Ribbon
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp)
                .width(32.dp)
                .height(48.dp)
                .background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Icon(
                imageVector = Icons.Rounded.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 6.dp).size(20.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 72.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = AppTranslation.translate("last_read", language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (lastReadPage >= 1) {
                    Text(
                        text = listOfNotNull(
                            lastReadSurah.takeIf { it.isNotBlank() },
                            lastReadVerse.takeIf { it >= 1 }?.let { "الآية " + it }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "الصفحة " + lastReadPage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = AppTranslation.translate("no_read_yet", language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PrayerTimeline(
    prayerTimes: PrayerTimes,
    nextPrayerName: String,
    language: AppLanguage
) {
    SideEffect {
        val isPlaceholder = prayerTimes.fajr == "--:--" && prayerTimes.shuruq == "--:--" && prayerTimes.dhuhr == "--:--" && prayerTimes.asr == "--:--" && prayerTimes.maghrib == "--:--" && prayerTimes.isha == "--:--"
        Log.d("STARTUP_TRACE", "TIMELINE_RECOMPOSE isPlaceholder=$isPlaceholder")
        DebugLogger.debug(LogCategory.PERFORMANCE, "TIMELINE_RECOMPOSE — isPlaceholder=$isPlaceholder fajr=${prayerTimes.fajr} nextPrayerName=$nextPrayerName")
    }
    val prayers = listOf(
        AppTranslation.translate("fajr", language) to prayerTimes.fajr,
        AppTranslation.translate("shorooq", language) to prayerTimes.shuruq,
        AppTranslation.translate("dhuhr", language) to prayerTimes.dhuhr,
        AppTranslation.translate("asr", language) to prayerTimes.asr,
        AppTranslation.translate("maghrib", language) to prayerTimes.maghrib,
        AppTranslation.translate("isha", language) to prayerTimes.isha
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppTranslation.translate("prayer_times", language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // First row (3 prayers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                prayers.take(3).forEach { (name, time) ->
                    val isNext = name == nextPrayerName || AppTranslation.translate(name, language) == AppTranslation.translate(nextPrayerName, language)
                    PrayerTile(name, time, isNext, Modifier.weight(1f))
                }
            }
            // Second row (3 prayers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                prayers.drop(3).forEach { (name, time) ->
                    val isNext = name == nextPrayerName || AppTranslation.translate(name, language) == AppTranslation.translate(nextPrayerName, language)
                    PrayerTile(name, time, isNext, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PrayerTile(name: String, time: String, isNext: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isNext) MaterialTheme.colorScheme.primary else (MaterialTheme.colorScheme.outlineVariant ?: Color.LightGray),
                RoundedCornerShape(16.dp)
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}


@Composable
private fun PremiumServiceTile(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant ?: Color.LightGray, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun formatRemainingDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "00:00:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
