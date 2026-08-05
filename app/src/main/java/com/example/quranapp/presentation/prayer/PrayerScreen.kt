package com.example.quranapp.presentation.prayer

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.data.prayer.PrayerStateMachine
import java.time.format.DateTimeFormatter

data class PrayerTimeInfo(
    val name: String,
    val time: String,
    val icon: ImageVector
)

private fun iconForPrayer(name: String): ImageVector = when (name) {
    "الفجر" -> Icons.Default.Bedtime
    "الشروق" -> Icons.Default.WbSunny
    "الظهر" -> Icons.Default.BrightnessHigh
    "العصر" -> Icons.Default.Cloud
    "المغرب" -> Icons.Default.WbTwilight
    "العشاء" -> Icons.Default.Bedtime
    else -> Icons.Default.Schedule
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(
    onBack: () -> Unit
) {
    val prayerTimesViewModel: PrayerTimesViewModel = viewModel()
    val uiState by prayerTimesViewModel.uiState.collectAsState()
    val prayerCards by PrayerStateMachine.prayerCards.collectAsState()

    val primaryGreen = Color(0xFF004d40)
    val goldColor = Color(0xFFC5A059)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val iconBg = Color(0xFFE8F5E9)

    val prayers = prayerCards.map {
        PrayerTimeInfo(it.name, it.time.format(DateTimeFormatter.ofPattern("HH:mm")), iconForPrayer(it.name))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "مواقيت الصلاة",
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
                actions = {
                    IconButton(onClick = { prayerTimesViewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        },
        containerColor = lightBg
    ) { paddingValues ->
        if (uiState.isLoading && prayers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = primaryGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري تحميل مواقيت الصلاة",
                        style = MaterialTheme.typography.bodyLarge.copy(color = textDark)
                    )
                }
            }
        } else if (prayers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "لا تتوفر بيانات حالياً",
                        style = MaterialTheme.typography.bodyLarge.copy(color = textDark)
                    )
                    val errorMsg = uiState.error
                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB00020))
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(prayers) { prayer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prayer.time,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = goldColor,
                                    fontSize = 26.sp
                                )
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = prayer.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark,
                                        fontSize = 20.sp
                                    )
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = iconBg,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = prayer.icon,
                                            contentDescription = prayer.name,
                                            tint = primaryGreen,
                                            modifier = Modifier.size(24.dp)
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
