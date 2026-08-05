package com.example.quranapp.data.bukhari

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class BukhariHadith(
    val number: Int,
    val text: String,
    val reference: String
)

data class BukhariKitab(
    val number: Int,
    val nameArabic: String,
    val startHadith: Int,
    val endHadith: Int
) {
    val hadithCount: Int get() = endHadith - startHadith + 1
}

class BukhariRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val bukhariFile get() = File(context.filesDir, "bukhari_arabic.json")

    // CDN URL for the Fawazahmed0 Hadith API (stable versioned link)
    private val downloadUrl = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-bukhari.min.json"

    // The 97 Kitabs of Sahih Bukhari with their hadith number ranges
    val kitabs: List<BukhariKitab> = listOf(
        BukhariKitab(1, "كتاب بدء الوحي", 1, 7),
        BukhariKitab(2, "كتاب الإيمان", 8, 58),
        BukhariKitab(3, "كتاب العلم", 59, 134),
        BukhariKitab(4, "كتاب الوضوء", 135, 247),
        BukhariKitab(5, "كتاب الغسل", 248, 293),
        BukhariKitab(6, "كتاب الحيض", 294, 333),
        BukhariKitab(7, "كتاب التيمم", 334, 348),
        BukhariKitab(8, "كتاب الصلاة", 349, 530),
        BukhariKitab(9, "كتاب مواقيت الصلاة", 531, 601),
        BukhariKitab(10, "كتاب الأذان", 602, 838),
        BukhariKitab(11, "كتاب الجمعة", 839, 944),
        BukhariKitab(12, "كتاب الخوف", 945, 948),
        BukhariKitab(13, "كتاب العيدين", 949, 1012),
        BukhariKitab(14, "كتاب الوتر", 1013, 1030),
        BukhariKitab(15, "كتاب الاستسقاء", 1031, 1054),
        BukhariKitab(16, "كتاب الكسوف", 1055, 1080),
        BukhariKitab(17, "كتاب السجود", 1081, 1087),
        BukhariKitab(18, "كتاب التقصير", 1088, 1109),
        BukhariKitab(19, "كتاب التهجد", 1110, 1185),
        BukhariKitab(20, "كتاب فضل الصلاة في مسجد مكة والمدينة", 1186, 1197),
        BukhariKitab(21, "كتاب العمل في الصلاة", 1198, 1240),
        BukhariKitab(22, "كتاب السهو", 1241, 1264),
        BukhariKitab(23, "كتاب الجنائز", 1265, 1415),
        BukhariKitab(24, "كتاب الزكاة", 1416, 1520),
        BukhariKitab(25, "كتاب صدقة الفطر", 1521, 1531),
        BukhariKitab(26, "كتاب الحج", 1532, 1773),
        BukhariKitab(27, "كتاب العمرة", 1774, 1798),
        BukhariKitab(28, "كتاب المحصر", 1799, 1813),
        BukhariKitab(29, "كتاب جزاء الصيد", 1814, 1854),
        BukhariKitab(30, "كتاب فضائل المدينة", 1855, 1889),
        BukhariKitab(31, "كتاب الصوم", 1890, 2007),
        BukhariKitab(32, "كتاب صلاة التراويح", 2008, 2013),
        BukhariKitab(33, "كتاب الاعتكاف", 2014, 2045),
        BukhariKitab(34, "كتاب البيوع", 2046, 2239),
        BukhariKitab(35, "كتاب السلم", 2240, 2259),
        BukhariKitab(36, "كتاب الشفعة", 2260, 2263),
        BukhariKitab(37, "كتاب الإجارة", 2264, 2286),
        BukhariKitab(38, "كتاب الحوالات", 2287, 2295),
        BukhariKitab(39, "كتاب الكفالة", 2296, 2301),
        BukhariKitab(40, "كتاب الوكالة", 2302, 2332),
        BukhariKitab(41, "كتاب المزارعة", 2333, 2353),
        BukhariKitab(42, "كتاب المساقاة", 2354, 2381),
        BukhariKitab(43, "كتاب الاستقراض", 2382, 2415),
        BukhariKitab(44, "كتاب الخصومات", 2416, 2424),
        BukhariKitab(45, "كتاب اللقطة", 2425, 2441),
        BukhariKitab(46, "كتاب المظالم", 2442, 2500),
        BukhariKitab(47, "كتاب الشركة", 2501, 2512),
        BukhariKitab(48, "كتاب الرهن", 2513, 2517),
        BukhariKitab(49, "كتاب العتق", 2518, 2561),
        BukhariKitab(50, "كتاب المكاتب", 2562, 2564),
        BukhariKitab(51, "كتاب الهبة", 2565, 2627),
        BukhariKitab(52, "كتاب الشهادات", 2628, 2681),
        BukhariKitab(53, "كتاب الصلح", 2682, 2706),
        BukhariKitab(54, "كتاب الشروط", 2707, 2741),
        BukhariKitab(55, "كتاب الوصايا", 2742, 2780),
        BukhariKitab(56, "كتاب الجهاد والسير", 2781, 3083),
        BukhariKitab(57, "كتاب فرض الخمس", 3084, 3134),
        BukhariKitab(58, "كتاب الجزية", 3135, 3187),
        BukhariKitab(59, "كتاب بدء الخلق", 3188, 3326),
        BukhariKitab(60, "كتاب أحاديث الأنبياء", 3327, 3558),
        BukhariKitab(61, "كتاب المناقب", 3559, 3649),
        BukhariKitab(62, "كتاب فضائل أصحاب النبي ﷺ", 3650, 3799),
        BukhariKitab(63, "كتاب مناقب الأنصار", 3800, 3913),
        BukhariKitab(64, "كتاب المغازي", 3914, 4473),
        BukhariKitab(65, "كتاب تفسير القرآن", 4474, 4967),
        BukhariKitab(66, "كتاب فضائل القرآن", 4968, 5048),
        BukhariKitab(67, "كتاب النكاح", 5049, 5215),
        BukhariKitab(68, "كتاب الطلاق", 5216, 5334),
        BukhariKitab(69, "كتاب النفقات", 5335, 5369),
        BukhariKitab(70, "كتاب الأطعمة", 5370, 5508),
        BukhariKitab(71, "كتاب العقيقة", 5467, 5477),
        BukhariKitab(72, "كتاب الذبائح والصيد", 5478, 5543),
        BukhariKitab(73, "كتاب الأضاحي", 5544, 5567),
        BukhariKitab(74, "كتاب الأشربة", 5568, 5639),
        BukhariKitab(75, "كتاب المرضى", 5640, 5680),
        BukhariKitab(76, "كتاب الطب", 5681, 5777),
        BukhariKitab(77, "كتاب اللباس", 5778, 5959),
        BukhariKitab(78, "كتاب الأدب", 5960, 6148),
        BukhariKitab(79, "كتاب الاستئذان", 6227, 6328),
        BukhariKitab(80, "كتاب الدعوات", 6306, 6461),
        BukhariKitab(81, "كتاب الرقاق", 6407, 6571),
        BukhariKitab(82, "كتاب القدر", 6594, 6627),
        BukhariKitab(83, "كتاب الأيمان والنذور", 6628, 6720),
        BukhariKitab(84, "كتاب الكفارات", 6709, 6732),
        BukhariKitab(85, "كتاب الفرائض", 6732, 6773),
        BukhariKitab(86, "كتاب الحدود", 6774, 6869),
        BukhariKitab(87, "كتاب الديات", 6870, 6915),
        BukhariKitab(88, "كتاب استتابة المرتدين", 6916, 6931),
        BukhariKitab(89, "كتاب الإكراه", 6940, 6952),
        BukhariKitab(90, "كتاب الحيل", 6953, 6981),
        BukhariKitab(91, "كتاب تعبير الرؤيا", 6982, 7047),
        BukhariKitab(92, "كتاب الفتن", 7048, 7135),
        BukhariKitab(93, "كتاب الأحكام", 7136, 7258),
        BukhariKitab(94, "كتاب التمني", 7232, 7261),
        BukhariKitab(95, "كتاب أخبار الآحاد", 7253, 7274),
        BukhariKitab(96, "كتاب الاعتصام بالكتاب والسنة", 7269, 7361),
        BukhariKitab(97, "كتاب التوحيد", 7372, 7563)
    )

    fun isDownloaded(): Boolean = bukhariFile.exists() && bukhariFile.length() > 1_000_000L

    fun getFileSizeMB(): Double = bukhariFile.length() / (1024.0 * 1024.0)

    suspend fun downloadBukhari(onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("BukhariRepo", "Failed to download: ${response.code}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()

            bukhariFile.outputStream().use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(totalRead.toFloat() / contentLength.toFloat())
                        }
                    }
                }
            }

            onProgress(1f)
            true
        } catch (e: Exception) {
            Log.e("BukhariRepo", "Download error", e)
            if (bukhariFile.exists()) bukhariFile.delete()
            false
        }
    }

    suspend fun loadHadithsForKitab(kitab: BukhariKitab): List<BukhariHadith> = withContext(Dispatchers.IO) {
        if (!isDownloaded()) return@withContext emptyList()

        try {
            val content = bukhariFile.readText()
            return@withContext tryLoadFawazArrayFormat(content, kitab)
                ?: tryLoadFawazFormat(content, kitab)
                ?: tryLoadSimpleFormat(content, kitab)
                ?: emptyList()
        } catch (e: Exception) {
            Log.e("BukhariRepo", "Load error", e)
            emptyList()
        }
    }

    // Format from fawazahmed0 API: { "hadiths": [ { "hadithnumber": 1, "text": "..." }, ... ] }
    private fun tryLoadFawazFormat(content: String, kitab: BukhariKitab): List<BukhariHadith>? {
        return try {
            val root = json.parseToJsonElement(content).jsonObject
            val hadithsElement = root["hadiths"] ?: return null
            val hadithArray = hadithsElement.jsonObject["hadiths"]
                ?: return null
            // Actually the correct key is at top level
            null
        } catch (e: Exception) {
            null
        }
    }

    // Format from fawazahmed0 API (correct): root has "hadiths" key which is a JsonArray
    private fun tryLoadFawazArrayFormat(content: String, kitab: BukhariKitab): List<BukhariHadith>? {
        return try {
            val root = json.parseToJsonElement(content).jsonObject
            val hadithsArray = root["hadiths"] ?: return null
            // Check if it's actually an array
            val arr = hadithsArray as? kotlinx.serialization.json.JsonArray ?: return null
            val result = mutableListOf<BukhariHadith>()
            for (elem in arr) {
                val obj = elem.jsonObject
                val num = obj["hadithnumber"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                if (num < kitab.startHadith || num > kitab.endHadith) continue
                val text = obj["text"]?.jsonPrimitive?.content ?: continue
                result.add(BukhariHadith(num, text, "البخاري: $num"))
            }
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            null
        }
    }

    // Format: { "1": { "text": "...", "reference": "Bukhari: 1" }, "2": {...} }
    private fun tryLoadSimpleFormat(content: String, kitab: BukhariKitab): List<BukhariHadith>? {
        return try {
            val root = json.parseToJsonElement(content)
            val obj = root.jsonObject
            val result = mutableListOf<BukhariHadith>()
            for (num in kitab.startHadith..kitab.endHadith) {
                val entry = obj[num.toString()]?.jsonObject ?: continue
                val text = entry["text"]?.jsonPrimitive?.content ?: continue
                val reference = entry["reference"]?.jsonPrimitive?.content ?: "Bukhari: $num"
                result.add(BukhariHadith(num, text, reference))
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchHadiths(query: String): List<BukhariHadith> = withContext(Dispatchers.IO) {
        if (!isDownloaded() || query.length < 3) return@withContext emptyList()

        try {
            val content = bukhariFile.readText()
            val root = json.parseToJsonElement(content).jsonObject
            val result = mutableListOf<BukhariHadith>()
            for ((key, value) in root) {
                val num = key.toIntOrNull() ?: continue
                val entry = value.jsonObject
                val text = entry["text"]?.jsonPrimitive?.content ?: continue
                if (text.contains(query)) {
                    result.add(BukhariHadith(num, text, "Bukhari: $num"))
                }
                if (result.size >= 50) break
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteDownloadedFile() {
        if (bukhariFile.exists()) bukhariFile.delete()
    }
}
