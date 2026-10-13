package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StoreConfigEntity
import com.example.printer.PrinterConnectionState
import com.example.ui.components.InteractiveReceiptView
import com.example.ui.components.AutoSelectBasicTextField
import com.example.ui.components.ReceiptBitmapHelper
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.NumberUtils
import com.example.ui.util.toEnglishDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewInvoiceScreen(
    viewModel: InvoiceViewModel,
    onNavigateToPrinterSetup: () -> Unit
) {
    val context = LocalContext.current
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val storeConfig = storeConfigState ?: StoreConfigEntity()
    val allProducts by viewModel.allProducts.collectAsState()
    val allInvoices by viewModel.allInvoices.collectAsState()
    val printerState by viewModel.printerState.collectAsState()

    var showProductPickerSheet by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showClearInvoiceDialog by remember { mutableStateOf(false) }

    val formatter = DecimalFormat("#,##0.##")
    val customerSuggestions = remember(allInvoices, viewModel.customerName) {
        val q = viewModel.customerName.trim()
        if (q.isBlank()) emptyList() else allInvoices.asSequence()
            .map { it.invoice.customerName.trim() }
            .filter { it.isNotBlank() && it != "عميل نقدي" && it.contains(q, ignoreCase = true) }
            .distinct()
            .take(4)
            .toList()
    }

    Scaffold(
        containerColor = NeuBackground,
        topBar = {
            // Compressed Neumorphic Top Bar: smaller icons, raised higher
            Surface(
                color = NeuSurfaceRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "فاتورة #${viewModel.invoiceNumber} - ${storeConfig.storeNameArabic.ifEmpty { "بقالة العزي" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = NeuTextPrimary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Quick Product Catalog Pick
                        NeuCircleButton(
                            onClick = { showProductPickerSheet = true },
                            size = 28.dp
                        ) {
                            Icon(
                                AppIcons.Category,
                                contentDescription = "دليل الأصناف",
                                tint = NeuAccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 2. Printer Status Indicator
                        NeuCircleButton(
                            onClick = onNavigateToPrinterSetup,
                            size = 28.dp
                        ) {
                            when (printerState) {
                                is PrinterConnectionState.Connected -> Icon(
                                    AppIcons.Print,
                                    contentDescription = "الطابعة متصلة",
                                    tint = NeuSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                is PrinterConnectionState.Connecting -> CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = NeuAccentBlue
                                )
                                else -> Icon(
                                    AppIcons.PrintDisabled,
                                    contentDescription = "غير متصل بالطابعة",
                                    tint = NeuError,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // 3. New Clean Operation / Reset Form (نظافة عملية جديدة)
                        NeuCircleButton(
                            onClick = {
                                if (viewModel.dualRows.any { it.hasRightData || it.hasLeftData } || viewModel.customerName.isNotBlank()) {
                                    showClearInvoiceDialog = true
                                } else {
                                    viewModel.resetForm()
                                }
                            },
                            size = 28.dp
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "عملية جديدة / تصفير",
                                tint = NeuTextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Neumorphic Bottom Action Card
            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = 3.dp,
                contentPadding = PaddingValues(6.dp)
            ) {
                // Grand Total Display (Sunken Neumorphic Box with Discount field)
                NeuInsetBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "خصم:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeuTextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                modifier = Modifier
                                    .width(62.dp)
                                    .height(26.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = NeuSurfaceRaised
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = viewModel.discountAmountStr,
                                        onValueChange = { viewModel.discountAmountStr = it.toEnglishDigits() },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeuError,
                                            textAlign = TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        cursorBrush = SolidColor(NeuAccentBlue),
                                        decorationBox = { inner ->
                                            if (viewModel.discountAmountStr.isEmpty()) {
                                                Text("0.0", fontSize = 10.5.sp, color = NeuTextMuted, textAlign = TextAlign.Center)
                                            }
                                            inner()
                                        }
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (viewModel.getDiscountAmount() > 0) {
                                Text(
                                    text = "قبل الخصم: ${formatter.format(viewModel.calculateSubtotal())}",
                                    fontSize = 9.5.sp,
                                    color = NeuTextSecondary
                                )
                            }
                            Text(
                                text = "${formatter.format(viewModel.calculateGrandTotal())} ${storeConfig.currencySymbol}",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.5.sp,
                                color = NeuAccentBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Neumorphic Action Buttons (Compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Save Button (Soft Raised)
                    NeuButton(
                        onClick = { viewModel.saveInvoiceToDatabase() },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        isPrimary = false
                    ) {
                        Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeuTextPrimary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("حفظ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    }

                    // 2. Thermal Print Button (Vibrant #0072ff Glow)
                    NeuButton(
                        onClick = {
                            try {
                                val bitmap = ReceiptBitmapHelper.createReceiptBitmap(
                                    context = context,
                                    storeConfig = storeConfig,
                                    invoiceNumber = viewModel.invoiceNumber,
                                    dateString = viewModel.dateString,
                                    customerName = viewModel.customerName,
                                    paymentType = viewModel.paymentType,
                                    dualRows = viewModel.dualRows
                                )
                                if (printerState is PrinterConnectionState.Connected) {
                                    viewModel.printInvoiceAsBitmap(bitmap)
                                } else {
                                    Toast.makeText(context, "الرجاء الاتصال بالطابعة أولاً من شاشة الطابعة", Toast.LENGTH_SHORT).show()
                                    onNavigateToPrinterSetup()
                                }
                            } catch (e: Throwable) {
                                Toast.makeText(context, "تعذر تجهيز الفاتورة للطباعة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        isPrimary = true
                    ) {
                        Icon(AppIcons.Print, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("طباعة حرارية", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // 3. Save / Share Image
                    NeuButton(
                        onClick = { showPreviewDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        isPrimary = false
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeuAccentBlue)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("مشاركة", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. QUICK ITEM ENTRY RULER ROW (AT TOP OF PAGE)
            item {
                QuickItemRulerBar(
                    onAddItem = { name, qty, tot ->
                        viewModel.addQuickItem(name, qty, tot)
                    }
                )
            }

            // 2. MAIN DIRECT INPUT PAPER RECEIPT VIEW (DUAL COLUMN)
            item {
                InteractiveReceiptView(
                    storeConfig = storeConfig,
                    invoiceNumber = viewModel.invoiceNumber,
                    onInvoiceNumberChange = { viewModel.invoiceNumber = it },
                    dateString = viewModel.dateString,
                    onDateStringChange = { viewModel.dateString = it },
                    customerName = viewModel.customerName,
                    onCustomerNameChange = { viewModel.customerName = it },
                    paymentType = viewModel.paymentType,
                    onPaymentTypeChange = { viewModel.paymentType = it },
                    dualRows = viewModel.dualRows,
                    onUpdateRightDescription = { idx, v -> viewModel.updateRightDescription(idx, v) },
                    onUpdateRightQuantity = { idx, v -> viewModel.updateRightQuantity(idx, v) },
                    onUpdateRightTotalAmount = { idx, v -> viewModel.updateRightTotalAmount(idx, v) },
                    onUpdateLeftDescription = { idx, v -> viewModel.updateLeftDescription(idx, v) },
                    onUpdateLeftQuantity = { idx, v -> viewModel.updateLeftQuantity(idx, v) },
                    onUpdateLeftTotalAmount = { idx, v -> viewModel.updateLeftTotalAmount(idx, v) },
                    onRemoveRow = { idx -> viewModel.removeRow(idx) },
                    onAddManualRow = { viewModel.addManualRow() },
                    customerSuggestions = customerSuggestions,
                    onCustomerSuggestionClick = { selected -> viewModel.customerName = selected }
                )
            }
        }
    }

    // Quick Product Picker Modal
    if (showProductPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProductPickerSheet = false },
            containerColor = NeuSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر صنفاً لإدراجه مباشرة في الفاتورة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NeuTextPrimary
                    )
                    IconButton(onClick = { showProductPickerSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = NeuTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var searchFilter by remember { mutableStateOf("") }
                NeuInsetBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = searchFilter,
                            onValueChange = { searchFilter = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                            cursorBrush = SolidColor(NeuAccentBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchFilter.isEmpty()) {
                                    Text("ابحث عن صنف...", fontSize = 13.sp, color = NeuTextMuted)
                                }
                                inner()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val availableProducts = remember(allProducts, searchFilter) {
                    if (searchFilter.isBlank()) allProducts
                    else allProducts.filter { it.name.contains(searchFilter, ignoreCase = true) }
                }

                if (availableProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (allProducts.isEmpty()) "لا توجد أصناف في الدليل بعد. أضف أصنافاً من تبويب 'الأصناف'." else "لم يتم العثور على نتائج.",
                            color = NeuTextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableProducts) { product ->
                            NeuCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addCatalogProductToInvoice(product.name, product.defaultUnitPrice)
                                        showProductPickerSheet = false
                                    },
                                shape = RoundedCornerShape(14.dp),
                                elevation = 4.dp,
                                contentPadding = PaddingValues(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NeuTextPrimary)
                                        Text(product.category, fontSize = 11.sp, color = NeuTextSecondary)
                                    }
                                    Text(
                                        text = "${formatter.format(product.defaultUnitPrice)} ${storeConfig.currencySymbol}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = NeuAccentBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Invoice Image Preview & Share Dialog
    if (showPreviewDialog) {
        val invoiceBitmap = remember(viewModel.dualRows, storeConfig) {
            ReceiptBitmapHelper.createReceiptBitmap(
                context = context,
                storeConfig = storeConfig,
                invoiceNumber = viewModel.invoiceNumber,
                dateString = viewModel.dateString,
                customerName = viewModel.customerName,
                paymentType = viewModel.paymentType,
                dualRows = viewModel.dualRows,
                widthPx = 2160 // 4K Ultra-HD resolution
            )
        }

        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            containerColor = NeuSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeuBadge(size = 38.dp) {
                        Icon(AppIcons.Pdf, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = "تصدير ومشاركة الفاتورة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NeuTextPrimary
                        )
                        Text(
                            text = "مستند PDF عالي الجودة أو صورة PNG",
                            fontSize = 11.sp,
                            color = NeuTextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Share as PDF (Primary Action)
                    NeuButton(
                        onClick = {
                            val pdfUri = viewModel.getShareableInvoicePdfUri()
                            if (pdfUri != null) {
                                val shareIntent = com.example.ui.components.InvoicePdfHelper.createSharePdfIntent(
                                    context = context,
                                    pdfUri = pdfUri,
                                    invoiceNumber = viewModel.invoiceNumber,
                                    customerName = viewModel.customerName
                                )
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كملف PDF"))
                            } else {
                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = true
                    ) {
                        Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة كملف PDF احترافي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    // 2. Secondary Row: Save PDF / Share Image / Save Image
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Save PDF to Downloads
                        NeuButton(
                            onClick = {
                                viewModel.saveInvoicePdfToDownloads()
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuAccentBlue)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("حفظ PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                        }

                        // Share Image
                        NeuButton(
                            onClick = {
                                val uri = viewModel.getShareableImageUri(invoiceBitmap)
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة صورة الفاتورة"))
                                } else {
                                    Toast.makeText(context, "تعذر تجهيز الصورة للمشاركة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuTextPrimary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("مشاركة صورة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                        }

                        // Save Image to Gallery
                        NeuButton(
                            onClick = {
                                viewModel.saveInvoiceImageToGallery(invoiceBitmap)
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeuTextPrimary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("حفظ صورة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text("إغلاق", color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(4.dp)
                ) {
                    InteractiveReceiptView(
                        storeConfig = storeConfig,
                        invoiceNumber = viewModel.invoiceNumber,
                        onInvoiceNumberChange = {},
                        dateString = viewModel.dateString,
                        onDateStringChange = {},
                        customerName = viewModel.customerName,
                        onCustomerNameChange = {},
                        paymentType = viewModel.paymentType,
                        onPaymentTypeChange = {},
                        dualRows = viewModel.dualRows.filterNot { it.isCompletelyEmpty }.ifEmpty {
                            listOf(com.example.ui.viewmodels.DualReceiptRow())
                        },
                        onUpdateRightDescription = { _, _ -> },
                        onUpdateRightQuantity = { _, _ -> },
                        onUpdateRightTotalAmount = { _, _ -> },
                        onUpdateLeftDescription = { _, _ -> },
                        onUpdateLeftQuantity = { _, _ -> },
                        onUpdateLeftTotalAmount = { _, _ -> },
                        onRemoveRow = {},
                        onAddManualRow = {},
                        isEditable = false
                    )
                }
            }
        )
    }

    // Dialog for "نظافة عملية جديدة" (Clear current invoice and start clean new operation)
    if (showClearInvoiceDialog) {
        AlertDialog(
            onDismissRequest = { showClearInvoiceDialog = false },
            containerColor = NeuSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("بدء عملية جديدة (تصفير الفاتورة)", fontWeight = FontWeight.Black, fontSize = 15.sp, color = NeuTextPrimary)
                }
            },
            text = {
                Text(
                    "هل ترغب في تنظيف وتصفير بنود الفاتورة الحالية والبدء بعملية بيع جديدة نظيفة؟",
                    fontSize = 13.sp,
                    color = NeuTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetForm()
                        showClearInvoiceDialog = false
                        Toast.makeText(context, "تم تجهيز فاتورة جديدة نظيفة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuAccentBlue)
                ) {
                    Text("نعم، عملية جديدة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearInvoiceDialog = false }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            }
        )
    }
}

@Composable
fun QuickItemRulerBar(
    onAddItem: (name: String, quantity: String, total: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    var itemName by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("1") }
    var unitPriceStr by remember { mutableStateOf("") }
    var totalAmountStr by remember { mutableStateOf("") }
    var lastEditedField by remember { mutableStateOf("TOTAL") } // "TOTAL" or "UNIT"

    val currentQty = (quantityStr.toEnglishDigits().toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)

    // Deduce unit price or total based on user inputs
    val calculatedUnitPrice = remember(quantityStr, totalAmountStr, unitPriceStr, lastEditedField) {
        if (lastEditedField == "UNIT") {
            unitPriceStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
        } else {
            val tot = totalAmountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
            if (currentQty > 0.0) tot / currentQty else 0.0
        }
    }

    val calculatedTotal = remember(quantityStr, totalAmountStr, unitPriceStr, lastEditedField) {
        if (lastEditedField == "UNIT") {
            val up = unitPriceStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
            up * currentQty
        } else {
            totalAmountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
        }
    }

    val submitItem = {
        val finalTot = if (lastEditedField == "UNIT" && calculatedTotal > 0.0) {
            formatter.format(calculatedTotal)
        } else {
            totalAmountStr.toEnglishDigits()
        }
        val cleanName = itemName.trim()
        val cleanQty = quantityStr.toEnglishDigits().ifBlank { "1" }
        if (cleanName.isNotBlank() || finalTot.isNotBlank()) {
            onAddItem(cleanName, cleanQty, finalTot)
            itemName = ""
            quantityStr = "1"
            unitPriceStr = ""
            totalAmountStr = ""
            lastEditedField = "TOTAL"
        }
    }

    // 1. Single Horizontal Ruler Row + Full Width Add Button underneath (Compact)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = NeuSurfaceRaised,
        shadowElevation = 2.dp,
        border = BorderStroke(0.8.dp, NeuAccentBlue.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. المربع الأول: القيمة الإجمالية
                val displayTotal = if (lastEditedField == "UNIT" && calculatedTotal > 0.0) {
                    formatter.format(calculatedTotal)
                } else {
                    totalAmountStr
                }
                SmartRulerInputBox(
                    value = displayTotal,
                    onValueChange = { input ->
                        val clean = input.toEnglishDigits()
                        totalAmountStr = clean
                        lastEditedField = "TOTAL"
                        val tot = clean.toDoubleOrNull()
                        val q = (quantityStr.toEnglishDigits().toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                        if (tot != null && tot > 0) {
                            unitPriceStr = formatter.format(tot / q)
                        } else if (clean.isEmpty()) {
                            unitPriceStr = ""
                        }
                    },
                    placeholder = "القيمة الإجمالية",
                    modifier = Modifier.weight(0.9f),
                    isNumeric = true,
                    align = TextAlign.Center,
                    imeAction = ImeAction.Next,
                    textColor = NeuAccentBlue
                )

                // 2. المربع الثاني: العدد (الكمية)
                SmartRulerInputBox(
                    value = quantityStr,
                    onValueChange = { input ->
                        val clean = input.toEnglishDigits()
                        quantityStr = clean
                        val q = (clean.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                        if (lastEditedField == "UNIT" && unitPriceStr.isNotBlank()) {
                            val up = unitPriceStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
                            totalAmountStr = if (up * q > 0) formatter.format(up * q) else ""
                        } else if (totalAmountStr.isNotBlank()) {
                            val tot = totalAmountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
                            unitPriceStr = if (tot > 0) formatter.format(tot / q) else ""
                        }
                    },
                    placeholder = "العدد",
                    modifier = Modifier.weight(0.55f),
                    isNumeric = true,
                    align = TextAlign.Center,
                    imeAction = ImeAction.Next
                )

                // 3. المربع الثالث: التفاصيل (اسم الصنف / البيان)
                SmartRulerInputBox(
                    value = itemName,
                    onValueChange = { itemName = it },
                    placeholder = "التفاصيل",
                    modifier = Modifier.weight(1.6f),
                    isNumeric = false,
                    align = TextAlign.Start,
                    imeAction = ImeAction.Next
                )

                // 4. المربع الرابع: سعر الواحدة
                val displayUnitPrice = if (lastEditedField == "TOTAL" && calculatedUnitPrice > 0.0) {
                    formatter.format(calculatedUnitPrice)
                } else {
                    unitPriceStr
                }
                SmartRulerInputBox(
                    value = displayUnitPrice,
                    onValueChange = { input ->
                        val clean = input.toEnglishDigits()
                        unitPriceStr = clean
                        lastEditedField = "UNIT"
                        val up = clean.toDoubleOrNull()
                        val q = (quantityStr.toEnglishDigits().toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                        if (up != null && up > 0) {
                            totalAmountStr = formatter.format(up * q)
                        } else if (clean.isEmpty()) {
                            totalAmountStr = ""
                        }
                    },
                    placeholder = "سعر الواحدة",
                    modifier = Modifier.weight(0.8f),
                    isNumeric = true,
                    align = TextAlign.Center,
                    imeAction = ImeAction.Done,
                    onDone = { submitItem() }
                )
            }

            // Full-width Add Button underneath the input boxes spanning the entire width
            NeuButton(
                onClick = { submitItem() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(31.dp),
                shape = RoundedCornerShape(7.dp),
                isPrimary = true
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة الصنف",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "إضافة الصنف إلى الفاتورة",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
private fun SmartRulerInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = false,
    align: TextAlign = TextAlign.Start,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    textColor: Color = NeuTextPrimary
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.height(31.dp),
        shape = RoundedCornerShape(7.dp),
        color = if (isFocused) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
        border = BorderStroke(
            width = if (isFocused) 1.2.dp else 0.8.dp,
            color = if (isFocused) NeuAccentBlue else Color(0xFFCBD5E1)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            contentAlignment = when (align) {
                TextAlign.Start -> Alignment.CenterStart
                TextAlign.End -> Alignment.CenterEnd
                else -> Alignment.Center
            }
        ) {
            AutoSelectBasicTextField(
                value = value,
                onValueChange = onValueChange,
                onFocusChange = { isFocused = it },
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) NeuAccentBlue else textColor,
                    textAlign = align
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isNumeric) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onDone() }
                ),
                singleLine = true,
                placeholder = placeholder,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
