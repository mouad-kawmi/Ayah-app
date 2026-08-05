package com.example.quranapp.core.debug

data class PipelineStage(
    val stage: PipelineStageType,
    val timestampMs: Long,
    val details: String = ""
)
