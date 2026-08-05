package com.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    AR("ar", "العربية", true),
    EN("en", "English", false),
    FR("fr", "Français", false);

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: AR
        }
    }
}

object AppTranslation {
    private val dictionary = mapOf(
        "app_name" to mapOf(
            "ar" to "نور الإيمان",
            "en" to "Noor Al-Iman",
            "fr" to "Noor Al-Iman"
        ),
        "ministry_standards" to mapOf(
            "ar" to "وفق معايير وزارة الأوقاف المغربية",
            "en" to "According to Moroccan Awqaf Ministry",
            "fr" to "Selon les normes du Ministère des Habous"
        ),
        "home" to mapOf(
            "ar" to "الرئيسية",
            "en" to "Home",
            "fr" to "Accueil"
        ),
        "quran" to mapOf(
            "ar" to "القرآن",
            "en" to "Quran",
            "fr" to "Coran"
        ),
        "search" to mapOf(
            "ar" to "البحث",
            "en" to "Search",
            "fr" to "Recherche"
        ),
        "adhkar" to mapOf(
            "ar" to "الأذكار",
            "en" to "Adhkar",
            "fr" to "Adhkar"
        ),
        "more" to mapOf(
            "ar" to "المزيد",
            "en" to "More",
            "fr" to "Plus"
        ),
        "prayer_times" to mapOf(
            "ar" to "أوقات الصلاة",
            "en" to "Prayer Times",
            "fr" to "Heures de prière"
        ),
        "last_read" to mapOf(
            "ar" to "آخر قراءة • Récemment lu",
            "en" to "Last Read",
            "fr" to "Dernière lecture"
        ),
        "continue" to mapOf(
            "ar" to "متابعة",
            "en" to "Continue",
            "fr" to "Continuer"
        ),
        "start" to mapOf(
            "ar" to "البدء",
            "en" to "Start",
            "fr" to "Démarrer"
        ),
        "no_read_yet" to mapOf(
            "ar" to "لم تبدأ القراءة بعد. اضغط هنا للبدء الآن.",
            "en" to "You haven't started reading yet. Click here to start.",
            "fr" to "Vous n'avez pas encore commencé. Cliquez ici pour commencer."
        ),
        "settings_title" to mapOf(
            "ar" to "إعدادات التذكير والتنبيه",
            "en" to "Alert Settings",
            "fr" to "Paramètres d'Alerte"
        ),
        "settings_desc" to mapOf(
            "ar" to "اختر المدة المفضلة للتنبيه المسبق قبل موعد الأذن للوضوء والاستعداد للذكر:",
            "en" to "Choose your preferred pre-prayer alert to prepare yourself:",
            "fr" to "Choisissez votre alerte de pré-prière préférée pour vous préparer :"
        ),
        "alarm_off" to mapOf(
            "ar" to "إيقاف التنبيه المسبق (أذان فقط)",
            "en" to "Adhan only (no pre-alarm)",
            "fr" to "Adhan uniquement (pas de pré-alarme)"
        ),
        "min_5" to mapOf(
            "ar" to "5 دقائق قبل الصلاة",
            "en" to "5 minutes before prayer",
            "fr" to "5 minutes avant la prière"
        ),
        "min_10" to mapOf(
            "ar" to "10 دقائق قبل الصلاة",
            "en" to "10 minutes before prayer",
            "fr" to "10 minutes avant la prière"
        ),
        "min_15" to mapOf(
            "ar" to "15 دقيقة قبل الصلاة",
            "en" to "15 minutes before prayer",
            "fr" to "15 minutes avant la prière"
        ),
        "min_30" to mapOf(
            "ar" to "30 دقيقة قبل الصلاة",
            "en" to "30 minutes before prayer",
            "fr" to "30 minutes avant la prière"
        ),
        "ok" to mapOf(
            "ar" to "موافق",
            "en" to "OK",
            "fr" to "OK"
        ),
        "about" to mapOf(
            "ar" to "حول تطبيق نور الإيمان",
            "en" to "About Noor Al-Iman",
            "fr" to "À propos de Noor Al-Iman"
        ),
        "about_desc" to mapOf(
            "ar" to "تطبيق إسلامي مغربي متكامل لعرض أوقات الصلوات والقرآن الكريم والأذكار",
            "en" to "An integrated Moroccan Islamic application for prayer times, Quran, and Adhkar",
            "fr" to "Une application islamique marocaine intégrée pour les prières, le Coran et l'Adhkar"
        ),
        "qibla" to mapOf(
            "ar" to "بوصلة اتجاه القبلة",
            "en" to "Qibla Compass",
            "fr" to "Boussole de la Qibla"
        ),
        "qibla_desc" to mapOf(
            "ar" to "تحديد اتجاه الكعبة المشرفة بدقة",
            "en" to "Accurately locate the direction of Kaaba",
            "fr" to "Localiser précisément la direction de la Kaaba"
        ),
        "tasbih" to mapOf(
            "ar" to "المسبحة الإلكترونية",
            "en" to "Electronic Tasbih",
            "fr" to "Chapelet Électronique"
        ),
        "tasbih_desc" to mapOf(
            "ar" to "عداد التسبيح اليومي والأذكار والسنن",
            "en" to "Daily dhikr and tasbih counter",
            "fr" to "Compteur quotidien de dhikr et tasbih"
        ),
        "share" to mapOf(
            "ar" to "مشاركة التطبيق مع الأصحاب والخير",
            "en" to "Share app with friends",
            "fr" to "Partager l'application"
        ),
        "share_sub" to mapOf(
            "ar" to "الدال على الخير كفاعله",
            "en" to "Reward sharing the goodness",
            "fr" to "Partager pour faire le bien"
        ),
        "share_text" to mapOf(
            "ar" to "✨ حمل تطبيق نور الإيمان لمعرفة مواعيد الصلاة وقراءة القرآن الكريم والأذكار اليومية في المغرب! ✨",
            "en" to "✨ Download Noor Al-Iman App for prayer times, Holy Quran, and daily Adhkar! ✨",
            "fr" to "✨ Téléchargez l'application Noor Al-Iman pour les prières, le Coran et l'Adhkar ! ✨"
        ),
        "share_chooser" to mapOf(
            "ar" to "مشاركة نور الإيمان",
            "en" to "Share Noor Al-Iman",
            "fr" to "Partager Noor Al-Iman"
        ),
        "about_item" to mapOf(
            "ar" to "حول تطبيق نور الإيمان",
            "en" to "About Noor Al-Iman",
            "fr" to "À propos du Noor Al-Iman"
        ),
        "about_item_sub" to mapOf(
            "ar" to "معلومات الإصدار وتأصيل الأوقات الشرعية",
            "en" to "Version information and official prayer sources",
            "fr" to "Informations de version et méthodologie"
        ),
        "about_body1" to mapOf(
            "ar" to "برمجة هذا التطبيق لخدمة المسلمين في المملكة المغربية الشريفة وعموم بلدان العالم الإسلامي.",
            "en" to "Developed to serve Muslims in the Kingdom of Morocco and around the Islamic world.",
            "fr" to "Développé pour les musulmans au Royaume du Maroc et dans le monde islamique."
        ),
        "about_body2" to mapOf(
            "ar" to "تم حساب أوقات الصلوات وفقاً للمنهجية الشرعية للوزارة الموقرة للأوقاف والشؤون الإسلامية بالمغرب، للتأكد من المواعيد الرسمية للمملكة.",
            "en" to "Prayer times are computed according to the official religious guidelines of the Ministry of Awqaf and Islamic Affairs in Morocco.",
            "fr" to "Les heures de prière sont calculées selon la méthodologie du Ministère des Habous et des Affaires Islamiques du Maroc."
        ),
        "about_quote" to mapOf(
            "ar" to "«وَمَنْ أَحْسَنُ قَوْلًا مِّمَّن دَعَا إِلَى اللَّهِ وَعَمِلَ صَالِحًا وَقَالَ إِنَّنِي مِنَ الْمُسْلِمِينَ»",
            "en" to "“And who is better in speech than one who invites to Allah and does righteousness and says, 'Indeed, I am of the Muslims'”",
            "fr" to "« Et qui profère plus belles paroles que celui qui appelle à Allah, fait le bien et dit : Je suis du nombre des Musulmans »"
        ),
        "close_about" to mapOf(
            "ar" to "شكراً وموافق",
            "en" to "Close & Thank You",
            "fr" to "Fermer & Merci"
        ),
        "ayah_day" to mapOf(
            "ar" to "آية اليوم",
            "en" to "Ayah of the Day",
            "fr" to "Verset du Jour"
        ),
        "explore_ayah" to mapOf(
            "ar" to "تفكر في آيات الله الكريمة",
            "en" to "Contemplate the verses of Allah",
            "fr" to "Méditer sur les versets de Dieu"
        ),
        "search_hint" to mapOf(
            "ar" to "ابحث عن سورة، آية، أو كلمة بالقرآن الكريم...",
            "en" to "Search for surah, verse, or word in Quran...",
            "fr" to "Rechercher une sourate, un verset ou mot..."
        ),
        "search_surah_hint" to mapOf(
            "ar" to "بحث عن سورة...",
            "en" to "Search surah...",
            "fr" to "Rechercher une sourate..."
        ),
        "no_surah_found" to mapOf(
            "ar" to "لم يتم العثور على سورة",
            "en" to "No surah found",
            "fr" to "Aucune sourate trouvée"
        ),
        "surah_label" to mapOf(
            "ar" to "سورة",
            "en" to "Surah",
            "fr" to "Sourate"
        ),
        "ayah_label" to mapOf(
            "ar" to "الآية",
            "en" to "Verse",
            "fr" to "Verset"
        ),
        "page_label" to mapOf(
            "ar" to "صفحة",
            "en" to "Page",
            "fr" to "Page"
        ),
        "search_results" to mapOf(
            "ar" to "تم العثور على %d آية تطابق بحثك",
            "en" to "Found %d verses matching your search",
            "fr" to "Trouvé %d versets correspondant"
        ),
        "fajr" to mapOf(
            "ar" to "الفجر",
            "en" to "Fajr",
            "fr" to "Fajr"
        ),
        "shorooq" to mapOf(
            "ar" to "الشروق",
            "en" to "Sunrise",
            "fr" to "Lever du soleil"
        ),
        "dhuhr" to mapOf(
            "ar" to "الظهر",
            "en" to "Dhuhr",
            "fr" to "Dhuhr"
        ),
        "asr" to mapOf(
            "ar" to "العصر",
            "en" to "Asr",
            "fr" to "Asr"
        ),
        "maghrib" to mapOf(
            "ar" to "المغرب",
            "en" to "Maghrib",
            "fr" to "Maghrib"
        ),
        "isha" to mapOf(
            "ar" to "العشاء",
            "en" to "Isha",
            "fr" to "Isha"
        ),
        "next_prayer" to mapOf(
            "ar" to "الصلاة القادمة",
            "en" to "Next Prayer",
            "fr" to "Prochaine Prière"
        ),
        "remaining_time" to mapOf(
            "ar" to "الوقت المتبقي للأذان",
            "en" to "Time remaining for Adhan",
            "fr" to "Temps restant avant l'Adhan"
        ),
        "services_title" to mapOf(
            "ar" to "الخدمات المتاحة",
            "en" to "Available Services",
            "fr" to "Services Disponibles"
        ),
        "general_options" to mapOf(
            "ar" to "خيارات عامة",
            "en" to "General Options",
            "fr" to "Options Générales"
        ),
        "audio_settings" to mapOf(
            "ar" to "إعدادات الصوت",
            "en" to "Audio Settings",
            "fr" to "Paramètres Audio"
        ),
        "audio_settings_desc" to mapOf(
            "ar" to "اختيار القارئ وإدارة التحميلات",
            "en" to "Choose reader and manage downloads",
            "fr" to "Choisir le lecteur et gérer les téléchargements"
        ),
        "download_manager" to mapOf(
            "ar" to "مدير التحميل",
            "en" to "Download Manager",
            "fr" to "Gestionnaire de Téléchargements"
        ),
        "download_manager_desc" to mapOf(
            "ar" to "عرض وإدارة السور المحملة",
            "en" to "View and manage downloaded surahs",
            "fr" to "Voir et gérer les sourates téléchargées"
        ),
        "adhkar_categories" to mapOf(
            "ar" to "تصنيفات الأذكار",
            "en" to "Adhkar Categories",
            "fr" to "Catégories d'Adhkar"
        ),
        "tasbih_count" to mapOf(
            "ar" to "العداد",
            "en" to "Counter",
            "fr" to "Compteur"
        ),
        "reset" to mapOf(
            "ar" to "إعادة تعيين",
            "en" to "Reset",
            "fr" to "Réinitialiser"
        ),
        "change_language" to mapOf(
            "ar" to "مسؤولي اللغة • Language Setting",
            "en" to "App Language",
            "fr" to "Langue de l'application"
        ),
        "language_section" to mapOf(
            "ar" to "لغة التطبيق • Langues",
            "en" to "Select Application Language",
            "fr" to "Choisir la Langue"
        ),
        "save" to mapOf(
            "ar" to "حفظ",
            "en" to "Save",
            "fr" to "Enregistrer"
        ),
        "index" to mapOf(
            "ar" to "الفهرس",
            "en" to "Index",
            "fr" to "Index"
        ),
        "search_in_quran" to mapOf(
            "ar" to "البحث في القرآن",
            "en" to "Search in Quran",
            "fr" to "Recherche dans le Coran"
        ),
        "quick_search_suggestions" to mapOf(
            "ar" to "اقتراحات البحث السريع:",
            "en" to "Quick search suggestions:",
            "fr" to "Suggestions de recherche rapide :"
        ),
        "no_results_for" to mapOf(
            "ar" to "لم يتم العثور على نتائج لـ",
            "en" to "No results found for",
            "fr" to "Aucun résultat trouvé pour"
        ),
        "matching_surahs" to mapOf(
            "ar" to "السور المطابقة",
            "en" to "Matching Surahs",
            "fr" to "Sourates correspondantes"
        ),
        "matching_verses" to mapOf(
            "ar" to "الآيات المطابقة",
            "en" to "Matching Verses",
            "fr" to "Versets correspondants"
        ),
        "start_page" to mapOf(
            "ar" to "بداية الصفحة",
            "en" to "Starts at page",
            "fr" to "Commence à la page"
        ),
        "translation_en" to mapOf(
            "ar" to "الترجمة (الإنجليزية)",
            "en" to "Translation (English)",
            "fr" to "Traduction (Anglaise)"
        ),
        "translation_fr" to mapOf(
            "ar" to "الترجمة (الفرنسية)",
            "en" to "Translation (French)",
            "fr" to "Traduction (Française)"
        ),
        "tafsir_muyassar" to mapOf(
            "ar" to "التفسير الميسر",
            "en" to "Al-Moyassar Tafsir",
            "fr" to "Tafsir Al-Moyassar"
        ),
        "play_surah" to mapOf(
            "ar" to "تشغيل السورة",
            "en" to "Play Surah",
            "fr" to "Jouer la sourate"
        ),
        "stop_surah" to mapOf(
            "ar" to "إيقاف السورة",
            "en" to "Stop Surah",
            "fr" to "Arrêter la sourate"
        ),
        "adhkar_fortifications" to mapOf(
            "ar" to "الأذكار والتحصينات",
            "en" to "Adhkar & Fortifications",
            "fr" to "Adhkar & Invocations"
        ),
        "morning_adhkar" to mapOf(
            "ar" to "أذكار الصباح",
            "en" to "Morning Adhkar",
            "fr" to "Adhkar du Matin"
        ),
        "evening_adhkar" to mapOf(
            "ar" to "أذكار المساء",
            "en" to "Evening Adhkar",
            "fr" to "Adhkar du Soir"
        ),
        "after_prayer_adhkar" to mapOf(
            "ar" to "أذكار بعد الصلاة",
            "en" to "After Prayer Adhkar",
            "fr" to "Adhkar après la Prière"
        ),
        "sleep_adhkar" to mapOf(
            "ar" to "أذكار النوم",
            "en" to "Sleep Adhkar",
            "fr" to "Adhkar du Sommeil"
        ),
        "waking_up_adhkar" to mapOf(
            "ar" to "أذكار الاستيقاظ",
            "en" to "Waking Up Adhkar",
            "fr" to "Adhkar du Réveil"
        ),
        "travel_adhkar" to mapOf(
            "ar" to "أذكار السفر",
            "en" to "Travel Adhkar",
            "fr" to "Adhkar de Voyage"
        ),
        "sorrow_adhkar" to mapOf(
            "ar" to "أذكار الهم والحزن",
            "en" to "Sorrow & Anxiety Adhkar",
            "fr" to "Adhkar d'Anxiété et Tristesse"
        ),
        "of_adhkar" to mapOf(
            "ar" to "من الأذكار",
            "en" to "Adhkar",
            "fr" to "Adhkar"
        ),
        "completed" to mapOf(
            "ar" to "مكتمل",
            "en" to "Completed",
            "fr" to "Terminé"
        ),
        "click_card_to_repeat" to mapOf(
            "ar" to "انقر فوق البطاقة للتكرار",
            "en" to "Tap card to repeat",
            "fr" to "Appuyez pour répéter"
        ),
        "qibla_calibrating" to mapOf(
            "ar" to "جاري تحديد الموقع و اتجاه الكعبة...",
            "en" to "Locating Qibla...",
            "fr" to "Localisation de la Qibla..."
        ),
        "qibla_calibration_hint" to mapOf(
            "ar" to "ضع الهاتف مستوياً للحصول على دقة أفضل",
            "en" to "Keep phone flat for best accuracy",
            "fr" to "Gardez le téléphone à plat pour plus de précision"
        ),
        "qibla_direction_degrees" to mapOf(
            "ar" to "اتجاه القبلة:",
            "en" to "Qibla Direction:",
            "fr" to "Direction de la Qibla :"
        ),
        "degrees_from_north" to mapOf(
            "ar" to "درجة من الشمال",
            "en" to "degrees from North",
            "fr" to "degrés du Nord"
        ),
        "qibla_ready" to mapOf(
            "ar" to "جاهز للصلاة! الهاتف مطابق لاتجاه القبلة",
            "en" to "Ready for prayer! Phone aligned with Qibla",
            "fr" to "Prêt pour la prière ! Téléphone aligné vers la Qibla"
        ),
        "qibla_rotate_hint" to mapOf(
            "ar" to "يرجى تدوير الهاتف حتى يتطابق المؤشر مع القبلة",
            "en" to "Please rotate phone to align with Qibla indicator",
            "fr" to "Veuillez tourner le téléphone pour l'aligner avec la Qibla"
        ),
        "current_dhikr" to mapOf(
            "ar" to "الذكر الحالي",
            "en" to "Current Dhikr",
            "fr" to "Dhikr Actuel"
        ),
        "cycle_limit" to mapOf(
            "ar" to "دورة",
            "en" to "Cycle",
            "fr" to "Cycle"
        ),
        "total_tasbih_today" to mapOf(
            "ar" to "مجموع التسبيحات اليوم",
            "en" to "Total Tasbih Today",
            "fr" to "Total Tasbih Aujourd'hui"
        ),
        "khatma_planner" to mapOf(
            "ar" to "مخطّط الختمات",
            "en" to "Khatma Planner",
            "fr" to "Planificateur de Khatma"
        ),
        "khatma_planner_desc" to mapOf(
            "ar" to "خطط لختم القرآن الكريم في مدة محددة",
            "en" to "Plan your Quran completion in a set duration",
            "fr" to "Planifiez votre achèvement du Coran"
        ),
        "enable_gps_title" to mapOf(
            "ar" to "تفعيل الموقع",
            "en" to "Enable Location",
            "fr" to "Activer la localisation"
        ),
        "enable_gps_desc" to mapOf(
            "ar" to "لتحديد موقعك الحالي وتحديث مواقيت الصلاة بدقة، يرجى تفعيل خدمة الموقع. يمكنك استخدام التطبيق بدون تفعيل الموقع، ويمكنك تحديث موقعك في أي وقت من خلال السحب لتحديث الصفحة.",
            "en" to "To determine your current location and update prayer times accurately, please enable location services. You can use the app without location enabled, and update your location anytime by pulling to refresh.",
            "fr" to "Pour déterminer votre position actuelle et mettre à jour les heures de prière avec précision, veuillez activer les services de localisation. Vous pouvez utiliser l'application sans localisation activée et mettre à jour votre position à tout moment en tirant pour actualiser."
        ),
        "enable_gps" to mapOf(
            "ar" to "تفعيل الموقع",
            "en" to "Enable Location",
            "fr" to "Activer la localisation"
        ),
        "not_now" to mapOf(
            "ar" to "ليس الآن",
            "en" to "Not now",
            "fr" to "Pas maintenant"
        ),
        "battery_opt_title" to mapOf(
            "ar" to "المحافظة على مواعيد الأذان",
            "en" to "Keep Adhan on Time",
            "fr" to "Préserver les heures d'Adhan"
        ),
        "battery_opt_desc" to mapOf(
            "ar" to "لضمان عمل الأذان والتنبيهات في أوقاتها، يُرجى السماح للتطبيق بالعمل دون قيود على استهلاك البطارية.\n\nقد تمنع بعض الأجهزة تشغيل التنبيهات في الوقت المحدد عند تفعيل وضع توفير الطاقة.",
            "en" to "To ensure Adhan and alerts work on time, please allow the app to run without battery restrictions.\n\nSome devices may block scheduled alerts when battery saver is enabled.",
            "fr" to "Pour garantir le bon fonctionnement de l'Adhan et des alertes à l'heure, veuillez autoriser l'application à fonctionner sans restriction de batterie.\n\nCertains appareils peuvent bloquer les alertes programmées lorsque l'économie d'énergie est activée."
        ),
        "disable_battery_optimization" to mapOf(
            "ar" to "إلغاء قيود البطارية",
            "en" to "Disable Battery Optimization",
            "fr" to "Désactiver l'optimisation de la batterie"
        ),
        "battery_opt_later" to mapOf(
            "ar" to "لاحقًا",
            "en" to "Later",
            "fr" to "Plus tard"
        ),
        "battery_opt_hint_samsung" to mapOf(
            "ar" to "إرشادات سامسونج: الإعدادات → العناية بالجهاز → البطارية → التطبيقات غير المراقبة → إضافة التطبيق",
            "en" to "Samsung: Settings → Device care → Battery → Unmonitored apps → Add app",
            "fr" to "Samsung : Paramètres → Entretien de l'appareil → Batterie → Applications non surveillées → Ajouter l'application"
        ),
        "battery_opt_hint_xiaomi" to mapOf(
            "ar" to "إرشادات شاومي: الإعدادات → البطارية → قيود البطارية → لا توجد قيود → السماح للتشغيل التلقائي",
            "en" to "Xiaomi: Settings → Battery → Battery restrictions → No restrictions → Allow auto-start",
            "fr" to "Xiaomi : Paramètres → Batterie → Restrictions de batterie → Aucune restriction → Autoriser le démarrage auto"
        ),
        "battery_opt_hint_oppo" to mapOf(
            "ar" to "إرشادات أوبو: الإعدادات → البطارية → إدارة البطارية → التطبيقات → التطبيق → لا توجد قيود",
            "en" to "Oppo: Settings → Battery → Battery management → Apps → App → No restrictions",
            "fr" to "Oppo : Paramètres → Batterie → Gestion de la batterie → Applications → App → Aucune restriction"
        ),
        "battery_opt_hint_realme" to mapOf(
            "ar" to "إرشادات ريلمي: الإعدادات → البطارية → إدارة البطارية → التطبيقات → التطبيق → لا توجد قيود",
            "en" to "Realme: Settings → Battery → Battery management → Apps → App → No restrictions",
            "fr" to "Realme : Paramètres → Batterie → Gestion de la batterie → Applications → App → Aucune restriction"
        ),
        "battery_opt_hint_huawei" to mapOf(
            "ar" to "إرشادات هواوي: الإعدادات → البطارية → تشغيل التطبيقات → إدارة التطبيق → التطبيق → السماح",
            "en" to "Huawei: Settings → Battery → App launch → Manage app → App → Allow",
            "fr" to "Huawei : Paramètres → Batterie → Lancement des applications → Gérer l'application → App → Autoriser"
        ),
        "battery_opt_hint_vivo" to mapOf(
            "ar" to "إرشادات فيفو: مدير الهاتف → البطارية → إدارة الطاقة → التطبيقات الخلفية → التطبيق → السماح",
            "en" to "Vivo: Phone Manager → Battery → Power management → Background apps → App → Allow",
            "fr" to "Vivo : Gestionnaire du téléphone → Batterie → Gestion de l'alimentation → Applications en arrière-plan → App → Autoriser"
        ),
        "battery_opt_hint_pixel" to mapOf(
            "ar" to "إرشادات جوجل: الإعدادات → التطبيقات → التطبيق → البطارية → غير محدود",
            "en" to "Google/Pixel: Settings → Apps → App → Battery → Unrestricted",
            "fr" to "Google/Pixel : Paramètres → Applications → App → Batterie → Illimité"
        ),
        "battery_opt_hint_unknown" to mapOf(
            "ar" to "يفضل البحث في إعدادات البطارية عن خيار «لا تقييد» أو «عدم تحسين البطارية» لهذا التطبيق.",
            "en" to "Please check your battery settings for 'Unrestricted' or 'Don't optimize' for this app.",
            "fr" to "Veuillez vérifier les paramètres de batterie pour 'Illimité' ou 'Ne pas optimiser' pour cette application."
        )
    )

    fun translate(key: String, language: AppLanguage): String {
        return dictionary[key]?.get(language.code) ?: dictionary[key]?.get("ar") ?: key
    }
}
