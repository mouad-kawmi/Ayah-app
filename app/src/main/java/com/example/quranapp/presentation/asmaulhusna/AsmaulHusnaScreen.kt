package com.example.quranapp.presentation.asmaulhusna

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class AsmaulHusna(
    val id: Int,
    val name: String,
    val meaning: String,
    val enMeaning: String
)

val asmaulHusnaList = listOf(
    AsmaulHusna(1, "الرَّحْمَنُ", "كثير الرحمة بعباده في الدنيا والآخرة", "The Beneficent"),
    AsmaulHusna(2, "الرَّحِيمُ", "المنعم أبداً، دائم الرحمة بالمؤمنين", "The Merciful"),
    AsmaulHusna(3, "الْمَلِكُ", "المالك لجميع الأشياء، المتصرف فيها بلا ممانع ولا ممانعة", "The King, The Sovereign"),
    AsmaulHusna(4, "الْقُدُّوسُ", "المنزه عن كل نقص، الطاهر من كل عيب", "The Most Holy"),
    AsmaulHusna(5, "السَّلَامُ", "الذي سلم من كل عيب ونقص، وسلم عباده من الظلم", "Peace and Blessing"),
    AsmaulHusna(6, "الْمُؤْمِنُ", "الذي صدق وعده عباده، وأمنهم من الظلم", "The Guarantor"),
    AsmaulHusna(7, "الْمُهَيْمِنُ", "المطلع على خفايا الأمور، الرقيب على كل شيء", "The Guardian, the Preserver"),
    AsmaulHusna(8, "الْعَزِيزُ", "الغالب الذي لا يغلب، القوي الذي لا يقهر", "The Almighty, the Self Sufficient"),
    AsmaulHusna(9, "الْجَبَّارُ", "الذي قهر جميع العباد، وأذعن له كل شيء", "The Powerful, the Irresistible"),
    AsmaulHusna(10, "الْمُتَكَبِّرُ", "المتعالي عن صفات الخلق، المتفرد بالعظمة", "The Tremendous"),
    AsmaulHusna(11, "الْخَالِقُ", "المبدع لجميع المخلوقات من غير مثال سابق", "The Creator"),
    AsmaulHusna(12, "الْبَارِئُ", "الذي أوجد الخلق بقدرته، وسواهم بحكمته", "The Maker"),
    AsmaulHusna(13, "الْمُصَوِّرُ", "الذي صور المخلوقات وأعطاها أشكالها المتميزة", "The Fashioner of Forms"),
    AsmaulHusna(14, "الْغَفَّارُ", "كثير المغفرة لذنوب عباده المستغفرين", "The Ever Forgiving"),
    AsmaulHusna(15, "الْقَهَّارُ", "الغالب لكل شيء، الذي ذل له كل شيء", "The All Compelling Subduer"),
    AsmaulHusna(16, "الْوَهَّابُ", "المتفضل بالعطايا، المنعم بها بلا عوض", "The Bestower"),
    AsmaulHusna(17, "الرَّزَّاقُ", "المتكفل بالرزق لجميع الخلائق", "The Ever Providing"),
    AsmaulHusna(18, "الْفَتَّاحُ", "الذي يفتح خزائن رحمته ورزقه لعباده", "The Opener, the Victory Giver"),
    AsmaulHusna(19, "الْعَلِيمُ", "المحيط علما بكل شيء، ظاهرا وباطنا", "The All Knowing, the Omniscient"),
    AsmaulHusna(20, "الْقَابِضُ", "الذي يقبض الرزق عمن يشاء بحكمته", "The Restrainer, the Straightener"),
    AsmaulHusna(21, "الْبَاسِطُ", "الذي يوسع الرزق لمن يشاء بفضله", "The Expander, the Munificent"),
    AsmaulHusna(22, "الْخَافِضُ", "الذي يخفض الجبابرة والمتكبرين", "The Abaser"),
    AsmaulHusna(23, "الرَّافِعُ", "الذي يرفع المتقين والصالحين", "The Exalter"),
    AsmaulHusna(24, "الْمُعِزُّ", "الذي يعز من يشاء من عباده", "The Giver of Honor"),
    AsmaulHusna(25, "الْمُذِلُّ", "الذي يذل من يشاء من أعدائه", "The Giver of Dishonor"),
    AsmaulHusna(26, "السَّمِيعُ", "الذي لا يخفى عليه شيء من المسموعات", "The All Hearing"),
    AsmaulHusna(27, "الْبَصِيرُ", "الذي لا يخفى عليه شيء من المبصرات", "The All Seeing"),
    AsmaulHusna(28, "الْحَكَمُ", "الحاكم العدل الذي لا يحيف في حكمه", "The Judge, the Arbitrator"),
    AsmaulHusna(29, "الْعَدْلُ", "الذي لا يظلم أحدا من خلقه", "The Utterly Just"),
    AsmaulHusna(30, "اللَّطِيفُ", "العالم بخفايا الأمور ودقائقها", "The Subtly Kind"),
    AsmaulHusna(31, "الْخَبِيرُ", "المطلع على بواطن الأمور وحقائقها", "The All Aware"),
    AsmaulHusna(32, "الْحَلِيمُ", "الذي لا يعجل بالعقوبة على من عصاه", "The Forbearing, the Indulgent"),
    AsmaulHusna(33, "الْعَظِيمُ", "المتصف بكل صفات الكمال والجلال", "The Magnificent, the Infinite"),
    AsmaulHusna(34, "الْغَفُورُ", "الذي يغفر الذنوب ويستر العيوب", "The All Forgiving"),
    AsmaulHusna(35, "الشَّكُورُ", "الذي يجازي على اليسير من العمل بالكثير من الثواب", "The Grateful"),
    AsmaulHusna(36, "الْعَلِيُّ", "المتصف بالعلو المطلق ذاتا وقَدْرا", "The Sublimely Exalted"),
    AsmaulHusna(37, "الْكَبِيرُ", "العظيم الذي لا يساويه شيء", "The Great"),
    AsmaulHusna(38, "الْحَفِيظُ", "الذي يحفظ كل شيء أوجده بحكمته", "The Preserver"),
    AsmaulHusna(39, "الْمُقِيتُ", "المتيسر لأقوات الخلائق ورازقها", "The Nourisher"),
    AsmaulHusna(40, "الْحَسِيبُ", "الكافي لعباده، والمحاسب لهم على أعمالهم", "The Reckoner"),
    AsmaulHusna(41, "الْجَلِيلُ", "العظيم القدر المتصف بالجلال", "The Majestic"),
    AsmaulHusna(42, "الْكَرِيمُ", "كثير الخير والعطاء الذي لا ينفد", "The Bountiful, the Generous"),
    AsmaulHusna(43, "الرَّقِيبُ", "المطلع على كل شيء، الذي لا يغفل أبدا", "The Watchful"),
    AsmaulHusna(44, "الْمُجِيبُ", "الذي يجيب دعاء الداعين وسؤال السائلين", "The Responsive, the Answerer"),
    AsmaulHusna(45, "الْوَاسِعُ", "الذي وسعت رحمته وعلمه كل شيء", "The Vast, the All Encompassing"),
    AsmaulHusna(46, "الْحَكِيمُ", "الذي يضع الأشياء في مواضعها بحكمة", "The Wise"),
    AsmaulHusna(47, "الْوَدُودُ", "المحب لعباده الصالحين المحبوب لديهم", "The Loving, the Kind One"),
    AsmaulHusna(48, "الْمَجِيدُ", "عظيم الشأن المتصف بالمجد والكبرياء", "The All Glorious"),
    AsmaulHusna(49, "الْبَاعِثُ", "الذي يبعث الموتى للحساب والجزاء", "The Raiser of the Dead"),
    AsmaulHusna(50, "الشَّهِيدُ", "المطلع على كل شيء، الشاهد على أعمال العباد", "The Witness"),
    AsmaulHusna(51, "الْحَقُّ", "الموجود حقا، الذي لا يتغير ولا يزول", "The Truth, the Real"),
    AsmaulHusna(52, "الْوَكِيلُ", "المتكفل بأمور الخلائق، المعتمد عليه في كل شيء", "The Trustee, the Dependable"),
    AsmaulHusna(53, "الْقَوِيُّ", "الذي لا يغلبه شيء، المتصف بكمال القوة", "The Strong"),
    AsmaulHusna(54, "الْمَتِينُ", "الشديد القوة الذي لا يلحقه تعب ولا مشقة", "The Firm, the Steadfast"),
    AsmaulHusna(55, "الْوَلِيُّ", "الناصر لعباده المؤمنين المتولي لأمورهم", "The Protecting Friend, Patron, and Helper"),
    AsmaulHusna(56, "الْحَمِيدُ", "المستحق للحمد والثناء في كل حال", "The All Praiseworthy"),
    AsmaulHusna(57, "الْمُحْصِي", "العالم بعدد كل شيء، والمحيط بكل شيء", "The Accounter, the Numberer of All"),
    AsmaulHusna(58, "الْمُبْدِئُ", "الذي بدأ الخلق من غير مثال سابق", "The Producer, Originator, and Initiator of all"),
    AsmaulHusna(59, "الْمُعِيدُ", "الذي يعيد الخلق بعد فنائهم", "The Reinstater Who Brings Back All"),
    AsmaulHusna(60, "الْمُحْيِي", "الذي يحيي الموتى، ويبث الحياة في الكائنات", "The Giver of Life"),
    AsmaulHusna(61, "الْمُمِيتُ", "الذي يميت الكائنات وينزع الحياة منها", "The Bringer of Death, the Destroyer"),
    AsmaulHusna(62, "الْحَيُّ", "دائم الحياة الذي لا يموت ولا يفنى", "The Ever Living"),
    AsmaulHusna(63, "الْقَيُّومُ", "القائم بنفسه، المقيم لغيره المدبر له", "The Self Subsisting Sustainer of All"),
    AsmaulHusna(64, "الْوَاجِدُ", "الغني الذي لا يفتقر إلى شيء", "The Perceiver, the Finder, the Unfailing"),
    AsmaulHusna(65, "الْمَاجِدُ", "كثير الإحسان، عظيم المجد والشرف", "The Illustrious, the Magnificent"),
    AsmaulHusna(66, "الْوَاحِدُ", "الفرد الذي لا شريك له ولا نظير", "The One, the Unique, Manifestation of Unity"),
    AsmaulHusna(67, "الْأَحَدُ", "المتفرد بوحدانيته في ذاته وصفاته", "The One, the All Inclusive, the Indivisible"),
    AsmaulHusna(68, "الصَّمَدُ", "السيد المقصود في الحوائج كلها", "The Self Sufficient, the Impregnable"),
    AsmaulHusna(69, "الْقَادِرُ", "الذي يفعل ما يشاء بقدرته الشاملة", "The All Able"),
    AsmaulHusna(70, "الْمُقْتَدِرُ", "العظيم القدرة الذي لا يمتنع عليه شيء", "The All Determiner, the Dominant"),
    AsmaulHusna(71, "الْمُقَدِّمُ", "الذي يقدم من يشاء بفضله وحكمته", "The Expediter, He who brings forward"),
    AsmaulHusna(72, "الْمُؤَخِّرُ", "الذي يؤخر من يشاء بعدله وحكمته", "The Delayer, He who puts far away"),
    AsmaulHusna(73, "الْأَوَّلُ", "الذي ليس قبله شيء، الباقي أزلا", "The First"),
    AsmaulHusna(74, "الْآخِرُ", "الذي ليس بعده شيء، الباقي أبدا", "The Last"),
    AsmaulHusna(75, "الظَّاهِرُ", "الذي لا يخفى وجوده، الغالب على كل شيء", "The Manifest, the Evident, the Outer"),
    AsmaulHusna(76, "الْبَاطِنُ", "الذي لا تدركه الأبصار، المحيط بالبواطن", "The Hidden, the Unmanifest, the Inner"),
    AsmaulHusna(77, "الْوَالِي", "المالك المتصرف في جميع الأشياء", "The Patron"),
    AsmaulHusna(78, "الْمُتَعَالِي", "المنزه عن كل نقص، المرتفع عن صفات الخلق", "The Supremely Exalted, the Most High"),
    AsmaulHusna(79, "الْبَرُّ", "كثير الخير والعطاء، المحسن إلى عباده", "The Good, the Beneficent"),
    AsmaulHusna(80, "التَّوَّابُ", "الذي يقبل التوبة ويعفو عن السيئات", "The Ever Returning, Ever Relenting"),
    AsmaulHusna(81, "الْمُنْتَقِمُ", "الذي يعاقب العصاة والمتجبرين بعدله", "The Avenger"),
    AsmaulHusna(82, "الْعَفُوُّ", "الذي يمحو السيئات ويتجاوز عن الزلات", "The Pardoner, the Effacer of Sins"),
    AsmaulHusna(83, "الرَّءُوفُ", "كثير الرحمة والرأفة بعباده", "The Compassionate, the All Pitying"),
    AsmaulHusna(84, "مَالِكُ الْمُلْكِ", "المتصرف في ملكه كيف يشاء", "The Owner of All Sovereignty"),
    AsmaulHusna(85, "ذُو الْجَلَالِ وَالْإِكْرَامِ", "المستحق للتعظيم والتكريم المطلق", "The Lord of Majesty and Generosity"),
    AsmaulHusna(86, "الْمُقْسِطُ", "العادل في حكمه وفعله", "The Equitable, the Requiter"),
    AsmaulHusna(87, "الْجَامِعُ", "الذي يجمع الخلائق ليوم لا ريب فيه", "The Gatherer, the Unifier"),
    AsmaulHusna(88, "الْغَنِيُّ", "المستغني عن كل شيء سواه", "The All Rich, the Independent"),
    AsmaulHusna(89, "الْمُغْنِي", "الذي يعطي عباده ما يغنيهم", "The Enricher, the Emancipator"),
    AsmaulHusna(90, "الْمَانِعُ", "الذي يمنع بفضله ما يشاء عمن يشاء", "The Withholder, the Shielder, the Defender"),
    AsmaulHusna(91, "الضَّارُّ", "الذي يمس بالضر من يشاء بحكمته", "The Distresser, the Harmer"),
    AsmaulHusna(92, "النَّافِعُ", "الذي ينفع من يشاء بفضله ورحمته", "The Propitious, the Benefactor"),
    AsmaulHusna(93, "النُّورُ", "الذي ينير السماوات والأرض ويهدي القلوب", "The Light"),
    AsmaulHusna(94, "الْهَادِي", "الذي يرشد خلقه إلى الحق والصواب", "The Guide"),
    AsmaulHusna(95, "الْبَدِيعُ", "الذي أبدع الأشياء على غير مثال سابق", "The Incomparable, the Originator"),
    AsmaulHusna(96, "الْبَاقِي", "دائم الوجود الذي لا يفنى أبدا", "The Ever Enduring and Immutable"),
    AsmaulHusna(97, "الْوَارِثُ", "الذي يبقى بعد فناء الخلائق كلها", "The Heir, the Inheritor of All"),
    AsmaulHusna(98, "الرَّشِيدُ", "الذي يرشد إلى الحق ويهدي إليه", "The Guide, Infallible Teacher, and Knower"),
    AsmaulHusna(99, "الصَّبُورُ", "الذي لا يعجل بالعقوبة، ويؤخرها لحكمة", "The Patient, the Timeless")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmaulHusnaScreen(onBack: () -> Unit) {
    val goldAccent = Color(0xFFC5A059)
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)
    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF0F4F0), Color.White)
    )

    var selectedName by remember { mutableStateOf<AsmaulHusna?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "أسماء الله الحسنى",
                        fontWeight = FontWeight.Bold,
                        color = primaryGreen
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "عودة",
                            tint = primaryGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF0F4F0)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(paddingValues)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(asmaulHusnaList) { asma ->
                    AsmaulHusnaCard(
                        asma = asma,
                        goldAccent = goldAccent,
                        textDark = textDark,
                        onClick = { selectedName = asma }
                    )
                }
            }
        }

        // Selected Name Dialog
        selectedName?.let { asma ->
            AlertDialog(
                onDismissRequest = { selectedName = null },
                containerColor = Color(0xFFF8F9F8),
                shape = RoundedCornerShape(24.dp),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFDF7E7),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = asma.id.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold, color = goldAccent
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            text = asma.name,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold, color = primaryGreen, fontSize = 42.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = asma.enMeaning,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = goldAccent
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = asma.meaning,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = textDark,
                                fontSize = 20.sp,
                                lineHeight = 32.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedName = null },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إغلاق", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }
    }
}

val primaryGreen = Color(0xFF004d40)

@Composable
fun AsmaulHusnaCard(
    asma: AsmaulHusna,
    goldAccent: Color,
    textDark: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = asma.id.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = goldAccent,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = asma.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = primaryGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
