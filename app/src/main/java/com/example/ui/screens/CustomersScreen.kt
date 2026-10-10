package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.toEnglishString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(viewModel: InvoiceViewModel) {
    val invoices by viewModel.allInvoices.collectAsState()
    val allReceipts by viewModel.allReceipts.collectAsState()
    val config by viewModel.storeConfig.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<String?>(null) }

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    val customers = remember(invoices, allReceipts, query) {
        invoices
            .filter { it.invoice.customerName.isNotBlank() && it.invoice.customerName != "عميل نقدي" }
            .groupBy { it.invoice.customerName.trim() }
            .map { (name, list) ->
                val customerInvoicesTotal = list.sumOf { it.invoice.grandTotal }
                val customerReceiptsTotal = allReceipts.filter { it.customerName.trim() == name }.sumOf { it.amount }
                val netBalance = customerInvoicesTotal - customerReceiptsTotal
                Triple(name, list, netBalance)
            }
            .filter { query.isBlank() || it.first.contains(query, true) }
            .sortedByDescending { it.second.firstOrNull()?.invoice?.invoiceId ?: 0L }
    }

    Scaffold(containerColor = NeuBackground) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("إدارة العملاء والحسابات", fontSize = 19.sp, fontWeight = FontWeight.Black, color = NeuTextPrimary)

            Surface(shape = RoundedCornerShape(14.dp), color = NeuSurfaceRaised) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = NeuTextMuted)
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("ابحث عن اسم العميل...") },
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(customers, key = { it.first }) { triple ->
                    val name = triple.first
                    val list = triple.second
                    val netBalance = triple.third
                    val receiptsList = allReceipts.filter { it.customerName.trim() == name }

                    NeuCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCustomer = name },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(13.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                                Text(
                                    "${list.size.toEnglishString()} فاتورة • سندات القبض: ${receiptsList.size.toEnglishString()}",
                                    fontSize = 11.sp,
                                    color = NeuTextSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${formatter.format(netBalance)} ${config?.currencySymbol ?: "ر.ي"}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (netBalance > 0) NeuAccentBlue else NeuSuccess
                                )
                                Text("المتبقي / الرصيد", fontSize = 9.5.sp, color = NeuTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Customer Detail & Quick Payment Input at Top (صيغة الفاتورة وعمليات العميل)
    selectedCustomer?.let { name ->
        val customerInvoices = invoices.filter { it.invoice.customerName.trim() == name }.sortedBy { it.invoice.invoiceId }
        val customerReceipts = allReceipts.filter { it.customerName.trim() == name }.sortedBy { it.receiptId }

        var amountStr by remember { mutableStateOf("") }
        var paymentTypeChoice by remember { mutableStateOf("سند قبض نقدي") }
        var receiptNotes by remember { mutableStateOf("") }

        val paymentTypes = listOf("سند قبض نقدي", "تسديد جزئي للفاتورة", "تسديد الفاتورة كاملة", "تسديد جزئي من حساب العميل")

        val totalInvoices = customerInvoices.sumOf { it.invoice.grandTotal }
        val totalReceipts = customerReceipts.sumOf { it.amount }
        val remainingBalance = totalInvoices - totalReceipts

        AlertDialog(
            onDismissRequest = { selectedCustomer = null },
            containerColor = NeuSurface,
            title = {
                Column {
                    Text("حساب العميل: $name", fontWeight = FontWeight.Black, fontSize = 16.sp, color = NeuTextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "إجمالي الفواتير: ${formatter.format(totalInvoices)} | المدفوع: ${formatter.format(totalReceipts)} | المتبقي: ${formatter.format(remainingBalance)}",
                        fontSize = 11.5.sp,
                        color = NeuAccentBlue
                    )
                }
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- TOP QUICK INPUT ROW FOR RECEIPT / PAYMENT (في أعلى الصفحة في صف واحد مع زر الإضافة بصيغة الفاتورة) ---
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeuInsetBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("إضافة سند قبض / صرف سريع:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                paymentTypes.take(2).forEach { type ->
                                    FilterChip(
                                        selected = paymentTypeChoice == type,
                                        onClick = { paymentTypeChoice = type },
                                        label = { Text(type, fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Amount Input
                                NeuInsetBox(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                    TextField(
                                        value = amountStr,
                                        onValueChange = { amountStr = it.toEnglishDigits() },
                                        placeholder = { Text("المبلغ", fontSize = 11.sp) },
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

                                // Notes Input
                                NeuInsetBox(modifier = Modifier.weight(1.2f), shape = RoundedCornerShape(8.dp)) {
                                    TextField(
                                        value = receiptNotes,
                                        onValueChange = { receiptNotes = it },
                                        placeholder = { Text("التفاصيل / ملاحظات", fontSize = 11.sp) },
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

                                // Add Button in the same row
                                NeuButton(
                                    onClick = {
                                        val amt = amountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
                                        if (amt > 0) {
                                            viewModel.addCustomerReceipt(name, amt, paymentTypeChoice, receiptNotes)
                                            amountStr = ""
                                            receiptNotes = ""
                                        }
                                    },
                                    modifier = Modifier.width(75.dp).height(42.dp),
                                    isPrimary = true
                                ) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text("سجل العمليات (فواتير وسندات):", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = NeuTextPrimary)

                    // --- COMPACT TRANSACTIONS LIST WITH RUNNING BALANCE (عرض العمليات بصيغة الصندوق بشكل مصغر ومرتب مع الرصيد التراكمي والتاريخ والتفاصيل) ---
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

                    if (combinedTransactions.isEmpty()) {
                        Text("لا توجد عمليات مسجلة لهذا العميل.", fontSize = 11.sp, color = NeuTextMuted)
                    } else {
                        var runningBalance = 0.0
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            items(combinedTransactions, key = { "${it.rawType}_${it.id}" }) { tx ->
                                if (tx.isDebit) {
                                    runningBalance += tx.amount
                                } else {
                                    runningBalance -= tx.amount
                                }
                                val currentRunBal = runningBalance

                                Surface(shape = RoundedCornerShape(10.dp), color = NeuInsetBg, modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (tx.isDebit) "مدين (+)" else "دائن (-)",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (tx.isDebit) NeuAccentBlue else NeuSuccess
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(tx.title, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NeuTextPrimary)
                                            }
                                            Text("${tx.date} • ${tx.details}", fontSize = 10.sp, color = NeuTextSecondary)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "${formatter.format(tx.amount)} ${config?.currencySymbol ?: "ر.ي"}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp,
                                                color = if (tx.isDebit) NeuTextPrimary else NeuSuccess
                                            )
                                            Text(
                                                "التراكمي: ${formatter.format(currentRunBal)}",
                                                fontSize = 9.sp,
                                                color = NeuTextSecondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (!tx.isDebit) {
                                            Spacer(Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { viewModel.deleteCustomerReceipt(tx.id) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = NeuError, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCustomer = null }) {
                    Text("إغلاق", fontWeight = FontWeight.Bold, color = NeuTextSecondary)
                }
            }
        )
    }
}

data class CustomerTx(
    val date: String,
    val title: String,
    val details: String,
    val amount: Double,
    val isDebit: Boolean,
    val id: Long,
    val rawType: String
)
