package com.example.quranapp.presentation.bukhari

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.data.bukhari.BukhariKitab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithListScreen(
    kitabNumber: Int,
    onBack: () -> Unit,
    viewModel: BukhariViewModel = viewModel()
) {
    val state by viewModel.hadithListState.collectAsState()
    val kitab = viewModel.repository.kitabs.find { it.number == kitabNumber }

    val primaryGreen = Color(0xFF004d40)
    val goldAccent = Color(0xFFB8860B)
    val lightBg = Color(0xFFFAF7F0)
    val textDark = Color(0xFF2C1F0E)
    val textGray = Color(0xFF7F8C8D)

    LaunchedEffect(kitabNumber) {
        if (kitab != null && (state.hadiths.isEmpty() || state.kitab?.number != kitabNumber)) {
            viewModel.loadHadithsForKitab(kitab)
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        containerColor = lightBg,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            kitab?.nameArabic ?: "الكتاب",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = primaryGreen,
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                        )
                        Text(
                            "${kitab?.hadithCount ?: 0} حديث",
                            fontSize = 12.sp,
                            color = textGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = primaryGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = primaryGreen, modifier = Modifier.size(48.dp))
                        Text("جارٍ تحميل الأحاديث...", color = textGray, fontSize = 14.sp)
                    }
                }
            } else if (state.hadiths.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = textGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("لا توجد أحاديث للعرض", color = textGray, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(state.hadiths) { index, hadith ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn()
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(modifier = Modifier.padding(0.dp)) {
                                    // Hadith Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                            .background(Color(0xFFF5E6C8))
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            hadith.reference,
                                            fontSize = 12.sp,
                                            color = textGray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "حديث رقم",
                                                fontSize = 12.sp,
                                                color = textGray
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(goldAccent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "${hadith.number}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    // Hadith Text
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White)
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = hadith.text,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = textDark,
                                                fontSize = 16.sp,
                                                lineHeight = 28.sp,
                                                fontWeight = FontWeight.Normal,
                                                textDirection = TextDirection.Rtl
                                            ),
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Bottom green accent line
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                            .background(primaryGreen.copy(alpha = 0.15f))
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
