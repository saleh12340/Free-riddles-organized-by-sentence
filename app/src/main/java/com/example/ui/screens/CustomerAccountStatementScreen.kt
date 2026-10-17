package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerReceiptEntity
import com.example.data.local.InvoiceWithItems
import com.example.data.local.StoreConfigEntity
import com.example.ui.components.InvoicePdfHelper
import com.example.ui.components.ReceiptBitmapHelper
import com.example.ui.components.ReceiptInkNavy
import com.example.ui.theme.*
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.toEnglishString
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAccountStatementScreen(
    customerName: String,
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
    onEditInvoice: ((InvoiceWithItems) -> Unit)? = null
) {
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val allReceipts by viewModel.allReceipts.collectAsState()
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val config = storeConfigState ?: StoreConfigEntity()

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    BackHandler {
        onBack()
    }

    val customerInvoices = remember(invoices, customerName) {
        invoices.filter { it.invoice.customerName.trim() == customerName.trim() }
    }

    val customerReceipts = remember(allReceipts, customerName) {
        allReceipts.filter { it.customerName.trim() == customerName.trim() }
    }

    var amountStr by remember { mutableStateOf("") }
    var paymentTypeChoice by remember { mutableStateOf("سند قبض نقدي") }
    var receiptNotes by remember { mutableStateOf("") }

    // Multi-Selection State
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTxKeys = remember { mutableStateListOf<String>() }

    // Expanded rows (to show miniature itemized details)
    val expandedTxKeys = remember { mutableStateListOf<String>() }

    // Dialogs state
    var showEditReceiptDialog by remember { mutableStateOf<CustomerReceiptEntity?>(null) }
    var showDeleteReceiptConfirmId by remember { mutableStateOf<Long?>(null) }
    var showDeleteInvoiceConfirmId by remember { mutableStateOf<Long?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val paymentTypes = listOf("سند قبض نقدي", "تسديد جزئي", "تسديد كامل", "سند صرف")

    val totalInvoices = customerInvoices.sumOf { it.invoice.grandTotal }
    val totalReceipts = customerReceipts.sumOf { it.amount }
    val remainingBalance = totalInvoices - totalReceipts

    // 1. Build all transactions and compute exact cumulative running balance chronologically (oldest to newest),
    // then display them newest-first as requested by the user.
    val transactionsWithBalance = remember(customerInvoices, customerReceipts) {
        val allTxs = mutableListOf<CustomerTx>()
        customerInvoices.forEach { inv ->
            allTxs.add(
                CustomerTx(
                    date = inv.invoice.dateString,
                    title = "فاتورة #${inv.invoice.invoiceNumber.toEnglishString()} (${inv.invoice.paymentType})",
                    details = "${inv.items.size.toEnglishString()} أصناف",
                    amount = inv.invoice.grandTotal,
                    isDebit = true,
                    id = inv.invoice.invoiceId,
                    rawType = "invoice",
                    invoiceWithItems = inv
                )
            )
        }
        customerReceipts.forEach { rec ->
            allTxs.add(
                CustomerTx(
                    date = rec.dateString,
                    title = rec.paymentType,
                    details = rec.notes.ifEmpty { "سند قبض / تسديد" },
                    amount = rec.amount,
                    isDebit = false,
                    id = rec.receiptId,
                    rawType = "receipt",
                    receipt = rec
                )
            )
        }
        // Chronological order (oldest first)
        val chronological = allTxs.sortedWith(compareBy<CustomerTx> { it.date }.thenBy { it.id })

        var runningBal = 0.0
        val withBal = chronological.map { tx ->
            if (tx.isDebit) runningBal += tx.amount else runningBal -= tx.amount
            tx to runningBal
        }

        // Return newest first
        withBal.reversed()
    }

    // Helper to generate statement rows for PDF/thermal print
    fun getStatementRowsFor(txsWithBal: List<Pair<CustomerTx, Double>>): List<InvoicePdfHelper.CustomerStatementRow> {
        return txsWithBal.mapIndexed { idx, (tx, bal) ->
            InvoicePdfHelper.CustomerStatementRow(
                index = idx + 1,
                date = tx.date,
                type = if (tx.isDebit) "فاتورة" else tx.title,
                details = tx.details,
                debit = if (tx.isDebit) tx.amount else 0.0,
                credit = if (!tx.isDebit) tx.amount else 0.0,
                runningBalance = bal
            )
        }
    }

    Scaffold(
        containerColor = NeuBackground,
        topBar = {
            Surface(
                color = NeuSurfaceRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (isMultiSelectMode) {
                    // Multi-Select Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedTxKeys.clear()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد", tint = NeuTextPrimary)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "محدد: ${selectedTxKeys.size.toEnglishString()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeuAccentBlue
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Select All / Deselect All
                            TextButton(
                                onClick = {
                                    if (selectedTxKeys.size == transactionsWithBalance.size) {
                                        selectedTxKeys.clear()
                                    } else {
                                        selectedTxKeys.clear()
                                        selectedTxKeys.addAll(transactionsWithBalance.map { "${it.first.rawType}_${it.first.id}" })
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (selectedTxKeys.size == transactionsWithBalance.size) "إلغاء الكل" else "تحديد الكل",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeuTextSecondary
                                )
                            }

                            // Print Selected
                            if (selectedTxKeys.isNotEmpty()) {
                                NeuCircleButton(
                                    onClick = {
                                        val selectedTxs = transactionsWithBalance.filter { "${it.first.rawType}_${it.first.id}" in selectedTxKeys }
                                        val rows = getStatementRowsFor(selectedTxs)
                                        viewModel.printCustomerStatementSummary(customerName, rows)
                                    },
                                    size = 32.dp,
                                    isPrimary = true
                                ) {
                                    Icon(AppIcons.Print, contentDescription = "طباعة المحدد", tint = Color.White, modifier = Modifier.size(15.dp))
                                }

                                // Share Selected as PDF
                                NeuCircleButton(
                                    onClick = {
                                        val selectedTxs = transactionsWithBalance.filter { "${it.first.rawType}_${it.first.id}" in selectedTxKeys }
                                        val rows = getStatementRowsFor(selectedTxs)
                                        val debit = selectedTxs.filter { it.first.isDebit }.sumOf { it.first.amount }
                                        val credit = selectedTxs.filter { !it.first.isDebit }.sumOf { it.first.amount }
                                        val net = debit - credit
                                        val uri = viewModel.getShareableCustomerStatementPdf(customerName, rows, debit, credit, net)
                                        if (uri != null) {
                                            val shareIntent = InvoicePdfHelper.createShareStatementPdfIntent(context, uri, customerName)
                                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الحركات المحددة"))
                                        } else {
                                            Toast.makeText(context, "تعذر تجهيز التقرير", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    size = 32.dp
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة المحدد", tint = NeuAccentBlue, modifier = Modifier.size(15.dp))
                                }

                                // Delete Selected
                                NeuCircleButton(
                                    onClick = { showBatchDeleteConfirm = true },
                                    size = 32.dp
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف المحدد", tint = NeuError, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Normal Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NeuCircleButton(
                                onClick = onBack,
                                size = 34.dp
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = NeuTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        customerName,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeuTextPrimary
                                    )
                                }
                                Text(
                                    "كشف حساب وسجل حركات العميل",
                                    fontSize = 10.5.sp,
                                    color = NeuTextSecondary
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Toggle Multi-Select Mode
                            NeuButton(
                                onClick = {
                                    isMultiSelectMode = true
                                    selectedTxKeys.clear()
                                },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("تحديد", color = NeuAccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Print Thermal Statement
                            NeuCircleButton(
                                onClick = {
                                    val rows = getStatementRowsFor(transactionsWithBalance)
                                    viewModel.printCustomerStatementSummary(customerName, rows)
                                },
                                size = 32.dp
                            ) {
                                Icon(AppIcons.Print, contentDescription = "طباعة حرارية", tint = NeuTextPrimary, modifier = Modifier.size(14.dp))
                            }

                            // Share Statement as PDF
                            NeuButton(
                                onClick = {
                                    val rows = getStatementRowsFor(transactionsWithBalance)
                                    val uri = viewModel.getShareableCustomerStatementPdf(
                                        customerName = customerName,
                                        statementRows = rows,
                                        totalDebit = totalInvoices,
                                        totalCredit = totalReceipts,
                                        netBalance = remainingBalance
                                    )
                                    if (uri != null) {
                                        val shareIntent = InvoicePdfHelper.createShareStatementPdfIntent(context, uri, customerName)
                                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة كشف حساب $customerName"))
                                    } else {
                                        Toast.makeText(context, "تعذر تجهيز ملف كشف الحساب", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                isPrimary = true
                            ) {
                                Icon(AppIcons.Pdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. FINANCIAL SUMMARY KPI CARDS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Card 1: Total Invoices
                NeuCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    elevation = 2.dp,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeuIconBox(size = 20.dp, shape = RoundedCornerShape(5.dp), backgroundColor = NeuAccentBlue.copy(alpha = 0.12f)) {
                                Icon(AppIcons.Receipt, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(11.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("المشتريات", fontSize = 9.5.sp, color = NeuTextSecondary, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatter.format(totalInvoices),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = NeuTextPrimary
                        )
                        Text(config.currencySymbol, fontSize = 8.sp, color = NeuTextMuted)
                    }
                }

                // Card 2: Total Receipts
                NeuCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    elevation = 2.dp,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeuIconBox(size = 20.dp, shape = RoundedCornerShape(5.dp), backgroundColor = NeuSuccess.copy(alpha = 0.12f)) {
                                Icon(AppIcons.Payment, contentDescription = null, tint = NeuSuccess, modifier = Modifier.size(11.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("المسدد", fontSize = 9.5.sp, color = NeuTextSecondary, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatter.format(totalReceipts),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = NeuSuccess
                        )
                        Text(config.currencySymbol, fontSize = 8.sp, color = NeuTextMuted)
                    }
                }

                // Card 3: Net Balance
                NeuCard(
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(10.dp),
                    elevation = 2.dp,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeuIconBox(
                                size = 20.dp,
                                shape = RoundedCornerShape(5.dp),
                                backgroundColor = if (remainingBalance > 0) NeuAccentBlue.copy(alpha = 0.12f) else NeuSuccess.copy(alpha = 0.12f)
                            ) {
                                Icon(
                                    AppIcons.Store,
                                    contentDescription = null,
                                    tint = if (remainingBalance > 0) NeuAccentBlue else NeuSuccess,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (remainingBalance > 0) "المتبقي" else if (remainingBalance < 0) "له رصيد" else "خالص",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingBalance > 0) NeuAccentBlue else NeuSuccess
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatter.format(remainingBalance),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (remainingBalance > 0) NeuAccentBlue else NeuSuccess
                        )
                        Text(config.currencySymbol, fontSize = 8.sp, color = NeuTextMuted)
                    }
                }
            }

            // 2. QUICK ADD RECEIPT / PAYMENT COMPACT BAR
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeuIconBox(size = 20.dp, shape = RoundedCornerShape(5.dp)) {
                                Icon(AppIcons.Payment, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(12.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "تسجيل سند قبض / دفعة نقدية",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeuTextPrimary
                            )
                        }

                        // Quick Share Statement via WhatsApp
                        TextButton(
                            onClick = {
                                val textMsg = "مرحباً يا $customerName،\nكشف حسابك لدى ${config.storeNameArabic.ifEmpty { "متجرنا" }}:\n" +
                                        "• إجمالي المشتريات: ${formatter.format(totalInvoices)} ${config.currencySymbol}\n" +
                                        "• إجمالي المدفوعات: ${formatter.format(totalReceipts)} ${config.currencySymbol}\n" +
                                        "• الرصيد الحالي: ${formatter.format(remainingBalance)} ${config.currencySymbol}\n" +
                                        "شكراً لتعاملكم معنا."
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textMsg)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة الحساب عبر الواتساب"))
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("واتساب", fontSize = 10.5.sp, color = NeuAccentBlue)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Amount input
                        NeuInsetBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            BasicTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it.toEnglishDigits() },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary),
                                cursorBrush = SolidColor(NeuAccentBlue),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (amountStr.isEmpty()) {
                                        Text("المبلغ (${config.currencySymbol})", fontSize = 10.sp, color = NeuTextMuted)
                                    }
                                    inner()
                                }
                            )
                        }

                        // Notes input
                        NeuInsetBox(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            BasicTextField(
                                value = receiptNotes,
                                onValueChange = { receiptNotes = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp, color = NeuTextPrimary),
                                cursorBrush = SolidColor(NeuAccentBlue),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (receiptNotes.isEmpty()) {
                                        Text("البيان / ملاحظات", fontSize = 10.sp, color = NeuTextMuted)
                                    }
                                    inner()
                                }
                            )
                        }

                        // Save Receipt Button
                        NeuButton(
                            onClick = {
                                val amt = amountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
                                if (amt > 0) {
                                    viewModel.addCustomerReceipt(customerName, amt, paymentTypeChoice, receiptNotes)
                                    amountStr = ""
                                    receiptNotes = ""
                                    Toast.makeText(context, "تم حفظ السند بمبلغ $amt بنجاح", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "يرجى كتابة مبلغ صحيح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            isPrimary = true
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 3. TRANSACTIONS LIST HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "سجل العمليات (الأحدث أولاً):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = NeuTextPrimary
                )
                NeuPillBadge(
                    text = "${transactionsWithBalance.size.toEnglishString()} حركة",
                    fontSize = 10.sp,
                    textColor = NeuTextSecondary
                )
            }

            // 4. TRANSACTIONS LIST (NEWEST FIRST WITH MINIATURE DETAILS AND RUNNING BALANCE ON THE LEFT)
            if (transactionsWithBalance.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Receipt, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("لا توجد حركات أو فواتير مسجلة لهذا العميل حتى الآن", fontSize = 12.sp, color = NeuTextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(transactionsWithBalance, key = { "${it.first.rawType}_${it.first.id}" }) { (tx, runBal) ->
                        val itemKey = "${tx.rawType}_${tx.id}"
                        val isSelected = itemKey in selectedTxKeys
                        val isExpanded = itemKey in expandedTxKeys

                        NeuCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isMultiSelectMode) {
                                        if (isSelected) selectedTxKeys.remove(itemKey) else selectedTxKeys.add(itemKey)
                                    } else {
                                        // Toggle expand/collapse miniature details
                                        if (isExpanded) expandedTxKeys.remove(itemKey) else expandedTxKeys.add(itemKey)
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            elevation = if (isSelected) 4.dp else 2.dp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Right Side: Selection checkbox + Icon + Title + Date
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isMultiSelectMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) selectedTxKeys.add(itemKey) else selectedTxKeys.remove(itemKey)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }

                                        NeuIconBox(
                                            size = 28.dp,
                                            shape = RoundedCornerShape(6.dp),
                                            backgroundColor = if (tx.isDebit) NeuAccentBlue.copy(alpha = 0.12f) else NeuSuccess.copy(alpha = 0.12f)
                                        ) {
                                            Icon(
                                                if (tx.isDebit) AppIcons.Receipt else AppIcons.Payment,
                                                contentDescription = null,
                                                tint = if (tx.isDebit) NeuAccentBlue else NeuSuccess,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                NeuPillBadge(
                                                    text = if (tx.isDebit) "فاتورة" else "سند",
                                                    textColor = if (tx.isDebit) NeuAccentBlue else NeuSuccess,
                                                    backgroundColor = if (tx.isDebit) NeuAccentBlue.copy(alpha = 0.12f) else NeuSuccess.copy(alpha = 0.12f),
                                                    fontSize = 8.5.sp
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    tx.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    color = NeuTextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "${tx.date} • ${tx.details}",
                                                fontSize = 9.sp,
                                                color = NeuTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Left Side: Transaction Amount + RUNNING BALANCE ON THE LEFT
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(
                                            "${formatter.format(tx.amount)} ${config.currencySymbol}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = if (tx.isDebit) NeuTextPrimary else NeuSuccess
                                        )
                                        // Running balance on the left side
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (runBal > 0) NeuAccentBlue.copy(alpha = 0.12f) else NeuSuccess.copy(alpha = 0.12f),
                                            modifier = Modifier.padding(top = 1.dp)
                                        ) {
                                            Text(
                                                text = "الرصيد: ${formatter.format(runBal)}",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (runBal > 0) NeuAccentBlue else NeuSuccess,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick Action Bar for this Transaction (Print, Share, Edit, Expand)
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Expand/Collapse Details Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            if (isExpanded) expandedTxKeys.remove(itemKey) else expandedTxKeys.add(itemKey)
                                        }
                                    ) {
                                        Icon(
                                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = NeuTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            if (isExpanded) "إخفاء التفاصيل" else "عرض التفاصيل المصغرة",
                                            fontSize = 9.5.sp,
                                            color = NeuTextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Action Buttons: Edit, Print, Share, Delete
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Edit Action
                                        IconButton(
                                            onClick = {
                                                if (tx.isDebit && tx.invoiceWithItems != null) {
                                                    onEditInvoice?.invoke(tx.invoiceWithItems)
                                                } else if (!tx.isDebit && tx.receipt != null) {
                                                    showEditReceiptDialog = tx.receipt
                                                }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = NeuAccentBlue, modifier = Modifier.size(13.dp))
                                        }

                                        // 2. Print Action
                                        IconButton(
                                            onClick = {
                                                if (tx.isDebit && tx.invoiceWithItems != null) {
                                                    val inv = tx.invoiceWithItems
                                                    val bitmap = ReceiptBitmapHelper.createReceiptBitmapFromItems(
                                                        context = context,
                                                        storeConfig = config,
                                                        invoiceNumber = inv.invoice.invoiceNumber,
                                                        dateString = inv.invoice.dateString,
                                                        customerName = inv.invoice.customerName,
                                                        paymentType = inv.invoice.paymentType,
                                                        items = inv.items,
                                                        receivedAmount = inv.invoice.paidAmount
                                                    )
                                                    viewModel.printInvoiceAsBitmap(bitmap)
                                                } else if (!tx.isDebit && tx.receipt != null) {
                                                    viewModel.printCustomerReceiptVoucher(tx.receipt)
                                                }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(AppIcons.Print, contentDescription = "طباعة", tint = NeuTextPrimary, modifier = Modifier.size(13.dp))
                                        }

                                        // 3. Share Action
                                        IconButton(
                                            onClick = {
                                                if (tx.isDebit && tx.invoiceWithItems != null) {
                                                    val inv = tx.invoiceWithItems
                                                    val pdfFile = InvoicePdfHelper.createInvoicePdfFromItems(
                                                        context = context,
                                                        storeConfig = config,
                                                        invoiceNumber = inv.invoice.invoiceNumber,
                                                        dateString = inv.invoice.dateString,
                                                        customerName = inv.invoice.customerName,
                                                        paymentType = inv.invoice.paymentType,
                                                        items = inv.items,
                                                        receivedAmount = inv.invoice.paidAmount
                                                    )
                                                    if (pdfFile != null) {
                                                        val uri = InvoicePdfHelper.getShareablePdfUri(context, pdfFile)
                                                        if (uri != null) {
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "application/pdf"
                                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة #${inv.invoice.invoiceNumber}"))
                                                        }
                                                    }
                                                } else if (!tx.isDebit && tx.receipt != null) {
                                                    val msg = "سند قبض مالي رقم #${tx.receipt.receiptId}\n" +
                                                            "العميل: ${tx.receipt.customerName}\n" +
                                                            "المبلغ: ${formatter.format(tx.receipt.amount)} ${config.currencySymbol}\n" +
                                                            "التاريخ: ${tx.receipt.dateString}\n" +
                                                            "البيان: ${tx.receipt.notes.ifEmpty { "سند قبض" }}"
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, msg)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة السند"))
                                                }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = NeuAccentBlue, modifier = Modifier.size(13.dp))
                                        }

                                        // 4. Delete Action
                                        IconButton(
                                            onClick = {
                                                if (tx.isDebit) {
                                                    showDeleteInvoiceConfirmId = tx.id
                                                } else {
                                                    showDeleteReceiptConfirmId = tx.id
                                                }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = NeuError, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }

                                // MINIATURE FULL-DETAILS ACCORDION
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NeuInsetBg)
                                            .border(0.6.dp, NeuInsetBorder, RoundedCornerShape(6.dp))
                                            .padding(6.dp)
                                    ) {
                                        if (tx.isDebit && tx.invoiceWithItems != null) {
                                            val inv = tx.invoiceWithItems
                                            // Mini Table Header
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(ReceiptInkNavy)
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("م", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                                                Text("الصنف", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                Text("الكمية", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(35.dp), textAlign = TextAlign.Center)
                                                Text("الإجمالي", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(55.dp), textAlign = TextAlign.End)
                                            }

                                            // Mini Items Rows
                                            inv.items.forEachIndexed { i, itm ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${i + 1}", fontSize = 9.sp, color = NeuTextSecondary, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                                                    Text(itm.description, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = NeuTextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                                                    Text(itm.quantity.toEnglishString(), fontSize = 9.sp, color = NeuTextPrimary, modifier = Modifier.width(35.dp), textAlign = TextAlign.Center)
                                                    Text("${formatter.format(itm.totalAmount)} ${config.currencySymbol}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary, modifier = Modifier.width(55.dp), textAlign = TextAlign.End)
                                                }
                                                if (i < inv.items.lastIndex) {
                                                    HorizontalDivider(color = NeuInsetBorder, thickness = 0.5.dp)
                                                }
                                            }

                                            // Invoice Financial Breakdown
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("الإجمالي: ${formatter.format(inv.invoice.grandTotal)} ${config.currencySymbol}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                                                if (inv.invoice.paidAmount > 0) {
                                                    Text("الواصل: ${formatter.format(inv.invoice.paidAmount)}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = NeuSuccess)
                                                    Text("المتبقي: ${formatter.format(inv.invoice.remainingAmount)}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = NeuError)
                                                }
                                            }
                                        } else if (!tx.isDebit && tx.receipt != null) {
                                            val rec = tx.receipt
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("رقم السند: #${rec.receiptId.toString().toEnglishDigits()}", fontSize = 9.5.sp, color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                                                Text("طريقة الدفع: ${rec.paymentType}", fontSize = 9.5.sp, color = NeuTextSecondary)
                                                if (rec.notes.isNotEmpty()) {
                                                    Text("ملاحظات / البيان: ${rec.notes}", fontSize = 9.5.sp, color = NeuTextPrimary)
                                                }
                                                Text("تاريخ السند: ${rec.dateString}", fontSize = 9.sp, color = NeuTextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- EDIT RECEIPT DIALOG ---
    showEditReceiptDialog?.let { receiptToEdit ->
        var editAmountStr by remember { mutableStateOf(receiptToEdit.amount.toString().toEnglishDigits()) }
        var editPaymentType by remember { mutableStateOf(receiptToEdit.paymentType) }
        var editNotes by remember { mutableStateOf(receiptToEdit.notes) }

        AlertDialog(
            onDismissRequest = { showEditReceiptDialog = null },
            containerColor = NeuSurface,
            title = {
                Text("تعديل سند القبض #${receiptToEdit.receiptId}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeuTextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المبلغ (${config.currencySymbol}):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        TextField(
                            value = editAmountStr,
                            onValueChange = { editAmountStr = it.toEnglishDigits() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    Text("نوع الحركة:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        paymentTypes.take(2).forEach { pType ->
                            FilterChip(
                                selected = editPaymentType == pType,
                                onClick = { editPaymentType = pType },
                                label = { Text(pType, fontSize = 10.5.sp) }
                            )
                        }
                    }

                    Text("البيان / ملاحظات:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        TextField(
                            value = editNotes,
                            onValueChange = { editNotes = it },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAmt = editAmountStr.toDoubleOrNull() ?: 0.0
                        if (newAmt > 0) {
                            viewModel.updateCustomerReceipt(
                                receiptToEdit.copy(
                                    amount = newAmt,
                                    paymentType = editPaymentType,
                                    notes = editNotes
                                )
                            )
                            showEditReceiptDialog = null
                            Toast.makeText(context, "تم حفظ تعديلات السند بنجاح", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "يرجى إدخال مبلغ صحيح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuAccentBlue)
                ) {
                    Text("حفظ التعديل", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditReceiptDialog = null }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            }
        )
    }

    // --- DELETE SINGLE RECEIPT CONFIRMATION ---
    showDeleteReceiptConfirmId?.let { receiptId ->
        AlertDialog(
            onDismissRequest = { showDeleteReceiptConfirmId = null },
            containerColor = NeuSurface,
            title = { Text("تأكيد حذف السند", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeuTextPrimary) },
            text = { Text("هل أنت متأكد من رغبتك في حذف سند القبض هذا؟ سيتم إعادة حساب الرصيد فوراً.", fontSize = 12.sp, color = NeuTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomerReceipt(receiptId)
                        showDeleteReceiptConfirmId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuError)
                ) {
                    Text("نعم، حذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteReceiptConfirmId = null }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            }
        )
    }

    // --- DELETE SINGLE INVOICE CONFIRMATION ---
    showDeleteInvoiceConfirmId?.let { invoiceId ->
        AlertDialog(
            onDismissRequest = { showDeleteInvoiceConfirmId = null },
            containerColor = NeuSurface,
            title = { Text("تأكيد حذف الفاتورة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeuTextPrimary) },
            text = { Text("هل أنت متأكد من رغبتك في حذف هذه الفاتورة؟ سيتم تعديل رصيد العميل فوراً.", fontSize = 12.sp, color = NeuTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInvoice(invoiceId)
                        showDeleteInvoiceConfirmId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuError)
                ) {
                    Text("نعم، حذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteInvoiceConfirmId = null }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            }
        )
    }

    // --- BATCH DELETE CONFIRMATION ---
    if (showBatchDeleteConfirm) {
        val invoiceCount = selectedTxKeys.count { it.startsWith("invoice_") }
        val receiptCount = selectedTxKeys.count { it.startsWith("receipt_") }

        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = NeuSurface,
            title = { Text("تأكيد الحذف الجماعي", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeuTextPrimary) },
            text = {
                Text(
                    "هل أنت متأكد من رغبتك في حذف العناصر المحددة ($invoiceCount فاتورة و $receiptCount سند)؟ هذه العملية لا يمكن التراجع عنها.",
                    fontSize = 12.5.sp,
                    color = NeuTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val invoiceIds = selectedTxKeys.filter { it.startsWith("invoice_") }.map { it.removePrefix("invoice_").toLong() }
                        val receiptIds = selectedTxKeys.filter { it.startsWith("receipt_") }.map { it.removePrefix("receipt_").toLong() }

                        if (invoiceIds.isNotEmpty()) viewModel.deleteMultipleInvoices(invoiceIds)
                        if (receiptIds.isNotEmpty()) viewModel.deleteMultipleReceipts(receiptIds)

                        selectedTxKeys.clear()
                        isMultiSelectMode = false
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuError)
                ) {
                    Text("نعم، حذف الكل المحدد", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            }
        )
    }
}
