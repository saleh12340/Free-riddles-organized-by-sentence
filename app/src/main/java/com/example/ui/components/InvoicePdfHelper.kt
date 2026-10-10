package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.InvoiceItemEntity
import com.example.data.local.StoreConfigEntity
import com.example.ui.viewmodels.DualReceiptRow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object InvoicePdfHelper {

    /**
     * Generates a crystal-clear, true 4K Ultra-HD PDF document for an invoice (2160px native resolution).
     * Saved in the app's cache directory ready for instant sharing via WhatsApp/Email/Telegram without any blur.
     */
    fun createInvoicePdf(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        dualRows: List<DualReceiptRow>
    ): File? {
        return try {
            // 1. Render true 4K Ultra-HD 2160px receipt bitmap
            val bitmap = ReceiptBitmapHelper.createReceiptBitmap(
                context = context,
                storeConfig = storeConfig,
                invoiceNumber = invoiceNumber,
                dateString = dateString,
                customerName = customerName,
                paymentType = paymentType,
                dualRows = dualRows,
                widthPx = 2160 // 4K Ultra-HD native rendering
            )

            // 2. Prepare 4K PDF Page Dimensions (1:1 with 2160px bitmap for zero quality loss)
            val pdfWidth = 2160
            val pdfHeight = bitmap.height

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, 1).create()
            val page = document.startPage(pageInfo)

            val canvas: Canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
            }

            // Draw 4K bitmap at 1:1 scale
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            document.finishPage(page)

            // 3. Write PDF to cache directory
            val pdfDir = File(context.cacheDir, "invoices_pdf")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val cleanCustomer = customerName.replace(" ", "_").take(15).ifEmpty { "عميل" }
            val pdfFile = File(pdfDir, "فاتورة_${invoiceNumber}_$cleanCustomer.pdf")

            val outputStream = FileOutputStream(pdfFile)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            document.close()

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates an invoice PDF directly from saved database items in 4K resolution.
     */
    fun createInvoicePdfFromItems(
        context: Context,
        storeConfig: StoreConfigEntity,
        invoiceNumber: Int,
        dateString: String,
        customerName: String,
        paymentType: String,
        items: List<InvoiceItemEntity>
    ): File? {
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

        return createInvoicePdf(
            context = context,
            storeConfig = storeConfig,
            invoiceNumber = invoiceNumber,
            dateString = dateString,
            customerName = customerName,
            paymentType = paymentType,
            dualRows = rows
        )
    }

    /**
     * Returns a content:// URI for sharing via Intent using FileProvider.
     */
    fun getShareablePdfUri(context: Context, pdfFile: File): Uri? {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates an Android Share Intent with standard application/pdf mime type.
     */
    fun createSharePdfIntent(context: Context, pdfUri: Uri, invoiceNumber: Int, customerName: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, "فاتورة رقم #$invoiceNumber - $customerName")
            putExtra(Intent.EXTRA_TEXT, "مرفق فاتورة إلكترونية رسمية عالية الجودة 4K رقم #$invoiceNumber للعميل $customerName.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Saves the PDF permanently to the device's Downloads/Documents folder.
     */
    fun savePdfToDownloads(context: Context, pdfFile: File, fileName: String): Uri? {
        var fos: OutputStream? = null
        var fileUri: Uri? = null
        val finalName = "فاتورة_${fileName}_${System.currentTimeMillis()}.pdf"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fatooraty")
                }
                val resolver = context.contentResolver
                fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (fileUri != null) {
                    fos = resolver.openOutputStream(fileUri)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fatooratyDir = File(downloadsDir, "Fatooraty")
                if (!fatooratyDir.exists()) fatooratyDir.mkdirs()
                val destFile = File(fatooratyDir, finalName)
                fos = FileOutputStream(destFile)
                fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }

            if (fos != null) {
                pdfFile.inputStream().use { input ->
                    input.copyTo(fos!!)
                }
                fos!!.flush()
                fos!!.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return fileUri
    }
}
