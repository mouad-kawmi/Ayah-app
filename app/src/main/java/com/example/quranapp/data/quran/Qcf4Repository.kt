package com.example.quranapp.data.quran

import android.content.Context
import com.example.quranapp.data.model.MushafPageMetadata
import com.example.quranapp.data.model.Page
import com.example.quranapp.domain.model.Surah
import kotlinx.serialization.json.*

class Qcf4Repository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private var chaptersCache: List<Surah>? = null
    private var fontMapCache: Map<String, String>? = null

    fun getSurahs(): List<Surah> {
        if (chaptersCache != null) return chaptersCache!!
        val inputStream = context.assets.open("index.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(jsonString).jsonObject
        val chapters = root["chapters"]?.jsonArray ?: return emptyList()
        
        chaptersCache = chapters.map { chapter ->
            val obj = chapter.jsonObject
            val pages = obj["pages"]!!.jsonArray
            Surah(
                id = obj["id"]!!.jsonPrimitive.int,
                nameArabic = obj["name_arabic"]!!.jsonPrimitive.content,
                nameTransliterated = obj["name"]!!.jsonPrimitive.content,
                numberOfAyahs = obj["verses_count"]!!.jsonPrimitive.int,
                revelationPlace = obj["revelation_place"]!!.jsonPrimitive.content,
                firstPage = pages[0].jsonPrimitive.int,
                lastPage = pages[1].jsonPrimitive.int
            )
        }
        return chaptersCache!!
    }

    fun getSurahById(surahId: Int): Surah? {
        return getSurahs().find { it.id == surahId }
    }

    fun getPage(pageNumber: Int): Page {
        val fileName = "pages/${pageNumber.toString().padStart(3, '0')}.json"
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val element = json.parseToJsonElement(jsonString)
        return json.decodeFromJsonElement<Page>(element)
    }
    
    fun getFontNameForPage(pageNumber: Int): String {
        if (fontMapCache == null) {
            val inputStream = context.assets.open("font-map.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(jsonString).jsonObject
            fontMapCache = root.entries.associate { it.key to it.value.jsonPrimitive.content }
        }
        return fontMapCache!![pageNumber.toString()] ?: "QCF2M11"
    }

    fun getPageMetadata(pageNumber: Int, page: Page? = null): MushafPageMetadata {
        val resolvedPage = page ?: getPage(pageNumber)
        val firstSurahObj = resolvedPage.surahs.firstOrNull()
        val surahName = firstSurahObj?.name_arabic ?: "الفاتحة"
        
        val juzNumber = ((pageNumber - 1) / 20) + 1
        
        return MushafPageMetadata(
            pageNumber = pageNumber,
            surahNameArabic = surahName,
            juzNumber = juzNumber.coerceIn(1, 30)
        )
    }
}
