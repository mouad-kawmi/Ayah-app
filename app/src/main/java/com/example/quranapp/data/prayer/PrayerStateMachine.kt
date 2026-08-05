package com.example.quranapp.data.prayer

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.core.debug.Timings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Display mode for a [PrayerState]: "remaining" (counting down to the next prayer)
 * or "elapsed" (time elapsed since the current prayer reached, during the post-prayer window).
 */
enum class PrayerDisplayMode {
    REMAINING,
    ELAPSED
}

/**
 * A single prayer card shown by every UI (Home, Widget, future screens).
 * Built from the shared [PrayerStateMachine.prayerTimes] plus the enabled preferences.
 */
data class PrayerCardInfo(
    val name: String,
    val time: LocalTime,
    val isEnabled: Boolean = true
)

sealed class PrayerState {
    val displayMode: PrayerDisplayMode
        get() = when (this) {
            is BeforePrayer -> PrayerDisplayMode.REMAINING
            is AdhanPlaying, is PostPrayer -> PrayerDisplayMode.ELAPSED
        }

    abstract val prayerName: String
    abstract val prayerKey: String
    abstract val prayerTime: String

    data class BeforePrayer(
        override val prayerName: String,
        override val prayerKey: String,
        override val prayerTime: String,
        val remainingSeconds: Long,
        val progress: Float
    ) : PrayerState()

    data class AdhanPlaying(
        override val prayerName: String,
        override val prayerKey: String,
        override val prayerTime: String,
        val elapsedSeconds: Long,
        val progress: Float
    ) : PrayerState()

    data class PostPrayer(
        override val prayerName: String,
        override val prayerKey: String,
        override val prayerTime: String,
        val elapsedSeconds: Long,
        val progress: Float
    ) : PrayerState()
}

/**
 * The single source of truth for prayer state:
 * current prayer, next prayer, countdown, post-prayer window and display mode.
 *
 * Every UI (Home, Widget, future screens) consumes [state] and [prayerCards] and
 * must never recompute the countdown itself.
 */
object PrayerStateMachine {
    private const val PREFS_NAME = "prayer_settings"
    private const val KEY_CURRENT_PRAYER_KEY = "psm_current_prayer_key"
    private const val KEY_PRAYER_TIME_REACHED = "psm_prayer_time_reached"
    private const val KEY_ADHAN_STARTED = "psm_adhan_started"
    private const val KEY_STATE_TYPE = "psm_state_type"
    private const val KEY_CITY_UNIQUE_ID = "psm_city_unique_id"
    private const val STATE_ADHAN_PLAYING = "adhan_playing"
    private const val STATE_POST_PRAYER = "post_prayer"

    // Progress arc windows (presentation only, derived from the shared countdown).
    private const val NEXT_PRAYER_PROGRESS_WINDOW_SECONDS = 4 * 3600L
    private const val AFTER_ISHA_PROGRESS_WINDOW_SECONDS = 8 * 3600L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val _state = MutableStateFlow<PrayerState>(
        PrayerState.BeforePrayer("الفجر", "fajr", "--:--", 0L, 0f)
    )
    val state: StateFlow<PrayerState> = _state.asStateFlow()

    private val _prayerCards = MutableStateFlow<List<PrayerCardInfo>>(emptyList())
    val prayerCards: StateFlow<List<PrayerCardInfo>> = _prayerCards.asStateFlow()

    var prayerTimes: PrayerTimes = PrayerTimes()
        private set

    var postPrayerDurationMinutes: Long = 30L
        private set

    @Volatile private var adhanStartedAtMillis: Long = 0L
    @Volatile private var prayerTimeReachedAtMillis: Long = 0L
    @Volatile private var currentPrayerKey: String = ""
    @Volatile private var configuredCityUniqueId: Int = Int.MIN_VALUE
    @Volatile private var configuredDate: LocalDate? = null
    @Volatile private var repository: PrayerTimesRepository? = null

    private var appContext: Context? = null

    private val PRAYER_KEY_BY_NAME = mapOf(
        "الفجر" to "fajr",
        "الظهر" to "dhuhr",
        "العصر" to "asr",
        "المغرب" to "maghrib",
        "العشاء" to "isha"
    )

    fun prayerKeyForName(name: String): String? = PRAYER_KEY_BY_NAME[name]

    fun init(context: Context) {
        appContext = context.applicationContext
        DebugLogger.logOnce(
            LogCategory.PRAYER,
            "state-machine-init",
            Instrumentation.line("state-machine", Instrumentation.NO_TRACE, null, "State machine initialized")
        )
    }

    /**
     * Loads today's prayer times through the shared repository pipeline (same fallback
     * chain used everywhere) and configures the state machine from it.
     */
    fun loadToday(context: Context): PrayerTimes? {
        init(context)
        val times = todayTimesFromSharedPipeline(context) ?: return null
        configure(times, context)
        return times
    }

    /**
     * Ensures the state machine holds today's data (loading it through the shared
     * pipeline when needed, e.g. after a process restart) and returns the current
     * prayer times when available.
     */
    fun ensureConfigured(context: Context): PrayerTimes? {
        init(context)
        if (configuredDate != LocalDate.now()) {
            loadToday(context)
        }
        return prayerTimes.takeIf { it.isComplete() }
    }

    fun refreshPrayerCards(context: Context) {
        _prayerCards.value = buildPrayerCards(context)
    }

    fun configure(prayerTimes: PrayerTimes, context: Context) {
        this.prayerTimes = prayerTimes
        configuredDate = LocalDate.now()
        if (appContext == null) appContext = context.applicationContext
        refreshPrayerCards(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        postPrayerDurationMinutes = prefs.getLong("post_prayer_duration_minutes", 30L).coerceIn(10L, 30L)

        val cityUniqueId = PrayerTimesCacheStore.getCachedCityUniqueId(context) ?: -1
        val cityName = PrayerTimesCacheStore.getCachedCityName(context)

        // Restore persisted adhan/post-prayer state ONLY when it was recorded for the
        // same city; a different city means the persisted state is stale and must be dropped.
        val savedPrayerKey = prefs.getString(KEY_CURRENT_PRAYER_KEY, "") ?: ""
        val savedPrayerTimeReached = prefs.getLong(KEY_PRAYER_TIME_REACHED, 0L)
        val savedAdhanStarted = prefs.getLong(KEY_ADHAN_STARTED, 0L)
        val savedStateType = prefs.getString(KEY_STATE_TYPE, "") ?: ""
        val savedCityId = prefs.getInt(KEY_CITY_UNIQUE_ID, Int.MIN_VALUE)

        val canRestore = savedPrayerKey.isNotEmpty() && savedPrayerTimeReached > 0L && savedCityId == cityUniqueId
        if (!canRestore && savedPrayerKey.isNotEmpty()) {
            clearPersistedState(prefs)
        }

        if (canRestore) {
            currentPrayerKey = savedPrayerKey
            prayerTimeReachedAtMillis = savedPrayerTimeReached
            adhanStartedAtMillis = savedAdhanStarted
            configuredCityUniqueId = cityUniqueId

            val nowElapsedMs = SystemClock.elapsedRealtime()
            if (nowElapsedMs < savedPrayerTimeReached) {
                clearPersistedState(prefs)
                currentPrayerKey = ""
                prayerTimeReachedAtMillis = 0L
                adhanStartedAtMillis = 0L
            } else {
                val elapsedSeconds = (nowElapsedMs - savedPrayerTimeReached) / 1000
                val postDurationSec = postPrayerDurationMinutes * 60L

                if (elapsedSeconds >= postDurationSec) {
                    clearPersistedState(prefs)
                    currentPrayerKey = ""
                    prayerTimeReachedAtMillis = 0L
                    adhanStartedAtMillis = 0L
                } else if (savedStateType == STATE_ADHAN_PLAYING) {
                    _state.value = PrayerState.AdhanPlaying(
                        prayerName = translatePrayerKey(savedPrayerKey),
                        prayerKey = savedPrayerKey,
                        prayerTime = timeTextForPrayerKey(savedPrayerKey),
                        elapsedSeconds = elapsedSeconds,
                        progress = elapsedProgress(elapsedSeconds)
                    )
                    DebugLogger.info(
                        LogCategory.PRAYER,
                        Instrumentation.line(savedPrayerKey, Instrumentation.NO_TRACE, null, "Restored state -> AdhanPlaying elapsed=${elapsedSeconds}s")
                    )
                    emitSnapshot("", cityName)
                    recalculate()
                    return
                } else {
                    _state.value = PrayerState.PostPrayer(
                        prayerName = translatePrayerKey(savedPrayerKey),
                        prayerKey = savedPrayerKey,
                        prayerTime = timeTextForPrayerKey(savedPrayerKey),
                        elapsedSeconds = elapsedSeconds,
                        progress = elapsedProgress(elapsedSeconds)
                    )
                    DebugLogger.info(
                        LogCategory.PRAYER,
                        Instrumentation.line(savedPrayerKey, Instrumentation.NO_TRACE, null, "Restored state -> PostPrayer elapsed=${elapsedSeconds}s")
                    )
                    emitSnapshot("", cityName)
                    recalculate()
                    return
                }
            }
        }

        // In-session city change: discard any in-memory prayer state from the previous city.
        // (Int.MIN_VALUE means this is the first configuration in the process, not a switch.)
        if (configuredCityUniqueId != Int.MIN_VALUE && configuredCityUniqueId != cityUniqueId) {
            Log.i("PRAYER_STATE", "Active city changed (${configuredCityUniqueId} -> $cityUniqueId) — resetting prayer state")
            DebugLogger.info(
                LogCategory.PRAYER,
                Instrumentation.line("state-machine", Instrumentation.NO_TRACE, null, "Active city changed (${configuredCityUniqueId} -> $cityUniqueId) — resetting prayer state")
            )
            currentPrayerKey = ""
            adhanStartedAtMillis = 0L
            prayerTimeReachedAtMillis = 0L
        }
        configuredCityUniqueId = cityUniqueId

        recalculate()
        emitSnapshot("", cityName)
    }

    fun markAdhanStarted(prayerKey: String, traceId: String = "", city: String? = null) {
        val now = SystemClock.elapsedRealtime()
        adhanStartedAtMillis = now
        prayerTimeReachedAtMillis = now
        currentPrayerKey = prayerKey
        _state.value = PrayerState.AdhanPlaying(
            prayerName = translatePrayerKey(prayerKey),
            prayerKey = prayerKey,
            prayerTime = timeTextForPrayerKey(prayerKey),
            elapsedSeconds = 0L,
            progress = 0f
        )
        persistState(STATE_ADHAN_PLAYING)
        DebugLogger.info(
            LogCategory.PRAYER,
            Instrumentation.line(prayerKey, traceId, city, "State -> AdhanPlaying")
        )
        emitSnapshot(traceId, city)
    }

    fun markAdhanEnded(traceId: String = "", city: String? = null) {
        val current = _state.value
        if (current is PrayerState.AdhanPlaying) {
            _state.value = PrayerState.PostPrayer(
                prayerName = current.prayerName,
                prayerKey = current.prayerKey,
                prayerTime = current.prayerTime,
                elapsedSeconds = current.elapsedSeconds,
                progress = elapsedProgress(current.elapsedSeconds)
            )
            persistState(STATE_POST_PRAYER)
            DebugLogger.info(
                LogCategory.PRAYER,
                Instrumentation.line(current.prayerKey, traceId, city, "State -> PostPrayer")
            )
            emitSnapshot(traceId, city)
        }
    }

    fun recalculate() {
        tickJob?.cancel()
        evaluate()
        tickJob = scope.launch {
            while (true) {
                delay(1_000)
                evaluate()
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun evaluate() {
        val ctx = appContext
        if (ctx != null && configuredDate != LocalDate.now()) {
            reloadTodayTimesQuietly(ctx)
        }

        val previous = _state.value

        val _evalPT = prayerTimes
        val _evalNow = LocalTime.now()
        val now = _evalNow
        val times = _evalPT

        val orderedPairs = listOf(
            "fajr" to parseTimeSafely(times.fajr),
            "dhuhr" to parseTimeSafely(times.dhuhr),
            "asr" to parseTimeSafely(times.asr),
            "maghrib" to parseTimeSafely(times.maghrib),
            "isha" to parseTimeSafely(times.isha)
        )
        val filtered = orderedPairs.mapNotNull { (key, time) -> if (time != null) key to time else null }
        if (filtered.isEmpty()) return

        try {
            var currentIdx = -1
            var nextIdx = -1
            for (i in filtered.indices) {
                val time = filtered[i].second
                if (now.isBefore(time)) {
                    nextIdx = i
                    break
                } else {
                    currentIdx = i
                }
            }

            val nowElapsedMs = SystemClock.elapsedRealtime()

            if (nextIdx == -1) {
                val ishaKey = filtered.last().first
                val ishaTime = filtered.last().second

                if (currentPrayerKey.isNotEmpty() && ishaKey == currentPrayerKey && prayerTimeReachedAtMillis > 0) {
                    val elapsedSeconds = if (nowElapsedMs > prayerTimeReachedAtMillis) {
                        (nowElapsedMs - prayerTimeReachedAtMillis) / 1000
                    } else 0L
                    val postDurationSec = postPrayerDurationMinutes * 60L

                    if (_state.value is PrayerState.AdhanPlaying) {
                        if (elapsedSeconds < postDurationSec) {
                            _state.value = PrayerState.AdhanPlaying(
                                prayerName = translatePrayerKey(ishaKey),
                                prayerKey = ishaKey,
                                prayerTime = ishaTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                elapsedSeconds = elapsedSeconds,
                                progress = elapsedProgress(elapsedSeconds)
                            )
                            emitSnapshotIfChanged(previous)
                            return
                        }
                        currentPrayerKey = ""
                        adhanStartedAtMillis = 0L
                        prayerTimeReachedAtMillis = 0L
                        clearPersistedState()
                    }

                    if (elapsedSeconds < postDurationSec) {
                        _state.value = PrayerState.PostPrayer(
                            prayerName = translatePrayerKey(ishaKey),
                            prayerKey = ishaKey,
                            prayerTime = ishaTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            elapsedSeconds = elapsedSeconds,
                            progress = elapsedProgress(elapsedSeconds)
                        )
                        emitSnapshotIfChanged(previous)
                        return
                    }
                    currentPrayerKey = ""
                    adhanStartedAtMillis = 0L
                    prayerTimeReachedAtMillis = 0L
                    clearPersistedState()
                }

                val fajrKey = filtered.first().first
                val fajrTime = filtered.first().second
                val secondsUntilMidnight = 86400L - now.toSecondOfDay()
                val remaining = secondsUntilMidnight + fajrTime.toSecondOfDay()
                if (currentPrayerKey.isNotEmpty()) {
                    clearPersistedState()
                    currentPrayerKey = ""
                }
                _state.value = PrayerState.BeforePrayer(
                    prayerName = translatePrayerKey(fajrKey),
                    prayerKey = fajrKey,
                    prayerTime = fajrTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    remainingSeconds = remaining,
                    progress = remainingProgress(remaining, AFTER_ISHA_PROGRESS_WINDOW_SECONDS)
                )
                emitSnapshotIfChanged(previous)
                return
            }

            val nextKey = filtered[nextIdx].first
            val nextTime = filtered[nextIdx].second

            if (currentIdx == -1) {
                val seconds = ChronoUnit.SECONDS.between(now, nextTime)
                if (currentPrayerKey.isNotEmpty()) {
                    clearPersistedState()
                    currentPrayerKey = ""
                }
                _state.value = PrayerState.BeforePrayer(
                    prayerName = translatePrayerKey(nextKey),
                    prayerKey = nextKey,
                    prayerTime = nextTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    remainingSeconds = seconds.coerceAtLeast(0L),
                    progress = remainingProgress(seconds.coerceAtLeast(0L), NEXT_PRAYER_PROGRESS_WINDOW_SECONDS)
                )
                emitSnapshotIfChanged(previous)
                return
            }

            val currentKey = filtered[currentIdx].first
            val currentTime = filtered[currentIdx].second

            if (currentKey == currentPrayerKey && prayerTimeReachedAtMillis > 0) {
                val elapsedSeconds = if (nowElapsedMs > prayerTimeReachedAtMillis) {
                    (nowElapsedMs - prayerTimeReachedAtMillis) / 1000
                } else 0L

                if (_state.value is PrayerState.AdhanPlaying) {
                    val postDurationSec = postPrayerDurationMinutes * 60L
                    if (elapsedSeconds < postDurationSec) {
                        _state.value = PrayerState.AdhanPlaying(
                            prayerName = translatePrayerKey(currentKey),
                            prayerKey = currentKey,
                            prayerTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            elapsedSeconds = elapsedSeconds,
                            progress = elapsedProgress(elapsedSeconds)
                        )
                        emitSnapshotIfChanged(previous)
                        return
                    }
                    currentPrayerKey = ""
                    adhanStartedAtMillis = 0L
                    prayerTimeReachedAtMillis = 0L
                    clearPersistedState()
                }

                val postDurationSec = postPrayerDurationMinutes * 60L
                if (elapsedSeconds < postDurationSec) {
                    _state.value = PrayerState.PostPrayer(
                        prayerName = translatePrayerKey(currentKey),
                        prayerKey = currentKey,
                        prayerTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        elapsedSeconds = elapsedSeconds,
                        progress = elapsedProgress(elapsedSeconds)
                    )
                    emitSnapshotIfChanged(previous)
                    return
                } else {
                    currentPrayerKey = ""
                    adhanStartedAtMillis = 0L
                    prayerTimeReachedAtMillis = 0L
                    clearPersistedState()
                }
            }

            if (currentKey != currentPrayerKey) {
                if (currentPrayerKey.isNotEmpty()) {
                    clearPersistedState()
                }
                currentPrayerKey = ""
                adhanStartedAtMillis = 0L
                prayerTimeReachedAtMillis = 0L
            }

            val seconds = ChronoUnit.SECONDS.between(now, nextTime)
            _state.value = PrayerState.BeforePrayer(
                prayerName = translatePrayerKey(nextKey),
                prayerKey = nextKey,
                prayerTime = nextTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                remainingSeconds = seconds.coerceAtLeast(0L),
                progress = remainingProgress(seconds.coerceAtLeast(0L), NEXT_PRAYER_PROGRESS_WINDOW_SECONDS)
            )
            emitSnapshotIfChanged(previous)
        } catch (e: Exception) {
            Log.w("PRAYER_STATE", "evaluate exception", e)
        }
    }

    fun skipToNextPrayer() {
        currentPrayerKey = ""
        adhanStartedAtMillis = 0L
        prayerTimeReachedAtMillis = 0L
        clearPersistedState()
        recalculate()
        DebugLogger.info(
            LogCategory.PRAYER,
            Instrumentation.line("state-machine", Instrumentation.NO_TRACE, null, "Skipped to next prayer")
        )
    }

    // --- Shared data pipeline ---

    private fun todayTimesFromSharedPipeline(context: Context): PrayerTimes? {
        val lat = getSavedLat(context)
        val lon = getSavedLon(context)
        val cityName = PrayerTimesCacheStore.getCachedCityName(context) ?: ""
        return Timings.measure("Prayer repository load") {
            repository(context).getCachedPrayerTimesForToday(lat, lon, cityName, context)
        }
    }

    private fun repository(context: Context): PrayerTimesRepository {
        repository?.let { return it }
        val built = PrayerTimesRepository(
            context,
            ProviderSelector(HabousProvider(), AlAdhanProvider()),
            CountryDetector(context)
        )
        repository = built
        return built
    }

    private fun getSavedLat(context: Context): Double {
        val lat = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getFloat("last_lat", 0f).toDouble()
        return if (lat == 0.0) 33.5731 else lat
    }

    private fun getSavedLon(context: Context): Double {
        val lon = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getFloat("last_lon", 0f).toDouble()
        return if (lon == 0.0) -7.5898 else lon
    }

    private fun reloadTodayTimesQuietly(context: Context) {
        val times = todayTimesFromSharedPipeline(context) ?: return
        this.prayerTimes = times
        configuredDate = LocalDate.now()
        refreshPrayerCards(context)
        currentPrayerKey = ""
        adhanStartedAtMillis = 0L
        prayerTimeReachedAtMillis = 0L
        clearPersistedState()
    }

    // --- Display helpers ---

    private fun buildPrayerCards(context: Context): List<PrayerCardInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return listOf(
            PrayerCardInfo("الفجر", parseTime(prayerTimes.fajr), prefs.getBoolean("enable_fajr", true)),
            PrayerCardInfo("الشروق", parseTime(prayerTimes.shuruq), isEnabled = false),
            PrayerCardInfo("الظهر", parseTime(prayerTimes.dhuhr), prefs.getBoolean("enable_dhuhr", true)),
            PrayerCardInfo("العصر", parseTime(prayerTimes.asr), prefs.getBoolean("enable_asr", true)),
            PrayerCardInfo("المغرب", parseTime(prayerTimes.maghrib), prefs.getBoolean("enable_maghrib", true)),
            PrayerCardInfo("العشاء", parseTime(prayerTimes.isha), prefs.getBoolean("enable_isha", true))
        )
    }

    private fun parseTime(time: String): LocalTime {
        return try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            LocalTime.of(0, 0)
        }
    }

    private fun PrayerTimes.isComplete(): Boolean =
        listOf(fajr, dhuhr, asr, maghrib, isha).all { parseTimeSafely(it) != null }

    private fun remainingProgress(remainingSeconds: Long, windowSeconds: Long): Float {
        if (windowSeconds <= 0) return 0f
        val clamped = remainingSeconds.coerceIn(0L, windowSeconds)
        return ((windowSeconds - clamped).toFloat() / windowSeconds.toFloat()).coerceIn(0f, 1f)
    }

    private fun elapsedProgress(elapsedSeconds: Long): Float {
        val window = postPrayerDurationMinutes * 60L
        if (window <= 0) return 0f
        return (elapsedSeconds.toFloat() / window.toFloat()).coerceIn(0f, 1f)
    }

    private fun timeTextForPrayerKey(key: String): String = when (key) {
        "fajr" -> prayerTimes.fajr
        "dhuhr" -> prayerTimes.dhuhr
        "asr" -> prayerTimes.asr
        "maghrib" -> prayerTimes.maghrib
        "isha" -> prayerTimes.isha
        else -> "--:--"
    }

    // --- Snapshot logging (diagnostics only; no behavior change) ---

    /**
     * Logs a concise runtime snapshot of the visible prayer state. Emitted on
     * explicit transitions and on identity changes detected by [evaluate] so the
     * exported logs alone reveal State / Current / Next / Remaining-or-Elapsed /
     * City / Trace.
     */
    private fun emitSnapshot(traceId: String = "", city: String? = null) {
        val resolvedCity = city ?: currentCity()
        DebugLogger.info(
            LogCategory.PRAYER,
            Instrumentation.line(_state.value.prayerKey, traceId, resolvedCity, snapshotBody())
        )
    }

    private fun emitSnapshotIfChanged(previous: PrayerState) {
        val current = _state.value
        if (previous::class == current::class && previous.prayerKey == current.prayerKey) return
        emitSnapshot()
    }

    private fun snapshotBody(): String {
        val state = _state.value
        val (currentKey, nextKey) = currentAndNext()
        return buildString {
            append("Snapshot: State=").append(state::class.simpleName)
            append(" Current=").append(translatePrayerKey(currentKey))
            append(" Next=").append(translatePrayerKey(nextKey))
            when (state) {
                is PrayerState.BeforePrayer -> append(" Remaining=").append(Instrumentation.clock(state.remainingSeconds))
                is PrayerState.AdhanPlaying -> append(" Elapsed=").append(Instrumentation.clock(state.elapsedSeconds))
                is PrayerState.PostPrayer -> append(" Elapsed=").append(Instrumentation.clock(state.elapsedSeconds))
            }
        }
    }

    private fun currentAndNext(): Pair<String, String> {
        val now = LocalTime.now()
        val ordered = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        val times = listOf(
            parseTimeSafely(prayerTimes.fajr),
            parseTimeSafely(prayerTimes.dhuhr),
            parseTimeSafely(prayerTimes.asr),
            parseTimeSafely(prayerTimes.maghrib),
            parseTimeSafely(prayerTimes.isha)
        )
        var currentKey = "isha"
        var nextKey = "fajr"
        for (i in ordered.indices) {
            val t = times[i] ?: continue
            if (now.isBefore(t)) {
                nextKey = ordered[i]
                if (i > 0) currentKey = ordered[i - 1]
                break
            }
            currentKey = ordered[i]
        }
        return currentKey to nextKey
    }

    private fun currentCity(): String? =
        appContext?.let { runCatching { PrayerTimesCacheStore.getCachedCityName(it) }.getOrNull() }

    private fun persistState(stateType: String) {
        try {
            val ctx = appContext ?: return
            val cityId = PrayerTimesCacheStore.getCachedCityUniqueId(ctx) ?: -1
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CURRENT_PRAYER_KEY, currentPrayerKey)
                .putLong(KEY_PRAYER_TIME_REACHED, prayerTimeReachedAtMillis)
                .putLong(KEY_ADHAN_STARTED, adhanStartedAtMillis)
                .putString(KEY_STATE_TYPE, stateType)
                .putInt(KEY_CITY_UNIQUE_ID, cityId)
                .commit()
        } catch (_: Exception) {
        }
    }

    private fun clearPersistedState(prefs: android.content.SharedPreferences? = null) {
        try {
            val p = prefs ?: appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
            p.edit()
                .remove(KEY_CURRENT_PRAYER_KEY)
                .remove(KEY_PRAYER_TIME_REACHED)
                .remove(KEY_ADHAN_STARTED)
                .remove(KEY_STATE_TYPE)
                .remove(KEY_CITY_UNIQUE_ID)
                .commit()
        } catch (_: Exception) {
        }
    }

    private fun translatePrayerKey(key: String): String = when (key) {
        "fajr" -> "الفجر"
        "dhuhr" -> "الظهر"
        "asr" -> "العصر"
        "maghrib" -> "المغرب"
        "isha" -> "العشاء"
        else -> key
    }

    private fun parseTimeSafely(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr.replace(" ", ""))
        } catch (e: Exception) {
            null
        }
    }
}
