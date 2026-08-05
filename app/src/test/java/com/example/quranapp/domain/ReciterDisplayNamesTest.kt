package com.example.quranapp.domain

import com.example.quranapp.domain.model.Reciter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that reciter names resolve to Arabic everywhere in the UI
 * (IDs and API names are never modified — only the displayed label).
 */
class ReciterDisplayNamesTest {

    @Test
    fun knownReciterByApiNameResolvesToArabic() {
        val reciter = Reciter(
            id = 97,
            name = "Yasser Ad Dussary",
            style = "Murattal",
            translatedName = "Yasser Ad Dussary"
        )
        assertEquals("ياسر الدوسري", ReciterDisplayNames.displayName(reciter))
    }

    @Test
    fun knownReciterByApiNameVariantResolvesToArabic() {
        val reciter = Reciter(
            id = 999,
            name = "Saud Al Shuraim",
            style = null,
            translatedName = null
        )
        assertEquals("سعود الشريم", ReciterDisplayNames.displayName(reciter))
    }

    @Test
    fun reciterMappedByIdEvenIfNameUnknown() {
        val reciter = Reciter(
            id = 7,
            name = "Mishari Rashid al-`Afasy",
            style = null,
            translatedName = null
        )
        assertEquals("مشاري راشد العفاسي", ReciterDisplayNames.displayName(reciter))
    }

    @Test
    fun arabicTranslatedNameFromApiIsPreferred() {
        // Future-proofing: if QDC ever serves an Arabic translated_name, use it.
        val reciter = Reciter(
            id = 12345,
            name = "Someone New",
            style = null,
            translatedName = "مقرئ جديد"
        )
        assertEquals("مقرئ جديد", ReciterDisplayNames.displayName(reciter))
    }

    @Test
    fun englishTranslatedNameFromApiIsIgnored() {
        // Today QDC serves translated_name in English; the local mapping wins.
        val reciter = Reciter(
            id = 10,
            name = "Sa'ud ash-Shuraim",
            style = null,
            translatedName = "Sa`ud ash-Shuraym"
        )
        assertEquals("سعود الشريم", ReciterDisplayNames.displayName(reciter))
    }

    @Test
    fun unknownReciterFallsBackToEnglishName() {
        val reciter = Reciter(
            id = 424242,
            name = "Totally New Qari",
            style = null,
            translatedName = null
        )
        assertEquals("Totally New Qari", ReciterDisplayNames.displayName(reciter))
    }

    @Test
    fun nullReciterReturnsNull() {
        assertEquals(null, ReciterDisplayNames.displayName(null))
    }

    @Test
    fun downloadEntryResolvedByIdAndStoredName() {
        assertEquals(
            "عبد الباسط عبد الصمد",
            ReciterDisplayNames.displayName(reciterId = 2, storedName = "AbdulBaset AbdulSamad")
        )
        assertEquals(
            "عبد الباسط عبد الصمد",
            ReciterDisplayNames.displayName(reciterId = 2, storedName = "AbdulBaset AbdulSamad")
        )
    }

    @Test
    fun downloadEntryWithUnknownNameStillResolvesById() {
        assertEquals(
            "محمد صديق المنشاوي",
            ReciterDisplayNames.displayName(reciterId = 168, storedName = "some unknown label")
        )
    }

    @Test
    fun downloadEntryAlreadyArabicIsKept() {
        assertEquals(
            "ماهر المعيقلي",
            ReciterDisplayNames.displayName(reciterId = -1, storedName = "ماهر المعيقلي")
        )
    }

    @Test
    fun unknownDownloadEntryFallsBackToStoredName() {
        assertEquals(
            "Unknown Qari",
            ReciterDisplayNames.displayName(reciterId = -1, storedName = "Unknown Qari")
        )
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val reciter = Reciter(
            id = -5,
            name = "MISHARI RASHID AL-`AFASY",
            style = null,
            translatedName = null
        )
        assertEquals("مشاري راشد العفاسي", ReciterDisplayNames.displayName(reciter))
    }

    @Test
    fun userSpecifiedExamplesAllResolve() {
        assertEquals("ياسر الدوسري", ReciterDisplayNames.displayName(97, "Yasser Ad Dussary"))
        assertEquals("مشاري راشد العفاسي", ReciterDisplayNames.displayName(7, "Mishari Rashid al-`Afasy"))
        assertEquals("ماهر المعيقلي", ReciterDisplayNames.displayName(-1, "Maher Al Muaiqly"))
        assertEquals("عبد الباسط عبد الصمد", ReciterDisplayNames.displayName(2, "AbdulBaset AbdulSamad"))
        assertEquals("سعود الشريم", ReciterDisplayNames.displayName(10, "Sa'ud ash-Shuraim"))
        assertEquals("عبد الرحمن السديس", ReciterDisplayNames.displayName(3, "Abdur-Rahman as-Sudais"))
    }
}
