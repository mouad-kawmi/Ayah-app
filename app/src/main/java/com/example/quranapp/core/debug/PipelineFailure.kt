package com.example.quranapp.core.debug

data class PipelineFailure(
    val prayerKey: String,
    val stage: PipelineStageType,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)
