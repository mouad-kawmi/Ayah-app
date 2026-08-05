package com.example.quranapp.core.debug

data class PipelineWarning(
    val prayerKey: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)
