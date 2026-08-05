package com.example.quranapp.core.debug

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DiagnosticsDashboard {
    private const val MAX_TRACE_HISTORY = 100
    private const val MAX_FAILURE_HISTORY = 50
    private const val MAX_WARNINGS = 100
    private const val TRACE_ID_LENGTH = 8

    private val lock = ReentrantLock()

    private val pipelines = LinkedHashMap<String, PrayerPipelineState>()
    private val traceHistory = ArrayDeque<PipelineTrace>()
    private val failureHistory = ArrayDeque<PipelineFailure>()
    private val warnings = ArrayDeque<PipelineWarning>()
    private var completedCount = 0
    private var failedCount = 0

    fun generateTraceId(): String {
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return uuid.take(TRACE_ID_LENGTH).uppercase()
    }

    fun startNewTrace(prayerKey: String, traceId: String = generateTraceId()) {
        lock.withLock {
            val now = System.currentTimeMillis()
            pipelines[prayerKey] = PrayerPipelineState(
                prayerKey = prayerKey,
                traceId = traceId,
                startedAtMs = now
            )
        }
    }

    fun updateStage(
        prayerKey: String,
        traceId: String,
        stage: PipelineStageType,
        details: String = ""
    ) {
        lock.withLock {
            val current = pipelines[prayerKey] ?: return
            if (current.traceId != traceId) return
            val now = System.currentTimeMillis()
            val updated = current.copy(
                stages = current.stages + PipelineStage(stage = stage, timestampMs = now, details = details)
            )

            when (stage) {
                PipelineStageType.PIPELINE_COMPLETED -> {
                    val done = updated.copy(isActive = false, isCompleted = true, completedAtMs = now)
                    completedCount++
                    traceHistory.addLast(done.toTrace())
                    trimTraceHistory()
                    pipelines.remove(prayerKey)
                }

                PipelineStageType.PIPELINE_FAILED -> {
                    val failed = updated.copy(isActive = false, isFailed = true, completedAtMs = now)
                    failedCount++
                    recordFailureLocked(PipelineFailure(prayerKey, stage, details.ifBlank { "Pipeline failed" }))
                    traceHistory.addLast(failed.toTrace())
                    trimTraceHistory()
                    pipelines.remove(prayerKey)
                }

                else -> pipelines[prayerKey] = updated
            }
        }
    }

    fun recordFailure(
        prayerKey: String,
        traceId: String,
        stage: PipelineStageType,
        message: String
    ) {
        lock.withLock {
            val current = pipelines[prayerKey] ?: return
            if (current.traceId != traceId) return
            val now = System.currentTimeMillis()
            val failure = PipelineFailure(prayerKey, stage, message, now)
            val failed = current.copy(
                stages = current.stages + PipelineStage(stage = stage, timestampMs = now, details = message),
                isActive = false,
                isFailed = true,
                failure = failure,
                completedAtMs = now
            )
            failedCount++
            recordFailureLocked(failure)
            traceHistory.addLast(failed.toTrace())
            trimTraceHistory()
            pipelines.remove(prayerKey)
        }
    }

    fun recordWarning(prayerKey: String, message: String) {
        lock.withLock {
            warnings.addLast(PipelineWarning(prayerKey = prayerKey, message = message))
            trimWarnings()
        }
    }

    fun reportSnapshot(): DashboardReport {
        lock.withLock {
            return DashboardReport(
                completedCount = completedCount,
                failedCount = failedCount,
                warnings = warnings.toList(),
                pipelines = pipelines.toMap(),
                traceHistory = traceHistory.toList(),
                failureHistory = failureHistory.toList()
            )
        }
    }

    fun reset() {
        lock.withLock {
            pipelines.clear()
            traceHistory.clear()
            failureHistory.clear()
            warnings.clear()
            completedCount = 0
            failedCount = 0
        }
    }

    private fun recordFailureLocked(failure: PipelineFailure) {
        failureHistory.addLast(failure)
        if (failureHistory.size > MAX_FAILURE_HISTORY) {
            failureHistory.removeFirst()
        }
    }

    private fun trimTraceHistory() {
        while (traceHistory.size > MAX_TRACE_HISTORY) {
            traceHistory.removeFirst()
        }
    }

    private fun trimWarnings() {
        while (warnings.size > MAX_WARNINGS) {
            warnings.removeFirst()
        }
    }

    private fun PrayerPipelineState.toTrace(): PipelineTrace {
        return PipelineTrace(
            prayerKey = prayerKey,
            traceId = traceId,
            startedAtMs = startedAtMs,
            totalDurationMs = totalDurationMs,
            isCompleted = isCompleted,
            isFailed = isFailed
        )
    }
}
