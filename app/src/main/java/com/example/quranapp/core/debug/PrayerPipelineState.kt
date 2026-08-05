package com.example.quranapp.core.debug

data class PrayerPipelineState(
    val prayerKey: String,
    val traceId: String,
    val startedAtMs: Long,
    val stages: List<PipelineStage> = emptyList(),
    val isActive: Boolean = true,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val failure: PipelineFailure? = null,
    val completedAtMs: Long? = null
) {
    val totalDurationMs: Long
        get() = (completedAtMs ?: System.currentTimeMillis()) - startedAtMs
}
