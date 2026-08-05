package com.example.quranapp.presentation.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DhikrItem(
    val id: Int,
    val text: String,
    val reference: String,
    val count: Int = 1
)

data class DhikrCategory(
    val id: String,
    val title: String,
    val countText: String,
    val iconTint: Color,
    val iconBg: Color,
    val azkar: List<DhikrItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzkarScreen() {
    val primaryGreen = Color(0xFF004d40)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)

    var selectedCategory by remember { mutableStateOf<DhikrCategory?>(null) }

    val categories = remember {
        listOf(
            DhikrCategory(
                id = "sabah",
                title = "أذكار الصباح",
                countText = "18 ذكراً",
                iconTint = Color(0xFF2E7D32),
                iconBg = Color(0xFFE8F5E9),
                azkar = listOf(
                    DhikrItem(1, "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۚ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۚ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۚ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ", "آية الكرسي - سورة البقرة 255", 1),
                    DhikrItem(2, "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ", "سورة الإخلاص", 3),
                    DhikrItem(3, "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، مِن شَرِّ مَا خَلَقَ، وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ، وَمِن شَرِّ النَّفَّاتَاتِ فِي الْعُقَدِ، وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ", "سورة الفلق", 3),
                    DhikrItem(4, "قُلْ أَعُوذُ بِرَبِّ النَّاسِ، مَلِكِ النَّاسِ، إِلَهِ النَّاسِ، مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ، الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ، مِنَ الْجِنَّةِ وَالنَّاسِ", "سورة الناس", 3),
                    DhikrItem(5, "أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير. رب أسألك خير ما في هذا اليوم وخير ما بعده، وأعوذ بك من شر ما في هذا اليوم وشر ما بعده. رب أعوذ بك من الكسل وسوء الكبر. رب أعوذ بك من عذاب في النار وعذاب في القبر.", "رواه مسلم", 1),
                    DhikrItem(6, "اللهم بك أصبحنا، وبك أمسينا، وبك نحيا، وبك نموت، وإليك النشور.", "رواه الترمذي", 1),
                    DhikrItem(7, "اللهم أنت ربي لا إله إلا أنت، خلقتني وأنا عبدك، وأنا على عهدك ووعدك ما استطعت، أعوذ بك من شر ما صنعت، أبوء لك بنعمتك علي وأبوء بذنبي فاغفر لي فإنه لا يغفر الذنوب إلا أنت.", "رواه البخاري", 1),
                    DhikrItem(8, "اللهم إني أصبحت أشهدك وأشهد حملة عرشك وملائكتك وجميع خلقك أنك أنت الله لا إله إلا أنت وأن محمداً عبدك ورسولك.", "رواه أبو داود", 4),
                    DhikrItem(9, "اللهم ما أصبح بي من نعمة فمنك وحدك لا شريك لك فلك الحمد ولك الشكر.", "رواه أبو داود", 1),
                    DhikrItem(10, "اللهم عافني في بدني، اللهم عافني في سمعي، اللهم عافني في بصري، لا إله إلا أنت.", "رواه أبو داود", 3),
                    DhikrItem(11, "أعوذ بكلمات الله التامات من شر ما خلق.", "رواه مسلم", 3),
                    DhikrItem(12, "حسبي الله لا إله إلا هو عليه توكلت وهو رب العرش العظيم.", "رواه أبو داود", 7),
                    DhikrItem(13, "بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم.", "رواه أبو داود والترمذي", 3),
                    DhikrItem(14, "رضيت بالله رباً وبالإسلام ديناً وبمحمد صلى الله عليه وسلم نبياً.", "رواه أبو داود", 3),
                    DhikrItem(15, "يا حي يا قيوم برحمتك أستغيث أصلح لي شأني كله ولا تكلني إلى نفسي طرفة عين.", "رواه الحاكم وصححه", 3),
                    DhikrItem(16, "سبحان الله وبحمده عدد خلقه ورضا نفسه وزنة عرشه ومداد كلماته.", "رواه مسلم", 3),
                    DhikrItem(17, "لا إله إلا الله وحده لا شريك له له الملك وله الحمد وهو على كل شيء قدير.", "متفق عليه", 10),
                    DhikrItem(18, "اللهم صل وسلم على نبينا محمد.", "رواه مسلم", 10)
                )
            ),
            DhikrCategory(
                id = "masa",
                title = "أذكار المساء",
                countText = "17 ذكراً",
                iconTint = Color(0xFFF57C00),
                iconBg = Color(0xFFFFF3E0),
                azkar = listOf(
                    DhikrItem(1, "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ...", "آية الكرسي - سورة البقرة 255", 1),
                    DhikrItem(2, "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ", "سورة الإخلاص", 3),
                    DhikrItem(3, "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ...", "سورة الفلق", 3),
                    DhikrItem(4, "قُلْ أَعُوذُ بِرَبِّ النَّاسِ...", "سورة الناس", 3),
                    DhikrItem(5, "أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير. رب أسألك خير ما في هذه الليلة وخير ما بعدها، وأعوذ بك من شر ما في هذه الليلة وشر ما بعدها. رب أعوذ بك من الكسل وسوء الكبر. رب أعوذ بك من عذاب في النار وعذاب في القبر.", "رواه مسلم", 1),
                    DhikrItem(6, "اللهم بك أمسينا، وبك أصبحنا، وبك نحيا، وبك نموت، وإليك المصير.", "رواه الترمذي", 1),
                    DhikrItem(7, "اللهم أنت ربي لا إله إلا أنت، خلقتني وأنا عبدك، وأنا على عهدك ووعدك ما استطعت، أعوذ بك من شر ما صنعت، أبوء لك بنعمتك علي وأبوء بذنبي فاغفر لي فإنه لا يغفر الذنوب إلا أنت.", "رواه البخاري", 1),
                    DhikrItem(8, "اللهم إني أمسيت أشهدك وأشهد حملة عرشك وملائكتك وجميع خلقك أنك أنت الله لا إله إلا أنت وأن محمداً عبدك ورسولك.", "رواه أبو داود", 4),
                    DhikrItem(9, "اللهم ما أمسى بي من نعمة فمنك وحدك لا شريك لك فلك الحمد ولك الشكر.", "رواه أبو داود", 1),
                    DhikrItem(10, "اللهم عافني في بدني، اللهم عافني في سمعي، اللهم عافني في بصري، لا إله إلا أنت.", "رواه أبو داود", 3),
                    DhikrItem(11, "أعوذ بكلمات الله التامات من شر ما خلق.", "رواه مسلم", 3),
                    DhikrItem(12, "بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم.", "رواه أبو داود والترمذي", 3),
                    DhikrItem(13, "رضيت بالله رباً وبالإسلام ديناً وبمحمد صلى الله عليه وسلم نبياً.", "رواه أبو داود", 3),
                    DhikrItem(14, "يا حي يا قيوم برحمتك أستغيث أصلح لي شأني كله ولا تكلني إلى نفسي طرفة عين.", "رواه الحاكم وصححه", 3),
                    DhikrItem(15, "اللهم إني أسألك العافية في الدنيا والآخرة، اللهم إني أسألك العفو والعافية في ديني ودنياي وأهلي ومالي.", "رواه أبو داود", 1),
                    DhikrItem(16, "سبحان الله وبحمده عدد خلقه ورضا نفسه وزنة عرشه ومداد كلماته.", "رواه مسلم", 3),
                    DhikrItem(17, "لا إله إلا الله وحده لا شريك له له الملك وله الحمد وهو على كل شيء قدير.", "متفق عليه", 10)
                )
            ),
            DhikrCategory(
                id = "salah",
                title = "أذكار بعد الصلاة",
                countText = "13 ذكراً",
                iconTint = Color(0xFF1976D2),
                iconBg = Color(0xFFE3F2FD),
                azkar = listOf(
                    DhikrItem(1, "أستغفر الله، أستغفر الله، أستغفر الله. اللهم أنت السلام ومنك السلام، تباركت يا ذا الجلال والإكرام.", "رواه مسلم", 1),
                    DhikrItem(2, "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير. اللهم لا مانع لما أعطيت ولا معطي لما منعت ولا ينفع ذا الجد منك الجد.", "متفق عليه", 1),
                    DhikrItem(3, "سبحان الله (33 مرة) والحمد لله (33 مرة) والله أكبر (33 مرة) - ثم يقال تمام المائة: لا إله إلا الله وحده لا شريك له له الملك وله الحمد وهو على كل شيء قدير.", "رواه مسلم", 1),
                    DhikrItem(4, "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد، يحيي ويميت وهو على كل شيء قدير.", "رواه البخاري", 10),
                    DhikrItem(5, "اللهم أعني على ذكرك وشكرك وحسن عبادتك.", "رواه أبو داود", 1),
                    DhikrItem(6, "اللهم إني أسألك علماً نافعاً ورزقاً طيباً وعملاً متقبلاً.", "رواه ابن ماجه", 1),
                    DhikrItem(7, "اللهم اغفر لي ذنوبي وخطاياي كلهم. اللهم أنعشني واجبرني واهدني لصالح الأعمال والأخلاق.", "رواه أبو داود", 1),
                    DhikrItem(8, "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ...", "آية الكرسي - سورة البقرة 255", 1),
                    DhikrItem(9, "قُلْ هُوَ اللَّهُ أَحَدٌ...", "سورة الإخلاص", 3),
                    DhikrItem(10, "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ...", "سورة الفلق", 3),
                    DhikrItem(11, "قُلْ أَعُوذُ بِرَبِّ النَّاسِ...", "سورة الناس", 3),
                    DhikrItem(12, "لا إله إلا الله وحده لا شريك له له الملك وله الحمد وهو على كل شيء قدير.", "رواه الترمذي", 1),
                    DhikrItem(13, "سبحان الله وبحمده.", "رواه مسلم", 3)
                )
            ),
            DhikrCategory(
                id = "sleep",
                title = "أذكار النوم",
                countText = "11 ذكراً",
                iconTint = Color(0xFF7B1FA2),
                iconBg = Color(0xFFF3E5F5),
                azkar = listOf(
                    DhikrItem(1, "باسمك ربي وضعت جنبي وبك أرفعه، إن أمسكت نفسي فارحمها، وإن أرسلتها فاحفظها بما تحفظ به عبادك الصالحين.", "متفق عليه", 1),
                    DhikrItem(2, "اللهم قني عذابك يوم تبعث عبادك.", "رواه أبو داود", 3),
                    DhikrItem(3, "باسم الله اللهم إني أحيا وأموت.", "رواه البخاري", 1),
                    DhikrItem(4, "الحمد لله الذي أطعمنا وسقانا وكفانا وآوانا، فكم ممن لا كافي له ولا مؤوي.", "رواه مسلم", 1),
                    DhikrItem(5, "اللهم رب السماوات السبع ورب العرش العظيم، ربنا ورب كل شيء، فالق الحب والنوى، منزل التوراة والإنجيل والفرقان، أعوذ بك من شر كل شيء أنت آخذ بناصيته، اللهم أنت الأول فليس قبلك شيء، وأنت الآخر فليس بعدك شيء، وأنت الظاهر فليس فوقك شيء، وأنت الباطن فليس دونك شيء، اقض عنا الدين وأغننا من الفقر.", "رواه مسلم", 1),
                    DhikrItem(6, "اللهم أسلمت نفسي إليك، وفوضت أمري إليك، وألجأت ظهري إليك، رهبة ورغبة إليك، لا ملجأ ولا منجا منك إلا إليك، آمنت بكتابك الذي أنزلت وبنبيك الذي أرسلت.", "رواه البخاري", 1),
                    DhikrItem(7, "سبحان الله (33 مرة)، والحمد لله (33 مرة)، والله أكبر (34 مرة).", "متفق عليه", 1),
                    DhikrItem(8, "آية الكرسي - اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ...", "سورة البقرة 255", 1),
                    DhikrItem(9, "آمن الرسول بما أنزل إليه من ربه والمؤمنون...", "سورة البقرة 285-286", 1),
                    DhikrItem(10, "قُلْ هُوَ اللَّهُ أَحَدٌ...", "سورة الإخلاص", 3),
                    DhikrItem(11, "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ...", "سورة الفلق", 3)
                )
            ),
            DhikrCategory(
                id = "wake",
                title = "أذكار الاستيقاظ",
                countText = "7 أذكار",
                iconTint = Color(0xFF00897B),
                iconBg = Color(0xFFE0F2F1),
                azkar = listOf(
                    DhikrItem(1, "الحمد لله الذي أحيانا بعد ما أماتنا وإليه النشور.", "رواه البخاري", 1),
                    DhikrItem(2, "الحمد لله الذي عافاني في جسدي ورد علي روحي وأذن لي بذكره.", "رواه الترمذي", 1),
                    DhikrItem(3, "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير. سبحان الله والحمد لله ولا إله إلا الله والله أكبر ولا حول ولا قوة إلا بالله العلي العظيم.", "رواه البخاري", 1),
                    DhikrItem(4, "رب اغفر لي.", "رواه ابن ماجه", 1),
                    DhikrItem(5, "اللهم إني أسألك من خير هذا اليوم، فتحه ونصره ونوره وبركته وهداه، وأعوذ بك من شر ما فيه وشر ما بعده.", "رواه أبو داود", 1),
                    DhikrItem(6, "الحمد لله الذي رد علي روحي وعافاني في جسدي وأذن لي بذكره.", "رواه الترمذي", 1),
                    DhikrItem(7, "اللهم بك أصبحنا، وبك أمسينا، وبك نحيا، وبك نموت، وإليك النشور.", "رواه الترمذي", 1)
                )
            ),
            DhikrCategory(
                id = "home",
                title = "أذكار البيت والمسجد",
                countText = "10 أذكار",
                iconTint = Color(0xFFFBC02D),
                iconBg = Color(0xFFFFFDE7),
                azkar = listOf(
                    DhikrItem(1, "بسم الله ولجنا، وبسم الله خرجنا، وعلى ربنا توكلنا.", "رواه أبو داود", 1),
                    DhikrItem(2, "اللهم إني أسألك خير المولج وخير المخرج، باسم الله ولجنا وباسم الله خرجنا وعلى الله ربنا توكلنا.", "رواه أبو داود", 1),
                    DhikrItem(3, "اللهم إني أعوذ بك أن أضل أو أضل، أو أزل أو أزل، أو أظلم أو أظلم، أو أجهل أو يجهل علي.", "رواه أبو داود", 1),
                    DhikrItem(4, "بسم الله توكلت على الله، لا حول ولا قوة إلا بالله.", "رواه أبو داود", 1),
                    DhikrItem(5, "اللهم إني أعوذ بالله العظيم وبوجهه الكريم وسلطانه القديم من الشيطان الرجيم.", "رواه أبو داود", 1),
                    DhikrItem(6, "اللهم افتح لي أبواب رحمتك.", "رواه مسلم", 1),
                    DhikrItem(7, "اللهم إني أسألك من فضلك.", "رواه مسلم", 1),
                    DhikrItem(8, "أعوذ بالله العظيم وبوجهه الكريم وسلطانه القديم من الشيطان الرجيم. بسم الله الرحمن الرحيم.", "رواه أحمد", 1),
                    DhikrItem(9, "اللهم اغفر لي ذنوبي وافتح لي أبواب رحمتك.", "رواه ابن السني", 1),
                    DhikrItem(10, "اللهم إني أعوذ بك من الخبث والخبائث.", "متفق عليه", 1)
                )
            ),
            DhikrCategory(
                id = "shamilah",
                title = "التسبيح والتحميد والتهليل",
                countText = "15 ذكراً",
                iconTint = Color(0xFF3949AB),
                iconBg = Color(0xFFE8EAF6),
                azkar = listOf(
                    DhikrItem(1, "سبحان الله وبحمده، سبحان الله العظيم.", "متفق عليه", 10),
                    DhikrItem(2, "سبحان الله (33 مرة)، والحمد لله (33 مرة)، والله أكبر (34 مرة).", "متفق عليه", 1),
                    DhikrItem(3, "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير.", "متفق عليه", 10),
                    DhikrItem(4, "سبحان الله وبحمده عدد خلقه ورضا نفسه وزنة عرشه ومداد كلماته.", "رواه مسلم", 3),
                    DhikrItem(5, "سبحان الله والحمد لله ولا إله إلا الله والله أكبر ولا حول ولا قوة إلا بالله العلي العظيم.", "رواه الترمذي", 10),
                    DhikrItem(6, "سبحان الله العظيم وبحمده.", "رواه البخاري", 10),
                    DhikrItem(7, "لا إله إلا الله.", "رواه مسلم", 100),
                    DhikrItem(8, "سبحان الله.", "رواه مسلم", 33),
                    DhikrItem(9, "الحمد لله.", "رواه مسلم", 33),
                    DhikrItem(10, "الله أكبر.", "رواه مسلم", 34),
                    DhikrItem(11, "لا حول ولا قوة إلا بالله.", "رواه الترمذي", 10),
                    DhikrItem(12, "سبحان الله وبحمده.", "رواه مسلم", 100),
                    DhikrItem(13, "الله أكبر كبيراً والحمد لله كثيراً وسبحان الله بكرة وأصيلاً.", "رواه مسلم", 1),
                    DhikrItem(14, "الحمد لله الذي لا إله إلا هو وهو للحمد أهل وهو على كل شيء قدير.", "رواه ابن أبي شيبة", 1),
                    DhikrItem(15, "سبحان الملك القدوس.", "رواه أبو داود", 3)
                )
            ),
            DhikrCategory(
                id = "wudu",
                title = "أذكار الوضوء",
                countText = "6 أذكار",
                iconTint = Color(0xFF00ACC1),
                iconBg = Color(0xFFE0F7FA),
                azkar = listOf(
                    DhikrItem(1, "بسم الله.", "رواه أبو داود", 1),
                    DhikrItem(2, "اللهم اغفر لي ذنبي ووسع لي في داري وبارك لي في رزقي.", "رواه الترمذي", 1),
                    DhikrItem(3, "اللهم اجعلني من التوابين واجعلني من المتطهرين.", "رواه الترمذي", 1),
                    DhikrItem(4, "سبحانك اللهم وبحمدك أشهد أن لا إله إلا أنت أستغفرك وأتوب إليك.", "رواه النسائي", 1),
                    DhikrItem(5, "اللهم اجعلني من التوابين واجعلني من المتطهرين.", "رواه أبو داود", 1),
                    DhikrItem(6, "اللهم اجعله نوراً في قلبي ونوراً في قبري ونوراً في بصري ونوراً في سمعي.", "رواه ابن ماجه", 1)
                )
            ),
            DhikrCategory(
                id = "food",
                title = "أذكار الطعام",
                countText = "7 أذكار",
                iconTint = Color(0xFFE65100),
                iconBg = Color(0xFFFFF3E0),
                azkar = listOf(
                    DhikrItem(1, "بسم الله.", "رواه البخاري", 1),
                    DhikrItem(2, "اللهم بارك لنا فيما رزقتنا وقنا عذاب النار.", "رواه الترمذي", 1),
                    DhikrItem(3, "الحمد لله الذي أطعمنا وسقانا وجعلنا مسلمين.", "رواه أبو داود", 1),
                    DhikrItem(4, "الحمد لله الذي أطعمنا هذا الطعام ورزقناه من غير حول منا ولا قوة.", "رواه أبو داود", 1),
                    DhikrItem(5, "اللهم إني صمت لك وآمنت بك وعليك توكلت وبعظمتك أستجير.", "رواه البيهقي", 1),
                    DhikrItem(6, "بسم الله في أوله وآخره.", "رواه أبو داود", 1),
                    DhikrItem(7, "الحمد لله حمداً كثيراً طيباً مباركاً فيه غير مكفي ولا مودع ولا مستغنى عنه ربنا.", "رواه البخاري", 1)
                )
            ),
            DhikrCategory(
                id = "travel",
                title = "أذكار السفر",
                countText = "7 أذكار",
                iconTint = Color(0xFF5D4037),
                iconBg = Color(0xFFEFEBE9),
                azkar = listOf(
                    DhikrItem(1, "الله أكبر، الله أكبر، الله أكبر، سبحان الذي سخر لنا هذا وما كنا له مقرنين وإنا إلى ربنا لمنقلبون. اللهم إنا نسألك في سفرنا هذا البر والتقوى ومن العمل ما ترضى. اللهم هون علينا سفرنا هذا واطو عنا بعده. اللهم أنت الصاحب في السفر والخليفة في الأهل. اللهم إني أعوذ بك من وعثاء السفر وكآبة المنظر وسوء المنقلب في المال والأهل.", "رواه مسلم", 1),
                    DhikrItem(2, "اللهم إني أعوذ بك من وعثاء السفر وكآبة المنظر وسوء المنقلب في المال والأهل والولد.", "رواه مسلم", 1),
                    DhikrItem(3, "اللهم أنت الصاحب في السفر والخليفة في الأهل. اللهم اصحبنا في سفرنا واخلفنا في أهلنا.", "رواه مسلم", 1),
                    DhikrItem(4, "سبحان الله (حين يصعد الأكمة) والله أكبر (حين ينزل الأودية).", "رواه البخاري", 1),
                    DhikrItem(5, "آيبون تائبون عابدون لربنا حامدون.", "رواه البخاري", 3),
                    DhikrItem(6, "اللهم رب السموات السبع ورب الأرضين ورب الشياطين ورب الرياح، نسألك خير هذه القرية وخير أهلها وخير ما فيها، ونعوذ بك من شرها وشر أهلها وشر ما فيها.", "رواه النسائي", 1),
                    DhikrItem(7, "اللهم إني أسألك خير هذه الرحلة، اللهم إني أعوذ بك من شر هذه الرحلة.", "رواه أحمد", 1)
                )
            ),
            DhikrCategory(
                id = "rain",
                title = "أذكار المطر والرعد",
                countText = "5 أذكار",
                iconTint = Color(0xFF1565C0),
                iconBg = Color(0xFFE3F2FD),
                azkar = listOf(
                    DhikrItem(1, "اللهم صيباً نافعاً.", "رواه البخاري", 3),
                    DhikrItem(2, "اللهم حوالينا ولا علينا، اللهم على الآكام والظراب وبطون الأودية ومنابت الشجر.", "متفق عليه", 1),
                    DhikrItem(3, "مطرنا بفضل الله ورحمته.", "متفق عليه", 1),
                    DhikrItem(4, "اللهم إني أسألك خيرها وخير ما فيها وخير ما أرسلت به، وأعوذ بك من شرها وشر ما فيها وشر ما أرسلت به.", "رواه مسلم", 1),
                    DhikrItem(5, "سبحان الذي يسبح الرعد بحمده والملائكة من خيفته.", "رواه الموطأ", 1)
                )
            ),
            DhikrCategory(
                id = "distress",
                title = "أذكار الكرب والهم",
                countText = "8 أذكار",
                iconTint = Color(0xFFC62828),
                iconBg = Color(0xFFFFEBEE),
                azkar = listOf(
                    DhikrItem(1, "لا إله إلا الله العظيم الحليم، لا إله إلا الله رب العرش العظيم، لا إله إلا الله رب السموات ورب الأرض ورب العرش الكريم.", "متفق عليه", 1),
                    DhikrItem(2, "اللهم رحمتك أرجو فلا تكلني إلى نفسي طرفة عين وأصلح لي شأني كله لا إله إلا أنت.", "رواه أبو داود", 1),
                    DhikrItem(3, "لا إله إلا أنت سبحانك إني كنت من الظالمين.", "سورة الأنبياء 87", 1),
                    DhikrItem(4, "حسبي الله لا إله إلا هو عليه توكلت وهو رب العرش العظيم.", "رواه أبو داود", 7),
                    DhikrItem(5, "اللهم إني عبدك وابن عبدك وابن أمتك، ناصيتي بيدك ماض في حكمك عدل في قضاؤك، أسألك بكل اسم هو لك سميت به نفسك أو أنزلته في كتابك أو علمته أحداً من خلقك أو استأثرت به في علم الغيب عندك أن تجعل القرآن ربيع قلبي ونور صدري وجلاء حزني وذهاب همي.", "رواه أحمد", 1),
                    DhikrItem(6, "اللهم إني أعوذ بك من الهم والحزن والعجز والكسل والبخل والجبن وضلع الدين وغلبة الرجال.", "رواه البخاري", 1),
                    DhikrItem(7, "اللهم لا سهل إلا ما جعلته سهلاً وأنت تجعل الحزن إذا شئت سهلاً.", "رواه ابن حبان", 1),
                    DhikrItem(8, "اللهم إني أسألك بأن لك الحمد لا إله إلا أنت المنان بديع السموات والأرض يا ذا الجلال والإكرام يا حي يا قيوم إني أسألك.", "رواه أبو داود", 1)
                )
            ),
            DhikrCategory(
                id = "istikhara",
                title = "صلاة الاستخارة",
                countText = "ذكر واحد",
                iconTint = Color(0xFF4527A0),
                iconBg = Color(0xFFEDE7F6),
                azkar = listOf(
                    DhikrItem(1, "اللهم إني أستخيرك بعلمك وأستقدرك بقدرتك وأسألك من فضلك العظيم، فإنك تقدر ولا أقدر وتعلم ولا أعلم وأنت علام الغيوب. اللهم إن كنت تعلم أن هذا الأمر (تسمي حاجتك) خير لي في ديني ومعاشي وعاقبة أمري فاقدره لي ويسره لي ثم بارك لي فيه، وإن كنت تعلم أن هذا الأمر شر لي في ديني ومعاشي وعاقبة أمري فاصرفه عني واصرفني عنه واقدر لي الخير حيث كان ثم أرضني به.", "رواه البخاري", 1)
                )
            ),
            DhikrCategory(
                id = "sick",
                title = "أذكار المريض والعيادة",
                countText = "6 أذكار",
                iconTint = Color(0xFFD32F2F),
                iconBg = Color(0xFFFFCDD2),
                azkar = listOf(
                    DhikrItem(1, "لا بأس طهور إن شاء الله.", "رواه البخاري", 1),
                    DhikrItem(2, "أسأل الله العظيم رب العرش العظيم أن يشفيك.", "رواه الترمذي", 7),
                    DhikrItem(3, "بسم الله أرقيك من كل شيء يؤذيك، من شر كل نفس أو عين حاسد، الله يشفيك بسم الله أرقيك.", "رواه مسلم", 3),
                    DhikrItem(4, "أذهب الباس رب الناس، واشف أنت الشافي، لا شفاء إلا شفاؤك شفاء لا يغادر سقماً.", "متفق عليه", 3),
                    DhikrItem(5, "اللهم رب الناس أذهب الباس واشف أنت الشافي لا شفاء إلا شفاؤك.", "متفق عليه", 3),
                    DhikrItem(6, "اللهم إني أسألك العافية في الدنيا والآخرة.", "رواه أبو داود", 3)
                )
            ),
            DhikrCategory(
                id = "funeral",
                title = "أذكار الجنائز",
                countText = "7 أذكار",
                iconTint = Color(0xFF37474F),
                iconBg = Color(0xFFECEFF1),
                azkar = listOf(
                    DhikrItem(1, "إنا لله وإنا إليه راجعون، اللهم أجرني في مصيبتي واخلف لي خيراً منها.", "رواه مسلم", 1),
                    DhikrItem(2, "اللهم اغفر له وارحمه وعافه واعف عنه وأكرم نزله ووسع مدخله واغسله بالماء والثلج والبرد ونقه من الخطايا كما ينقى الثوب الأبيض من الدنس، وأبدله داراً خيراً من داره وأهلاً خيراً من أهله وزوجاً خيراً من زوجه وأدخله الجنة وأعذه من عذاب القبر ومن عذاب النار.", "رواه مسلم", 1),
                    DhikrItem(3, "اللهم اغفر لحينا وميتنا وصغيرنا وكبيرنا وذكرنا وأنثانا وشاهدنا وغائبنا، اللهم من أحييته منا فأحيه على الإسلام، ومن توفيته منا فتوفه على الإيمان.", "رواه الترمذي", 1),
                    DhikrItem(4, "اللهم إن فلان بن فلان (تقول اسم الميت) في ذمتك وحبل جوارك فقه من فتنة القبر وعذاب النار وأنت أهل الوفاء والحق، فاغفر له وارحمه إنك أنت الغفور الرحيم.", "رواه ابن ماجه", 1),
                    DhikrItem(5, "اللهم عبدك وابن أمتك احتاج إلى رحمتك، وأنت غني عن عذابه، إن كان محسناً فزد في حسناته وإن كان مسيئاً فتجاوز عنه.", "رواه الحاكم", 1),
                    DhikrItem(6, "اللهم لا تحرمنا أجره ولا تفتنا بعده واغفر لنا وله.", "رواه ابن ماجه", 1),
                    DhikrItem(7, "اللهم اجعل قبره روضة من رياض الجنة ولا تجعله حفرة من حفر النار.", "رواه الترمذي", 1)
                )
            ),
            DhikrCategory(
                id = "istighfar",
                title = "الاستغفار",
                countText = "8 أذكار",
                iconTint = Color(0xFF2E7D32),
                iconBg = Color(0xFFE8F5E9),
                azkar = listOf(
                    DhikrItem(1, "أستغفر الله.", "رواه مسلم", 100),
                    DhikrItem(2, "أستغفر الله وأتوب إليه.", "رواه البخاري", 100),
                    DhikrItem(3, "اللهم أنت ربي لا إله إلا أنت، خلقتني وأنا عبدك، وأنا على عهدك ووعدك ما استطعت، أعوذ بك من شر ما صنعت، أبوء لك بنعمتك علي وأبوء بذنبي فاغفر لي فإنه لا يغفر الذنوب إلا أنت.", "رواه البخاري", 1),
                    DhikrItem(4, "رب اغفر لي وتب علي إنك أنت التواب الرحيم.", "رواه أبو داود", 100),
                    DhikrItem(5, "اللهم اغفر لي خطيئتي وجهلي وإسرافي في أمري وما أنت أعلم به مني. اللهم اغفر لي هزلي وجدي وخطاي وعمدي وكل ذلك عندي.", "متفق عليه", 1),
                    DhikrItem(6, "اللهم اغفر لي ما قدمت وما أخرت وما أسررت وما أعلنت وما أنت أعلم به مني. أنت المقدم وأنت المؤخر وأنت على كل شيء قدير.", "متفق عليه", 1),
                    DhikrItem(7, "اللهم إني ظلمت نفسي ظلماً كثيراً ولا يغفر الذنوب إلا أنت فاغفر لي مغفرة من عندك وارحمني إنك أنت الغفور الرحيم.", "متفق عليه", 1),
                    DhikrItem(8, "اللهم إنك عفو تحب العفو فاعف عني.", "رواه الترمذي", 3)
                )
            ),
            DhikrCategory(
                id = "salawat",
                title = "الصلاة على النبي ﷺ",
                countText = "7 أذكار",
                iconTint = Color(0xFF6A1B9A),
                iconBg = Color(0xFFF3E5F5),
                azkar = listOf(
                    DhikrItem(1, "اللهم صل على محمد وعلى آل محمد كما صليت على إبراهيم وعلى آل إبراهيم إنك حميد مجيد. اللهم بارك على محمد وعلى آل محمد كما باركت على إبراهيم وعلى آل إبراهيم إنك حميد مجيد.", "متفق عليه", 10),
                    DhikrItem(2, "اللهم صل على محمد عبدك ورسولك كما صليت على إبراهيم، وبارك على محمد وعلى آل محمد كما باركت على إبراهيم.", "رواه البخاري", 1),
                    DhikrItem(3, "اللهم صل على محمد وعلى أزواجه وذريته كما صليت على آل إبراهيم، وبارك على محمد وعلى أزواجه وذريته كما باركت على آل إبراهيم إنك حميد مجيد.", "متفق عليه", 1),
                    DhikrItem(4, "اللهم صل على محمد النبي الأمي وعلى آل محمد.", "رواه مسلم", 1),
                    DhikrItem(5, "اللهم صل على محمد وعلى آل محمد كما صليت على إبراهيم إنك حميد مجيد، وبارك على محمد وعلى آل محمد كما باركت على إبراهيم إنك حميد مجيد.", "رواه مسلم", 1),
                    DhikrItem(6, "اللهم اجعل صلواتك ورحمتك وبركاتك على سيد المرسلين وإمام المتقين وخاتم النبيين محمد عبدك ورسولك إمام الخير وقائد الخير ورسول الرحمة.", "رواه ابن ماجه", 1),
                    DhikrItem(7, "اللهم صل على محمد وأهل بيته.", "رواه الطبراني", 3)
                )
            )
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = selectedCategory?.title ?: "الأذكار",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    if (selectedCategory != null) {
                        IconButton(onClick = { selectedCategory = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        },
        containerColor = lightBg
    ) { paddingValues ->
        if (selectedCategory == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(categories) { _, category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = category },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Navigate",
                                tint = textGray,
                                modifier = Modifier.size(24.dp)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textDark,
                                            fontSize = 18.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = category.countText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textGray,
                                            fontSize = 13.sp
                                        )
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = category.iconBg,
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = null,
                                            tint = category.iconTint,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val currentCategory = selectedCategory!!
            val countsState = remember(currentCategory.id) {
                mutableStateMapOf<Int, Int>().apply {
                    currentCategory.azkar.forEach { put(it.id, 0) }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(currentCategory.azkar) { _, dhikr ->
                    val currentCount = countsState[dhikr.id] ?: 0
                    val isCompleted = currentCount >= dhikr.count

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCompleted) Color(0xFFEFEFEF) else cardBg
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = dhikr.text,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = if (isCompleted) textGray else textDark,
                                    fontSize = 18.sp,
                                    lineHeight = 30.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                textAlign = TextAlign.Right
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = dhikr.reference,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textGray,
                                    fontSize = 13.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFEAEFEA)
                                ) {
                                    Text(
                                        text = "التكرار: ${dhikr.count}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryGreen,
                                            fontSize = 13.sp
                                        )
                                    )
                                }

                                if (isCompleted) {
                                    Button(
                                        onClick = { countsState[dhikr.id] = 0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(text = "اضغط للإعادة", color = Color.White)
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clickable {
                                                val nextCount = currentCount + 1
                                                if (nextCount <= dhikr.count) {
                                                    countsState[dhikr.id] = nextCount
                                                }
                                            },
                                        shape = CircleShape,
                                        color = Color(0xFFF4F7F4),
                                        border = androidx.compose.foundation.BorderStroke(2.dp, primaryGreen)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = currentCount.toString(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryGreen,
                                                    fontSize = 20.sp
                                                )
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
