package com.example

fun String.normalizeArabic(): String {
    var result = this

    val diacritics = listOf(
        "\u064B", "\u064C", "\u064D",
        "\u064E", "\u064F", "\u0650",
        "\u0651", "\u0652", "\u0653",
        "\u0654", "\u0670", "\u0656"
    )

    for (d in diacritics) {
        result = result.replace(d, "")
    }

    result = result.replace("أ", "ا")
    result = result.replace("إ", "ا")
    result = result.replace("آ", "ا")
    result = result.replace("ٱ", "ا")
    result = result.replace("ى", "ي")

    result = result.replace("واة", "اة")
    result = result.replace("ربواا", "ربا")

    result = result.replace("ة", "ه")

    result = result.replace("صلوه", "صلاه")
    result = result.replace("زكوه", "زكاه")
    result = result.replace("حيوه", "حياه")
    result = result.replace("نجوه", "نجاه")
    result = result.replace("منوه", "مناه")
    result = result.replace("غدوه", "غداه")

    result = result.replace("ـ", "")

    return result
}
