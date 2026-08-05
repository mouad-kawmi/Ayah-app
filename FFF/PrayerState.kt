package com.example

sealed class PrayerState {
    abstract val prayerName: String
    abstract val prayerKey: String

    data class BeforePrayer(
        override val prayerName: String,
        override val prayerKey: String,
        val prayerTime: String,
        val remainingSeconds: Long
    ) : PrayerState()

    data class AdhanPlaying(
        override val prayerName: String,
        override val prayerKey: String,
        val elapsedSeconds: Long
    ) : PrayerState()

    data class PostPrayer(
        override val prayerName: String,
        override val prayerKey: String,
        val elapsedSeconds: Long
    ) : PrayerState()
}