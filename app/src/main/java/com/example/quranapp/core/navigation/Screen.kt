package com.example.quranapp.core.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Quran : Screen("quran")
    object Prayer : Screen("prayer")
    object Azkar : Screen("azkar")
    object Audio : Screen("audio")
    object Settings : Screen("settings")
    object Qibla : Screen("qibla")
    object AdhanSettings : Screen("adhan_settings")
    object Khatma : Screen("khatma")
    object KhatmaDay : Screen("khatma/{dayNumber}") {
        fun createRoute(dayNumber: Int) = "khatma/$dayNumber"
    }
    object QuranDetail : Screen("quran/{surahId}?page={page}&isKhatma={isKhatma}") {
        fun createRoute(surahId: Int, page: Int? = null, isKhatma: Boolean = false): String {
            val base = "quran/$surahId"
            val queryParams = mutableListOf<String>()
            if (page != null) queryParams.add("page=$page")
            if (isKhatma) queryParams.add("isKhatma=true")
            
            return if (queryParams.isEmpty()) base else "$base?${queryParams.joinToString("&")}"
        }
    }
    object DownloadedAudio : Screen("downloaded_audio")
    object SunnahReminders : Screen("sunnah_reminders")
    object Search : Screen("search")
    object AsmaulHusna : Screen("asmaul_husna")
    object SahihBukhari : Screen("sahih_bukhari")
    object HadithList : Screen("hadith_list/{kitabNumber}") {
        fun createRoute(kitabNumber: Int) = "hadith_list/$kitabNumber"
    }
    object DeveloperTools : Screen("developer_tools")
    object LogViewer : Screen("log_viewer")
    object TafsirManager : Screen("tafsir_manager")
    object SourcesLicenses : Screen("sources_licenses")
}
