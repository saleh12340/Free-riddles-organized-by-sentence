package com.example.ui.components

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.CustomerReceiptEntity
import com.example.data.local.InvoiceItemEntity
import com.example.data.local.StoreConfigEntity
import com.example.ui.util.toEnglishDigits
import com.example.ui.viewmodels.ReceiptRow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ReceiptBitmapHelper {

    private val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    fun createReceiptBitmap(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        rows: List<ReceiptRow>,
        receivedAmount: Double = 0.0,
        widthPx: Int = 384 // Default 384px (58mm native thermal resolution)
    ): Bitmap {
        val baseWidth = 384f
        val scale = widthPx.toFloat() / baseWidth

        // Filter out completely empty rows
        val activeRows = rows.filterNot { it.isCompletelyEmpty }.ifEmpty {
            listOf(ReceiptRow(description = "صنف", quantityStr = "1", totalAmountStr = "0"))
        }

        val grandTotal = activeRows.sumOf { it.total }
        val remaining = (grandTotal - receivedAmount).coerceAtLeast(0.0)
        val hasReceivedSection = receivedAmount > 0.0

        // Dynamic height calculations scaled proportionally
        val headerHeight = 90f * scale
        val tableHeaderHeight = 26f * scale
        val rowHeight = 25f * scale
        val tableHeight = activeRows.size * rowHeight
        val grandTotalHeight = 32f * scale
        val receivedHeight = if (hasReceivedSection) 46f * scale else 0f
        val footerHeight = 24f * scale
        val padding = 8f * scale
        val totalHeight = (headerHeight + tableHeaderHeight + tableHeight + grandTotalHeight + receivedHeight + footerHeight + (24f * scale)).toInt().coerceAtLeast(300)

        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val amiriTypeface = ResourcesCompat.getFont(context, R.font.amiri) ?: Typeface.DEFAULT
        val amiriBold = Typeface.create(amiriTypeface, Typeface.BOLD)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f * scale
            typeface = amiriBold
            textAlign = Paint.Align.CENTER
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f * scale
            typeface = amiriTypeface
            textAlign = Paint.Align.CENTER
        }

        val labelBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11.5f * scale
            typeface = amiriBold
        }

        val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10.5f * scale
            typeface = amiriTypeface
        }

        val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * scale
            typeface = amiriBold
        }

        val grandTotalNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f * scale
            typeface = amiriBold
        }

        val solidBlackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val borderStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * scale
        }

        val thinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f * scale
        }

        val dottedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            strokeWidth = 1f * scale
            pathEffect = DashPathEffect(floatArrayOf(4f * scale, 3f * scale), 0f)
        }

        val smallBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f * scale
            typeface = amiriBold
        }

        var currentY = padding

        // 1. STORE HEADER
        val storeName = storeConfig.storeNameArabic.ifEmpty { "بقالة العزي" }
        canvas.drawText(storeName, widthPx / 2f, currentY + (22f * scale), titlePaint)
        currentY += 27f * scale

        val phones = "هاتف: ${storeConfig.phone1.ifEmpty { "772437314" }}" +
                if (storeConfig.phone2.isNotEmpty()) " - ${storeConfig.phone2}" else ""
        canvas.drawText(phones, widthPx / 2f, currentY + (11f * scale), subPaint)
        currentY += 15f * scale

        if (storeConfig.address.isNotEmpty()) {
            canvas.drawText(storeConfig.address, widthPx / 2f, currentY + (10f * scale), subPaint)
            currentY += 14f * scale
        }

        canvas.drawLine(padding, currentY + (2f * scale), widthPx - padding, currentY + (2f * scale), dottedLinePaint)
        currentY += 8f * scale

        // 2. INVOICE META ROW
        labelBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فاتورة: #${invoiceNumber.toString().toEnglishDigits()}", widthPx - padding, currentY + (13f * scale), labelBoldPaint)

        labelBoldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(paymentType, widthPx / 2f, currentY + (13f * scale), labelBoldPaint)

        regularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dateString.toEnglishDigits(), padding, currentY + (13f * scale), regularPaint)
        currentY += 17f * scale

        // Customer Name Row
        labelBoldPaint.textAlign = Paint.Align.RIGHT
        val custText = "العميل: ${customerName.ifEmpty { "عميل نقدي" }}"
        canvas.drawText(custText, widthPx - padding, currentY + (13f * scale), labelBoldPaint)
        currentY += 19f * scale

        // 3. TABLE HEADER
        val colNoW = 28f * scale
        val colUnitW = 54f * scale
        val colQtyW = 44f * scale
        val colTotalW = 68f * scale

        canvas.drawRect(padding, currentY, widthPx - padding, currentY + tableHeaderHeight, solidBlackFill)

        whiteTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("م", widthPx - padding - (colNoW / 2f), currentY + (17f * scale), whiteTextPaint)
        canvas.drawText("البيان / الصنف", padding + colTotalW + colQtyW + colUnitW + ((widthPx - (2 * padding) - colTotalW - colQtyW - colUnitW - colNoW) / 2f), currentY + (17f * scale), whiteTextPaint)
        canvas.drawText("العدد", padding + colTotalW + colUnitW + (colQtyW / 2f), currentY + (17f * scale), whiteTextPaint)
        canvas.drawText("السعر", padding + colTotalW + (colUnitW / 2f), currentY + (17f * scale), whiteTextPaint)
        canvas.drawText("الإجمالي", padding + (colTotalW / 2f), currentY + (17f * scale), whiteTextPaint)

        currentY += tableHeaderHeight

        // 4. ITEMS ROWS
        val rowDescPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f * scale
            typeface = amiriTypeface
        }
        val rowNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10.5f * scale
            typeface = amiriBold
            textAlign = Paint.Align.CENTER
        }

        activeRows.forEachIndexed { idx, row ->
            val rowY = currentY
            val q = row.quantityStr.toDoubleOrNull() ?: 1.0
            val tot = row.total
            val unitVal = if (q > 0) tot / q else tot

            // م
            rowNumberPaint.textAlign = Paint.Align.CENTER
            canvas.drawText((idx + 1).toString().toEnglishDigits(), widthPx - padding - (colNoW / 2f), rowY + (17f * scale), rowNumberPaint)

            // الإجمالي
            rowNumberPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(formatter.format(tot).toEnglishDigits(), padding + (colTotalW / 2f), rowY + (17f * scale), rowNumberPaint)

            // العدد
            rowNumberPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(row.quantityStr.ifEmpty { "1" }.toEnglishDigits(), padding + colTotalW + colUnitW + (colQtyW / 2f), rowY + (17f * scale), rowNumberPaint)

            // البيان
            rowDescPaint.textAlign = Paint.Align.RIGHT
            if (row.description.isNotBlank()) {
                val maxChars = (24 * (if (scale > 1f) 1.2f else 1.0f)).toInt()
                val desc = if (row.description.length > maxChars) row.description.take(maxChars - 1) + ".." else row.description
                canvas.drawText(desc, widthPx - padding - colNoW - (2f * scale), rowY + (17f * scale), rowDescPaint)
            }

            // السعر
            rowDescPaint.textAlign = Paint.Align.CENTER
            if (unitVal > 0.0) {
                canvas.drawText(formatter.format(unitVal).toEnglishDigits(), padding + colTotalW + (colUnitW / 2f), rowY + (17f * scale), rowDescPaint)
            }

            canvas.drawLine(padding, rowY + rowHeight, widthPx - padding, rowY + rowHeight, thinLinePaint)
            currentY += rowHeight
        }

        currentY += 4f * scale

        // 5. GRAND TOTAL DIRECTLY UNDERNEATH
        canvas.drawRect(padding, currentY, widthPx - padding, currentY + grandTotalHeight, solidBlackFill)

        whiteTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ الإجمالي العام:", widthPx - padding - (6f * scale), currentY + (21f * scale), whiteTextPaint)

        grandTotalNumberPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${formatter.format(grandTotal).toEnglishDigits()} ${storeConfig.currencySymbol}", padding + (6f * scale), currentY + (21f * scale), grandTotalNumberPaint)

        currentY += grandTotalHeight + (4f * scale)

        // 6. PAID & REMAINING SECTION (المبلغ الواصل والمتبقي)
        if (hasReceivedSection) {
            val paidBoxH = 20f * scale
            val remBoxH = 20f * scale

            // الواصل
            labelBoldPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("المبلغ الواصل (المدفوع):", widthPx - padding - (4f * scale), currentY + (14f * scale), labelBoldPaint)
            labelBoldPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${formatter.format(receivedAmount).toEnglishDigits()} ${storeConfig.currencySymbol}", padding + (4f * scale), currentY + (14f * scale), labelBoldPaint)
            currentY += paidBoxH

            // المتبقي
            labelBoldPaint.textAlign = Paint.Align.RIGHT
            val remLabel = if (remaining > 0) "المتبقي (الدين):" else "حالة الحساب:"
            val remVal = if (remaining > 0) "${formatter.format(remaining).toEnglishDigits()} ${storeConfig.currencySymbol}" else "خالص (مسدد بالكامل)"
            canvas.drawText(remLabel, widthPx - padding - (4f * scale), currentY + (14f * scale), labelBoldPaint)
            labelBoldPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(remVal, padding + (4f * scale), currentY + (14f * scale), labelBoldPaint)
            currentY += remBoxH + (4f * scale)
        }

        // 7. ULTRA-COMPACT SIGNATURES & DISCLAIMER
        smallBoldPaint.textSize = 9.5f * scale
        smallBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ت.البائع: ......", widthPx - padding, currentY + (13f * scale), smallBoldPaint)

        smallBoldPaint.textAlign = Paint.Align.CENTER
        val note = storeConfig.defaultDisclaimerNote.take(35)
        canvas.drawText(note, widthPx / 2f, currentY + (13f * scale), smallBoldPaint)

        smallBoldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ت.المشتري: ......", padding, currentY + (13f * scale), smallBoldPaint)

        return bitmap
    }

    fun createReceiptBitmapFromItems(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        items: List<InvoiceItemEntity>,
        receivedAmount: Double = 0.0,
        widthPx: Int = 384
    ): Bitmap {
        val rows = items.map { item ->
            ReceiptRow(
                description = item.description,
                quantityStr = if (item.quantity > 0) item.quantity.toString() else "1",
                totalAmountStr = if (item.totalAmount > 0) item.totalAmount.toString() else ""
            )
        }.ifEmpty { listOf(ReceiptRow(description = "صنف", quantityStr = "1", totalAmountStr = "0")) }

        return createReceiptBitmap(
            context = context,
            storeConfig = storeConfig,
            invoiceNumber = invoiceNumber,
            dateString = dateString,
            customerName = customerName,
            paymentType = paymentType,
            rows = rows,
            receivedAmount = receivedAmount,
            widthPx = widthPx
        )
    }

    /**
     * Thermal print voucher for a customer receipt (سند قبض نقدي)
     */
    fun createVoucherBitmap(
        context: Context,
        storeConfig: StoreConfigEntity,
        receipt: CustomerReceiptEntity,
        widthPx: Int = 384
    ): Bitmap {
        val baseWidth = 384f
        val scale = widthPx.toFloat() / baseWidth

        val totalHeight = (270f * scale).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val amiriTypeface = ResourcesCompat.getFont(context, R.font.amiri) ?: Typeface.DEFAULT
        val amiriBold = Typeface.create(amiriTypeface, Typeface.BOLD)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f * scale
            typeface = amiriBold
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f * scale
            typeface = amiriTypeface
            textAlign = Paint.Align.CENTER
        }
        val labelBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f * scale
            typeface = amiriBold
        }
        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f * scale
            typeface = amiriBold
            textAlign = Paint.Align.CENTER
        }
        val solidBlackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val dottedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            strokeWidth = 1f * scale
            pathEffect = DashPathEffect(floatArrayOf(4f * scale, 3f * scale), 0f)
        }

        val padding = 10f * scale
        var currentY = padding

        // Header
        canvas.drawText(storeConfig.storeNameArabic.ifEmpty { "متجرنا" }, widthPx / 2f, currentY + (20f * scale), titlePaint)
        currentY += 25f * scale
        canvas.drawText("سند قبض وتسديد مالي #${receipt.receiptId.toString().toEnglishDigits()}", widthPx / 2f, currentY + (12f * scale), subPaint)
        currentY += 18f * scale

        canvas.drawLine(padding, currentY, widthPx - padding, currentY, dottedLinePaint)
        currentY += 8f * scale

        // Meta info
        labelBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التاريخ: ${receipt.dateString.toEnglishDigits()}", widthPx - padding, currentY + (14f * scale), labelBoldPaint)
        currentY += 18f * scale

        canvas.drawText("استلمنا من الأخ: ${receipt.customerName}", widthPx - padding, currentY + (14f * scale), labelBoldPaint)
        currentY += 20f * scale

        // Amount Box
        val boxH = 34f * scale
        canvas.drawRect(padding, currentY, widthPx - padding, currentY + boxH, solidBlackFill)
        val amtStr = "مبلغ وقدره: ${formatter.format(receipt.amount).toEnglishDigits()} ${storeConfig.currencySymbol}"
        canvas.drawText(amtStr, widthPx / 2f, currentY + (23f * scale), amountPaint)
        currentY += boxH + (12f * scale)

        // Notes & Type
        labelBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("نوع الحركة: ${receipt.paymentType}", widthPx - padding, currentY + (14f * scale), labelBoldPaint)
        currentY += 18f * scale

        if (receipt.notes.isNotEmpty()) {
            canvas.drawText("البيان: ${receipt.notes}", widthPx - padding, currentY + (14f * scale), labelBoldPaint)
            currentY += 18f * scale
        }

        currentY += 8f * scale
        canvas.drawLine(padding, currentY, widthPx - padding, currentY, dottedLinePaint)
        currentY += 14f * scale

        // Signatures
        labelBoldPaint.textSize = 10f * scale
        labelBoldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المستلم / الصندوق: ......", widthPx - padding, currentY + (10f * scale), labelBoldPaint)
        labelBoldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المسدد: ......", padding, currentY + (10f * scale), labelBoldPaint)

        return bitmap
    }

    /**
     * Thermal print for consolidated customer statement (selected or full transactions)
     */
    fun createCustomerStatementThermalBitmap(
        context: Context,
        storeConfig: StoreConfigEntity,
        customerName: String,
        statementRows: List<InvoicePdfHelper.CustomerStatementRow>,
        widthPx: Int = 384
    ): Bitmap {
        val baseWidth = 384f
        val scale = widthPx.toFloat() / baseWidth

        val headerH = 75f * scale
        val rowH = 22f * scale
        val tableH = (statementRows.size + 1) * rowH
        val footerH = 65f * scale
        val padding = 8f * scale
        val totalH = (headerH + tableH + footerH + (20f * scale)).toInt().coerceAtLeast(300)

        val bitmap = Bitmap.createBitmap(widthPx, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val amiriTypeface = ResourcesCompat.getFont(context, R.font.amiri) ?: Typeface.DEFAULT
        val amiriBold = Typeface.create(amiriTypeface, Typeface.BOLD)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 19f * scale
            typeface = amiriBold
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f * scale
            typeface = amiriTypeface
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10.5f * scale
            typeface = amiriBold
        }
        val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f * scale
            typeface = amiriTypeface
        }
        val solidBlackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val whiteText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f * scale
            typeface = amiriBold
            textAlign = Paint.Align.CENTER
        }
        val thinLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f * scale
        }

        var currentY = padding

        // Header
        canvas.drawText(storeConfig.storeNameArabic.ifEmpty { "متجرنا" }, widthPx / 2f, currentY + (18f * scale), titlePaint)
        currentY += 22f * scale
        canvas.drawText("كشف حساب: $customerName", widthPx / 2f, currentY + (12f * scale), subPaint)
        currentY += 16f * scale

        // Table Header
        canvas.drawRect(padding, currentY, widthPx - padding, currentY + rowH, solidBlackFill)
        canvas.drawText("التاريخ", widthPx - padding - (45f * scale), currentY + (15f * scale), whiteText)
        canvas.drawText("العملية", widthPx - padding - (110f * scale), currentY + (15f * scale), whiteText)
        canvas.drawText("المبلغ", padding + (90f * scale), currentY + (15f * scale), whiteText)
        canvas.drawText("الرصيد", padding + (35f * scale), currentY + (15f * scale), whiteText)
        currentY += rowH

        // Rows
        boldPaint.textSize = 9.5f * scale
        regularPaint.textSize = 9f * scale

        statementRows.forEach { r ->
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(r.date.takeLast(5).toEnglishDigits(), widthPx - padding - (5f * scale), currentY + (15f * scale), regularPaint)

            boldPaint.textAlign = Paint.Align.RIGHT
            val desc = r.type.take(12)
            canvas.drawText(desc, widthPx - padding - (60f * scale), currentY + (15f * scale), boldPaint)

            val amt = if (r.debit > 0) r.debit else r.credit
            boldPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(formatter.format(amt).toEnglishDigits(), padding + (90f * scale), currentY + (15f * scale), boldPaint)

            boldPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(formatter.format(r.runningBalance).toEnglishDigits(), padding + (5f * scale), currentY + (15f * scale), boldPaint)

            canvas.drawLine(padding, currentY + rowH, widthPx - padding, currentY + rowH, thinLine)
            currentY += rowH
        }

        currentY += 8f * scale

        // Net Balance Box
        val lastBal = statementRows.lastOrNull()?.runningBalance ?: 0.0
        canvas.drawRect(padding, currentY, widthPx - padding, currentY + (28f * scale), solidBlackFill)
        whiteText.textAlign = Paint.Align.RIGHT
        canvas.drawText("الرصيد النهائي المستحق:", widthPx - padding - (8f * scale), currentY + (18f * scale), whiteText)
        whiteText.textAlign = Paint.Align.LEFT
        canvas.drawText("${formatter.format(lastBal).toEnglishDigits()} ${storeConfig.currencySymbol}", padding + (8f * scale), currentY + (18f * scale), whiteText)

        return bitmap
    }
}
