package com.example.quranapp.core.debug

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives the hidden Developer Mode activation gesture: a fixed number of
 * consecutive taps on the app version, reset when the taps pause too long.
 *
 * The counter is process-local; the enabled flag lives in [DeveloperMode].
 */
class DeveloperModeActivator(
    private val developerMode: DeveloperMode,
    private val scope: CoroutineScope,
    private val onFeedback: (String) -> Unit
) {
    private var tapCount = 0
    private var firstTapElapsedMs = 0L
    private var resetJob: Job? = null

    /** Records a tap on the version label. Safe to call repeatedly. */
    fun onVersionTapped() {
        if (developerMode.isEnabled) return
        resetJob?.cancel()

        if (tapCount == 0) {
            firstTapElapsedMs = SystemClock.elapsedRealtime()
        }
        tapCount++

        if (tapCount >= REQUIRED_TAPS) {
            val timeRequiredMs = SystemClock.elapsedRealtime() - firstTapElapsedMs
            activate(timeRequiredMs)
        } else {
            val remaining = REQUIRED_TAPS - tapCount
            onFeedback("Developer mode in $remaining tap" + if (remaining == 1) "" else "s" + "...")
            resetJob = scope.launch {
                delay(TAP_TIMEOUT_MS)
                resetCounter()
            }
        }
    }

    /** Cancels any pending reset and clears the counter. */
    fun reset() {
        resetJob?.cancel()
        resetJob = null
        tapCount = 0
        firstTapElapsedMs = 0L
    }

    private fun activate(timeRequiredMs: Long) {
        val finalCount = tapCount
        val elapsed = timeRequiredMs
        resetJob = null
        tapCount = 0
        firstTapElapsedMs = 0L

        developerMode.enable()
        DiagnosticsDashboard.recordWarning(
            prayerKey = "developer_mode",
            message = "Activated in ${finalCount} taps, ${elapsed}ms"
        )
        DebugLogger.info(
            LogCategory.APP,
            "Developer mode activation: taps=$finalCount timeRequiredMs=$elapsed"
        )
        onFeedback("Developer mode enabled.")
    }

    private fun resetCounter() {
        resetJob = null
        tapCount = 0
        firstTapElapsedMs = 0L
    }

    companion object {
        const val REQUIRED_TAPS = 7
        const val TAP_TIMEOUT_MS = 3_000L
    }
}
