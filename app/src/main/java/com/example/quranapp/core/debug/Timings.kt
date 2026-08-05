package com.example.quranapp.core.debug

/**
 * Lightweight, optional performance instrumentation.
 *
 * Measures the wall-clock execution time of a block and logs it on the async
 * [DebugLogger] writer under the PERFORMANCE category. Only used at low-frequency
 * diagnostic points (repository load, widget render, sync fetch, location resolve);
 * it must never be placed inside per-second or hot paths.
 */
object Timings {
    internal inline fun <T> measure(label: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - start) / 1_000_000L
        DebugLogger.info(
            LogCategory.PERFORMANCE,
            Instrumentation.line("perf", Instrumentation.NO_TRACE, null, "$label in $ms ms")
        )
        return result
    }
}
