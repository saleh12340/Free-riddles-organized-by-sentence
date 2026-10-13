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
import androidx.compose.ui.platform.LocalContext
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
            .sortedBy { it.invoice.invoiceId }
    }

    val customerReceipts = remember(allReceipts, customerName) {
        allReceipts.filter { it.customerName.trim() == customerName.trim() }
            .sortedBy { it.receiptId }
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
        txs.sortedBy { it.id }
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
            // 1. FINANCIAL SUMMARY KPI CARDS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Card 1: Total Invoices
                NeuCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("إجمالي المشتريات", fontSize = 10.sp, color = NeuTextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${formatter.format(totalInvoices)}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = NeuTextPrimary
                        )
                        Text(config.currencySymbol, fontSize = 9.sp, color = NeuTextMuted)
                    }
                }

                // Card 2: Total Receipts
                NeuCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("إجمالي المسدد", fontSize = 10.sp, color = NeuTextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${formatter.format(totalReceipts)}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = NeuSuccess
                        )
                        Text(config.currencySymbol, fontSize = 9.sp, color = NeuTextMuted)
                    }
                }

                // Card 3: Net Balance
                NeuCard(
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (remainingBalance > 0) "المتبقي (عليه)" else if (remainingBalance < 0) "له رصيد" else "الحساب خالص",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingBalance > 0) NeuAccentBlue else if (remainingBalance < 0) NeuSuccess else NeuTextSecondary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${formatter.format(remainingBalance)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (remainingBalance > 0) NeuAccentBlue else NeuSuccess
                        )
                        Text(config.currencySymbol, fontSize = 9.sp, color = NeuTextMuted)
                    }
                }
            }

            // 2. QUICK ADD RECEIPT / PAYMENT RULER BAR
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "تسجيل سند قبض / دفعة نقدية سريعة",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuTextPrimary
                        )

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
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("واتساب", fontSize = 11.sp, color = NeuAccentBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Operation Type Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        paymentTypes.forEach { type ->
                            FilterChip(
                                selected = paymentTypeChoice == type,
                                onClick = { paymentTypeChoice = type },
                                label = { Text(type, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Inputs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Amount
                        NeuInsetBox(modifier = Modifier.weight(1.1f), shape = RoundedCornerShape(10.dp)) {
                            TextField(
                                value = amountStr,
                                onValueChange = { amountStr = it.toEnglishDigits() },
                                placeholder = { Text("المبلغ (${config.currencySymbol})", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Notes
                        NeuInsetBox(modifier = Modifier.weight(1.4f), shape = RoundedCornerShape(10.dp)) {
                            TextField(
                                value = receiptNotes,
                                onValueChange = { receiptNotes = it },
                                placeholder = { Text("البيان / ملاحظات", fontSize = 11.sp) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
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
                            modifier = Modifier.width(76.dp).height(44.dp),
                            isPrimary = true
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
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
                    fontSize = 12.5.sp,
                    color = NeuTextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeuSurfaceRaised
                ) {
                    Text(
                        "${combinedTransactions.size} حركة",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuTextSecondary
                    )
                }
            }

            if (combinedTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Receipt, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("لا توجد حركات أو فواتير مسجلة لهذا العميل حتى الآن", fontSize = 12.5.sp, color = NeuTextSecondary)
                    }
                }
            } else {
                var runningBalance = 0.0
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (tx.isDebit) NeuAccentBlue.copy(alpha = 0.15f) else NeuSuccess.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (tx.isDebit) "فاتورة (مدين +)" else "سند قبض (دائن -)",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (tx.isDebit) NeuAccentBlue else NeuSuccess
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            tx.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = NeuTextPrimary
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "${tx.date} • ${tx.details}",
                                        fontSize = 10.5.sp,
                                        color = NeuTextSecondary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${formatter.format(tx.amount)} ${config.currencySymbol}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = if (tx.isDebit) NeuTextPrimary else NeuSuccess
                                    )
                                    Text(
                                        "الرصيد التراكمي: ${formatter.format(currentRunBal)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentRunBal > 0) NeuAccentBlue else NeuSuccess
                                    )
                                }

                                if (!tx.isDebit && tx.amount > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = tx.id },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف السند", tint = NeuError, modifier = Modifier.size(15.dp))
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
