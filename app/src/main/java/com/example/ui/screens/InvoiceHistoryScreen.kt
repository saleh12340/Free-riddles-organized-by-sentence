package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceWithItems
import com.example.printer.PrinterConnectionState
import com.example.ui.components.InteractiveReceiptView
import com.example.ui.components.ReceiptBitmapHelper
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.toEnglishDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceHistoryScreen(
    viewModel: InvoiceViewModel,
    onEditInvoice: (InvoiceWithItems) -> Unit,
    onNavigateToPrinterSetup: () -> Unit
) {
    val context = LocalContext.current
    val invoicesWithItems by viewModel.allInvoices.collectAsState()
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val storeConfig = storeConfigState ?: com.example.data.local.StoreConfigEntity()
    val printerState by viewModel.printerState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedInvoiceForDetail by remember { mutableStateOf<InvoiceWithItems?>(null) }

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    val filteredList = remember(invoicesWithItems, searchQuery) {
        if (searchQuery.isBlank()) invoicesWithItems
        else invoicesWithItems.filter {
            it.invoice.customerName.contains(searchQuery, ignoreCase = true) ||
            it.invoice.invoiceNumber.toString().contains(searchQuery) ||
            it.invoice.paymentType.contains(searchQuery)
        }
    }

    val totalSalesSum = remember(filteredList) {
        filteredList.sumOf { it.invoice.grandTotal }
    }

    Scaffold(
        containerColor = NeuBackground,
        topBar = {
            Surface(
                color = NeuSurfaceRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل الفواتير (الأحدث أولاً)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NeuTextPrimary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sunken Neumorphic Search Bar
            NeuInsetBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.5.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("بحث باسم العميل أو رقم الفاتورة...", fontSize = 13.5.sp, color = NeuTextMuted)
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = NeuTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Stats Neumorphic Card
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "عدد الفواتير:",
                            fontSize = 11.5.sp,
                            color = NeuTextSecondary
                        )
                        Text(
                            text = "${filteredList.size} فاتورة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NeuTextPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "إجمالي المبيعات:",
                            fontSize = 11.5.sp,
                            color = NeuTextSecondary
                        )
                        Text(
                            text = "${formatter.format(totalSalesSum)} ${storeConfig.currencySymbol}",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = NeuAccentBlue
                        )
                    }
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد فواتير مسجلة في السجل حتى الآن.",
                        color = NeuTextMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.invoice.invoiceId }) { item ->
                        val inv = item.invoice
                        NeuCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedInvoiceForDetail = item },
                            shape = RoundedCornerShape(18.dp),
                            elevation = 6.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Soft invoice badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = NeuInsetBg
                                    ) {
                                        Text(
                                            text = "#${inv.invoiceNumber}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = NeuAccentBlue
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = inv.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = NeuTextPrimary
                                    )
                                }

                                Text(
                                    text = "${formatter.format(inv.grandTotal)} ${storeConfig.currencySymbol}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = NeuAccentBlue
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "التاريخ: ${inv.dateString} | ${inv.paymentType} (${item.items.size} أصناف)",
                                    fontSize = 11.5.sp,
                                    color = NeuTextSecondary
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. PDF Share Button
                                    NeuCircleButton(
                                        onClick = {
                                            val pdfUri = viewModel.getShareablePdfUriForInvoice(inv, item.items)
                                            if (pdfUri != null) {
                                                val shareIntent = com.example.ui.components.InvoicePdfHelper.createSharePdfIntent(
                                                    context = context,
                                                    pdfUri = pdfUri,
                                                    invoiceNumber = inv.invoiceNumber,
                                                    customerName = inv.customerName
                                                )
                                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كملف PDF"))
                                            } else {
                                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        size = 32.dp
                                    ) {
                                        Icon(AppIcons.Pdf, contentDescription = "مشاركة PDF", tint = NeuAccentBlue, modifier = Modifier.size(15.dp))
                                    }

                                    // 2. Edit Button
                                    NeuCircleButton(
                                        onClick = { onEditInvoice(item) },
                                        size = 32.dp
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = NeuTextPrimary, modifier = Modifier.size(15.dp))
                                    }

                                    // 3. Print Button
                                    NeuCircleButton(
                                        onClick = {
                                            try {
                                                val bitmap = ReceiptBitmapHelper.createReceiptBitmapFromItems(
                                                    context = context,
                                                    storeConfig = storeConfig,
                                                    invoiceNumber = inv.invoiceNumber,
                                                    dateString = inv.dateString,
                                                    customerName = inv.customerName,
                                                    paymentType = inv.paymentType,
                                                    items = item.items
                                                )
                                                if (printerState is PrinterConnectionState.Connected) {
                                                    viewModel.printInvoiceAsBitmap(bitmap)
                                                } else {
                                                    Toast.makeText(context, "الرجاء الاتصال بالطابعة أولاً من شاشة الطابعة", Toast.LENGTH_SHORT).show()
                                                    onNavigateToPrinterSetup()
                                                }
                                            } catch (e: Throwable) {
                                                Toast.makeText(context, "خطأ في الطباعة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        size = 32.dp
                                    ) {
                                        Icon(AppIcons.Print, contentDescription = "طباعة", tint = NeuTextPrimary, modifier = Modifier.size(15.dp))
                                    }

                                    // 4. Delete Button
                                    NeuCircleButton(
                                        onClick = { viewModel.deleteInvoice(inv.invoiceId) },
                                        size = 32.dp
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = NeuError, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedInvoiceForDetail?.let { item ->
        val inv = item.invoice
        AlertDialog(
            onDismissRequest = { selectedInvoiceForDetail = null },
            containerColor = NeuSurface,
            title = {
                Text(
                    text = "تفاصيل فاتورة #${inv.invoiceNumber} - ${inv.customerName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeuTextPrimary
                )
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuButton(
                        onClick = {
                            val pdfUri = viewModel.getShareablePdfUriForInvoice(inv, item.items)
                            if (pdfUri != null) {
                                val shareIntent = com.example.ui.components.InvoicePdfHelper.createSharePdfIntent(
                                    context = context,
                                    pdfUri = pdfUri,
                                    invoiceNumber = inv.invoiceNumber,
                                    customerName = inv.customerName
                                )
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كملف PDF"))
                            } else {
                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        isPrimary = true
                    ) {
                        Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    NeuButton(
                        onClick = {
                            onEditInvoice(item)
                            selectedInvoiceForDetail = null
                        },
                        isPrimary = false
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeuTextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تعديل", color = NeuTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedInvoiceForDetail = null }) {
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
                    val half = (item.items.size + 1) / 2
                    val rightItems = item.items.take(half)
                    val leftItems = item.items.drop(half)
                    val maxCount = maxOf(rightItems.size, leftItems.size).coerceAtLeast(1)
                    val rows = (0 until maxCount).map { i ->
                        val r = rightItems.getOrNull(i)
                        val l = leftItems.getOrNull(i)
                        com.example.ui.viewmodels.DualReceiptRow(
                            rightDescription = r?.description ?: "",
                            rightQuantityStr = r?.quantity?.let { if (it > 0) it.toString() else "1" } ?: "1",
                            rightTotalAmountStr = r?.totalAmount?.let { if (it > 0) it.toString() else "" } ?: "",
                            leftDescription = l?.description ?: "",
                            leftQuantityStr = l?.quantity?.let { if (it > 0) it.toString() else "1" } ?: "1",
                            leftTotalAmountStr = l?.totalAmount?.let { if (it > 0) it.toString() else "" } ?: ""
                        )
                    }

                    InteractiveReceiptView(
                        storeConfig = storeConfig,
                        invoiceNumber = inv.invoiceNumber,
                        onInvoiceNumberChange = {},
                        dateString = inv.dateString,
                        onDateStringChange = {},
                        customerName = inv.customerName,
                        onCustomerNameChange = {},
                        paymentType = inv.paymentType,
                        onPaymentTypeChange = {},
                        dualRows = rows,
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
}
