package com.example.quranapp.data.tafsir

import android.content.Context
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.Instrumentation
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.core.utils.QuranPreferences
import com.example.quranapp.data.resource.ResourceFileStore
import com.example.quranapp.data.resource.ResourceType

/**
 * Persisted tafsir selection (architecture §3.2.7).
 *
 * The selection is validated against what is actually installed. The app can
 * never be left without a valid tafsir: when the selected id is unknown or no
 * longer installed, the selection falls back to the bundled Muyassar.
 */
class TafsirSelectionStore(private val context: Context) {

    private val fileStore by lazy { ResourceFileStore.getInstance(context) }

    fun selectedTafsirId(): String {
        val saved = QuranPreferences.getSelectedTafsirId(context)
        if (saved == TafsirReader.BUNDLED_TAFSIR_ID) return saved
        if (saved.isNotBlank() && fileStore.resourceExists(ResourceType.TAFSIR, saved)) return saved
        if (saved.isNotBlank()) {
            DebugLogger.warning(
                LogCategory.TAFSIR,
                Instrumentation.line("selection", Instrumentation.NO_TRACE, null, "Selected tafsir '$saved' not installed — falling back to ${TafsirReader.BUNDLED_TAFSIR_ID}")
            )
        }
        selectTafsir(TafsirReader.BUNDLED_TAFSIR_ID)
        return TafsirReader.BUNDLED_TAFSIR_ID
    }

    fun selectTafsir(tafsirId: String) {
        val resolved = if (tafsirId == TafsirReader.BUNDLED_TAFSIR_ID ||
            fileStore.resourceExists(ResourceType.TAFSIR, tafsirId)
        ) tafsirId else TafsirReader.BUNDLED_TAFSIR_ID
        if (resolved != tafsirId) {
            DebugLogger.warning(
                LogCategory.TAFSIR,
                Instrumentation.line("selection", Instrumentation.NO_TRACE, null, "Rejected selection '$tafsirId' (not installed) — kept ${TafsirReader.BUNDLED_TAFSIR_ID}")
            )
        }
        QuranPreferences.saveSelectedTafsirId(context, resolved)
    }
}
