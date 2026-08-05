package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldDim
import com.example.ui.theme.GoldSurface
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldSurface
import com.example.ui.theme.NightBlack
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.debug.PerformanceProfiler
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ==========================================
// DATA MODELS FOR ADHKAR
// ==========================================
data class DhikrItem(
    val text: String,
    val countMax: Int,
    var currentCount: Int = 0,
    val description: String = ""
)

data class AdhkarCategory(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val items: List<DhikrItem>
)

// Helper to provide all Adhkar data
fun getAdhkarData(language: AppLanguage): List<AdhkarCategory> {
    return listOf(
        AdhkarCategory(
            name = AppTranslation.translate("morning_adhkar", language),
            icon = Icons.Rounded.WbSunny,
            items = listOf(
                DhikrItem(
                    text = "أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ: (اللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ لاَ تَأْخُذُهُ سِنَةٌ وَلاَ نَوْمٌ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الأَرْضِ مَن ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلاَّ بِإِذْنِهِ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ وَلاَ يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلاَّ بِمَا شَاء وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالأَرْضَ وَلاَ يَؤُودُهُ حِفْظُهُمَا وَهُوَ الْعَلِيُّ الْعَظِيمُ).",
                    countMax = 1,
                    description = "من قالها حين يصبح أجير من الجن حتى يمسي."
                ),
                DhikrItem(
                    text = "سُورَةُ الإِخْلاَصِ: (قُلْ هُوَ اللَّهُ أَحَدٌ * اللَّهُ الصَّمَدُ * لَمْ يَلِدْ وَلَمْ يُولَدْ * وَلَمْ يَكُن لَّهُ كُفُواً أَحَدٌ).",
                    countMax = 3,
                    description = "من قالها ثلاث مرات حين يصبح وحين يمسي تكفيه من كل شيء."
                ),
                DhikrItem(
                    text = "سُورَةُ الفَلَقِ: (قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ * مِن شَرِّ مَا خَلَقَ * وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ * وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ * وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ).",
                    countMax = 3,
                    description = "من قالها ثلاث مرات تكفيه من كل شيء."
                ),
                DhikrItem(
                    text = "سُورَةُ النَّاسِ: (قُلْ أَعُوذُ بِرَبِّ النَّاسِ * مَلِكِ النَّاسِ * إِلَهِ النَّاسِ * مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ * الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ * مِنَ الْجِنَّةِ وَالنَّاسِ).",
                    countMax = 3,
                    description = "من قالها ثلاث مرات تكفيه من كل شيء."
                ),
                DhikrItem(
                    text = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.",
                    countMax = 1,
                    description = "يقال مرة واحدة في الصباح"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ.",
                    countMax = 1,
                    description = "يقال مرة واحدة"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
                    countMax = 1,
                    description = "سيد الاستغفار"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ إِنِّي أَصْبَحْتُ أُشْهِدُكَ، وَأُشْهِدُ حَمَلَةَ عَرْشِكَ، وَمَلَائِكَتَكَ، وَجَمِيعَ خَلْقِكَ، أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ، وَأَنَّ مُحَمَّداً عَبْدُكَ وَرَسُولُكَ.",
                    countMax = 4,
                    description = "من قالها أعتقه الله من النار"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ، فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ.",
                    countMax = 1,
                    description = "من قالها فقد أدى شكر يومه"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَهَ إِلَّا أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ، وَالْفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ، لَا إِلَهَ إِلَّا أَنْتَ.",
                    countMax = 3,
                    description = "دعاء العافية"
                ),
                DhikrItem(
                    text = "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.",
                    countMax = 7,
                    description = "من قالها كفاه الله ما أهمه من أمر الدنيا والآخرة"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَيْنِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي.",
                    countMax = 1,
                    description = "دعاء الحفظ"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ عَالِمَ الْغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَاوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطَانِ وَشِرْكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ.",
                    countMax = 1,
                    description = "قله إذا أصبحت وإذا أمسيت وإذا أخذت مضجعك"
                ),
                DhikrItem(
                    text = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.",
                    countMax = 3,
                    description = "من قالها ثلاثاً لم يضره شيء"
                ),
                DhikrItem(
                    text = "رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيّاً.",
                    countMax = 3,
                    description = "من قالها كان حقاً على الله أن يرضيه"
                ),
                DhikrItem(
                    text = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.",
                    countMax = 3,
                    description = "دعاء الاستغاثة برحمة الله"
                ),
                DhikrItem(
                    text = "أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشْرِكِينَ.",
                    countMax = 1,
                    description = "المداومة عليها تربط المؤمن بدينه"
                ),
                DhikrItem(
                    text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ.",
                    countMax = 3,
                    description = "يقال ثلاث مرات"
                ),
                DhikrItem(
                    text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.",
                    countMax = 100,
                    description = "من قالها مائة مرة حطت خطاياه وإن كانت مثل زبد البحر"
                ),
                DhikrItem(
                    text = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                    countMax = 10,
                    description = "من قالها عشر مرات كان كمن أعتق أربعة أنفس من ولد إسماعيل"
                ),
                DhikrItem(
                    text = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                    countMax = 100,
                    description = "كانت له عدل عشر رقاب، وكتبت له مائة حسنة، ومحيت عنه مائة سيئة، وكانت له حرزا من الشيطان"
                ),
                DhikrItem(
                    text = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ.",
                    countMax = 100,
                    description = "مائة مرة في اليوم"
                )
            )
        ),
        AdhkarCategory(
            name = AppTranslation.translate("evening_adhkar", language),
            icon = Icons.Rounded.NightsStay,
            items = listOf(
                DhikrItem(
                    text = "أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ: (اللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ لاَ تَأْخُذُهُ سِنَةٌ وَلاَ نَوْمٌ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الأَرْضِ مَن ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلاَّ بِإِذْنِهِ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ وَلاَ يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلاَّ بِمَا شَاء وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالأَرْضَ وَلاَ يَؤُودُهُ حِفْظُهُمَا وَهُوَ الْعَلِيُّ الْعَظِيمُ).",
                    countMax = 1,
                    description = "من قالها حين يمسي أجير من الجن حتى يصبح."
                ),
                DhikrItem(
                    text = "سُورَةُ الإِخْلاَصِ: (قُلْ هُوَ اللَّهُ أَحَدٌ * اللَّهُ الصَّمَدُ * لَمْ يَلِدْ وَلَمْ يُولَدْ * وَلَمْ يَكُن لَّهُ كُفُواً أَحَدٌ).",
                    countMax = 3,
                    description = "من قالها ثلاث مرات حين يصبح وحين يمسي تكفيه من كل شيء."
                ),
                DhikrItem(
                    text = "سُورَةُ الفَلَقِ: (قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ * مِن شَرِّ مَا خَلَقَ * وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ * وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ * وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ).",
                    countMax = 3,
                    description = "من قالها ثلاث مرات تكفيه من كل شيء."
                ),
                DhikrItem(
                    text = "سُورَةُ النَّاسِ: (قُلْ أَعُوذُ بِرَبِّ النَّاسِ * مَلِكِ النَّاسِ * إِلَهِ النَّاسِ * مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ * الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ * مِنَ الْجِنَّةِ وَالنَّاسِ).",
                    countMax = 3,
                    description = "من قالها ثلاث مرات تكفيه من كل شيء."
                ),
                DhikrItem(
                    text = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.",
                    countMax = 1,
                    description = "يقال مرة واحدة في المساء"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ.",
                    countMax = 1,
                    description = "يقال مرة واحدة"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
                    countMax = 1,
                    description = "سيد الاستغفار"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ إِنِّي أَمْسَيْتُ أُشْهِدُكَ، وَأُشْهِدُ حَمَلَةَ عَرْشِكَ، وَمَلَائِكَتَكَ، وَجَمِيعَ خَلْقِكَ، أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ، وَأَنَّ مُحَمَّداً عَبْدُكَ وَرَسُولُكَ.",
                    countMax = 4,
                    description = "من قالها أعتقه الله من النار"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ مَا أَمْسَى بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ، فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ.",
                    countMax = 1,
                    description = "من قالها فقد أدى شكر ليلته"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَهَ إِلَّا أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ، وَالْفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ، لَا إِلَهَ إِلَّا أَنْتَ.",
                    countMax = 3,
                    description = "دعاء العافية"
                ),
                DhikrItem(
                    text = "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.",
                    countMax = 7,
                    description = "من قالها كفاه الله ما أهمه من أمر الدنيا والآخرة"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَيْنِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي.",
                    countMax = 1,
                    description = "دعاء الحفظ"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ عَالِمَ الْغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَاوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطَانِ وَشِرْكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ.",
                    countMax = 1,
                    description = "قله إذا أصبحت وإذا أمسيت وإذا أخذت مضجعك"
                ),
                DhikrItem(
                    text = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.",
                    countMax = 3,
                    description = "من قالها ثلاثاً لم يضره شيء"
                ),
                DhikrItem(
                    text = "رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيّاً.",
                    countMax = 3,
                    description = "من قالها كان حقاً على الله أن يرضيه"
                ),
                DhikrItem(
                    text = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.",
                    countMax = 3,
                    description = "دعاء الاستغاثة برحمة الله"
                ),
                DhikrItem(
                    text = "أَمْسَيْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشْرِكِينَ.",
                    countMax = 1,
                    description = "المداومة عليها تربط المؤمن بدينه"
                ),
                DhikrItem(
                    text = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.",
                    countMax = 3,
                    description = "حماية من الضر الهوام والسموم"
                ),
                DhikrItem(
                    text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.",
                    countMax = 100,
                    description = "من قالها مائة مرة حطت خطاياه وإن كانت مثل زبد البحر"
                ),
                DhikrItem(
                    text = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ.",
                    countMax = 100,
                    description = "مائة مرة في اليوم"
                )
            )
        ),
        AdhkarCategory(
            name = AppTranslation.translate("after_prayer_adhkar", language),
            icon = Icons.Rounded.Mosque,
            items = listOf(
                DhikrItem(
                    text = "أَسْتَغْفِرُ اللهَ، أَسْتَغْفِرُ اللهَ، أَسْتَغْفِرُ اللهَ. اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ.",
                    countMax = 1,
                    description = "دعاء الاستغفار بعد التسليم"
                ),
                DhikrItem(
                    text = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، اللَّهُمَّ لَا مَانِعَ لِمَا أَعْطَيْتَ، وَلَا مُعْطِيَ لِمَا مَنَعْتَ، وَلَا يَنْفَعُ ذَا الْجَدِّ مِنْكَ الْجَدُّ.",
                    countMax = 1,
                    description = "بعد كل صلاة مكتوبة"
                ),
                DhikrItem(
                    text = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ، لَا إِلَهَ إِلَّا اللَّهُ، وَلَا نَعْبُدُ إِلَّا إِيَّاهُ، لَهُ النِّعْمَةُ وَلَهُ الْفَضْلُ وَلَهُ الثَّنَاءُ الْحَسَنُ، لَا إِلَهَ إِلَّا اللَّهُ مُخْلِصِينَ لَهُ الدِّينَ وَلَوْ كَرِهَ الْكَافِرُونَ.",
                    countMax = 1,
                    description = "بعد كل صلاة مكتوبة"
                ),
                DhikrItem(
                    text = "سُبْحَانَ اللهِ",
                    countMax = 33,
                    description = "التسبيح بعد الصلاة"
                ),
                DhikrItem(
                    text = "الْحَمْدُ للهِ",
                    countMax = 33,
                    description = "التحميد بعد الصلاة"
                ),
                DhikrItem(
                    text = "اللهُ أَكْبَرُ",
                    countMax = 33,
                    description = "التكبير بعد الصلاة"
                ),
                DhikrItem(
                    text = "لَا إِلَهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                    countMax = 1,
                    description = "تمام المائة بعد التسبيح والتحميد والتكبير"
                ),
                DhikrItem(
                    text = "آيَةُ الْكُرْسِيِّ: (اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ مَن ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاء وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ وَلَا يَؤُودُهُ حِفْظُهُمَا وَهُوَ الْعَلِيُّ الْعَظِيمُ).",
                    countMax = 1,
                    description = "من قرأها دبر كل صلاة مكتوبة لم يمنعه من دخول الجنة إلا أن يموت"
                ),
                DhikrItem(
                    text = "قُلْ هُوَ اللَّهُ أَحَدٌ، قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، قُلْ أَعُوذُ بِرَبِّ النَّاسِ.",
                    countMax = 1,
                    description = "تقرأ مرة واحدة بعد كل صلاة، وثلاث مرات بعد صلاتي الفجر والمغرب"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً.",
                    countMax = 1,
                    description = "بعد السلام من صلاة الفجر"
                )
            )
        ),
        AdhkarCategory(
            name = AppTranslation.translate("sleep_adhkar", language),
            icon = Icons.Rounded.NightsStay,
            items = listOf(
                DhikrItem(
                    text = "بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي، وَبِكَ أَرْفَعُـه، فَإِن أَمْسَـكْتَ نَفْسـي فارْحَـمْها ، وَإِنْ أَرْسَلْتَـها فاحْفَظْـها بِمـا تَحْفَـظُ بِه عِبـادَكَ الصّـالِحـين.",
                    countMax = 1,
                    description = "يقال قبل النوم"
                ),
                DhikrItem(
                    text = "اللّهُـمَّ إِنَّـكَ خَلَـقْتَ نَفْسـي وَأَنْـتَ تَوَفّـاهـا لَكَ ممَـاتـها وَمَحْـياها، إِنْ أَحْيَيْـتَها فاحْفَظْـها، وَإِنْ أَمَتَّـها فَاغْفِـرْ لَـها. اللّهُـمَّ إِنَّـي أَسْـأَلُـكَ العـافِـيَة.",
                    countMax = 1,
                    description = "يقال قبل النوم"
                ),
                DhikrItem(
                    text = "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا.",
                    countMax = 1,
                    description = "إذا أخذ مضجعه"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ.",
                    countMax = 3,
                    description = "كان رسول الله يضع يده اليمنى تحت خده ويقولها"
                )
            )
        ),
        AdhkarCategory(
            name = AppTranslation.translate("waking_up_adhkar", language),
            icon = Icons.Rounded.WbSunny,
            items = listOf(
                DhikrItem(
                    text = "الْحَمْدُ للهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ.",
                    countMax = 1,
                    description = "عند الاستيقاظ من النوم"
                ),
                DhikrItem(
                    text = "لَا إِلَهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، سُبْحَانَ اللهِ، وَالْحَمْدُ للهِ، وَلَا إِلَهَ إِلَّا اللهُ، وَاللهُ أَكْبَرُ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللهِ الْعَلِيِّ الْعَظِيمِ، رَبِّ اغْفِرْ لِي.",
                    countMax = 1,
                    description = "من قالها غفر له"
                ),
                DhikrItem(
                    text = "الْحَمْدُ للهِ الَّذِي عَافَانِي فِي جَسَدِي، وَرَدَّ عَلَيَّ رُوحِي، وَأَذِنَ لِي بِذِكْرِهِ.",
                    countMax = 1,
                    description = "عند الاستيقاظ"
                )
            )
        ),
        AdhkarCategory(
            name = AppTranslation.translate("travel_adhkar", language),
            icon = Icons.Rounded.DirectionsCar,
            items = listOf(
                DhikrItem(
                    text = "اللهُ أكبَر، اللهُ أكبَر، اللهُ أكبَر، (سُبْحانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ * وَإِنَّا إِلَى رَبِّنَا لَمُنقَلِبُونَ) اللّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا البِرَّ وَالتَّقْوَى، وَمِنَ العَمَلِ مَا تَرْضَى، اللّهُمَّ هَوِّنْ عَلَيْنَا سَفَرَنَا هَذَا وَاطْوِ عَنَّا بُعْدَهُ، اللّهُمَّ أَنْتَ الصَّاحِبُ فِي السَّفَرِ، وَالخَلِيفَةُ فِي الأَهْلِ، اللّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ وَعْثَاءِ السَّفَرِ، وَكَآبَةِ الْمَنْظَرِ، وَسُوءِ الْمُنقَلَبِ فِي الْمَالِ وَالأَهْلِ",
                    countMax = 1,
                    description = "دعاء السفر"
                ),
                DhikrItem(
                    text = "اللّهُمَّ أَنْتَ الصَّاحِبُ فِي السَّفَرِ، وَالخَلِيفَةُ فِي الأَهْلِ، اللّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ وَعْثَاءِ السَّفَرِ، وَكَآبَةِ الْمَنْظَرِ، وَسُوءِ الْمُنقَلَبِ فِي الْمَالِ وَالأَهْلِ",
                    countMax = 1,
                    description = "دعاء السفر"
                ),
                DhikrItem(
                    text = "آيِبُونَ، تَائِبُونَ، عَابِدُونَ، لِرَبِّنَا حَامِدُونَ.",
                    countMax = 1,
                    description = "دعاء الرجوع من السفر"
                )
            )
        ),
        AdhkarCategory(
            name = AppTranslation.translate("sorrow_adhkar", language),
            icon = Icons.Rounded.FormatQuote,
            items = listOf(
                DhikrItem(
                    text = "اللَّهُمَّ إِنِّي عَبْدُكَ، ابْنُ عَبْدِكَ، ابْنُ أَمَتِكَ، نَاصِيَتِي بِيَدِكَ، مَاضٍ فِيَّ حُكْمُكَ، عَدْلٌ فِيَّ قَضَاؤُكَ، أَسْأَلُكَ بِكُلِّ اسْمٍ هُوَ لَكَ، سَمَّيْتَ بِهِ نَفْسَكَ، أَوْ أَنْزَلْتَهُ فِي كِتَابِكَ، أَوْ عَلَّمْتَهُ أَحَداً مِنْ خَلْقِكَ، أَوِ اسْتَأْثَرْتَ بِهِ فِي عِلْمِ الْغَيْبِ عِنْدَكَ، أَنْ تَجْعَلَ الْقُرْآنَ رَبِيعَ قَلْبِي، وَنُورَ صَدْرِي، وَجَلَاءَ حُزْنِي، وَذَهَابَ هَمِّي",
                    countMax = 1,
                    description = "دعاء الهم والحزن"
                ),
                DhikrItem(
                    text = "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ، وَالْعَجْزِ وَالْكَسَلِ، وَالْبُخْلِ وَالْجُبْنِ، وَضَلَعِ الدَّيْنِ وَغَلَبَةِ الرِّجَالِ.",
                    countMax = 1,
                    description = "دعاء الهم والحزن"
                ),
                DhikrItem(
                    text = "لَا إِلَهَ إِلَّا اللهُ الْعَظِيمُ الْحَلِيمُ، لَا إِلَهَ إِلَّا اللهُ رَبُّ الْعَرْشِ الْعَظِيمِ، لَا إِلَهَ إِلَّا اللهُ رَبُّ السَّمَوَاتِ وَرَبُّ الْأَرْضِ وَرَبُّ الْعَرْشِ الْكَرِيمِ.",
                    countMax = 1,
                    description = "دعاء الكرب"
                )
            )
        )
    )
}

// ==========================================
// ATHKAR SCREEN IMPLEMENTATION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthkarScreen(
    language: AppLanguage,
    onNavigateUp: (() -> Unit)? = null
) {
    SideEffect {
        android.util.Log.d("NAV_TRACE", "ADHKAR_SCREEN_READY")
        android.util.Log.d("NAV_TRACE", "ADHKAR_RECOMPOSE")
    }
    LaunchedEffect(Unit) {
        android.util.Log.d("NAV_TRACE", "ADHKAR_SCREEN_FIRST_COMPOSITION")
    }
    SideEffect {
        PerformanceProfiler.recordScreenRecomposition("AthkarScreen")
    }
    var categories by remember { mutableStateOf(getAdhkarData(language)) }
    var selectedCategoryIndex by remember { mutableStateOf<Int?>(null) }
    
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedCategoryIndex == null) AppTranslation.translate("adhkar_fortifications", language) else categories[selectedCategoryIndex!!].name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                navigationIcon = {
                    if (selectedCategoryIndex != null || onNavigateUp != null) {
                        IconButton(onClick = {
                            if (selectedCategoryIndex != null) {
                                selectedCategoryIndex = null
                            } else {
                                onNavigateUp?.invoke()
                            }
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedCategoryIndex == null) {
                // Category list view
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(categories) { index, category ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(ElevatedSurface)
                                .clickable { selectedCategoryIndex = index }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        GoldPrimary.copy(alpha = 0.20f),
                                                        GoldPrimary.copy(alpha = 0.07f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = category.name,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${category.items.size} ${AppTranslation.translate("of_adhkar", language)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronLeft,
                                    contentDescription = "عرض",
                                    tint = GoldPrimary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            } else {
                // Specific Athkar items inside the category
                val category = categories[selectedCategoryIndex!!]
                val haptic = LocalHapticFeedback.current

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Button(
                            onClick = {
                                // Reset all counts for this category
                                val updated = categories.toMutableList()
                                val updatedItems = category.items.map { it.copy(currentCount = 0) }
                                updated[selectedCategoryIndex!!] = category.copy(items = updatedItems)
                                categories = updated
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "إعادة ضبط")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إعادة ضبط الأذكار", fontWeight = FontWeight.Bold)
                        }
                    }

                    itemsIndexed(category.items) { itemIndex, dhikrItem ->
                        val isCompleted = dhikrItem.currentCount >= dhikrItem.countMax
                        
                        val cardBgColor = if (isCompleted) EmeraldSurface else ElevatedSurface
                        val textColor   = if (isCompleted) EmeraldAccent else TextPrimary
                        val descColor   = if (isCompleted) EmeraldAccent.copy(alpha=0.7f) else TextSecondary.copy(alpha = 0.65f)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBgColor)
                                .border(
                                    width = 1.dp,
                                    color = if(isCompleted) EmeraldAccent.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    if (dhikrItem.currentCount < dhikrItem.countMax) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val updated = categories.toMutableList()
                                        val updatedItems = category.items.toMutableList()
                                        updatedItems[itemIndex] = dhikrItem.copy(currentCount = dhikrItem.currentCount + 1)
                                        updated[selectedCategoryIndex!!] = category.copy(items = updatedItems)
                                        categories = updated
                                        
                                        if (dhikrItem.currentCount + 1 == dhikrItem.countMax) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Dhikr Arabic Text
                                Text(
                                    text = dhikrItem.text,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        lineHeight = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = textColor
                                )
                                
                                // Description label
                                if (dhikrItem.description.isNotEmpty()) {
                                    Text(
                                        text = dhikrItem.description,
                                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = descColor,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Right
                                    )
                                }
                                
                                HorizontalDivider(
                                    color = if (isCompleted) EmeraldAccent.copy(alpha = 0.3f) else BorderSubtle,
                                    thickness = 1.dp
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val context = LocalContext.current
                                        IconButton(
                                            onClick = {
                                                val shareText = """
                                                    ✨ *من الأذكار والتحصينات اليومية* ✨
                                                    
                                                    «${dhikrItem.text}»
                                                    
                                                    ${if (dhikrItem.description.isNotEmpty()) "📝 *الفضل والتعليمات:* ${dhikrItem.description}" else ""}
                                                    
                                                    تكرار الأجر: ${dhikrItem.countMax} ${if (dhikrItem.countMax == 1) "مرة واحدة" else "مرات"}.
                                                    تمت المشاركة من تطبيق نور الإيمان.
                                                """.trimIndent()
                                                val sendIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(android.content.Intent.createChooser(sendIntent, "مشاركة الذكر"))
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Share,
                                                contentDescription = "مشاركة الذكر",
                                                tint = if (isCompleted) EmeraldAccent else GoldPrimary
                                            )
                                        }

                                        if (isCompleted) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "اكتمل",
                                                    tint = EmeraldAccent
                                                )
                                                Text(
                                                    text = AppTranslation.translate("completed", language),
                                                    color = EmeraldAccent,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = AppTranslation.translate("click_card_to_repeat", language),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    
                                    // Progress counter pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isCompleted) androidx.compose.ui.graphics.SolidColor(EmeraldAccent)
                                                else Brush.horizontalGradient(listOf(GoldPrimary, GoldDim))
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${dhikrItem.currentCount} / ${dhikrItem.countMax}",
                                            color = NightBlack,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ==========================================
// QIBLA SCREEN IMPLEMENTATION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    language: AppLanguage,
    latitude: Double = 33.5731, // Casablanca default
    longitude: Double = -7.5898,
    onNavigateUp: () -> Unit
) {
    SideEffect {
        PerformanceProfiler.recordScreenRecomposition("QiblaScreen")
    }
    val context = LocalContext.current
    var azimuth by remember { mutableStateOf(-1f) } // -1f means uninitialized, so first read updates immediately without spin
    var qiblaDirection by remember { mutableStateOf(0.0) }
    
    // Calculates Qibla direction from degrees of current location
    LaunchedEffect(latitude, longitude) {
        val meccaLat = Math.toRadians(21.4225)
        val meccaLon = Math.toRadians(39.8262)
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        
        val dLon = meccaLon - lon
        val y = sin(dLon)
        val x = cos(lat) * tanMecca(meccaLat) - sin(lat) * cos(dLon)
        val qiblaRad = atan2(y, x)
        qiblaDirection = (Math.toDegrees(qiblaRad) + 360) % 360
    }
    
    // Sensor management for dynamic relative angle tracking
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val callbackStart = SystemClock.elapsedRealtime()
                if (event != null && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    PerformanceProfiler.recordAllocationHotspot("QiblaScreen.rotationSensorArrays", 2)
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientationValues = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    
                    // Convert azimuth to degrees and map to 0-360
                    val deg = Math.toDegrees(orientationValues[0].toDouble())
                    val newAzimuth = ((deg + 360) % 360).toFloat()
                    
                    if (azimuth < 0f) {
                        azimuth = newAzimuth
                    } else {
                        val rawDiff = newAzimuth - azimuth
                        val diff = (((rawDiff + 180f) % 360f + 360f) % 360f) - 180f
                        azimuth = (azimuth + diff * 0.15f + 360f) % 360f
                    }
                }
                PerformanceProfiler.recordMainThreadBlock(
                    "QiblaScreen.rotationSensorCallback",
                    SystemClock.elapsedRealtime() - callbackStart
                )
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        var fallbackListener: SensorEventListener? = null
        
        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Fallback sensors if ROTATION_VECTOR is unavailable
            val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            val localFallback = object : SensorEventListener {
                var lastAccelerometer = FloatArray(3)
                var lastMagnetometer = FloatArray(3)
                var lastAccelerometerSet = false
                var lastMagnetometerSet = false
                
                override fun onSensorChanged(event: SensorEvent?) {
                    val callbackStart = SystemClock.elapsedRealtime()
                    if (event == null) return
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
                        lastAccelerometerSet = true
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
                        lastMagnetometerSet = true
                    }
                    if (lastAccelerometerSet && lastMagnetometerSet) {
                        val R = FloatArray(9)
                        val I = FloatArray(9)
                        if (SensorManager.getRotationMatrix(R, I, lastAccelerometer, lastMagnetometer)) {
                            val orientation = FloatArray(3)
                            SensorManager.getOrientation(R, orientation)
                            val deg = Math.toDegrees(orientation[0].toDouble())
                            val newAzimuth = ((deg + 360) % 360).toFloat()
                            
                            if (azimuth < 0f) {
                                azimuth = newAzimuth
                            } else {
                                val rawDiff = newAzimuth - azimuth
                                val diff = (((rawDiff + 180f) % 360f + 360f) % 360f) - 180f
                                azimuth = (azimuth + diff * 0.15f + 360f) % 360f
                            }
                        }
                    }
                    PerformanceProfiler.recordMainThreadBlock(
                        "QiblaScreen.fallbackSensorCallback",
                        SystemClock.elapsedRealtime() - callbackStart
                    )
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            fallbackListener = localFallback
            sensorManager.registerListener(localFallback, magSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(localFallback, accelSensor, SensorManager.SENSOR_DELAY_UI)
        }
        
        onDispose {
            sensorManager.unregisterListener(listener)
            fallbackListener?.let {
                sensorManager.unregisterListener(it)
            }
        }
    }
    
    // Computing needle offset safely considering initial uninitialized state (-1f)
    val currentHeading = if (azimuth < 0f) 0f else azimuth
    val needleRotation = (qiblaDirection - currentHeading).toFloat()
    
        val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(AppTranslation.translate("qibla", language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Text(
                text = AppTranslation.translate("qibla_calibration_hint", language),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            
            // Compass graphical drawing box
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .border(4.dp, GoldPrimary.copy(alpha = 0.2f), CircleShape)
                    .background(ElevatedSurface), // Premium Dark Surface
                contentAlignment = Alignment.Center
            ) {
                // Background rotating dial markings
                Canvas(modifier = Modifier.fillMaxSize().rotate(-currentHeading)) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)
                    
                    // Draw outer minor ticks
                    val radius = w / 2f - 16.dp.toPx()
                    for (angle in 0 until 360 step 15) {
                        val angleRad = Math.toRadians(angle.toDouble())
                        val tickLen = if (angle % 90 == 0) 14.dp.toPx() else 6.dp.toPx()
                        val tickWidth = if (angle % 90 == 0) 3.dp.toPx() else 1.dp.toPx()
                        
                        val start = Offset(
                            (center.x + radius * cos(angleRad)).toFloat(),
                            (center.y + radius * sin(angleRad)).toFloat()
                        )
                        val end = Offset(
                            (center.x + (radius - tickLen) * cos(angleRad)).toFloat(),
                            (center.y + (radius - tickLen) * sin(angleRad)).toFloat()
                        )
                        drawLine(
                            color = if (angle == 0) EmeraldAccent else GoldDim.copy(alpha = 0.5f),
                            start = start,
                            end = end,
                            strokeWidth = tickWidth
                        )
                    }
                }
                
                // Static arrow / indicator frame for current phone heading (Up is North relative)
                // Rotating main Qibla needle inside pointing to Mecca!
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .rotate(needleRotation),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val center = Offset(w / 2f, h / 2f)
                        val arrowLen = w / 2f - 18.dp.toPx()
                        
                        // Golden decorative Mecca direction needle pointing UP (which represents Mecca relative bearing)
                        // Left/right side shapes for needle
                        val pathNorth = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y - arrowLen)
                            lineTo(center.x - 12.dp.toPx(), center.y)
                            lineTo(center.x, center.y - 4.dp.toPx())
                            close()
                        }
                        val pathNorthShade = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y - arrowLen)
                            lineTo(center.x + 12.dp.toPx(), center.y)
                            lineTo(center.x, center.y - 4.dp.toPx())
                            close()
                        }
                        val pathSouth = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y + arrowLen)
                            lineTo(center.x - 8.dp.toPx(), center.y)
                            lineTo(center.x, center.y + 2.dp.toPx())
                            close()
                        }
                        val pathSouthShade = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y + arrowLen)
                            lineTo(center.x + 8.dp.toPx(), center.y)
                            lineTo(center.x, center.y + 2.dp.toPx())
                            close()
                        }
                        
                        // Draw paths
                        drawPath(pathNorth, color = Color(0xFFD4B872)) // Golden brass
                        drawPath(pathNorthShade, color = Color(0xFF9E7E38)) // Shade brass
                        drawPath(pathSouth, color = Color(0xFF8B4513)) // Brown
                        drawPath(pathSouthShade, color = Color(0xFF5C2D0C)) // Dark Shade
                        
                        // Mecca Kaaba visual block indicator
                        drawCircle(
                            color = Color.Black,
                            radius = 16.dp.toPx(),
                            center = Offset(center.x, center.y - arrowLen - 2.dp.toPx())
                        )
                        // A small golden cube representing the Kaaba
                        drawRect(
                            color = Color(0xFFFFD700),
                            topLeft = Offset(center.x - 8.dp.toPx(), center.y - arrowLen - 10.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 16.dp.toPx())
                        )
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(center.x - 8.dp.toPx() + 3.dp.toPx(), center.y - arrowLen - 8.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx())
                        )
                        
                        // Center golden pin pivot
                        drawCircle(color = Color(0xFFFFD700), radius = 8.dp.toPx(), center = center)
                        drawCircle(color = Color(0xFF8B4513), radius = 4.dp.toPx(), center = center)
                    }
                }
            }
            
            // Informational section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElevatedSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = GoldPrimary
                        )
                        Text(
                            text = "${AppTranslation.translate("qibla_direction_degrees", language)} ${qiblaDirection.toInt()}° ${AppTranslation.translate("degrees_from_north", language)}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    
                    Text(
                        text = AppTranslation.translate("qibla_desc", language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
}

// Math function workaround for tan inside Qibla calculator
private fun tanMecca(angleRad: Double): Double {
    return sin(angleRad) / cos(angleRad)
}

// ==========================================
// TASBIH SCREEN IMPLEMENTATION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbihScreen(
    language: AppLanguage,
    onNavigateUp: () -> Unit
) {
    SideEffect {
        PerformanceProfiler.recordScreenRecomposition("TasbihScreen")
    }
    val azkarList = listOf(
        "سُبْحَانَ اللهِ" to "Subhan Allah",
        "الْحَمْدُ للهِ" to "Alhamdulillah",
        "اللهُ أَكْبَرُ" to "Allahu Akbar",
        "أَسْتَغْفِرُ اللهَ" to "Astaghfirullah",
        "لَا إِلَهَ إِلَّا اللهُ" to "La ilaha illallah",
        "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ" to "Salawat"
    )
    
    var selectedZikrIndex by remember { mutableStateOf(0) }
    var todayCount by remember { mutableStateOf(0) }
    var currentCycleCount by remember { mutableStateOf(0) }
    var targetCountLimit by remember { mutableStateOf(33) }
    
    val haptic = LocalHapticFeedback.current
    
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(AppTranslation.translate("tasbih", language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dhikr selector banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElevatedSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = AppTranslation.translate("current_dhikr", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldPrimary
                    )
                    Text(
                        text = azkarList[selectedZikrIndex].first,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = azkarList[selectedZikrIndex].second,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Selector slider row of choices
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        selectedZikrIndex = (selectedZikrIndex + 1) % azkarList.size
                        currentCycleCount = 0
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Rounded.SwapHoriz, contentDescription = "تغيير")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppTranslation.translate("change_dhikr", language) ?: "تغيير الذكر", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = {
                        targetCountLimit = if (targetCountLimit == 33) 99 else if (targetCountLimit == 99) 100 else 33
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(imageVector = Icons.Rounded.Flag, contentDescription = "الهدف")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${AppTranslation.translate("target", language) ?: "الهدف"}: $targetCountLimit", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.weight(0.1f))
            
            // Huge tactile click counter button circle
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                GoldPrimary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(2.dp, GoldPrimary.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        todayCount++
                        currentCycleCount++
                        
                        // Target hit trigger
                        if (currentCycleCount >= targetCountLimit) {
                            currentCycleCount = 0
                            // Extra vibrating feedback when cycle boundary hit
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Circular progress ring drawing
                Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    val angle = (currentCycleCount.toFloat() / targetCountLimit.toFloat()) * 360f
                    drawArc(
                        color = BorderSubtle.copy(alpha = 0.5f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx())
                    )
                    drawArc(
                        color = GoldPrimary,
                        startAngle = -90f,
                        sweepAngle = angle,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentCycleCount.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "${AppTranslation.translate("cycle_limit", language)} ${targetCountLimit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(0.1f))
            
            // Reset and session count triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive reset with long press
                IconButton(
                    onClick = {
                        currentCycleCount = 0
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "تصفير",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = AppTranslation.translate("total_tasbih_today", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = todayCount.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
}
