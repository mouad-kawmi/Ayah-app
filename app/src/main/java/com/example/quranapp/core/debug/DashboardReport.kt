package com.example.quranapp.core.debug

data class DashboardReport(
    val completedCount: Int,
    val failedCount: Int,
    val warnings: List<PipelineWarning>,
    val pipelines: Map<String, PrayerPipelineState>,
    val traceHistory: List<PipelineTrace>,
    val failureHistory: List<PipelineFailure>
)

data class PipelineTrace(
    val prayerKey: String,
    val traceId: String,
    val startedAtMs: Long,
    val totalDurationMs: Long,
    val isCompleted: Boolean,
    val isFailed: Boolean
)
