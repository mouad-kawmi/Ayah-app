package com.example.quranapp.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MenuBook

enum class BottomTab { HOME, QURAN, AZKAR, SEARCH, SETTINGS }

@Composable
fun BottomNavigationBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit
) {
    val primaryGreen = Color(0xFF004d40)
    val lightGreenIndicator = Color(0xFFD8E7D8)

    // Order from Right to Left: المزيد، البحث، الأذكار، القرآن، الرئيسية
    val items = listOf(
        Triple(BottomTab.SETTINGS, "المزيد", Icons.Default.Menu),
        Triple(BottomTab.SEARCH, "البحث", Icons.Default.Search),
        Triple(BottomTab.AZKAR, "الأذكار", Icons.Default.DateRange),
        Triple(BottomTab.QURAN, "القرآن", Icons.Default.MenuBook),
        Triple(BottomTab.HOME, "الرئيسية", Icons.Default.Home)
    )

    NavigationBar(
        containerColor = Color(0xFFF7F9F7),
        tonalElevation = 0.dp
    ) {
        items.forEach { (tab, label, icon) ->
            val selected = selectedTab == tab

            NavigationBarItem(
                icon = {
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = lightGreenIndicator,
                            modifier = Modifier.size(width = 56.dp, height = 32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = primaryGreen)
                            }
                        }
                    } else {
                        Icon(icon, contentDescription = null, tint = Color.Gray)
                    }
                },
                label = {
                    Text(
                        text = label,
                        color = if (selected) primaryGreen else Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = selected,
                onClick = {
                    if (selectedTab != tab) onTabSelected(tab)
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
