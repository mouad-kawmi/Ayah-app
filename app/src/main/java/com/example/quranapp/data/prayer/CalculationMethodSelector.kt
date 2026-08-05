package com.example.quranapp.data.prayer

data class CalculationMethod(
    val method: Int,
    val school: Int = 0,
    val latitudeAdjustmentMethod: Int = 3,
    val midnightMode: Int = 0
)

object CalculationMethodSelector {
    fun methodFor(countryCode: String): CalculationMethod {
        return when (countryCode) {
            "FR" -> CalculationMethod(method = 12)  // Union des Organisations Islamiques de France (UOIF)
            "DZ" -> CalculationMethod(method = 19)  // Algeria (official)
            "TN" -> CalculationMethod(method = 18)  // Tunisia (official)
            "EG" -> CalculationMethod(method = 5)   // Egyptian General Authority of Survey
            "SA" -> CalculationMethod(method = 4)   // Umm Al-Qura University, Makkah
            "AE" -> CalculationMethod(method = 4)   // Umm Al-Qura University, Makkah
            "QA" -> CalculationMethod(method = 10)  // Qatar (official)
            "KW" -> CalculationMethod(method = 9)   // Kuwait (official)
            "JO" -> CalculationMethod(method = 23)  // Ministry of Awqaf, Jordan (official)
            "IR" -> CalculationMethod(method = 7)   // Institute of Geophysics, University of Tehran
            "TR" -> CalculationMethod(method = 13)  // Diyanet İşleri Başkanlığı, Turkey
            "RU" -> CalculationMethod(method = 14)  // Spiritual Administration of Muslims of Russia
            "PK" -> CalculationMethod(method = 1)   // University of Islamic Sciences, Karachi
            "IN" -> CalculationMethod(method = 1)   // University of Islamic Sciences, Karachi
            "BD" -> CalculationMethod(method = 1)   // University of Islamic Sciences, Karachi
            "MY" -> CalculationMethod(method = 17)  // Jabatan Kemajuan Islam Malaysia (JAKIM)
            "ID" -> CalculationMethod(method = 20)  // Kementerian Agama Republik Indonesia (KEMENAG)
            "SG" -> CalculationMethod(method = 11)  // Majlis Ugama Islam Singapura, Singapore
            "PT" -> CalculationMethod(method = 22)  // Comunidade Islamica de Lisboa
            "US" -> CalculationMethod(method = 2)   // Islamic Society of North America (ISNA)
            "CA" -> CalculationMethod(method = 2)   // Islamic Society of North America (ISNA)
            else  -> CalculationMethod(method = 3)  // Muslim World League (universal default)
        }
    }
}
