package com.example.quranapp.core.utils

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

object Qcf4FontManager {
    private val cache = ConcurrentHashMap<String, Typeface>()
    private val fontFamilyCache = ConcurrentHashMap<String, androidx.compose.ui.text.font.FontFamily>()

    fun getFont(context: Context, fontName: String): Typeface {
        return cache.getOrPut(fontName) {
            val fileName = if (fontName == "QCF4_QBSML") "QCF4_QBSML.ttf" else "${fontName}_W.ttf"
            Typeface.createFromAsset(context.assets, "fonts/$fileName")
        }
    }

    fun getFontFamily(context: Context, fontName: String): androidx.compose.ui.text.font.FontFamily {
        return fontFamilyCache.getOrPut(fontName) {
            val tf = getFont(context, fontName)
            androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(tf))
        }
    }

    fun preloadAllFonts(context: Context) {
        val fontNames = mutableListOf("QCF4_QBSML")
        for (i in 1..46) {
            fontNames.add("QCF4_Hafs_${i.toString().padStart(2, '0')}")
        }
        for (name in fontNames) {
            getFontFamily(context, name)
        }
    }
}
