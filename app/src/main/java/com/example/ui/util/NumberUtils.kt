package com.example.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberUtils {
    val englishSymbols: DecimalFormatSymbols = DecimalFormatSymbols(Locale.US)
    val standardFormatter: DecimalFormat = DecimalFormat("#,##0.##", englishSymbols)
    val currencyFormatter: DecimalFormat = DecimalFormat("#,##0.00", englishSymbols)
    val integerFormatter: DecimalFormat = DecimalFormat("#,##0", englishSymbols)
}

/**
 * Converts Eastern Arabic numerals (٠-٩) and Persian numerals (۰-۹) to standard Western English digits (0-9).
 * Also normalizes Arabic decimal comma (٫ / ،) to a standard dot (.).
 */
fun String.toEnglishDigits(): String {
    if (this.isEmpty()) return this
    val builder = StringBuilder(this.length)
    for (ch in this) {
        when (ch) {
            in '٠'..'٩' -> builder.append((ch - '٠' + '0'.code).toChar())
            in '۰'..'۹' -> builder.append((ch - '۰' + '0'.code).toChar())
            '٫', '،' -> builder.append('.')
            else -> builder.append(ch)
        }
    }
    return builder.toString()
}

fun Double.formatEnglish(pattern: String = "#,##0.##"): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    return DecimalFormat(pattern, symbols).format(this)
}

fun Number.toEnglishString(): String {
    return this.toString().toEnglishDigits()
}
