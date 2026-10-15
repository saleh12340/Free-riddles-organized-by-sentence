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
import com.example.ui.components.ReceiptBitmapHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.VoiceInvoiceParser
import com.example.ui.util.ParsedVoiceItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class ReceiptRow(
    val id: String = UUID.randomUUID().toString(),
    var description: String = "",
    var quantityStr: String = "1",
    var totalAmountStr: String = ""
) {
    val total: Double get() = totalAmountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
    val hasData: Boolean get() = description.isNotBlank() || totalAmountStr.isNotBlank()
    val isCompletelyEmpty: Boolean get() = !hasData
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
    var receivedAmountStr by mutableStateOf("")

    // Single-column expanding rows: Starts with DEFAULT_ROW_COUNT
    val receiptRows = mutableStateListOf<ReceiptRow>()

    // Voice Input State
    var lastSpokenVoiceText by mutableStateOf("")
        private set
    var lastParsedVoiceItems by mutableStateOf<List<ParsedVoiceItem>>(emptyList())
        private set
    private var previousReceiptRowsSnapshot: List<ReceiptRow>? = null
    var canUndoVoiceInput by mutableStateOf(false)
        private set

    fun processVoiceInput(spokenText: String): Int {
        val clean = spokenText.trim()
        if (clean.isBlank()) return 0
        lastSpokenVoiceText = clean
        val parsed = VoiceInvoiceParser.parseVoiceInput(clean)
        if (parsed.isEmpty()) {
            _uiEventMessage.value = "لم يتم التعرف على أي صنف من الصوت المنطوق"
            return 0
        }

        // Save snapshot for 1-click Undo
        previousReceiptRowsSnapshot = receiptRows.map { it.copy() }
        canUndoVoiceInput = true
        lastParsedVoiceItems = parsed

        // Remove trailing empty rows first so we append cleanly
        while (receiptRows.isNotEmpty() && receiptRows.last().isCompletelyEmpty) {
            receiptRows.removeAt(receiptRows.lastIndex)
        }

        // Append parsed items
        parsed.forEach { item ->
            receiptRows.add(
                ReceiptRow(
                    description = item.description,
                    quantityStr = item.quantity,
                    totalAmountStr = item.totalAmount
                )
            )
        }

        // Always keep an empty trailing row for quick manual entry
        receiptRows.add(ReceiptRow())

        _uiEventMessage.value = "تمت إضافة ${parsed.size} صنف بالصوت تلقائياً إلى الفاتورة"
        return parsed.size
    }

    fun undoLastVoiceInput() {
        val snapshot = previousReceiptRowsSnapshot ?: return
        receiptRows.clear()
        receiptRows.addAll(snapshot)
        previousReceiptRowsSnapshot = null
        canUndoVoiceInput = false
        lastParsedVoiceItems = emptyList()
        _uiEventMessage.value = "تم التراجع عن الأصناف المضافة بالصوت"
    }

    fun clearVoiceBar() {
        lastSpokenVoiceText = ""
        lastParsedVoiceItems = emptyList()
        canUndoVoiceInput = false
        previousReceiptRowsSnapshot = null
    }

    var searchQuery by mutableStateOf("")

    private val _uiEventMessage = MutableStateFlow<String?>(null)
    val uiEventMessage: StateFlow<String?> = _uiEventMessage.asStateFlow()

    companion object {
        const val DEFAULT_ROW_COUNT = 2
    }

    init {
        viewModelScope.launch {
            invoiceNumber = repository.getNextInvoiceNumber()
            if (receiptRows.isEmpty()) {
                repeat(DEFAULT_ROW_COUNT) {
                    receiptRows.add(ReceiptRow())
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
            receivedAmountStr = ""
            receiptRows.clear()
            repeat(DEFAULT_ROW_COUNT) {
                receiptRows.add(ReceiptRow())
            }
        }
    }

    private fun checkAutoExpand(editedIndex: Int) {
        if (editedIndex !in receiptRows.indices) return
        val row = receiptRows[editedIndex]
        // Automatically add a new row when the user types in the last row
        if (editedIndex == receiptRows.lastIndex && row.hasData) {
            receiptRows.add(ReceiptRow())
        }
    }

    fun updateDescription(index: Int, desc: String) {
        if (index in receiptRows.indices) {
            receiptRows[index] = receiptRows[index].copy(description = desc)
            checkAutoExpand(index)
        }
    }

    fun updateQuantity(index: Int, qty: String) {
        if (index in receiptRows.indices) {
            receiptRows[index] = receiptRows[index].copy(quantityStr = qty.toEnglishDigits())
            checkAutoExpand(index)
        }
    }

    fun updateTotalAmount(index: Int, total: String) {
        if (index in receiptRows.indices) {
            receiptRows[index] = receiptRows[index].copy(totalAmountStr = total.toEnglishDigits())
            checkAutoExpand(index)
        }
    }

    fun addManualRow() {
        receiptRows.add(ReceiptRow())
    }

    fun removeRow(index: Int) {
        if (receiptRows.size > 1 && index in receiptRows.indices) {
            receiptRows.removeAt(index)
        }
    }

    fun calculateSubtotal(): Double = receiptRows.sumOf { it.total }
    fun getReceivedAmount(): Double = receivedAmountStr.toEnglishDigits().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    fun calculateGrandTotal(): Double = calculateSubtotal()

    fun saveInvoiceToDatabase(onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            if (customerName.isBlank()) {
                customerName = "عميل نقدي"
            }
            val itemsToSave = receiptRows.filter { it.hasData }.map { row ->
                val q = row.quantityStr.toDoubleOrNull() ?: 1.0
                val tot = row.total
                InvoiceItemEntity(
                    invoiceId = 0,
                    description = row.description.ifBlank { "صنف" },
                    quantity = q,
                    unitPrice = if (q > 0) tot / q else tot,
                    totalAmount = tot
                )
            }

            if (itemsToSave.isEmpty()) {
                _uiEventMessage.value = "يرجى كتابة أصناف الفاتورة أولاً"
                return@launch
            }

            try {
                val subtotal = calculateSubtotal()
                val grandTotal = calculateGrandTotal()
                val currentInvoiceNum = invoiceNumber
                val received = getReceivedAmount().coerceAtMost(grandTotal)
                val remaining = (grandTotal - received).coerceAtLeast(0.0)

                val invoiceEntity = InvoiceEntity(
                    invoiceNumber = currentInvoiceNum,
                    dateString = dateString,
                    customerName = customerName,
                    paymentType = paymentType,
                    subtotal = subtotal,
                    discount = 0.0,
                    paidAmount = received,
                    remainingAmount = remaining,
                    grandTotal = grandTotal,
                    notes = notes
                )

                val savedId = repository.saveInvoice(invoiceEntity, itemsToSave)
                if (received > 0.0 && customerName != "عميل نقدي") {
                    db.customerReceiptDao().insertReceipt(
                        CustomerReceiptEntity(
                            customerName = customerName.trim(),
                            amount = received,
                            dateString = dateString,
                            paymentType = "المبلغ الواصل للفاتورة #$currentInvoiceNum",
                            notes = "دفعة نقدية مرتبطة بالفاتورة رقم #$currentInvoiceNum"
                        )
                    )
                }
                _uiEventMessage.value = "تم حفظ الفاتورة بنجاح برقم #$currentInvoiceNum وحفظها في السجل!"

                // Advance to next invoice number and prepare fresh rows
                invoiceNumber = repository.getNextInvoiceNumber()
                customerName = ""
                receivedAmountStr = ""
                receiptRows.clear()
                repeat(DEFAULT_ROW_COUNT) {
                    receiptRows.add(ReceiptRow())
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
        receivedAmountStr = if (inv.paidAmount > 0) inv.paidAmount.toString() else if (inv.discount > 0) inv.discount.toString() else ""

        receiptRows.clear()
        invoiceWithItems.items.forEach { item ->
            receiptRows.add(
                ReceiptRow(
                    description = item.description,
                    quantityStr = if (item.quantity > 0) item.quantity.toString() else "1",
                    totalAmountStr = if (item.totalAmount > 0) item.totalAmount.toString() else ""
                )
            )
        }
        // Add 1 trailing empty row
        receiptRows.add(ReceiptRow())
    }

    fun deleteInvoice(invoiceId: Long) {
        viewModelScope.launch {
            repository.deleteInvoice(invoiceId)
            _uiEventMessage.value = "تم حذف الفاتورة"
        }
    }

    fun printInvoiceAsBitmap(bitmap: Bitmap, widthDots: Int? = null) {
        viewModelScope.launch {
            val targetWidth = widthDots ?: (storeConfig.value?.printerWidthDots ?: 384)
            val result = bluetoothPrinterManager.printReceiptBitmap(bitmap, targetWidth)
            result.onSuccess {
                _uiEventMessage.value = "تمت طباعة الفاتورة حرارياً بنجاح!"
            }.onFailure { e ->
                _uiEventMessage.value = e.message ?: "خطأ في الطباعة"
            }
        }
    }

    fun updatePrinterPaperWidth(paperWidthMm: Int) {
        viewModelScope.launch {
            val current = storeConfig.value ?: StoreConfigEntity()
            val updated = current.copy(printerPaperWidth = paperWidthMm)
            repository.saveStoreConfig(updated)
            _uiEventMessage.value = "تم تعيين مقاس ورق الطابعة إلى ${paperWidthMm} مم"
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
            rows = receiptRows,
            receivedAmount = getReceivedAmount()
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
            items = items,
            receivedAmount = invoice.paidAmount
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
        
        var placed = false
        for (i in receiptRows.indices) {
            val row = receiptRows[i]
            if (!row.hasData) {
                receiptRows[i] = row.copy(description = name, totalAmountStr = priceStr)
                placed = true
                break
            }
        }

        if (!placed) {
            receiptRows.add(ReceiptRow(description = name, totalAmountStr = priceStr))
        }
        _uiEventMessage.value = "تم إدراج الصنف في الفاتورة"
    }

    fun addQuickItem(name: String, quantityStr: String, totalAmountStr: String) {
        val cleanName = name.trim().ifEmpty { "صنف" }
        val cleanQty = quantityStr.trim().toEnglishDigits().ifEmpty { "1" }
        val cleanTot = totalAmountStr.trim().toEnglishDigits().ifEmpty { "0" }

        var placed = false
        for (i in receiptRows.indices) {
            val row = receiptRows[i]
            if (!row.hasData) {
                receiptRows[i] = row.copy(
                    description = cleanName,
                    quantityStr = cleanQty,
                    totalAmountStr = cleanTot
                )
                placed = true
                break
            }
        }

        if (!placed) {
            receiptRows.add(
                ReceiptRow(
                    description = cleanName,
                    quantityStr = cleanQty,
                    totalAmountStr = cleanTot
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

    fun updateCustomerReceipt(receipt: CustomerReceiptEntity) {
        viewModelScope.launch {
            db.customerReceiptDao().insertReceipt(receipt)
            _uiEventMessage.value = "تم تعديل سند القبض بنجاح"
        }
    }

    fun deleteMultipleInvoices(invoiceIds: List<Long>) {
        viewModelScope.launch {
            invoiceIds.forEach { repository.deleteInvoice(it) }
            _uiEventMessage.value = "تم حذف ${invoiceIds.size} فاتورة بنجاح"
        }
    }

    fun deleteMultipleReceipts(receiptIds: List<Long>) {
        viewModelScope.launch {
            receiptIds.forEach { db.customerReceiptDao().deleteReceipt(it) }
            _uiEventMessage.value = "تم حذف ${receiptIds.size} سند بنجاح"
        }
    }

    fun printCustomerReceiptVoucher(receipt: CustomerReceiptEntity) {
        viewModelScope.launch {
            val config = storeConfig.value ?: StoreConfigEntity()
            val width = config.printerWidthDots
            val bitmap = ReceiptBitmapHelper.createVoucherBitmap(
                context = getApplication(),
                storeConfig = config,
                receipt = receipt,
                widthPx = width
            )
            val result = bluetoothPrinterManager.printReceiptBitmap(bitmap, width)
            result.onSuccess {
                _uiEventMessage.value = "تمت طباعة سند القبض حرارياً بنجاح!"
            }.onFailure { e ->
                _uiEventMessage.value = e.message ?: "خطأ في طباعة السند"
            }
        }
    }

    fun printCustomerStatementSummary(customerName: String, statementRows: List<InvoicePdfHelper.CustomerStatementRow>) {
        viewModelScope.launch {
            val config = storeConfig.value ?: StoreConfigEntity()
            val width = config.printerWidthDots
            val bitmap = ReceiptBitmapHelper.createCustomerStatementThermalBitmap(
                context = getApplication(),
                storeConfig = config,
                customerName = customerName,
                statementRows = statementRows,
                widthPx = width
            )
            val result = bluetoothPrinterManager.printReceiptBitmap(bitmap, width)
            result.onSuccess {
                _uiEventMessage.value = "تمت طباعة تقرير كشف الحساب حرارياً!"
            }.onFailure { e ->
                _uiEventMessage.value = e.message ?: "خطأ في طباعة كشف الحساب"
            }
        }
    }
}

