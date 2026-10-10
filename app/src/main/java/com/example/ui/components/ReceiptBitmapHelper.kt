package com.example.ui.components

import android.content.Context
import android.graphics.*
import com.example.data.local.InvoiceItemEntity
import com.example.data.local.StoreConfigEntity
import com.example.ui.viewmodels.DualReceiptRow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.toEnglishDigits

object ReceiptBitmapHelper {

    private val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    /**
     * Renders a crisp, high-contrast 2-column receipt without heavy table boxes or gridlines.
     * Supports arbitrary resolutions including native 384px (58mm ESC/POS thermal), 576px (80mm),
     * and 2160px (4K Ultra-HD for PDF / Gallery Export) with perfect proportional scaling.
     */
    fun createReceiptBitmap(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        dualRows: List<DualReceiptRow>,
        widthPx: Int = 384 // Default 384px (58mm native thermal resolution)
    ): Bitmap {
        val baseWidth = 384f
        val scale = widthPx.toFloat() / baseWidth

        // Filter out completely empty rows
        val activeRows = dualRows.filterNot { it.isCompletelyEmpty }.ifEmpty {
            listOf(DualReceiptRow(rightDescription = "صنف", rightQuantityStr = "1", rightTotalAmountStr = "0"))
        }

        val rightSubtotal = activeRows.sumOf { it.rightTotal }
        val leftSubtotal = activeRows.sumOf { it.leftTotal }
        val grandTotal = rightSubtotal + leftSubtotal

        // Dynamic height calculations scaled proportionally
        val headerHeight = 90f * scale
        val tableHeaderHeight = 26f * scale
        val rowHeight = 25f * scale
        val tableHeight = activeRows.size * rowHeight
        val subtotalsHeight = 24f * scale
        val grandTotalHeight = 32f * scale
        val footerHeight = 22f * scale
        val padding = 8f * scale
        val totalHeight = (headerHeight + tableHeaderHeight + tableHeight + subtotalsHeight + grandTotalHeight + footerHeight + (20f * scale)).toInt().coerceAtLeast(300)

        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Ultra-Bold, Solid Black Paints (Proportionally scaled font sizes & stroke widths)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        val heavyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 15f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        val smallBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * scale
        }

        val thinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * scale
        }

        val solidBlackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        val grandTotalNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        val contentWidth = widthPx - (2 * padding)
        var currentY = padding

        // 1. TOP HEADER (اسم المحل، الهاتف، نوع الدفع، الرقم والتاريخ)
        val storeName = storeConfig.storeNameArabic.ifEmpty { "بقالة العزي" }
        titlePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(storeName, widthPx - padding, currentY + (20f * scale), titlePaint)

        // Store phone
        // Store phone (English digits)
        val phoneText = "ج: ${storeConfig.phone1.ifEmpty { "772437314" }.toEnglishDigits()}"
        smallBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(phoneText, widthPx - padding, currentY + (38f * scale), smallBoldPaint)

        // Payment type (Center)
        boldPaint.textAlign = Paint.Align.CENTER
        val payText = if (paymentType == "نقداً") "[✓] نقداً   [ ] أجل" else "[ ] نقداً   [✓] أجل"
        canvas.drawText(payText, widthPx / 2f, currentY + (20f * scale), boldPaint)

        // Invoice Number & Date (Left, English digits)
        heavyBoldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الرقم: #${invoiceNumber.toString().toEnglishDigits()}", padding, currentY + (20f * scale), heavyBoldPaint)

        smallBoldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("التاريخ: ${dateString.toEnglishDigits()}", padding, currentY + (38f * scale), smallBoldPaint)

        currentY += 46f * scale

        // Separator line under store info
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, linePaint)
        currentY += 6f * scale

        // 2. CUSTOMER NAME LINE ("المطلوب من الأخ")
        boldPaint.textAlign = Paint.Align.RIGHT
        val custText = "المطلوب من الأخ: ${customerName.ifEmpty { "عميل نقدي" }}"
        canvas.drawText(custText, widthPx - padding, currentY + (14f * scale), boldPaint)

        currentY += 22f * scale

        // 3. TWO-COLUMN TABLE LAYOUT: EXACT ORDER (1. Total -> 2. Qty -> 3. Details -> 4. Unit Price)
        val halfWidth = contentWidth / 2f
        val rightStartX = padding + halfWidth
        val leftStartX = padding

        // Sub-column Widths (Proportional: Total 23.5%, Qty 13.5%, Description 42.5%, Unit Price 20.5%)
        val colTotalW = halfWidth * 0.235f
        val colQtyW = halfWidth * 0.135f
        val colDescW = halfWidth * 0.425f
        val colUnitW = halfWidth * 0.205f

        // Top line for column headers
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, linePaint)

        // Draw Column Headers in Bold Black
        smallBoldPaint.textAlign = Paint.Align.CENTER

        // Right Half Headers (RTL: 1. Total -> 2. Qty -> 3. Details -> 4. Unit Price)
        canvas.drawText("الإجمالي", rightStartX + halfWidth - (colTotalW / 2f), currentY + (17f * scale), smallBoldPaint)
        canvas.drawText("العدد", rightStartX + halfWidth - colTotalW - (colQtyW / 2f), currentY + (17f * scale), smallBoldPaint)
        smallBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التفاصيل", rightStartX + halfWidth - colTotalW - colQtyW - (2f * scale), currentY + (17f * scale), smallBoldPaint)
        smallBoldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("سعر الواحدة", rightStartX + (colUnitW / 2f), currentY + (17f * scale), smallBoldPaint)

        // Left Half Headers (RTL: 1. Total -> 2. Qty -> 3. Details -> 4. Unit Price)
        canvas.drawText("الإجمالي", leftStartX + halfWidth - (colTotalW / 2f), currentY + (17f * scale), smallBoldPaint)
        canvas.drawText("العدد", leftStartX + halfWidth - colTotalW - (colQtyW / 2f), currentY + (17f * scale), smallBoldPaint)
        smallBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التفاصيل", leftStartX + halfWidth - colTotalW - colQtyW - (2f * scale), currentY + (17f * scale), smallBoldPaint)
        smallBoldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("سعر الواحدة", leftStartX + (colUnitW / 2f), currentY + (17f * scale), smallBoldPaint)

        // Vertical divider between right and left columns in the header
        canvas.drawLine(padding + halfWidth, currentY, padding + halfWidth, currentY + tableHeaderHeight, thinLinePaint)

        currentY += tableHeaderHeight

        // Bottom line under headers
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, linePaint)

        // 4. DATA ROWS
        val rowDescPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        val rowTotalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 13.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }

        for (row in activeRows) {
            val rowY = currentY

            // --- Right Section Cells (1. Total -> 2. Qty -> 3. Details -> 4. Unit Price) ---
            rowTotalPaint.textAlign = Paint.Align.CENTER
            if (row.rightTotalAmountStr.isNotBlank()) {
                canvas.drawText(row.rightTotalAmountStr.toEnglishDigits(), rightStartX + halfWidth - (colTotalW / 2f), rowY + (17f * scale), rowTotalPaint)
            }

            rowDescPaint.textAlign = Paint.Align.CENTER
            if (row.rightQuantityStr.isNotBlank()) {
                canvas.drawText(row.rightQuantityStr.toEnglishDigits(), rightStartX + halfWidth - colTotalW - (colQtyW / 2f), rowY + (17f * scale), rowDescPaint)
            }

            rowDescPaint.textAlign = Paint.Align.RIGHT
            if (row.rightDescription.isNotBlank()) {
                val maxChars = (18 * (if (scale > 1f) 1.2f else 1.0f)).toInt()
                val desc = if (row.rightDescription.length > maxChars) row.rightDescription.take(maxChars - 1) + ".." else row.rightDescription
                canvas.drawText(desc, rightStartX + halfWidth - colTotalW - colQtyW - (2f * scale), rowY + (17f * scale), rowDescPaint)
            }

            rowDescPaint.textAlign = Paint.Align.CENTER
            val rightQtyValue = (row.rightQuantityStr.toEnglishDigits().toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
            val rightUnitValue = if (rightQtyValue > 0.0 && row.rightTotal > 0.0) row.rightTotal / rightQtyValue else 0.0
            if (rightUnitValue > 0.0) {
                canvas.drawText(formatter.format(rightUnitValue).toEnglishDigits(), rightStartX + (colUnitW / 2f), rowY + (17f * scale), rowDescPaint)
            }

            // --- Left Section Cells (1. Total -> 2. Qty -> 3. Details -> 4. Unit Price) ---
            rowTotalPaint.textAlign = Paint.Align.CENTER
            if (row.leftTotalAmountStr.isNotBlank()) {
                canvas.drawText(row.leftTotalAmountStr.toEnglishDigits(), leftStartX + halfWidth - (colTotalW / 2f), rowY + (17f * scale), rowTotalPaint)
            }

            rowDescPaint.textAlign = Paint.Align.CENTER
            if (row.leftQuantityStr.isNotBlank()) {
                canvas.drawText(row.leftQuantityStr.toEnglishDigits(), leftStartX + halfWidth - colTotalW - (colQtyW / 2f), rowY + (17f * scale), rowDescPaint)
            }

            rowDescPaint.textAlign = Paint.Align.RIGHT
            if (row.leftDescription.isNotBlank()) {
                val maxChars = (18 * (if (scale > 1f) 1.2f else 1.0f)).toInt()
                val desc = if (row.leftDescription.length > maxChars) row.leftDescription.take(maxChars - 1) + ".." else row.leftDescription
                canvas.drawText(desc, leftStartX + halfWidth - colTotalW - colQtyW - (2f * scale), rowY + (17f * scale), rowDescPaint)
            }

            rowDescPaint.textAlign = Paint.Align.CENTER
            val leftQtyValue = (row.leftQuantityStr.toEnglishDigits().toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
            val leftUnitValue = if (leftQtyValue > 0.0 && row.leftTotal > 0.0) row.leftTotal / leftQtyValue else 0.0
            if (leftUnitValue > 0.0) {
                canvas.drawText(formatter.format(leftUnitValue).toEnglishDigits(), leftStartX + (colUnitW / 2f), rowY + (17f * scale), rowDescPaint)
            }

            // Central vertical divider between columns
            canvas.drawLine(padding + halfWidth, rowY, padding + halfWidth, rowY + rowHeight, thinLinePaint)

            // Horizontal row separator
            canvas.drawLine(padding, rowY + rowHeight, widthPx - padding, rowY + rowHeight, thinLinePaint)

            currentY += rowHeight
        }

        currentY += 2f * scale

        // 5. SUBTOTALS (كل شيء إجمالي لحاله)
        boldPaint.textSize = 12.5f * scale
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("إجمالي اليمين: ${formatter.format(rightSubtotal).toEnglishDigits()} ${storeConfig.currencySymbol}", rightStartX + (halfWidth / 2f), currentY + (16f * scale), boldPaint)
        canvas.drawText("إجمالي اليسار: ${formatter.format(leftSubtotal).toEnglishDigits()} ${storeConfig.currencySymbol}", leftStartX + (halfWidth / 2f), currentY + (16f * scale), boldPaint)

        // Central divider in subtotals
        canvas.drawLine(padding + halfWidth, currentY, padding + halfWidth, currentY + subtotalsHeight, thinLinePaint)
        currentY += subtotalsHeight

        // Dividing line before Grand Total
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, linePaint)
        currentY += 3f * scale

        // 6. GRAND TOTAL DIRECTLY UNDERNEATH (المبلغ الإجمالي العام)
        canvas.drawRect(padding, currentY, widthPx - padding, currentY + grandTotalHeight, solidBlackFill)

        whiteTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ الإجمالي العام:", widthPx - padding - (6f * scale), currentY + (21f * scale), whiteTextPaint)

        grandTotalNumberPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${formatter.format(grandTotal).toEnglishDigits()} ${storeConfig.currencySymbol}", padding + (6f * scale), currentY + (21f * scale), grandTotalNumberPaint)

        currentY += grandTotalHeight + (4f * scale)

        // 7. ULTRA-COMPACT SIGNATURES & DISCLAIMER
        smallBoldPaint.textSize = 10f * scale
        smallBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ت.البائع: ......", widthPx - padding, currentY + (14f * scale), smallBoldPaint)

        smallBoldPaint.textAlign = Paint.Align.CENTER
        val note = storeConfig.defaultDisclaimerNote.take(35)
        canvas.drawText(note, widthPx / 2f, currentY + (14f * scale), smallBoldPaint)

        smallBoldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ت.المشتري: ......", padding, currentY + (14f * scale), smallBoldPaint)

        return bitmap
    }

    /**
     * Converts a list of InvoiceItemEntity into DualReceiptRow and renders the Bitmap.
     */
    fun createReceiptBitmapFromItems(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        items: List<InvoiceItemEntity>,
        widthPx: Int = 384
    ): Bitmap {
        val half = (items.size + 1) / 2
        val rightItems = items.take(half)
        val leftItems = items.drop(half)

        val maxCount = maxOf(rightItems.size, leftItems.size).coerceAtLeast(1)
        val rows = (0 until maxCount).map { i ->
            val r = rightItems.getOrNull(i)
            val l = leftItems.getOrNull(i)
            DualReceiptRow(
                rightDescription = r?.description ?: "",
                rightQuantityStr = r?.quantity?.let { if (it > 0) it.toString() else "1" } ?: "1",
                rightTotalAmountStr = r?.totalAmount?.let { if (it > 0) it.toString() else "" } ?: "",
                leftDescription = l?.description ?: "",
                leftQuantityStr = l?.quantity?.let { if (it > 0) it.toString() else "1" } ?: "1",
                leftTotalAmountStr = l?.totalAmount?.let { if (it > 0) it.toString() else "" } ?: ""
            )
        }

        return createReceiptBitmap(
            context = context,
            storeConfig = storeConfig,
            invoiceNumber = invoiceNumber,
            dateString = dateString,
            customerName = customerName,
            paymentType = paymentType,
            dualRows = rows,
            widthPx = widthPx
        )
    }
}
