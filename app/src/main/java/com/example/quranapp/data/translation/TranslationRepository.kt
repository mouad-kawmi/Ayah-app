package com.example.quranapp.data.translation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class TranslationRepository(private val context: Context) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    val supportedEditions = listOf(
        Edition("fr.hamidullah", "fr", "Traduction Française", "French (Hamidullah)", "translation"),
        Edition("en.sahih", "en", "English Translation", "English (Sahih)", "translation")
    )

    fun isEditionDownloaded(identifier: String): Boolean {
        val file = File(context.filesDir, "translation_$identifier.json")
        return file.exists() && file.length() > 0
    }

    suspend fun downloadEdition(identifier: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.alquran.cloud/v1/quran/$identifier"
            Log.d("TranslationRepo", "Downloading from $url")
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e("TranslationRepo", "Failed to download: ${response.code}")
                return@withContext false
            }

            val bodyString = response.body?.string() ?: return@withContext false
            
            // Parse the API response
            val parsedResponse = json.decodeFromString<TranslationResponse>(bodyString)
            
            // Convert to a flat map: "surahId:ayahId" -> "Text"
            val flatMap = mutableMapOf<String, String>()
            parsedResponse.data.surahs.forEach { surah ->
                surah.ayahs.forEach { ayah ->
                    val verseKey = "${surah.number}:${ayah.numberInSurah}"
                    flatMap[verseKey] = ayah.text
                }
            }
            
            // Save as a simple JSON map for faster loading and smaller size
            val simpleJson = json.encodeToString(flatMap)
            val file = File(context.filesDir, "translation_$identifier.json")
            file.writeText(simpleJson)
            
            Log.d("TranslationRepo", "Saved $identifier successfully: ${flatMap.size} verses")
            true
        } catch (e: Exception) {
            Log.e("TranslationRepo", "Error downloading edition", e)
            false
        }
    }

    suspend fun getTranslationText(identifier: String, verseKey: String): String? = withContext(Dispatchers.IO) {
        if (!isEditionDownloaded(identifier)) return@withContext null
        
        try {
            val file = File(context.filesDir, "translation_$identifier.json")
            val content = file.readText()
            val flatMap = json.decodeFromString<Map<String, String>>(content)
            
            flatMap[verseKey]
        } catch (e: Exception) {
            Log.e("TranslationRepo", "Error reading local translation", e)
            null
        }
    }
}
