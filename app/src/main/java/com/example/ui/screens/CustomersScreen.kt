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
    var showAddReceiptDialog by remember { mutableStateOf(false) }

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
                    val totalInvoices = list.sumOf { it.invoice.grandTotal }

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

    // Customer Detail & Payment Receipts Dialog
    selectedCustomer?.let { name ->
        val customerInvoices = invoices.filter { it.invoice.customerName.trim() == name }.sortedByDescending { it.invoice.invoiceId }
        val customerReceipts = allReceipts.filter { it.customerName.trim() == name }
        val totalInvoices = customerInvoices.sumOf { it.invoice.grandTotal }
        val totalReceipts = customerReceipts.sumOf { it.amount }
        val remainingBalance = totalInvoices - totalReceipts

        AlertDialog(
            onDismissRequest = { selectedCustomer = null },
            containerColor = NeuSurface,
            title = {
                Column {
                    Text("حساب وفواتير العميل: $name", fontWeight = FontWeight.Black, fontSize = 16.sp, color = NeuTextPrimary)
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
                        .heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button to add payment receipt / settlement
                    NeuButton(
                        onClick = { showAddReceiptDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = true
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("إضافة سند قبض / تسديد حساب أو فاتورة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Text("سندات القبض وتسديدات الحساب:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeuTextPrimary)
                    
                    if (customerReceipts.isEmpty()) {
                        Text("لا توجد سندات قبض مسجلة لهذا العميل حتى الآن.", fontSize = 11.5.sp, color = NeuTextMuted)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 140.dp)
                        ) {
                            items(customerReceipts, key = { it.receiptId }) { receipt ->
                                Surface(shape = RoundedCornerShape(10.dp), color = NeuInsetBg, modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(receipt.paymentType, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeuSuccess)
                                            Text("${receipt.dateString} ${if (receipt.notes.isNotBlank()) "• ${receipt.notes}" else ""}", fontSize = 10.sp, color = NeuTextSecondary)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${formatter.format(receipt.amount)} ${config?.currencySymbol ?: "ر.ي"}", fontWeight = FontWeight.Black, fontSize = 13.sp, color = NeuTextPrimary)
                                            Spacer(Modifier.width(6.dp))
                                            IconButton(onClick = { viewModel.deleteCustomerReceipt(receipt.receiptId) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = NeuError, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("فواتير العميل المسجلة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeuTextPrimary)

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 180.dp)
                    ) {
                        items(customerInvoices, key = { it.invoice.invoiceId }) { item ->
                            Surface(shape = RoundedCornerShape(10.dp), color = NeuInsetBg, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("#${item.invoice.invoiceNumber.toEnglishString()}", fontWeight = FontWeight.Bold, color = NeuAccentBlue, fontSize = 12.sp)
                                        Text("${item.invoice.dateString} (${item.invoice.paymentType})", fontSize = 10.sp, color = NeuTextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${formatter.format(item.invoice.grandTotal)} ${config?.currencySymbol ?: "ر.ي"}", fontWeight = FontWeight.Bold, color = NeuTextPrimary, fontSize = 13.sp)
                                        Text("${item.items.size.toEnglishString()} أصناف", fontSize = 10.sp, color = NeuTextSecondary)
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

    // Add Receipt / Settlement Dialog
    if (showAddReceiptDialog && selectedCustomer != null) {
        val custName = selectedCustomer!!
        var amountStr by remember { mutableStateOf("") }
        var paymentTypeChoice by remember { mutableStateOf("سند قبض نقدي") }
        var receiptNotes by remember { mutableStateOf("") }

        val paymentTypes = listOf("سند قبض نقدي", "تسديد جزئي للفاتورة", "تسديد الفاتورة كاملة", "تسديد جزئي من حساب العميل")

        AlertDialog(
            onDismissRequest = { showAddReceiptDialog = false },
            containerColor = NeuSurface,
            title = {
                Text("إضافة سند قبض / تسديد للعميل: $custName", fontWeight = FontWeight.Black, fontSize = 15.sp, color = NeuTextPrimary)
            },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("اختر نوع سند القبض أو التسديد:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuTextSecondary)
                    
                    paymentTypes.forEach { type ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { paymentTypeChoice = type }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (paymentTypeChoice == type),
                                onClick = { paymentTypeChoice = type }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(type, fontSize = 13.sp, color = NeuTextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("مبلغ القبض / التسديد:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuTextSecondary)
                    
                    NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        TextField(
                            value = amountStr,
                            onValueChange = { amountStr = it.toEnglishDigits() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("أدخل المبلغ (مثال: 5000)") },
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

                    Spacer(Modifier.height(4.dp))
                    Text("ملاحظات إضافية (اختياري):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuTextSecondary)
                    
                    NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        TextField(
                            value = receiptNotes,
                            onValueChange = { receiptNotes = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("ملاحظات سند القبض...") },
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
                // Full-width Add Button spanning the bottom
                NeuButton(
                    onClick = {
                        val amt = amountStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.addCustomerReceipt(custName, amt, paymentTypeChoice, receiptNotes)
                            showAddReceiptDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isPrimary = true
                ) {
                    Text("حفظ وتأكيد سند القبض", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReceiptDialog = false }) {
                    Text("إلغاء", color = NeuTextSecondary)
                }
            }
        )
    }
}
