package com.example.quranapp.presentation.tafsir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.data.resource.ResourceInstallState
import com.example.quranapp.data.resource.ResourceListItem
import com.example.quranapp.data.resource.ResourceMeta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TafsirManagerScreen(
    onBack: () -> Unit,
    viewModel: TafsirManagerViewModel? = null
) {
    val context = LocalContext.current
    val vm = viewModel ?: viewModel { TafsirManagerViewModel(context) }
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
                            text = "التفاسير",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.offline) {
                Surface(
                    color = Color(0xFFFFF3CD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا يوجد اتصال بالإنترنت — تُعرض النسخ المحفوظة فقط",
                        color = Color(0xFF856404),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "التفسير الميسر مثبت مع التطبيق ولا يمكن حذفه",
                    style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 12.sp)
                )
                IconButton(onClick = { vm.refresh() }) {
                    if (state.refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = primaryGreen)
                    }
                }
            }

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = primaryGreen)
                    }
                }
                state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد تفاسير متاحة حالياً",
                            color = textGray,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.items, key = { it.meta.id }) { item ->
                            TafsirCard(
                                item = item,
                                onDownload = { vm.download(item.meta) },
                                onCancel = { vm.cancel(item.meta) },
                                onDelete = { vm.delete(item.meta) },
                                onSelect = { vm.select(item.meta) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TafsirCard(
    item: ResourceListItem,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val meta = item.meta
    val primaryGreen = Color(0xFF004d40)
    val goldColor = Color(0xFF8D5B2C)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textDark,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${meta.author} · ${languageLabel(meta.language)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 12.sp)
                    )
                }
                if (item.isSelected || item.updateAvailable) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isSelected) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "المحدد",
                                    color = primaryGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (item.updateAvailable) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF5E9DA)
                            ) {
                                Text(
                                    text = "تحديث متوفر",
                                    color = goldColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (item.state) {
                        ResourceInstallState.INSTALLED ->
                            "الإصدار ${item.installedVersion ?: meta.version}"
                        ResourceInstallState.DOWNLOADING -> "جاري التحميل..."
                        ResourceInstallState.ERROR -> "فشل التحميل"
                        ResourceInstallState.NOT_INSTALLED ->
                            "الحجم: ${formatBytes(meta.downloadSizeBytes)}"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 12.sp)
                )
                if (item.state == ResourceInstallState.INSTALLED) {
                    Text(
                        text = formatBytes(item.installedSizeBytes),
                        style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 12.sp)
                    )
                }
            }

            if (item.state == ResourceInstallState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = primaryGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(color = textGray, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }

            if (item.state == ResourceInstallState.ERROR && item.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.errorMessage,
                    color = Color(0xFFB00020),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (item.appUpdateRequired) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "يتطلب إصدار أحدث من التطبيق (${meta.minAppVersion})",
                    color = Color(0xFFB00020),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.state) {
                    ResourceInstallState.NOT_INSTALLED -> {
                        if (item.appUpdateRequired) {
                            Text(
                                text = "غير متاح",
                                color = textGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        } else {
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحميل", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    ResourceInstallState.DOWNLOADING -> {
                        TextButton(onClick = onCancel) {
                            Text("إلغاء", color = textGray, fontWeight = FontWeight.Bold)
                        }
                    }
                    ResourceInstallState.ERROR -> {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                        }
                    }
                    ResourceInstallState.INSTALLED -> {
                        if (item.updateAvailable && item.appUpdateRequired) {
                            Text(
                                text = "غير متاح",
                                color = textGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        } else if (item.updateAvailable && !item.meta.bundled) {
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحديث", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!item.isSelected) {
                            OutlinedButton(
                                onClick = onSelect,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, primaryGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryGreen)
                            ) {
                                Text("اختيار", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!item.meta.bundled) {
                            TextButton(onClick = onDelete) {
                                Text("حذف", color = Color(0xFFB00020), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun languageLabel(language: String): String = when (language) {
    "ar" -> "العربية"
    "en" -> "الإنجليزية"
    "fr" -> "الفرنسية"
    "tr" -> "التركية"
    "ur" -> "الأردية"
    "id" -> "الإندونيسية"
    "de" -> "الألمانية"
    else -> language
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 بايت"
    val units = arrayOf("بايت", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} ${units[unit]}" else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}
