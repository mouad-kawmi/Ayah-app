package com.example.quranapp.data.tafsir

/** Minimal identity of an installed tafsir resource used during verification. */
data class TafsirResourceMeta(
    val id: String,
    val sha256: String
) {
    companion object {
        val NONE = TafsirResourceMeta(id = "", sha256 = "")
    }
}
