package com.example.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class ParsedVoiceItem(
    val description: String,
    val quantity: String,
    val totalAmount: String,
    val originalSegment: String = ""
)

object VoiceInvoiceParser {

    private val numberWordsMap = mapOf(
        "صفر" to 0.0,
        "واحد" to 1.0,
        "واحده" to 1.0,
        "واحدة" to 1.0,
        "اثنين" to 2.0,
        "اثنان" to 2.0,
        "ثلاثه" to 3.0,
        "ثلاثة" to 3.0,
        "ثلاث" to 3.0,
        "اربعه" to 4.0,
        "اربعة" to 4.0,
        "أربعة" to 4.0,
        "أربع" to 4.0,
        "اربع" to 4.0,
        "خمسه" to 5.0,
        "خمسة" to 5.0,
        "خمس" to 5.0,
        "سته" to 6.0,
        "ستة" to 6.0,
        "ست" to 6.0,
        "سبعه" to 7.0,
        "سبعة" to 7.0,
        "سبع" to 7.0,
        "ثمانيه" to 8.0,
        "ثمانية" to 8.0,
        "ثمان" to 8.0,
        "تسعه" to 9.0,
        "تسعة" to 9.0,
        "تسع" to 9.0,
        "عشره" to 10.0,
        "عشرة" to 10.0,
        "عشر" to 10.0,
        "عشرين" to 20.0,
        "ثلاثين" to 30.0,
        "اربعين" to 40.0,
        "أربعين" to 40.0,
        "خمسين" to 50.0,
        "ستين" to 60.0,
        "سبعين" to 70.0,
        "ثمانين" to 80.0,
        "تسعين" to 90.0,
        "ميه" to 100.0,
        "مية" to 100.0,
        "مئه" to 100.0,
        "مئة" to 100.0,
        "مائة" to 100.0,
        "مائتين" to 200.0,
        "مئتين" to 200.0,
        "ثلاثمائة" to 300.0,
        "أربعمائة" to 400.0,
        "خمسمائة" to 500.0,
        "الف" to 1000.0,
        "ألف" to 1000.0,
        "الفين" to 2000.0,
        "ألفين" to 2000.0,
        "مليون" to 1000000.0
    )

    private val commonIgnoredPrefixWords = setOf(
        "سجل", "اكتب", "ضيف", "أضف", "فاتورة", "فاتوره", "حط", "صنف", "البيان"
    )

    private val commonCurrencyWords = setOf(
        "ريال", "ريالات", "ريالا", "ر.ي", "سعر", "بسعر", "اجمالي", "إجمالي", "قيمة", "بقيمة", "مبلغ", "فلوس", "حساب"
    )

    /**
     * Splits a multi-item spoken sentence into distinct item segments.
     * Handles segments separated by " و " (when followed by numbers or item keywords),
     * or by "ثم" or commas / newlines.
     */
    fun splitIntoSegments(input: String): List<String> {
        val clean = input.trim()
        if (clean.isBlank()) return emptyList()

        // Replace common punctuation with separators
        var normalized = clean
            .replace("،", " | ")
            .replace(",", " | ")
            .replace("\n", " | ")
            .replace(" ثم ", " | ")

        // Split " و " only when followed by a number or common item indicator,
        // so we don't accidentally split compound descriptions like "شاي وحليب" or "أرز وسكر"
        // Regex: " و " followed by digit or number word
        val andRegex = Regex("\\s+و(?=\\s*(?:\\d|[٠-٩]|واحد|اثنين|اثنان|ثلاث|أربع|اربع|خمس|ست|سبع|ثمان|تسع|عشر|كيس|كرتون|علبة|حبة))")
        normalized = normalized.replace(andRegex, " | ")

        val segments = normalized.split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (segments.isNotEmpty()) segments else listOf(clean)
    }

    /**
     * Parses the full spoken text into a list of parsed invoice items.
     */
    fun parseVoiceInput(spokenText: String): List<ParsedVoiceItem> {
        val segments = splitIntoSegments(spokenText)
        return segments.mapNotNull { segment ->
            parseSingleSegment(segment)
        }
    }

    /**
     * Parses a single spoken segment (e.g. "2 كرتون عصير 4000" or "4000 كرتون عصير 2").
     */
    fun parseSingleSegment(segment: String): ParsedVoiceItem? {
        val raw = segment.trim().toEnglishDigits()
        if (raw.isBlank()) return null

        // 1. Tokenize words
        val rawTokens = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return null

        // Filter out introductory words like "سجل لي" or "اكتب" at the very beginning
        val tokens = mutableListOf<String>()
        var foundContent = false
        for (token in rawTokens) {
            val cleanToken = token.trim()
            if (!foundContent && cleanToken in commonIgnoredPrefixWords) {
                continue
            }
            foundContent = true
            tokens.add(cleanToken)
        }

        if (tokens.isEmpty()) return null

        // 2. Identify all numbers and their positions
        data class NumberMatch(val index: Int, val value: Double, val rawWord: String)
        val numbers = mutableListOf<NumberMatch>()

        for (i in tokens.indices) {
            val word = tokens[i]
            val numeric = word.toDoubleOrNull()
            if (numeric != null) {
                numbers.add(NumberMatch(i, numeric, word))
            } else {
                val mapped = numberWordsMap[word]
                if (mapped != null) {
                    numbers.add(NumberMatch(i, mapped, word))
                }
            }
        }

        var quantity = 1.0
        var totalAmount = 0.0
        val usedNumberIndices = mutableSetOf<Int>()

        if (numbers.size >= 2) {
            val first = numbers.first()
            val last = numbers.last()

            // Check if user spoke [Total] [Item] [Quantity] (e.g., 4000 كرتون عصير 2)
            // Or [Quantity] [Item] [Total] (e.g., 2 كرتون عصير 4000)
            if (first.value >= 100.0 && last.value in 0.1..100.0 && last.value == last.value.toInt().toDouble()) {
                // Highly probable: First is Total, Last is Quantity
                totalAmount = first.value
                quantity = last.value
                usedNumberIndices.add(first.index)
                usedNumberIndices.add(last.index)
            } else if (first.value in 0.1..100.0 && last.value >= 10.0) {
                // Highly probable: First is Quantity, Last is Total
                quantity = first.value
                totalAmount = last.value
                usedNumberIndices.add(first.index)
                usedNumberIndices.add(last.index)
            } else {
                // Fallback: First number is quantity, last is total
                quantity = first.value
                totalAmount = last.value
                usedNumberIndices.add(first.index)
                usedNumberIndices.add(last.index)
            }
        } else if (numbers.size == 1) {
            val num = numbers.first()
            usedNumberIndices.add(num.index)
            // If the number is at the beginning or is small (e.g., "5 حبات رز"), it could be quantity
            // If the number is large (e.g., "رز بسمتي 14000"), it's total amount
            if (num.value >= 100.0) {
                totalAmount = num.value
                quantity = 1.0
            } else if (num.index == 0) {
                quantity = num.value
                totalAmount = 0.0
            } else {
                totalAmount = num.value
                quantity = 1.0
            }
        }

        // 3. Extract the item description from remaining non-number words
        val descriptionWords = mutableListOf<String>()
        for (i in tokens.indices) {
            if (i in usedNumberIndices) continue
            val word = tokens[i]
            // Skip currency filler words if they are directly before or after the total amount
            if (word in commonCurrencyWords) continue
            descriptionWords.add(word)
        }

        val desc = descriptionWords.joinToString(" ").trim().ifEmpty {
            "صنف"
        }

        // Format quantity: remove .0 if whole number
        val qtyStr = if (quantity == quantity.toInt().toDouble()) {
            quantity.toInt().toString()
        } else {
            quantity.toString()
        }

        // Format total amount: remove .0 if whole number
        val totStr = if (totalAmount > 0.0) {
            if (totalAmount == totalAmount.toInt().toDouble()) {
                totalAmount.toInt().toString()
            } else {
                totalAmount.toString()
            }
        } else {
            ""
        }

        return ParsedVoiceItem(
            description = desc,
            quantity = qtyStr,
            totalAmount = totStr,
            originalSegment = segment
        )
    }
}
