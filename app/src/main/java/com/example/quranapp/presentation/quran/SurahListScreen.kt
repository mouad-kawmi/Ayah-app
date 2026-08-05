package com.example.quranapp.presentation.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.domain.model.Surah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahListScreen(
    viewModel: SurahListViewModel = viewModel(),
    onSurahClick: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryGreen = Color(0xFF004d40)
    val lightGreenBg = Color(0xFFF4F7F4)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "سور القرآن الكريم",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        },
        containerColor = lightGreenBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.surahs, key = { it.id }) { surah ->
                SurahListItem(surah = surah, onClick = { onSurahClick(surah.id) })
            }
        }
    }
}

@Composable
fun SurahListItem(surah: Surah, onClick: () -> Unit) {
    val primaryGreen = Color(0xFF004d40)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right side: Surah Name & Details (Arabic layout)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = surah.nameArabic,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray,
                        fontSize = 18.sp
                    ),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(4.dp))
                val placeText = if (surah.revelationPlace.lowercase() == "makkah") "Makkah" else "Madinah"
                Text(
                    text = "$placeText • ${surah.numberOfAyahs} آية",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.Gray,
                        fontSize = 13.sp
                    ),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Left side: Surah Number inside a stylish circle
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color(0xFFF0F4F0),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryGreen.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = surah.id.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen
                        )
                    )
                }
            }
        }
    }
}
