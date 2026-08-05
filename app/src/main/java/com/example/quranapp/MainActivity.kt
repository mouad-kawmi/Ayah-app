package com.example.quranapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.quranapp.core.navigation.Screen
import com.example.quranapp.core.ui.BottomNavigationBar
import com.example.quranapp.core.ui.BottomTab
import com.example.quranapp.core.ui.theme.QuranAppTheme
import com.example.quranapp.presentation.audio.AudioScreen
import com.example.quranapp.presentation.audio.DownloadedAudioScreen
import com.example.quranapp.presentation.home.HomeScreen
import com.example.quranapp.presentation.khatma.KhatmaScreen
import com.example.quranapp.presentation.prayer.AzkarScreen
import com.example.quranapp.presentation.prayer.PrayerScreen
import com.example.quranapp.presentation.qibla.QiblaScreen
import com.example.quranapp.presentation.quran.SurahDetailScreen
import com.example.quranapp.presentation.quran.SurahListScreen
import com.example.quranapp.presentation.search.SearchScreen
import com.example.quranapp.presentation.settings.AdhanSettingsScreen
import com.example.quranapp.presentation.settings.DeveloperToolsScreen
import com.example.quranapp.presentation.settings.LogViewerScreen
import com.example.quranapp.presentation.settings.SettingsScreen
import com.example.quranapp.presentation.bukhari.SahihBukhariScreen
import com.example.quranapp.presentation.bukhari.HadithListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuranAppTheme {
                val navController = rememberNavController()

                // Only NavHost for DEEP screens (no bottom bar)
                // Bottom tab screens are managed by MainScreen with a simple when()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route
                ) {
                    // MainScreen hosts all bottom tabs - only ONE tab is in composition at a time
                    composable(Screen.Home.route) {
                        MainScreen(onNavigateTo = { route -> navController.navigate(route) })
                    }

                    // --- Deep screens (full screen, no bottom bar) ---
                    composable(
                        route = Screen.QuranDetail.route,
                        arguments = listOf(
                            navArgument("surahId") { type = NavType.IntType },
                            navArgument("page") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("isKhatma") { type = NavType.BoolType; defaultValue = false }
                        )
                    ) { backStackEntry ->
                        val surahId = backStackEntry.arguments?.getInt("surahId") ?: 1
                        val pageArg = backStackEntry.arguments?.getInt("page") ?: -1
                        val isKhatma = backStackEntry.arguments?.getBoolean("isKhatma") ?: false
                        SurahDetailScreen(
                            surahId = surahId,
                            onBack = { navController.popBackStack() },
                            initialPageArg = if (pageArg != -1) pageArg else null,
                            isKhatmaMode = isKhatma
                        )
                    }
                    composable(Screen.Prayer.route) {
                        PrayerScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Audio.route) {
                        AudioScreen(
                            onNavigateToQuranDetail = { surahId, page ->
                                navController.navigate(Screen.QuranDetail.createRoute(surahId, page))
                            },
                            onNavigateToDownloads = {
                                navController.navigate(Screen.DownloadedAudio.route)
                            }
                        )
                    }
                    composable(Screen.DownloadedAudio.route) {
                        DownloadedAudioScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToQuranDetail = { surahId, page ->
                                navController.navigate(Screen.QuranDetail.createRoute(surahId, page))
                            }
                        )
                    }
                    composable(Screen.Qibla.route) {
                        QiblaScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.AdhanSettings.route) {
                        AdhanSettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Khatma.route) {
                        KhatmaScreen(
                            initialDayNumber = null,
                            onBack = { navController.popBackStack() },
                            onNavigateToDay = { dayNum -> navController.navigate(Screen.KhatmaDay.createRoute(dayNum)) },
                            onNavigateToQuranPage = { surahId, page ->
                                navController.navigate(Screen.QuranDetail.createRoute(surahId, page, isKhatma = true))
                            }
                        )
                    }
                    composable(
                        route = Screen.KhatmaDay.route,
                        arguments = listOf(navArgument("dayNumber") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val dayNum = backStackEntry.arguments?.getInt("dayNumber") ?: 1
                        KhatmaScreen(
                            initialDayNumber = dayNum,
                            onBack = { navController.popBackStack() },
                            onNavigateToDay = { _ -> },
                            onNavigateToQuranPage = { surahId, page ->
                                navController.navigate(Screen.QuranDetail.createRoute(surahId, page, isKhatma = true))
                            }
                        )
                    }
                    composable(Screen.SunnahReminders.route) {
                        com.example.quranapp.presentation.sunnah.SunnahRemindersScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.AsmaulHusna.route) {
                        com.example.quranapp.presentation.asmaulhusna.AsmaulHusnaScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.SahihBukhari.route) {
                        SahihBukhariScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToKitab = { kitabNumber ->
                                navController.navigate(Screen.HadithList.createRoute(kitabNumber))
                            }
                        )
                    }
                    composable(
                        route = Screen.HadithList.route,
                        arguments = listOf(navArgument("kitabNumber") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val kitabNumber = backStackEntry.arguments?.getInt("kitabNumber") ?: 1
                        HadithListScreen(
                            kitabNumber = kitabNumber,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.DeveloperTools.route) {
                        DeveloperToolsScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToLogViewer = {
                                navController.navigate(Screen.LogViewer.route)
                            }
                        )
                    }
                    composable(Screen.LogViewer.route) {
                        LogViewerScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.TafsirManager.route) {
                        com.example.quranapp.presentation.tafsir.TafsirManagerScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.SourcesLicenses.route) {
                        com.example.quranapp.presentation.settings.SourcesLicensesScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(onNavigateTo: (String) -> Unit) {
    // rememberSaveable preserves selected tab across config changes
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.HOME) }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab -> selectedTab = tab }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                BottomTab.HOME -> HomeScreen(
                    onNavigateToQuran = { selectedTab = BottomTab.QURAN },
                    onNavigateToPrayer = { onNavigateTo(Screen.Prayer.route) },
                    onNavigateToAzkar = { selectedTab = BottomTab.AZKAR },
                    onNavigateToQibla = { onNavigateTo(Screen.Qibla.route) },
                    onNavigateToKhatma = { onNavigateTo(Screen.Khatma.route) },
                    onNavigateToLastRead = { surahId, page ->
                        onNavigateTo(Screen.QuranDetail.createRoute(surahId, page))
                    }
                )
                BottomTab.QURAN -> SurahListScreen(
                    onSurahClick = { surahId ->
                        onNavigateTo(Screen.QuranDetail.createRoute(surahId))
                    }
                )
                BottomTab.AZKAR -> AzkarScreen()
                BottomTab.SEARCH -> SearchScreen(
                    onNavigateToQuranDetail = { surahId, page ->
                        onNavigateTo(Screen.QuranDetail.createRoute(surahId, page))
                    }
                )
                BottomTab.SETTINGS -> SettingsScreen(
                    onNavigateToPrayer = { onNavigateTo(Screen.Prayer.route) },
                    onNavigateToAudio = { onNavigateTo(Screen.Audio.route) },
                    onNavigateToQibla = { onNavigateTo(Screen.Qibla.route) },
                    onNavigateToAdhanSettings = { onNavigateTo(Screen.AdhanSettings.route) },
                    onNavigateToKhatma = { onNavigateTo(Screen.Khatma.route) },
                    onNavigateToAsmaulHusna = { onNavigateTo(Screen.AsmaulHusna.route) },
                    onNavigateToSunnahReminders = { onNavigateTo(Screen.SunnahReminders.route) },
                    onNavigateToBukhari = { onNavigateTo(Screen.SahihBukhari.route) },
                    onNavigateToDeveloperTools = { onNavigateTo(Screen.DeveloperTools.route) },
                    onNavigateToTafsirManager = { onNavigateTo(Screen.TafsirManager.route) },
                    onNavigateToSourcesLicenses = { onNavigateTo(Screen.SourcesLicenses.route) }
                )
            }
        }
    }
}
