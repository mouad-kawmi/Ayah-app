package com.example

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.debug.DebugLogger
import com.example.debug.LogCategory
import com.aistudio.quran.mwkpqz.BuildConfig
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

private const val PREFS_NAME = "prayer_settings"
private const val KEY_CURRENT_PRAYER_KEY = "psm_current_prayer_key"
private const val KEY_PRAYER_TIME_REACHED = "psm_prayer_time_reached"
private const val KEY_ADHAN_STARTED = "psm_adhan_started"
private const val KEY_STATE_TYPE = "psm_state_type"
private const val STATE_ADHAN_PLAYING = "adhan_playing"
private const val STATE_POST_PRAYER = "post_prayer"

object PrayerStateMachine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val _state = MutableStateFlow<PrayerState>(PrayerState.BeforePrayer(
        prayerName = "الفجر",
        prayerKey = "fajr",
        prayerTime = "--:--",
        remainingSeconds = 0L
    ))
    val state: StateFlow<PrayerState> = _state.asStateFlow()

    var prayerTimes: PrayerTimes = PrayerTimes()
        private set

    var postPrayerDurationMinutes: Long = 20L
        private set

    @Volatile private var adhanStartedAtMillis: Long = 0L
    @Volatile private var prayerTimeReachedAtMillis: Long = 0L
    @Volatile private var currentPrayerKey: String = ""

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun configure(prayerTimes: PrayerTimes, context: Context) {
        Log.d("STARTUP_TRACE", "CONFIGURE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CONFIGURE_START — prayerTimes=$prayerTimes")
        DebugLogger.debug(LogCategory.PRAYER, "configure() ENTER — prayerTimes=$prayerTimes date=${LocalDate.now()} time=${LocalTime.now()}")
        this.prayerTimes = prayerTimes
        if (appContext == null) appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        postPrayerDurationMinutes = prefs.getLong("post_prayer_duration_minutes", 20L).coerceIn(10L, 30L)
        val savedPrayerKey = prefs.getString(KEY_CURRENT_PRAYER_KEY, "") ?: ""
        val savedPrayerTimeReached = prefs.getLong(KEY_PRAYER_TIME_REACHED, 0L)
        val savedAdhanStarted = prefs.getLong(KEY_ADHAN_STARTED, 0L)
        val savedStateType = prefs.getString(KEY_STATE_TYPE, "") ?: ""

        if (savedPrayerKey.isNotEmpty() && savedPrayerTimeReached > 0L) {
            currentPrayerKey = savedPrayerKey
            prayerTimeReachedAtMillis = savedPrayerTimeReached
            adhanStartedAtMillis = savedAdhanStarted

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
                        elapsedSeconds = elapsedSeconds
                    )
                    recalculate()
                    DebugLogger.debug(LogCategory.PRAYER, "configure() EXIT — restored AdhanPlaying state key=$savedPrayerKey")
                    Log.d("STARTUP_TRACE", "CONFIGURE_END")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "CONFIGURE_END")
                    return
                } else {
                    _state.value = PrayerState.PostPrayer(
                        prayerName = translatePrayerKey(savedPrayerKey),
                        prayerKey = savedPrayerKey,
                        elapsedSeconds = elapsedSeconds
                    )
                    recalculate()
                    DebugLogger.debug(LogCategory.PRAYER, "configure() EXIT — restored PostPrayer state key=$savedPrayerKey")
                    Log.d("STARTUP_TRACE", "CONFIGURE_END")
                    DebugLogger.debug(LogCategory.PERFORMANCE, "CONFIGURE_END")
                    return
                }
            }
        }

        recalculate()
        DebugLogger.debug(LogCategory.PRAYER, "configure() EXIT — fresh recalculate launched")
        Log.d("STARTUP_TRACE", "CONFIGURE_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "CONFIGURE_END")
    }

    fun updatePostPrayerDuration(minutes: Long, context: Context) {
        postPrayerDurationMinutes = minutes.coerceIn(10L, 30L)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("post_prayer_duration_minutes", postPrayerDurationMinutes)
            .apply()
    }

    fun markAdhanStarted(prayerKey: String) {
        val now = SystemClock.elapsedRealtime()
        adhanStartedAtMillis = now
        prayerTimeReachedAtMillis = now
        currentPrayerKey = prayerKey
        _state.value = PrayerState.AdhanPlaying(
            prayerName = translatePrayerKey(prayerKey),
            prayerKey = prayerKey,
            elapsedSeconds = 0L
        )
        persistState(STATE_ADHAN_PLAYING)
        if (BuildConfig.DEBUG) {
            Log.d("STARTUP_TRACE", "ADHAN_PLAYING_STATE prayerKey=$prayerKey")
            DebugLogger.debug(LogCategory.PERFORMANCE, "[PRAYER_STATE] AdhanPlaying($prayerKey)")
        }
    }

    fun markAdhanEnded() {
        val current = _state.value
        if (current is PrayerState.AdhanPlaying) {
            _state.value = PrayerState.PostPrayer(
                prayerName = current.prayerName,
                prayerKey = current.prayerKey,
                elapsedSeconds = current.elapsedSeconds
            )
            persistState(STATE_POST_PRAYER)
            if (BuildConfig.DEBUG) {
                Log.d("STARTUP_TRACE", "POST_PRAYER_STATE prayerName=${current.prayerName}")
                DebugLogger.debug(LogCategory.PERFORMANCE, "[PRAYER_STATE] PostPrayer(${current.prayerName})")
            }
        }
    }

    fun recalculate() {
        Log.d("STARTUP_TRACE", "RECALCULATE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "RECALCULATE_START")
        DebugLogger.debug(LogCategory.PRAYER, "recalculate() ENTER — prayerTimes=$prayerTimes")
        tickJob?.cancel()
        evaluate()
        tickJob = scope.launch {
            while (true) {
                delay(1_000)
                evaluate()
            }
        }
        DebugLogger.debug(LogCategory.PRAYER, "recalculate() EXIT — new tickJob=${tickJob?.hashCode()}")
        Log.d("STARTUP_TRACE", "RECALCULATE_END")
        DebugLogger.debug(LogCategory.PERFORMANCE, "RECALCULATE_END")
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun evaluate() {
        val _evalPT = prayerTimes
        val _evalNow = LocalTime.now()
        val evalStartMs = System.currentTimeMillis()
        Log.d("STARTUP_TRACE", "EVALUATE_START")
        DebugLogger.debug(LogCategory.PERFORMANCE, "EVALUATE_START")
        DebugLogger.debug(LogCategory.PRAYER, "evaluate() ENTER — prayerTimes=$_evalPT now=$_evalNow")
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
        DebugLogger.debug(LogCategory.PRAYER, "evaluate() — filtered.size=${filtered.size}")

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
                    remainingSeconds = remaining
                )
                DebugLogger.debug(LogCategory.PRAYER, "evaluate() EXIT — nextIdx=-1 fajrKey=$fajrKey remaining=$remaining")
                Log.d("STARTUP_TRACE", "EVALUATE_END nextPrayer=$fajrKey")
                if (BuildConfig.DEBUG) {
                    DebugLogger.debug(LogCategory.PERFORMANCE, "EVALUATE_END — nextPrayer=$fajrKey remainingSeconds=$remaining totalMs=${System.currentTimeMillis() - evalStartMs}")
                }
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
                    remainingSeconds = seconds.coerceAtLeast(0L)
                )
                DebugLogger.debug(LogCategory.PRAYER, "evaluate() EXIT — currentIdx=-1 nextKey=$nextKey remaining=${seconds.coerceAtLeast(0L)}")
                Log.d("STARTUP_TRACE", "EVALUATE_END nextPrayer=$nextKey")
                if (BuildConfig.DEBUG) {
                    DebugLogger.debug(LogCategory.PERFORMANCE, "EVALUATE_END — nextPrayer=$nextKey remainingSeconds=${seconds.coerceAtLeast(0L)} totalMs=${System.currentTimeMillis() - evalStartMs}")
                }
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
                            elapsedSeconds = elapsedSeconds
                        )
                        DebugLogger.debug(LogCategory.PRAYER, "evaluate() EXIT — AdhanPlaying continuing elapsed=$elapsedSeconds key=$currentKey")
                        Log.d("STARTUP_TRACE", "EVALUATE_END nextPrayer=$currentKey")
                        if (BuildConfig.DEBUG) {
                            DebugLogger.debug(LogCategory.PERFORMANCE, "EVALUATE_END — nextPrayer=$currentKey remainingSeconds=$elapsedSeconds totalMs=${System.currentTimeMillis() - evalStartMs}")
                        }
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
                        elapsedSeconds = elapsedSeconds
                    )
                    DebugLogger.debug(LogCategory.PRAYER, "evaluate() EXIT — PostPrayer continuing elapsed=$elapsedSeconds key=$currentKey")
                    Log.d("STARTUP_TRACE", "EVALUATE_END nextPrayer=$currentKey")
                    if (BuildConfig.DEBUG) {
                        DebugLogger.debug(LogCategory.PERFORMANCE, "EVALUATE_END — nextPrayer=$currentKey remainingSeconds=$elapsedSeconds totalMs=${System.currentTimeMillis() - evalStartMs}")
                    }
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
                remainingSeconds = seconds.coerceAtLeast(0L)
            )
            DebugLogger.debug(LogCategory.PRAYER, "evaluate() EXIT — default nextKey=$nextKey remaining=${seconds.coerceAtLeast(0L)}")
            Log.d("STARTUP_TRACE", "EVALUATE_END nextPrayer=$nextKey")
            if (BuildConfig.DEBUG) {
                DebugLogger.debug(LogCategory.PERFORMANCE, "EVALUATE_END — nextPrayer=$nextKey remainingSeconds=${seconds.coerceAtLeast(0L)} totalMs=${System.currentTimeMillis() - evalStartMs}")
            }
        } catch (e: Exception) {
            DebugLogger.error(LogCategory.PRAYER, "evaluate() EXCEPTION — prayerTimes=$_evalPT filtered.size=${filtered.size} orderedPairs=$orderedPairs now=$_evalNow", e)
            Log.d("STARTUP_TRACE", "EVALUATE_END exception=true")
            if (BuildConfig.DEBUG) {
                DebugLogger.debug(LogCategory.PERFORMANCE, "EVALUATE_END — exception=true totalMs=${System.currentTimeMillis() - evalStartMs}")
            }
            throw e
        }
    }

    fun skipToNextPrayer() {
        currentPrayerKey = ""
        adhanStartedAtMillis = 0L
        prayerTimeReachedAtMillis = 0L
        clearPersistedState()
        recalculate()
    }

    // ── Persistence ────────────────────────────────────────────

    private fun persistState(stateType: String) {
        try {
            val ctx = appContext
            if (ctx != null) {
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CURRENT_PRAYER_KEY, currentPrayerKey)
                    .putLong(KEY_PRAYER_TIME_REACHED, prayerTimeReachedAtMillis)
                    .putLong(KEY_ADHAN_STARTED, adhanStartedAtMillis)
                    .putString(KEY_STATE_TYPE, stateType)
                    .commit()
            }
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
                .commit()
        } catch (_: Exception) {
        }
    }

    // ── Internal helpers ──────────────────────────────────────

    private fun translatePrayerKey(key: String): String {
        return when (key) {
            "fajr" -> "الفجر"
            "dhuhr" -> "الظهر"
            "asr" -> "العصر"
            "maghrib" -> "المغرب"
            "isha" -> "العشاء"
            else -> key
        }
    }

    private fun parseTimeSafely(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr.replace(" ", ""))
        } catch (e: Exception) {
            null
        }
    }
}