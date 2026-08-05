package com.example.quranapp.presentation.settings

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranapp.core.utils.QuranPreferences

data class AdhanOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val assetFileName: String? = null,
    val uriString: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primaryGreen = Color(0xFF004d40)
    val goldColor = Color(0xFFC5A059)
    val lightBg = Color(0xFFF8F9F8)
    val cardBg = Color.White
    val textDark = Color(0xFF2C3E2D)
    val textGray = Color(0xFF7F8C8D)
    val iconBg = Color(0xFFE8F5E9)

    var volume by remember { mutableStateOf(QuranPreferences.getAdhanVolume(context)) }
    var selectedAdhanId by remember { mutableStateOf(QuranPreferences.getSelectedAdhanId(context)) }
    var customAdhanUri by remember { mutableStateOf(QuranPreferences.getCustomAdhanUri(context)) }
    var customAdhanName by remember { mutableStateOf(QuranPreferences.getCustomAdhanName(context)) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingAdhanId by remember { mutableStateOf<String?>(null) }

    val defaultAdhans = remember {
        listOf(
            AdhanOption("makkah", "أذان مكة", "من ملف Adhan-Makkah", "adhan/Adhan-Makkah.mp3"),
            AdhanOption("madinah", "أذان المدينة", "من ملف Adhan-Madinah", "adhan/Adhan-Madinah.mp3"),
            AdhanOption("abdulbasit", "عبد الباسط", "أذان بصوت عبد الباسط", "adhan/Abdul-Basit.mp3"),
            AdhanOption("minshawi", "المنشاوي", "أذان بصوت المنشاوي", "adhan/Minshawi.mp3"),
            AdhanOption("mishary", "مشاري راشد العفاسي", "أذان بصوت مشاري راشد العفاسي", "adhan/Mishary Rashid Alafasy.mp3"),
            AdhanOption("nasser", "ناصر القطامي", "أذان بصوت ناصر القطامي", "adhan/Nasser AL Qatami.mp3"),
            AdhanOption("yusuf", "يوسف إسلام", "أذان بصوت يوسف إسلام", "adhan/Yusuf-Islam.mp3")
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val flags = result.data?.flags?.let { it and Intent.FLAG_GRANT_READ_URI_PERMISSION } ?: 0
                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    Log.w("ADHAN_SETTINGS", "Could not persist URI permission", e)
                }
                val uriStr = uri.toString()
                val fileName = uri.lastPathSegment ?: "ملف صوتي مخصص"
                customAdhanUri = uriStr
                customAdhanName = fileName
                selectedAdhanId = "custom"
                QuranPreferences.saveCustomAdhan(context, uriStr, fileName)
                QuranPreferences.saveSelectedAdhanId(context, "custom")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    fun playPreview(adhan: AdhanOption) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (adhan.uriString != null) {
                    setDataSource(context, android.net.Uri.parse(adhan.uriString))
                } else if (adhan.assetFileName != null) {
                    val afd = context.assets.openFd(adhan.assetFileName)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
                val vol = volume / 100f
                setVolume(vol, vol)
                prepare()
                start()
                setOnCompletionListener {
                    playingAdhanId = null
                }
            }
            playingAdhanId = adhan.id
        } catch (e: Exception) {
            playingAdhanId = null
        }
    }

    fun stopPreview() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
        playingAdhanId = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "الإعدادات الأذان",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        stopPreview()
                        onBack()
                    }) {
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "حجم صوت الأذان",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen,
                            fontSize = 18.sp
                        )
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                type = "audio/*"
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            filePickerLauncher.launch(intent)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                    text = "إضافة أذان من الهاتف",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark,
                                        fontSize = 16.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = customAdhanName ?: "اختر ملفا صوتيا ليستعمله التطبيق للأذان",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textGray,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = iconBg,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = "Add custom adhan",
                                        tint = primaryGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                text = "${volume.toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = primaryGreen
                                )
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "حجم صوت الأذان",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark,
                                        fontSize = 16.sp
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Volume",
                                    tint = primaryGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Slider(
                            value = volume,
                            onValueChange = { newVal ->
                                volume = newVal
                                QuranPreferences.saveAdhanVolume(context, newVal)
                                mediaPlayer?.let { player ->
                                    val vol = newVal / 100f
                                    try { player.setVolume(vol, vol) } catch (e: Exception) {}
                                }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryGreen,
                                activeTrackColor = primaryGreen,
                                inactiveTrackColor = Color(0xFFD8E7D8)
                            )
                        )
                    }
                }
            }

            if (customAdhanUri != null) {
                item {
                    val customOption = AdhanOption("custom", customAdhanName ?: "أذان مخصص", "من ملفات الهاتف محلياً", null, customAdhanUri)
                    val isSelected = selectedAdhanId == "custom"
                    val isPlaying = playingAdhanId == "custom"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAdhanId = "custom"
                                QuranPreferences.saveSelectedAdhanId(context, "custom")
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFE8F5E9) else cardBg
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
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedAdhanId = "custom"
                                    QuranPreferences.saveSelectedAdhanId(context, "custom")
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = primaryGreen)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = customOption.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textDark,
                                            fontSize = 16.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = customOption.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textGray,
                                            fontSize = 12.sp
                                        ),
                                        textAlign = TextAlign.End
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = iconBg,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable {
                                            if (isPlaying) stopPreview() else playPreview(customOption)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                                            contentDescription = "Preview",
                                            tint = primaryGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(defaultAdhans) { adhan ->
                val isSelected = selectedAdhanId == adhan.id
                val isPlaying = playingAdhanId == adhan.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedAdhanId = adhan.id
                            QuranPreferences.saveSelectedAdhanId(context, adhan.id)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFE8F5E9) else cardBg
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
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedAdhanId = adhan.id
                                QuranPreferences.saveSelectedAdhanId(context, adhan.id)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryGreen)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = adhan.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textDark,
                                        fontSize = 16.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = adhan.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textGray,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = iconBg,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        if (isPlaying) stopPreview() else playPreview(adhan)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                                        contentDescription = "Preview",
                                        tint = primaryGreen,
                                        modifier = Modifier.size(22.dp)
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
