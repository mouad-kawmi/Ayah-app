package com.example.quranapp.presentation.khatma

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranapp.core.utils.QuranPreferences
import com.example.quranapp.data.quran.Qcf4Repository

data class KhatmaDay(
    val dayNumber: Int,
    val startPage: Int,
    val endPage: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatmaScreen(
    initialDayNumber: Int? = null,
    onBack: () -> Unit,
    onNavigateToDay: (Int) -> Unit,
    onNavigateToQuranPage: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val primaryGreen = Color(0xFF004d40)
    val goldColor = Color(0xFFC5A059)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)

    var durationDays by remember { mutableStateOf(QuranPreferences.getKhatmaDuration(context)) }
    var readPages: Set<Int> by remember { mutableStateOf(QuranPreferences.getKhatmaReadPages(context)) }

    val repository = remember { Qcf4Repository(context) }
    val allSurahs = remember { repository.getSurahs() }

    val daysList = remember(durationDays) {
        val totalPages = 604
        val list = mutableListOf<KhatmaDay>()
        val count = durationDays
        val pagesPerDay = totalPages / count
        var currentStart = 1

        for (i in 1..count) {
            val end = if (i == count) totalPages else (currentStart + pagesPerDay - 1).coerceAtMost(totalPages)
            list.add(KhatmaDay(dayNumber = i, startPage = currentStart, endPage = end))
            currentStart = end + 1
            if (currentStart > totalPages) break
        }
        list
    }

    val totalReadCount = readPages.size
    val totalRemaining = (604 - totalReadCount).coerceAtLeast(0)
    val progressPercent = (totalReadCount.toFloat() / 604f).coerceIn(0f, 1f)

    val nextUnreadPage = (1..604).firstOrNull { !readPages.contains(it) } ?: 1
    val activeDay = daysList.find { nextUnreadPage in it.startPage..it.endPage } ?: daysList.firstOrNull()

    fun togglePageRead(page: Int) {
        val newSet = readPages.toMutableSet()
        if (newSet.contains(page)) newSet.remove(page) else newSet.add(page)
        readPages = newSet
        QuranPreferences.saveKhatmaReadPages(context, newSet)
    }

    fun markNextAsReadAndAdvance() {
        togglePageRead(nextUnreadPage)
    }

    val isDetailView = initialDayNumber != null
    val currentDay = if (isDetailView) daysList.find { it.dayNumber == initialDayNumber } else null

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "الختمة",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        },
        containerColor = lightBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isDetailView) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = primaryGreen),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = goldColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "ختمة $durationDays يوم",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 18.sp
                                        )
                                    )
                                }

                                Text(
                                    text = "${(progressPercent * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = goldColor,
                                        fontSize = 20.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            LinearProgressIndicator(
                                progress = { progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = goldColor,
                                trackColor = Color(0xFF00332C)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "المقروء",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$totalReadCount/604",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "الباقي",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$totalRemaining",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "الصفحة التالية",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$nextUnreadPage",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = goldColor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { markNextAsReadAndAdvance() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = cardBg)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = primaryGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "علم الصفحة $nextUnreadPage وانتقل إلى التالية",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textDark
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val activeDayPages = activeDay?.let { (it.startPage..it.endPage).toList() } ?: emptyList()
                    val activeDayReadCount = activeDayPages.count { readPages.contains(it) }
                    val activeDayTotal = activeDayPages.size

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFDF7E7),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = goldColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = "ما المطلوب الآن؟",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val dayNum = activeDay?.dayNumber ?: 1
                                val start = activeDay?.startPage ?: 1
                                val end = activeDay?.endPage ?: 10
                                Text(
                                    text = "أكمل صفحات اليوم $dayNum: من $start حتى $end. الصفحة التالية هي $nextUnreadPage.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = textGray),
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "منجزة اليوم: $activeDayReadCount/$activeDayTotal",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = primaryGreen
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            readPages = emptySet()
                            QuranPreferences.saveKhatmaReadPages(context, emptySet())
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = primaryGreen)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 30, 60).forEach { days ->
                                val isSelected = durationDays == days
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { durationDays = days; QuranPreferences.saveKhatmaDuration(context, days) },
                                    label = { Text("$days يوم") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = primaryGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                items(daysList) { day ->
                    val dayPages = (day.startPage..day.endPage).toList()
                    val readInDay = dayPages.count { readPages.contains(it) }
                    val dayProgress = if (dayPages.isNotEmpty()) readInDay.toFloat() / dayPages.size.toFloat() else 0f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDay(day.dayNumber) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${day.startPage}-${day.endPage}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "اليوم ${day.dayNumber}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { dayProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = goldColor,
                                trackColor = Color(0xFFF0F4F0)
                            )
                        }
                    }
                }
            } else {
                if (currentDay != null) {
                    val dayPages = (currentDay.startPage..currentDay.endPage).toList()
                    val dayReadCount = dayPages.count { readPages.contains(it) }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
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
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF0F4F0)
                                ) {
                                    Text(
                                        text = "$dayReadCount/${dayPages.size}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryGreen
                                        )
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "اليوم ${currentDay.dayNumber}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textDark,
                                            fontSize = 18.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "من الصفحة ${currentDay.startPage} حتى ${currentDay.endPage}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = textGray)
                                    )
                                }
                            }
                        }
                    }

                    items(dayPages) { pageNum ->
                        val isChecked = readPages.contains(pageNum)
                        val meta = remember(pageNum) { repository.getPageMetadata(pageNum) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val surah = allSurahs.find { pageNum in it.firstPage..it.lastPage } ?: allSurahs[0]
                                    onNavigateToQuranPage(surah.id, pageNum)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) Color(0xFFF0F7F0) else cardBg
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { togglePageRead(pageNum) },
                                    colors = CheckboxDefaults.colors(checkedColor = primaryGreen)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "الصفحة $pageNum",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isChecked) primaryGreen else textDark,
                                                fontSize = 16.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = meta.surahNameArabic,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = textGray,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF0F4F0),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.MenuBook,
                                                contentDescription = null,
                                                tint = primaryGreen,
                                                modifier = Modifier.size(20.dp)
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
