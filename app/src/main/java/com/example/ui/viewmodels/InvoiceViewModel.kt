package com.example.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.*
import com.example.data.local.*
import com.example.data.repository.InvoiceRepository
import com.example.printer.BluetoothPrinterManager
import com.example.printer.PrinterConnectionState
import com.example.printer.ThermalImageConverter
import com.example.ui.components.InvoicePdfHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.ui.util.toEnglishDigits
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DualReceiptRow(
    val id: String = UUID.randomUUID().toString(),
    var rightDescription: String = "",
    var rightQuantityStr: String = "1",
    var rightTotalAmountStr: String = "",
    var leftDescription: String = "",
    var leftQuantityStr: String = "1",
    var leftTotalAmountStr: String = ""
) {
    val rightTotal: Double get() = rightTotalAmountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
    val leftTotal: Double get() = leftTotalAmountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
    val hasRightData: Boolean get() = rightDescription.isNotBlank() || rightTotalAmountStr.isNotBlank()
    val hasLeftData: Boolean get() = leftDescription.isNotBlank() || leftTotalAmountStr.isNotBlank()
    val isCompletelyEmpty: Boolean get() = !hasRightData && !hasLeftData
}

class InvoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = InvoiceRepository(db)
    val bluetoothPrinterManager = BluetoothPrinterManager(application)
    val backupManager = BackupManager(application, repository)

    val storeConfig = repository.storeConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StoreConfigEntity()
    )

    val allInvoices = repository.allInvoices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allProducts = repository.allProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allReceipts = db.customerReceiptDao().getAllReceipts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val printerState: StateFlow<PrinterConnectionState> = bluetoothPrinterManager.connectionState

    // Form State for Current Invoice (English digits for numbers and dates)
    var invoiceNumber by mutableStateOf(1001)
    var dateString by mutableStateOf(SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date()))
    var customerName by mutableStateOf("")
    var paymentType by mutableStateOf("نقداً") // "نقداً" or "أجل"
    var notes by mutableStateOf("")
    var discountAmountStr by mutableStateOf("")

    // Smart expanding dual-column rows: Starts with DEFAULT_ROW_COUNT
    val dualRows = mutableStateListOf<DualReceiptRow>()

    var searchQuery by mutableStateOf("")

    private val _uiEventMessage = MutableStateFlow<String?>(null)
    val uiEventMessage: StateFlow<String?> = _uiEventMessage.asStateFlow()

    companion object {
        const val DEFAULT_ROW_COUNT = 2
    }

    init {
        viewModelScope.launch {
            invoiceNumber = repository.getNextInvoiceNumber()
            if (dualRows.isEmpty()) {
                repeat(DEFAULT_ROW_COUNT) {
                    dualRows.add(DualReceiptRow())
                }
            }
        }
    }

    fun clearUiMessage() {
        _uiEventMessage.value = null
    }

    fun resetForm() {
        viewModelScope.launch {
            invoiceNumber = repository.getNextInvoiceNumber()
            dateString = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
            customerName = ""
            paymentType = "نقداً"
            notes = ""
            discountAmountStr = ""
            dualRows.clear()
            repeat(DEFAULT_ROW_COUNT) {
                dualRows.add(DualReceiptRow())
            }
        }
    }

    private fun checkAutoExpand(editedIndex: Int) {
        if (editedIndex !in dualRows.indices) return
        val row = dualRows[editedIndex]
        val rightComplete = row.rightDescription.isNotBlank() && row.rightTotalAmountStr.isNotBlank()
        val leftComplete = row.leftDescription.isNotBlank() && row.leftTotalAmountStr.isNotBlank()

        // Smart flow: finish the right cell first, then move to the left cell
        // of the same visual row. Only after the left cell is complete do we
        // create the next row and continue from the right side.
        if (editedIndex == dualRows.lastIndex && leftComplete) {
            dualRows.add(DualReceiptRow())
        } else if (rightComplete && !row.hasLeftData) {
            // Keep this row open for the left-side entry; do not create a
            // second right-side row prematurely.
        } else if (editedIndex == dualRows.lastIndex && row.hasRightData && !row.hasLeftData) {
            // Waiting for the left side is intentional.
        }
    }

    fun updateRightDescription(index: Int, desc: String) {
        if (index in dualRows.indices) {
            dualRows[index] = dualRows[index].copy(rightDescription = desc)
            checkAutoExpand(index)
        }
    }

    fun updateRightQuantity(index: Int, qty: String) {
        if (index in dualRows.indices) {
            dualRows[index] = dualRows[index].copy(rightQuantityStr = qty.toEnglishDigits())
            checkAutoExpand(index)
        }
    }

    fun updateRightTotalAmount(index: Int, total: String) {
        if (index in dualRows.indices) {
            dualRows[index] = dualRows[index].copy(rightTotalAmountStr = total.toEnglishDigits())
            checkAutoExpand(index)
        }
    }

    fun updateLeftDescription(index: Int, desc: String) {
        if (index in dualRows.indices) {
            dualRows[index] = dualRows[index].copy(leftDescription = desc)
            checkAutoExpand(index)
        }
    }

    fun updateLeftQuantity(index: Int, qty: String) {
        if (index in dualRows.indices) {
            dualRows[index] = dualRows[index].copy(leftQuantityStr = qty.toEnglishDigits())
            checkAutoExpand(index)
        }
    }

    fun updateLeftTotalAmount(index: Int, total: String) {
        if (index in dualRows.indices) {
            dualRows[index] = dualRows[index].copy(leftTotalAmountStr = total.toEnglishDigits())
            checkAutoExpand(index)
        }
    }

    fun addManualRow() {
        dualRows.add(DualReceiptRow())
    }

    fun removeRow(index: Int) {
        if (dualRows.size > 1 && index in dualRows.indices) {
            dualRows.removeAt(index)
        }
    }

    fun calculateRightSubtotal(): Double = dualRows.sumOf { it.rightTotal }
    fun calculateLeftSubtotal(): Double = dualRows.sumOf { it.leftTotal }
    fun calculateSubtotal(): Double = calculateRightSubtotal() + calculateLeftSubtotal()
    fun getDiscountAmount(): Double = discountAmountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
    fun calculateGrandTotal(): Double = (calculateSubtotal() - getDiscountAmount()).coerceAtLeast(0.0)

    fun saveInvoiceToDatabase(onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            if (customerName.isBlank()) {
                customerName = "عميل نقدي"
            }
            val itemsToSave = mutableListOf<InvoiceItemEntity>()
            // Right side items first (from top to bottom)
            dualRows.forEach { row ->
                if (row.hasRightData) {
                    val q = row.rightQuantityStr.toDoubleOrNull() ?: 1.0
                    val tot = row.rightTotal
                    itemsToSave.add(
                        InvoiceItemEntity(
                            invoiceId = 0,
                            description = row.rightDescription.ifBlank { "صنف" },
                            quantity = q,
                            unitPrice = if (q > 0) tot / q else tot,
                            totalAmount = tot
                        )
                    )
                }
            }
            // Left side items next (from top to bottom)
            dualRows.forEach { row ->
                if (row.hasLeftData) {
                    val q = row.leftQuantityStr.toDoubleOrNull() ?: 1.0
                    val tot = row.leftTotal
                    itemsToSave.add(
                        InvoiceItemEntity(
                            invoiceId = 0,
                            description = row.leftDescription.ifBlank { "صنف" },
                            quantity = q,
                            unitPrice = if (q > 0) tot / q else tot,
                            totalAmount = tot
                        )
                    )
                }
            }

            if (itemsToSave.isEmpty()) {
                _uiEventMessage.value = "يرجى كتابة أصناف الفاتورة أولاً"
                return@launch
            }

            try {
                val subtotal = calculateSubtotal()
                val discount = getDiscountAmount()
                val grandTotal = calculateGrandTotal()
                val currentInvoiceNum = invoiceNumber
                val invoiceEntity = InvoiceEntity(
                    invoiceNumber = currentInvoiceNum,
                    dateString = dateString,
                    customerName = customerName,
                    paymentType = paymentType,
                    subtotal = subtotal,
                    discount = discount,
                    grandTotal = grandTotal,
                    notes = notes
                )

                val savedId = repository.saveInvoice(invoiceEntity, itemsToSave)
                _uiEventMessage.value = "تم حفظ الفاتورة بنجاح برقم #$currentInvoiceNum وحفظها في السجل!"

                // Advance to next invoice number and prepare fresh rows
                invoiceNumber = repository.getNextInvoiceNumber()
                customerName = ""
                discountAmountStr = ""
                dualRows.clear()
                repeat(DEFAULT_ROW_COUNT) {
                    dualRows.add(DualReceiptRow())
                }

                onSuccess(savedId)
            } catch (e: Exception) {
                _uiEventMessage.value = "حدث خطأ أثناء حفظ الفاتورة: ${e.localizedMessage}"
            }
        }
    }

    fun loadInvoiceForEditing(invoiceWithItems: InvoiceWithItems) {
        val inv = invoiceWithItems.invoice
        invoiceNumber = inv.invoiceNumber
        dateString = inv.dateString
        customerName = inv.customerName
        paymentType = inv.paymentType
        notes = inv.notes
        discountAmountStr = if (inv.discount > 0) inv.discount.toString() else ""

        dualRows.clear()
        val allItems = invoiceWithItems.items
        val half = (allItems.size + 1) / 2
        val rightItems = allItems.take(half)
        val leftItems = allItems.drop(half)

        val maxCount = maxOf(rightItems.size, leftItems.size)
        for (i in 0 until maxCount) {
            val r = rightItems.getOrNull(i)
            val l = leftItems.getOrNull(i)
            dualRows.add(
                DualReceiptRow(
                    rightDescription = r?.description ?: "",
                    rightQuantityStr = r?.quantity?.toString() ?: "1",
                    rightTotalAmountStr = r?.totalAmount?.let { if (it > 0) it.toString() else "" } ?: "",
                    leftDescription = l?.description ?: "",
                    leftQuantityStr = l?.quantity?.toString() ?: "1",
                    leftTotalAmountStr = l?.totalAmount?.let { if (it > 0) it.toString() else "" } ?: ""
                )
            )
        }
        // Add 1 trailing empty row
        dualRows.add(DualReceiptRow())
    }

    fun deleteInvoice(invoiceId: Long) {
        viewModelScope.launch {
            repository.deleteInvoice(invoiceId)
            _uiEventMessage.value = "تم حذف الفاتورة"
        }
    }

    fun printInvoiceAsBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val result = bluetoothPrinterManager.printReceiptBitmap(bitmap)
            result.onSuccess {
                _uiEventMessage.value = "تمت طباعة الفاتورة حرارياً بنجاح!"
            }.onFailure { e ->
                _uiEventMessage.value = e.message ?: "خطأ في الطباعة"
            }
        }
    }

    fun saveInvoiceImageToGallery(bitmap: Bitmap): Uri? {
        val uri = ThermalImageConverter.saveBitmapToGallery(
            context = getApplication(),
            bitmap = bitmap,
            fileName = "$invoiceNumber"
        )
        if (uri != null) {
            _uiEventMessage.value = "تم حفظ الفاتورة كصورة في الاستوديو!"
        } else {
            _uiEventMessage.value = "تعذر حفظ الصورة"
        }
        return uri
    }

    fun getShareableImageUri(bitmap: Bitmap): Uri? {
        return ThermalImageConverter.getShareableUri(getApplication(), bitmap)
    }

    fun generateInvoicePdf(): File? {
        val config = storeConfig.value ?: StoreConfigEntity()
        return InvoicePdfHelper.createInvoicePdf(
            context = getApplication(),
            storeConfig = config,
            invoiceNumber = invoiceNumber,
            dateString = dateString,
            customerName = customerName,
            paymentType = paymentType,
            dualRows = dualRows
        )
    }

    fun getShareableInvoicePdfUri(): Uri? {
        val file = generateInvoicePdf() ?: return null
        return InvoicePdfHelper.getShareablePdfUri(getApplication(), file)
    }

    fun saveInvoicePdfToDownloads(): Uri? {
        val file = generateInvoicePdf() ?: return null
        val uri = InvoicePdfHelper.savePdfToDownloads(getApplication(), file, "$invoiceNumber")
        if (uri != null) {
            _uiEventMessage.value = "تم حفظ ملف PDF في مجلد التنزيلات بنجاح!"
        } else {
            _uiEventMessage.value = "تعذر حفظ ملف PDF"
        }
        return uri
    }

    fun generateInvoicePdfFromItems(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): File? {
        val config = storeConfig.value ?: StoreConfigEntity()
        return InvoicePdfHelper.createInvoicePdfFromItems(
            context = getApplication(),
            storeConfig = config,
            invoiceNumber = invoice.invoiceNumber,
            dateString = invoice.dateString,
            customerName = invoice.customerName,
            paymentType = invoice.paymentType,
            items = items
        )
    }

    fun getShareablePdfUriForInvoice(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): Uri? {
        val file = generateInvoicePdfFromItems(invoice, items) ?: return null
        return InvoicePdfHelper.getShareablePdfUri(getApplication(), file)
    }

    fun getShareableCustomerStatementPdf(
        customerName: String,
        statementRows: List<InvoicePdfHelper.CustomerStatementRow>,
        totalDebit: Double,
        totalCredit: Double,
        netBalance: Double
    ): Uri? {
        val config = storeConfig.value ?: StoreConfigEntity()
        val file = InvoicePdfHelper.createCustomerStatementPdf(
            context = getApplication(),
            storeConfig = config,
            customerName = customerName,
            statementRows = statementRows,
            totalDebit = totalDebit,
            totalCredit = totalCredit,
            netBalance = netBalance
        ) ?: return null
        return InvoicePdfHelper.getShareablePdfUri(getApplication(), file)
    }

    fun addCatalogProductToInvoice(name: String, price: Double) {
        val priceStr = if (price > 0) price.toString() else ""
        
        // 1. Fill empty RIGHT slot from top to bottom
        var placed = false
        for (i in dualRows.indices) {
            val row = dualRows[i]
            if (!row.hasRightData) {
                dualRows[i] = row.copy(rightDescription = name, rightTotalAmountStr = priceStr)
                placed = true
                break
            }
        }

        // 2. When RIGHT side is full, transfer to LEFT slot from top to bottom
        if (!placed) {
            for (i in dualRows.indices) {
                val row = dualRows[i]
                if (!row.hasLeftData) {
                    dualRows[i] = row.copy(leftDescription = name, leftTotalAmountStr = priceStr)
                    placed = true
                    break
                }
            }
        }

        // 3. When both sides of all rows are full, add a new row
        if (!placed) {
            dualRows.add(DualReceiptRow(rightDescription = name, rightTotalAmountStr = priceStr))
        }
        _uiEventMessage.value = "تم إدراج الصنف في الفاتورة"
    }

    fun addQuickItem(name: String, quantityStr: String, totalAmountStr: String) {
        val cleanName = name.trim().ifEmpty { "صنف" }
        val cleanQty = quantityStr.trim().toEnglishDigits().ifEmpty { "1" }
        val cleanTot = totalAmountStr.trim().toEnglishDigits().ifEmpty { "0" }

        // 1. Fill empty RIGHT slot from top to bottom
        var placed = false
        for (i in dualRows.indices) {
            val row = dualRows[i]
            if (!row.hasRightData) {
                dualRows[i] = row.copy(
                    rightDescription = cleanName,
                    rightQuantityStr = cleanQty,
                    rightTotalAmountStr = cleanTot
                )
                placed = true
                break
            }
        }

        // 2. When RIGHT column is full, transfer and fill LEFT slot from top to bottom
        if (!placed) {
            for (i in dualRows.indices) {
                val row = dualRows[i]
                if (!row.hasLeftData) {
                    dualRows[i] = row.copy(
                        leftDescription = cleanName,
                        leftQuantityStr = cleanQty,
                        leftTotalAmountStr = cleanTot
                    )
                    placed = true
                    break
                }
            }
        }

        // 3. When both columns of all existing rows are full, append a new row
        if (!placed) {
            dualRows.add(
                DualReceiptRow(
                    rightDescription = cleanName,
                    rightQuantityStr = cleanQty,
                    rightTotalAmountStr = cleanTot
                )
            )
        }

        _uiEventMessage.value = "تمت إضافة الصنف $cleanName بنجاح!"
    }

    fun addCatalogProduct(name: String, price: Double, category: String = "عام") {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.saveProduct(ProductCatalogEntity(name = name, defaultUnitPrice = price, category = category))
                _uiEventMessage.value = "تم إضافة الصنف إلى الدليل بنجاح"
            }
        }
    }

    fun deleteCatalogProduct(product: ProductCatalogEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            _uiEventMessage.value = "تم حذف الصنف"
        }
    }

    fun updateStoreConfig(config: StoreConfigEntity) {
        viewModelScope.launch {
            repository.saveStoreConfig(config)
            _uiEventMessage.value = "تم حفظ إعدادات المحل/التاجر بنجاح"
        }
    }

    fun createBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = backupManager.createBackupJson()
                onResult(json)
            } catch (e: Throwable) {
                _uiEventMessage.value = "فشل في إنشاء النسخة الاحتياطية: ${e.localizedMessage}"
            }
        }
    }

    fun restoreFromBackupJson(jsonString: String, onComplete: (BackupRestoreResult) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.restoreFromJson(jsonString)
            when (result) {
                is BackupRestoreResult.Success -> {
                    _uiEventMessage.value = "تم استعادة ${result.invoicesCount} فاتورة و ${result.productsCount} صنف بنجاح!"
                }
                is BackupRestoreResult.Error -> {
                    _uiEventMessage.value = "فشل في الاستعادة: ${result.message}"
                }
            }
            onComplete(result)
        }
    }

    fun addCustomerReceipt(customerName: String, amount: Double, paymentType: String, notes: String) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
            db.customerReceiptDao().insertReceipt(
                CustomerReceiptEntity(
                    customerName = customerName,
                    amount = amount,
                    dateString = dateStr,
                    paymentType = paymentType,
                    notes = notes
                )
            )
            _uiEventMessage.value = "تم حفظ سند القبض/التسديد بمبلغ $amount بنجاح"
        }
    }

    fun deleteCustomerReceipt(receiptId: Long) {
        viewModelScope.launch {
            db.customerReceiptDao().deleteReceipt(receiptId)
            _uiEventMessage.value = "تم حذف سند القبض"
        }
    }
}

