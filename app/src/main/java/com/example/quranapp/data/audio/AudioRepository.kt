package com.example.quranapp.data.audio

import android.content.Context
import android.util.Log
import com.example.quranapp.domain.model.AyahTimestamp
import com.example.quranapp.domain.model.SurahAudio
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSurahAudio(reciterId: Int, surahId: Int): SurahAudio = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, "audio_qdc_r${reciterId}_s${surahId}.json")
        val urlString = "https://api.qurancdn.com/api/qdc/audio/reciters/$reciterId/audio_files?chapter=$surahId&segments=true"

        Log.d("AudioDebug", "[Audio] Requested audio URL: $urlString")
        Log.d("RECITER_DEBUG", "selectedReciterId=$reciterId requestedApiUrl=$urlString")

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                cacheFile.writeText(responseString)
                val surahAudio = parseQdcResponse(responseString, reciterId, surahId)
                Log.d("RECITER_DEBUG", "returnedAudioUrl=${surahAudio.audioUrl} timestampsCount=${surahAudio.ayahTimestamps.size}")
                return@withContext surahAudio
            } else {
                Log.e("AudioDebug", "[Audio] API returned HTTP ${connection.responseCode} for reciter $reciterId surah $surahId")
            }
        } catch (e: Exception) {
            Log.e("AudioDebug", "[Audio] Network error fetching audio for reciter $reciterId surah $surahId: ${e.message}")
        }

        if (cacheFile.exists()) {
            try {
                val cachedString = cacheFile.readText()
                Log.d("AudioDebug", "[Audio] Using cached QDC data for reciter $reciterId surah $surahId")
                return@withContext parseQdcResponse(cachedString, reciterId, surahId)
            } catch (e: Exception) {
                Log.e("AudioDebug", "[Audio] Error reading cached QDC meta: ${e.message}")
            }
        }

        // Try old API as fallback for audio URL only (no timestamps)
        try {
            val oldUrl = URL("https://api.quran.com/api/v4/chapter_recitations/$reciterId/$surahId")
            val oldConn = oldUrl.openConnection() as HttpURLConnection
            oldConn.requestMethod = "GET"
            oldConn.connectTimeout = 6000
            oldConn.readTimeout = 6000
            if (oldConn.responseCode == 200) {
                val oldResponse = oldConn.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(oldResponse).jsonObject
                val audioFileObj = root["audio_file"]?.jsonObject
                val audioUrl = audioFileObj?.get("audio_url")?.jsonPrimitive?.content
                if (audioUrl != null) {
                    Log.d("RECITER_DEBUG", "fallbackToOldApi audioUrl=$audioUrl (no timestamps)")
                    return@withContext SurahAudio(
                        surahId = surahId,
                        reciterId = reciterId,
                        audioUrl = audioUrl,
                        durationMs = 0L,
                        ayahTimestamps = emptyList()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AudioDebug", "[Audio] Old API fallback also failed: ${e.message}")
        }

        Log.e("AudioDebug", "[Audio] No audio data available for reciter $reciterId surah $surahId")
        SurahAudio(
            surahId = surahId,
            reciterId = reciterId,
            audioUrl = "",
            durationMs = 0L,
            ayahTimestamps = emptyList()
        )
    }

    private fun parseQdcResponse(jsonString: String, reciterId: Int, surahId: Int): SurahAudio {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val audioFilesArray = root["audio_files"]?.jsonArray
        val audioFileObj = audioFilesArray?.firstOrNull()?.jsonObject
            ?: root // fallback if root is the audio_file directly

        val audioUrl = audioFileObj["audio_url"]?.jsonPrimitive?.content ?: ""
        val totalDurationMs = audioFileObj["duration"]?.jsonPrimitive?.longOrNull ?: 0L

        val verseTimingsArray = audioFileObj["verse_timings"]?.jsonArray ?: JsonArray(emptyList())

        val ayahTimestamps = mutableListOf<AyahTimestamp>()
        for (element in verseTimingsArray) {
            val obj = element.jsonObject
            val verseKey = obj["verse_key"]?.jsonPrimitive?.content ?: continue
            val timestampFrom = obj["timestamp_from"]?.jsonPrimitive?.longOrNull ?: continue
            val timestampTo = obj["timestamp_to"]?.jsonPrimitive?.longOrNull ?: continue
            val durationMs = timestampTo - timestampFrom
            ayahTimestamps.add(AyahTimestamp(
                verseKey = verseKey,
                startMs = timestampFrom,
                endMs = timestampTo,
                durationMs = durationMs.coerceAtLeast(0L)
            ))
        }

        Log.d("AudioDebug", "[Audio] Parsed QDC response: url=$audioUrl totalDurationMs=$totalDurationMs timestamps=${ayahTimestamps.size}")

        return SurahAudio(
            surahId = surahId,
            reciterId = reciterId,
            audioUrl = audioUrl,
            durationMs = totalDurationMs,
            ayahTimestamps = ayahTimestamps,
            verseTimingsReliable = true
        )
    }
}
