package com.example

import com.example.ui.util.VoiceInvoiceParser
import org.junit.Assert.*
import org.junit.Test

class VoiceInvoiceParserTest {

    @Test
    fun testBasicSpokenPhrases() {
        // Pattern: Quantity Item Price
        val res1 = VoiceInvoiceParser.parseVoiceInput("2 كرتون عصير 4000")
        assertEquals(1, res1.size)
        assertEquals("2", res1[0].quantity)
        assertEquals("4000", res1[0].totalAmount)
        assertTrue(res1[0].description.contains("عصير"))

        // Pattern: Price Item Quantity (Turn 6 user request)
        val res2 = VoiceInvoiceParser.parseVoiceInput("4000 كرتون عصير 2")
        assertEquals(1, res2.size)
        assertEquals("2", res2[0].quantity)
        assertEquals("4000", res2[0].totalAmount)
        assertTrue(res2[0].description.contains("عصير"))
    }

    @Test
    fun testCompoundArabicNumbers() {
        // "خمسة آلاف" = 5000
        val res1 = VoiceInvoiceParser.parseVoiceInput("سكر خمسة آلاف")
        assertEquals(1, res1.size)
        assertEquals("1", res1[0].quantity)
        assertEquals("5000", res1[0].totalAmount)
        assertEquals("سكر", res1[0].description)

        // "خمسة آلاف وخمسمائة" = 5500
        val res2 = VoiceInvoiceParser.parseVoiceInput("2 أرز بسمتي خمسة آلاف وخمسمائة")
        assertEquals(1, res2.size)
        assertEquals("2", res2[0].quantity)
        assertEquals("5500", res2[0].totalAmount)

        // "عشرين ألف" = 20000
        val res3 = VoiceInvoiceParser.parseVoiceInput("كيس سكر برازيلي عشرين ألف")
        assertEquals(1, res3.size)
        assertEquals("1", res3[0].quantity)
        assertEquals("20000", res3[0].totalAmount)

        // "خمسة وعشرين ألف" = 25000
        val res4 = VoiceInvoiceParser.parseVoiceInput("2 زيت عافية خمسة وعشرين ألف")
        assertEquals(1, res4.size)
        assertEquals("2", res4[0].quantity)
        assertEquals("25000", res4[0].totalAmount)
    }

    @Test
    fun testDualPackagingForms() {
        // "حبتين" = 2
        val res1 = VoiceInvoiceParser.parseVoiceInput("حبتين بيبسي الف")
        assertEquals(1, res1.size)
        assertEquals("2", res1[0].quantity)
        assertEquals("1000", res1[0].totalAmount)
        assertTrue(res1[0].description.contains("بيبسي"))

        // "كرتونين" = 2
        val res2 = VoiceInvoiceParser.parseVoiceInput("كرتونين عصير 8000")
        assertEquals(1, res2.size)
        assertEquals("2", res2[0].quantity)
        assertEquals("8000", res2[0].totalAmount)
        assertTrue(res2[0].description.contains("عصير"))
    }

    @Test
    fun testMultiItemSpokenSentence() {
        val res = VoiceInvoiceParser.parseVoiceInput("2 كرتون عصير 4000 و 3 كيك 1500 و كيس رز 14000")
        assertEquals(3, res.size)
        assertEquals("2", res[0].quantity)
        assertEquals("4000", res[0].totalAmount)
        assertEquals("3", res[1].quantity)
        assertEquals("1500", res[1].totalAmount)
        assertEquals("1", res[2].quantity)
        assertEquals("14000", res[2].totalAmount)
    }

    @Test
    fun testPrepositionsAndUnits() {
        // "بسعر 500" with quantity 2
        val res = VoiceInvoiceParser.parseVoiceInput("2 عصير بسعر 500")
        assertEquals(1, res.size)
        assertEquals("2", res[0].quantity)
        assertEquals("500", res[0].unitPrice)
        assertEquals("1000", res[0].totalAmount)
    }

    @Test
    fun testUserReportedPatterns() {
        // Pattern 1: [Total] [Item Name] [Quantity] with commas from Google Speech Recognizer
        val r1 = VoiceInvoiceParser.parseVoiceInput("4,000 كرتون عصير 2")
        assertEquals(1, r1.size)
        assertEquals("2", r1[0].quantity)
        assertEquals("4000", r1[0].totalAmount)

        // Pattern 2: Arabic-Indic numerals
        val r2 = VoiceInvoiceParser.parseVoiceInput("٤٠٠٠ كرتون عصير ٢")
        assertEquals(1, r2.size)
        assertEquals("2", r2[0].quantity)
        assertEquals("4000", r2[0].totalAmount)

        // Pattern 3: With currency: "4000 ريال كرتون عصير 2"
        val r3 = VoiceInvoiceParser.parseVoiceInput("4000 ريال كرتون عصير 2")
        assertEquals(1, r3.size)
        assertEquals("2", r3[0].quantity)
        assertEquals("4000", r3[0].totalAmount)

        // Pattern 4: [Total] [Item] [Quantity] e.g. "1000 تونة 2"
        val r4 = VoiceInvoiceParser.parseVoiceInput("1000 تونة 2")
        assertEquals(1, r4.size)
        assertEquals("2", r4[0].quantity)
        assertEquals("1000", r4[0].totalAmount)
        assertEquals("500", r4[0].unitPrice)

        // Pattern 5: [Total] [Item] e.g. "500 بيبسي"
        val r5 = VoiceInvoiceParser.parseVoiceInput("500 بيبسي")
        assertEquals(1, r5.size)
        assertEquals("1", r5[0].quantity)
        assertEquals("500", r5[0].totalAmount)

        // Pattern 6: Multiple items with Total First: "1000 تونة 2 و 500 حليب 1"
        val r6 = VoiceInvoiceParser.parseVoiceInput("1000 تونة 2 و 500 حليب 1")
        assertEquals(2, r6.size)
        assertEquals("1000", r6[0].totalAmount)
        assertEquals("2", r6[0].quantity)
        assertEquals("500", r6[1].totalAmount)
        assertEquals("1", r6[1].quantity)

        // Pattern 7: "15000 رز شعلان 3"
        val r7 = VoiceInvoiceParser.parseVoiceInput("15000 رز شعلان 3")
        assertEquals(1, r7.size)
        assertEquals("15000", r7[0].totalAmount)
        assertEquals("3", r7[0].quantity)
        assertEquals("5000", r7[0].unitPrice)

        // Pattern 8: Small values: "50 علكة 5"
        val r8 = VoiceInvoiceParser.parseVoiceInput("50 علكة 5")
        assertEquals(1, r8.size)
        assertEquals("50", r8[0].totalAmount)
        assertEquals("5", r8[0].quantity)
        assertEquals("10", r8[0].unitPrice)

        // Pattern 9: Dual packaging with total: "عصير كرتونين 4000"
        val r9 = VoiceInvoiceParser.parseVoiceInput("عصير كرتونين 4000")
        assertEquals(1, r9.size)
        assertEquals("4000", r9[0].totalAmount)
        assertEquals("2", r9[0].quantity)
        assertEquals("2000", r9[0].unitPrice)

        // Pattern 10: "4000 عصير كرتونين"
        val r10 = VoiceInvoiceParser.parseVoiceInput("4000 عصير كرتونين")
        assertEquals(1, r10.size)
        assertEquals("4000", r10[0].totalAmount)
        assertEquals("2", r10[0].quantity)
        assertEquals("2000", r10[0].unitPrice)
    }
}
