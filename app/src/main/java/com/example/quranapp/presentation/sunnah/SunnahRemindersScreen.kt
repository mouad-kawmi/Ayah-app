package com.example.quranapp.presentation.sunnah

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SunnahRemindersScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SunnahRemindersViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SunnahRemindersViewModel(context) as T
            }
        }
    )

    val enabledReminders by viewModel.enabledReminders.collectAsState()
    viewModel.reminderTimes.collectAsState()
    var pendingTimeReminder by remember { mutableStateOf<SunnahReminder?>(null) }
    val primaryGreen = Color(0xFF165A4A)
    val bgColor = Color(0xFFF7F9F7)

    // Show the native Material time picker when the user taps a reminder time.
    LaunchedEffect(pendingTimeReminder) {
        val pending = pendingTimeReminder ?: return@LaunchedEffect
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                viewModel.setReminderTime(pending.id, hourOfDay, minute)
            },
            pending.hour,
            pending.minute,
            DateFormat.is24HourFormat(context)
        ).apply {
            setOnDismissListener { pendingTimeReminder = null }
            show()
        }
    }

    // Using composition local to force RTL for Arabic layout just in case
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "تنبيهات السنن",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward, // RTL back arrow is ArrowForward
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
                )
            },
            containerColor = bgColor
        ) { paddingValues ->

            val groupedReminders = viewModel.reminders.groupBy { it.category }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedReminders.forEach { (category, reminders) ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = primaryGreen,
                                    fontSize = 18.sp
                                )
                            )
                        }
                    }

                    items(reminders) { reminder ->
                        val isEnabled = enabledReminders.contains(reminder.id)
                        ReminderCard(
                            reminder = reminder,
                            isEnabled = isEnabled,
                            onToggle = { checked ->
                                viewModel.toggleReminder(reminder.id, checked)
                            },
                            onTimeClick = { pendingTimeReminder = reminder }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: SunnahReminder,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    val primaryGreen = Color(0xFF165A4A)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon on far right (but since we are in RTL, it will be the rightmost visually because Row lays out left-to-right logically in code, but RTL reverses it.
            // Wait, in RTL, the FIRST element is on the right! 
            // The image shows the icon on the right. So it should be the first element.
            // Then the text column in the middle.
            // Then the switch on the far left.
            
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(reminder.iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForType(reminder.iconType),
                    contentDescription = null,
                    tint = reminder.iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E2D),
                        fontSize = 18.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = reminder.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF7F8C8D),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(primaryGreen.copy(alpha = 0.12f))
                        .clickable(onClick = onTimeClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = primaryGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminder.timeText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = primaryGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تغيير الوقت",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = primaryGreen,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Switch on the far left (which is the last element in RTL)
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = primaryGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFBDBDBD)
                ),
                modifier = Modifier.scale(0.9f)
            )
        }
    }
}

@Composable
fun getIconForType(type: IconType): androidx.compose.ui.graphics.vector.ImageVector {
    return when(type) {
        IconType.BOOK -> Icons.Default.MenuBook
        IconType.HEART -> Icons.Default.Favorite
        IconType.FASTING -> Icons.Default.CheckCircle // placeholder since we don't have non-standard icons
        IconType.MOON -> Icons.Default.Star // placeholder for moon
        IconType.SUN -> Icons.Default.Star // placeholder for sun
        IconType.SUNSET -> Icons.Default.Star // placeholder for sunset
        IconType.QURAN_BOOK -> Icons.Default.MenuBook
    }
}
