package com.example.quranapp.presentation.bukhari

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quranapp.data.bukhari.BukhariKitab
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SahihBukhariScreen(
    onBack: () -> Unit,
    onNavigateToKitab: (kitabNumber: Int) -> Unit,
    viewModel: BukhariViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val primaryGreen = Color(0xFF004d40)
    val goldAccent = Color(0xFFB8860B)
    val lightBg = Color(0xFFF8F5EE)
    val cardBg = Color.White
    val textDark = Color(0xFF2C1F0E)
    val textGray = Color(0xFF7F8C8D)

    Scaffold(
        containerColor = lightBg,
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "صحيح البخاري",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = primaryGreen
                        )
                        Text(
                            "الجامع المسند الصحيح",
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
                actions = {
                    if (uiState.isDownloaded) {
                        IconButton(onClick = { viewModel.showDeleteConfirm() }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Delete", tint = Color(0xFFB00020))
                        }
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
            when {
                uiState.isDownloading -> DownloadingState(uiState.downloadProgress, primaryGreen, textDark, textGray)
                !uiState.isDownloaded -> PreDownloadState(
                    onDownload = { viewModel.startDownload() },
                    hasError = uiState.downloadError,
                    primaryGreen = primaryGreen,
                    goldAccent = goldAccent,
                    textDark = textDark,
                    textGray = textGray
                )
                else -> KitabListState(
                    kitabs = uiState.kitabs,
                    fileSizeMB = uiState.fileSizeMB,
                    primaryGreen = primaryGreen,
                    goldAccent = goldAccent,
                    textDark = textDark,
                    textGray = textGray,
                    cardBg = cardBg,
                    onKitabClick = { kitab -> onNavigateToKitab(kitab.number) }
                )
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFB00020),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "حذف صحيح البخاري",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C1F0E),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "سيتم حذف ملف صحيح البخاري وتحرير ${String.format("%.1f", uiState.fileSizeMB)} ميغابايت من مساحة هاتفك. يمكنك إعادة تحميله في أي وقت.",
                    color = Color(0xFF7F8C8D),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownload()
                        viewModel.dismissDeleteConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حذف الملف", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissDeleteConfirm() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إلغاء", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun PreDownloadState(
    onDownload: () -> Unit,
    hasError: Boolean,
    primaryGreen: Color,
    goldAccent: Color,
    textDark: Color,
    textGray: Color
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            // Book Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFF5E6C8), Color(0xFFEDD9A3))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = goldAccent,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Title
            Text(
                "صحيح البخاري",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textDark
            )
            Text(
                "الجامع المسند الصحيح المختصر من أمور رسول الله ﷺ وسننه وأيامه",
                fontSize = 14.sp,
                color = textGray,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )

            // Info Cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoChip("7563", "حديثاً", Icons.Default.FormatListNumbered, primaryGreen, Modifier.weight(1f))
                InfoChip("97", "كتاباً", Icons.Default.LibraryBooks, goldAccent, Modifier.weight(1f))
            }

            // Storage Warning Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(24.dp).padding(top = 2.dp)
                    )
                    Column {
                        Text(
                            "معلومات التحميل",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFE65100)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• حجم الملف: حوالي 9.3 ميغابايت\n• سيُحفظ على مساحة هاتفك (وليس داخل التطبيق)\n• يمكنك حذفه في أي وقت لتحرير المساحة\n• مطلوب اتصال بالإنترنت مرة واحدة فقط",
                            fontSize = 13.sp,
                            color = Color(0xFF795548),
                            style = LocalTextStyle.current.copy(
                                textDirection = TextDirection.Rtl,
                                lineHeight = 22.sp
                            )
                        )
                    }
                }
            }

            if (hasError) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFB00020))
                        Text("فشل التحميل. تحقق من اتصالك بالإنترنت وحاول مجدداً.", color = Color(0xFFB00020), fontSize = 13.sp)
                    }
                }
            }

            // Download Button
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                elevation = ButtonDefaults.buttonElevation(4.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hasError) "إعادة المحاولة" else "تحميل صحيح البخاري كاملاً",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DownloadingState(
    progress: Float,
    primaryGreen: Color,
    textDark: Color,
    textGray: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "download_progress"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(96.dp),
                    color = primaryGreen,
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    "${(animatedProgress * 100).roundToInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = primaryGreen
                )
            }

            Text(
                "جارٍ تحميل صحيح البخاري...",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textDark
            )
            Text(
                "يرجى الانتظار. لا تغلق التطبيق.",
                fontSize = 14.sp,
                color = textGray
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = primaryGreen,
                trackColor = Color(0xFFE8F5E9)
            )
        }
    }
}

@Composable
private fun KitabListState(
    kitabs: List<BukhariKitab>,
    fileSizeMB: Double,
    primaryGreen: Color,
    goldAccent: Color,
    textDark: Color,
    textGray: Color,
    cardBg: Color,
    onKitabClick: (BukhariKitab) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header stats card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = primaryGreen),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem("7563", "حديث", Color.White)
                    Divider(
                        modifier = Modifier.height(40.dp).width(1.dp),
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    StatItem("97", "كتاب", Color.White)
                    Divider(
                        modifier = Modifier.height(40.dp).width(1.dp),
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    StatItem("${String.format("%.1f", fileSizeMB)}", "ميغابايت", Color.White)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "الكتب (${kitabs.size})",
                    fontWeight = FontWeight.Bold,
                    color = primaryGreen,
                    fontSize = 16.sp
                )
            }
        }

        items(kitabs) { kitab ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 2 }
            ) {
                KitabCard(
                    kitab = kitab,
                    primaryGreen = primaryGreen,
                    goldAccent = goldAccent,
                    textDark = textDark,
                    textGray = textGray,
                    cardBg = cardBg,
                    onClick = { onKitabClick(kitab) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KitabCard(
    kitab: BukhariKitab,
    primaryGreen: Color,
    goldAccent: Color,
    textDark: Color,
    textGray: Color,
    cardBg: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = textGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        kitab.nameArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textDark,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${kitab.hadithCount} حديث  •  من ${kitab.startHadith} إلى ${kitab.endHadith}",
                        fontSize = 12.sp,
                        color = textGray
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF8E1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${kitab.number}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = goldAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text(label, fontSize = 12.sp, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontSize = 12.sp, color = color.copy(alpha = 0.8f))
    }
}
