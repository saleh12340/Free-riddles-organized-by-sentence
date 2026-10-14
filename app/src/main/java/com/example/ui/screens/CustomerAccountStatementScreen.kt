package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StoreConfigEntity
import com.example.ui.components.InvoicePdfHelper
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
    onBack: () -> Unit
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
            .sortedWith(compareByDescending<InvoiceWithItems> { it.invoice.dateString }.thenByDescending { it.invoice.createdAtTimestamp }.thenByDescending { it.invoice.invoiceId })
    }

    val customerReceipts = remember(allReceipts, customerName) {
        allReceipts.filter { it.customerName.trim() == customerName.trim() }
            .sortedWith(compareByDescending<CustomerReceiptEntity> { it.dateString }.thenByDescending { it.createdAtTimestamp }.thenByDescending { it.receiptId })
    }

    var amountStr by remember { mutableStateOf("") }
    var paymentTypeChoice by remember { mutableStateOf("سند قبض نقدي") }
    var receiptNotes by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<Long?>(null) }

    val paymentTypes = listOf("سند قبض نقدي", "تسديد جزئي", "تسديد كامل", "سند صرف")

    val totalInvoices = customerInvoices.sumOf { it.invoice.grandTotal }
    val totalReceipts = customerReceipts.sumOf { it.amount }
    val remainingBalance = totalInvoices - totalReceipts

    val combinedTransactions = remember(customerInvoices, customerReceipts) {
        val txs = mutableListOf<CustomerTx>()
        customerInvoices.forEach { inv ->
            txs.add(
                CustomerTx(
                    date = inv.invoice.dateString,
                    title = "فاتورة #${inv.invoice.invoiceNumber.toEnglishString()} (${inv.invoice.paymentType})",
                    details = "${inv.items.size.toEnglishString()} أصناف",
                    amount = inv.invoice.grandTotal,
                    isDebit = true,
                    id = inv.invoice.invoiceId,
                    rawType = "invoice"
                )
            )
        }
        customerReceipts.forEach { rec ->
            txs.add(
                CustomerTx(
                    date = rec.dateString,
                    title = rec.paymentType,
                    details = rec.notes.ifEmpty { "سند قبض / تسديد" },
                    amount = rec.amount,
                    isDebit = false,
                    id = rec.receiptId,
                    rawType = "receipt"
                )
            )
        }
        txs.sortedWith(compareByDescending<CustomerTx> { it.date }.thenByDescending { it.id })
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
                            size = 36.dp
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
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeuTextPrimary
                                )
                            }
                            Text(
                                "كشف حساب العميل وحركات السندات",
                                fontSize = 11.sp,
                                color = NeuTextSecondary
                            )
                        }
                    }

                    // Print / Share Statement as PDF
                    NeuButton(
                        onClick = {
                            var bal = 0.0
                            val rows = combinedTransactions.mapIndexed { idx, tx ->
                                if (tx.isDebit) bal += tx.amount else bal -= tx.amount
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
                        modifier = Modifier.height(34.dp),
                        isPrimary = true
                    ) {
                        Icon(AppIcons.Pdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. FINANCIAL SUMMARY KPI CARDS (COMPACT & CONTENT-CONTAINING)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Card 1: Total Invoices
                NeuCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    elevation = 3.dp,
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
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = NeuTextPrimary
                        )
                        Text(config.currencySymbol, fontSize = 8.5.sp, color = NeuTextMuted)
                    }
                }

                // Card 2: Total Receipts
                NeuCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    elevation = 3.dp,
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
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = NeuSuccess
                        )
                        Text(config.currencySymbol, fontSize = 8.5.sp, color = NeuTextMuted)
                    }
                }

                // Card 3: Net Balance
                NeuCard(
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(10.dp),
                    elevation = 3.dp,
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = if (remainingBalance > 0) NeuAccentBlue else NeuSuccess
                        )
                        Text(config.currencySymbol, fontSize = 8.5.sp, color = NeuTextMuted)
                    }
                }
            }

            // 2. QUICK ADD RECEIPT / PAYMENT COMPACT BAR
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                elevation = 3.dp,
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
                            NeuIconBox(size = 22.dp, shape = RoundedCornerShape(5.dp)) {
                                Icon(AppIcons.Payment, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(13.dp))
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
                            Text("واتساب", fontSize = 10.5.sp, color = NeuAccentBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Compact Operation Type Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        paymentTypes.forEach { type ->
                            FilterChip(
                                selected = paymentTypeChoice == type,
                                onClick = { paymentTypeChoice = type },
                                label = { Text(type, fontSize = 9.sp, fontWeight = if (paymentTypeChoice == type) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Compact Inputs + Add Button in Single Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Amount
                        NeuInsetBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            BasicTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it.toEnglishDigits() },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp, color = NeuTextPrimary, fontWeight = FontWeight.Bold),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(NeuAccentBlue),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (amountStr.isEmpty()) {
                                        Text("المبلغ (${config.currencySymbol})", fontSize = 10.5.sp, color = NeuTextMuted)
                                    }
                                    inner()
                                }
                            )
                        }

                        // Notes
                        NeuInsetBox(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            BasicTextField(
                                value = receiptNotes,
                                onValueChange = { receiptNotes = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = NeuTextPrimary),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(NeuAccentBlue),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (receiptNotes.isEmpty()) {
                                        Text("البيان / ملاحظات", fontSize = 10.5.sp, color = NeuTextMuted)
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
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            isPrimary = true
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 3. TRANSACTIONS & MOVEMENT LOG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "سجل العمليات والحركات المالية:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NeuTextPrimary
                )
                NeuPillBadge(
                    text = "${combinedTransactions.size} حركة",
                    fontSize = 10.sp,
                    textColor = NeuTextSecondary
                )
            }

            if (combinedTransactions.isEmpty()) {
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
                var runningBalance = 0.0
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(combinedTransactions, key = { "${it.rawType}_${it.id}" }) { tx ->
                        if (tx.isDebit) {
                            runningBalance += tx.amount
                        } else {
                            runningBalance -= tx.amount
                        }
                        val currentRunBal = runningBalance

                        NeuCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${tx.date} • ${tx.details}",
                                            fontSize = 9.5.sp,
                                            color = NeuTextSecondary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${formatter.format(tx.amount)} ${config.currencySymbol}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = if (tx.isDebit) NeuTextPrimary else NeuSuccess
                                        )
                                        Text(
                                            "رصيد: ${formatter.format(currentRunBal)}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentRunBal > 0) NeuAccentBlue else NeuSuccess
                                        )
                                    }

                                    if (!tx.isDebit && tx.amount > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { showDeleteConfirmDialog = tx.id },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف السند", tint = NeuError, modifier = Modifier.size(14.dp))
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

    // Delete receipt confirmation dialog
    showDeleteConfirmDialog?.let { receiptId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            containerColor = NeuSurface,
            title = { Text("تأكيد حذف السند", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeuTextPrimary) },
            text = { Text("هل أنت متأكد من رغبتك في حذف سند القبض هذا؟ سيتم إعادة حساب الرصيد فوراً.", fontSize = 12.5.sp, color = NeuTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomerReceipt(receiptId)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuError)
                ) {
                    Text("نعم، حذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            }
        )
    }
}
