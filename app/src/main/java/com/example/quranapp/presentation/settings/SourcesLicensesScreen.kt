package com.example.quranapp.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesLicensesScreen(
    onBack: () -> Unit,
    viewModel: SourcesLicensesViewModel? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm = viewModel ?: viewModel { SourcesLicensesViewModel(context) }
    val primaryGreen = Color(0xFF004d40)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)

    val state by vm.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "المصادر والتراخيص",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        },
        containerColor = lightBg
    ) { paddingValues ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryGreen)
                }
            }
            state.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا تتوفر معلومات عن المصادر حالياً",
                        color = textGray,
                        fontSize = 14.sp
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "تُحضَّر النصوص مرة واحدة من مصادر معتمدة وتُستضاف على بنيتنا التحتية؛ التطبيق لا يعتمد على أي واجهات برمجية خارجية في وقت التشغيل (السياسة الرسمية §13).",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textGray,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(state.items, key = { it.meta.id }) { item ->
                        SourceLicenseCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceLicenseCard(item: SourceLicenseItem) {
    val primaryGreen = Color(0xFF004d40)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)

    Card(
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
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = item.meta.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textDark,
                            fontSize = 16.sp
                        )
                    )
                    if (item.meta.nameLatin.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.meta.nameLatin,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textGray,
                                fontSize = 12.sp
                            ),
                            textAlign = TextAlign.End
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(item = item)
            }

            Spacer(modifier = Modifier.height(12.dp))

            FieldRow(label = "المؤلف", value = item.meta.author)
            if (item.meta.source.isNotBlank()) FieldRow(label = "المصدر", value = item.meta.source)
            if (item.meta.edition.isNotBlank()) FieldRow(label = "الطبعة", value = item.meta.edition)
            if (item.meta.publisher.isNotBlank()) FieldRow(label = "الناشر", value = item.meta.publisher)
            FieldRow(label = "الترخيص", value = item.meta.license)
            if (item.meta.website.isNotBlank()) FieldRow(label = "الموقع الرسمي", value = item.meta.website)
            FieldRow(label = "الإصدار", value = item.installedVersion ?: item.meta.version)
            if (item.meta.copyrightNotice.isNotBlank()) FieldRow(label = "ملاحظة حقوق", value = item.meta.copyrightNotice)
        }
    }
}

@Composable
private fun StatusChip(item: SourceLicenseItem) {
    val installed = item.installed
    val bundled = item.meta.bundled
    val (background, content, label) = when {
        bundled -> Triple(Color(0xFFE0F2F1), Color(0xFF004d40), "مثبت مع التطبيق")
        installed -> Triple(Color(0xFFE8F5E9), Color(0xFF004d40), "مثبت")
        else -> Triple(Color(0xFFF1F3F2), Color(0xFF7F8C8D), "غير مثبت")
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = background
    ) {
        Text(
            text = label,
            color = content,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    val textGray = Color(0xFF7F8C8D)
    val textDark = Color(0xFF2C3E2D)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = textDark,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            ),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = textGray,
                fontSize = 12.sp
            )
        )
    }
}
