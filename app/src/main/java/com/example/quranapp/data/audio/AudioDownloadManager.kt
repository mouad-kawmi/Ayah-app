package com.example.quranapp.data.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.*

data class DownloadEntry(
    val reciterId: Int,
    val surahId: Int,
    val reciterName: String,
    val surahName: String,
    val audioUrl: String,
    val fileSize: Long = 0L
)

class AudioDownloadManager private constructor(private val context: Context) {
    private val downloadsDir = File(context.filesDir, "offline_audio")
    private val prefs = context.getSharedPreferences("audio_downloads_v2", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _downloads = MutableStateFlow<List<DownloadEntry>>(loadPersistedDownloads())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: AudioDownloadManager? = null

        fun getInstance(context: Context): AudioDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun downloadKey(reciterId: Int, surahId: Int) = "${reciterId}_$surahId"

    fun isDownloaded(reciterId: Int, surahId: Int): Boolean {
        return getLocalAudioFile(reciterId, surahId)?.exists() == true
    }

    fun isDownloading(reciterId: Int, surahId: Int): Boolean {
        return _downloadProgress.value.containsKey(downloadKey(reciterId, surahId))
    }

    fun getLocalAudioFile(reciterId: Int, surahId: Int): File? {
        val file = File(downloadsDir, "${reciterId}_${surahId}.mp3")
        val exists = file.exists()
        Log.d("OFFLINE_AUDIO_DEBUG", "getLocalAudioFile reciterId=$reciterId surahId=$surahId path=${file.absolutePath} exists=$exists length=${file.length()} canRead=${file.canRead()}")
        return if (exists) file else null
    }

    suspend fun downloadSurahAudio(
        reciterId: Int,
        surahId: Int,
        audioUrl: String,
        surahName: String,
        reciterName: String,
        onProgress: (Float) -> Unit = {}
    ) {
        val key = downloadKey(reciterId, surahId)
        if (_downloadProgress.value.containsKey(key)) return // already downloading

        _downloadProgress.value = _downloadProgress.value + (key to 0f)

        val tempFile = File(downloadsDir, "${key}.tmp")
        val finalFile = File(downloadsDir, "${key}.mp3")

        try {
            downloadsDir.mkdirs()

            withContext(Dispatchers.IO) {
                val url = URL(audioUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                val contentLength = connection.contentLengthLong
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = if (contentLength > 0) (totalRead.toFloat() / contentLength).coerceAtMost(1f) else 0.5f
                    _downloadProgress.value = _downloadProgress.value + (key to progress)
                    onProgress(progress)
                }

                outputStream.close()
                inputStream.close()
                connection.disconnect()
            }

            tempFile.renameTo(finalFile)

            val entry = DownloadEntry(
                reciterId = reciterId,
                surahId = surahId,
                reciterName = reciterName,
                surahName = surahName,
                audioUrl = audioUrl,
                fileSize = finalFile.length()
            )

            persistDownload(entry)
            _downloads.value = _downloads.value + entry
            _downloadProgress.value = _downloadProgress.value - key

            Log.d("AudioDebug", "[Download] Completed: $key (${finalFile.length()} bytes)")

        } catch (e: Exception) {
            Log.e("AudioDebug", "[Download] Error for $key: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            _downloadProgress.value = _downloadProgress.value - key
            throw e
        }
    }

    fun deleteDownload(reciterId: Int, surahId: Int) {
        val key = downloadKey(reciterId, surahId)
        val file = getLocalAudioFile(reciterId, surahId)
        file?.delete()

        val updated = _downloads.value.filter { it.reciterId != reciterId || it.surahId != surahId }
        _downloads.value = updated
        persistAllDownloads(updated)

        Log.d("AudioDebug", "[Download] Deleted: $key")
    }

    fun getDownloadEntry(reciterId: Int, surahId: Int): DownloadEntry? {
        val entry = _downloads.value.find { it.reciterId == reciterId && it.surahId == surahId }
        Log.d("OFFLINE_AUDIO_DEBUG", "getDownloadEntry reciterId=$reciterId surahId=$surahId found=${entry != null}")
        return entry
    }

    private fun persistDownload(entry: DownloadEntry) {
        val all = _downloads.value.toMutableList()
        val existing = all.indexOfFirst { it.reciterId == entry.reciterId && it.surahId == entry.surahId }
        if (existing >= 0) {
            all[existing] = entry
        } else {
            all.add(entry)
        }
        persistAllDownloads(all)
    }

    private fun persistAllDownloads(entries: List<DownloadEntry>) {
        val list = JsonArray(entries.map { e ->
            val obj = JsonObject(mapOf(
                "reciterId" to JsonPrimitive(e.reciterId),
                "surahId" to JsonPrimitive(e.surahId),
                "reciterName" to JsonPrimitive(e.reciterName),
                "surahName" to JsonPrimitive(e.surahName),
                "audioUrl" to JsonPrimitive(e.audioUrl),
                "fileSize" to JsonPrimitive(e.fileSize)
            ))
            obj
        })
        prefs.edit().putString("downloads", list.toString()).apply()
    }

    private fun loadPersistedDownloads(): List<DownloadEntry> {
        val raw = prefs.getString("downloads", null)
        Log.d("OFFLINE_AUDIO_DEBUG", "loadPersistedDownloads raw=${raw?.take(200)}")
        if (raw == null) return emptyList()
        return try {
            val array = json.parseToJsonElement(raw).jsonArray
            val result = array.mapNotNull { element ->
                val obj = element.jsonObject
                val reciterId = obj["reciterId"]?.jsonPrimitive?.int ?: return@mapNotNull null
                val surahId = obj["surahId"]?.jsonPrimitive?.int ?: return@mapNotNull null
                val file = getLocalAudioFile(reciterId, surahId)
                if (file == null) {
                    Log.w("OFFLINE_AUDIO_DEBUG", "loadPersistedDownloads filtering out reciterId=$reciterId surahId=$surahId (file missing)")
                    return@mapNotNull null
                }
                DownloadEntry(
                    reciterId = reciterId,
                    surahId = surahId,
                    reciterName = obj["reciterName"]?.jsonPrimitive?.content ?: "",
                    surahName = obj["surahName"]?.jsonPrimitive?.content ?: "",
                    audioUrl = obj["audioUrl"]?.jsonPrimitive?.content ?: "",
                    fileSize = obj["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L
                )
            }
            Log.d("OFFLINE_AUDIO_DEBUG", "loadPersistedDownloads restored ${result.size} entries")
            result
        } catch (e: Exception) {
            Log.e("OFFLINE_AUDIO_DEBUG", "loadPersistedDownloads error: ${e.message}")
            emptyList()
        }
    }
}
