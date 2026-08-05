package com.example.quranapp.data.resource

/**
 * Identity of the bundled (embedded) tafsir resource, matching the asset
 * `assets/tafsir/muyassar.db`. Bundled resources are always considered
 * installed by the catalog layer and can never be deleted.
 */
object TafsirBundled {
    const val ID = "muyassar"
    const val NAME = "التفسير الميسر"
    const val NAME_LATIN = "Tafsir Al-Muyassar"
    const val AUTHOR = "مجموعة من العلماء"
    const val VERSION = "1.0.0"
    const val LANGUAGE = "ar"
    const val SCHEMA_VERSION = 1
    const val MIN_APP_VERSION = "1.0"
    const val SIZE_BYTES = 2912256L
    const val SHA256 = "c59dcfdb9714f64c56610aabce22dab37bf7c82eed7ff1edc1782d48011ef1f4"
}
