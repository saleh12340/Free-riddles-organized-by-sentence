package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.local.InvoiceWithItems
import com.example.ui.components.InvoicePdfHelper
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.toEnglishString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: InvoiceViewModel,
    onNavigateToCustomerStatement: ((String) -> Unit)? = null,
    onEditInvoice: ((InvoiceWithItems) -> Unit)? = null
) {
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val allReceipts by viewModel.allReceipts.collectAsState()
    val config by viewModel.storeConfig.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<String?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    // If a customer is selected, open the dedicated full screen statement
    selectedCustomer?.let { customerName ->
        CustomerAccountStatementScreen(
            customerName = customerName,
            viewModel = viewModel,
            onBack = { selectedCustomer = null },
            onEditInvoice = onEditInvoice
        )
        return
    }

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    // Aggregate all unique customers from both Invoices and Receipts
    val customers = remember(invoices, allReceipts, query) {
        val invoiceNames = invoices.map { it.invoice.customerName.trim() }
        val receiptNames = allReceipts.map { it.customerName.trim() }
        val allNames = (invoiceNames + receiptNames)
            .filter { it.isNotBlank() && it != "عميل نقدي" }
            .distinct()

        allNames.map { name ->
            val customerInvoices = invoices.filter { it.invoice.customerName.trim() == name }
            val customerInvoicesTotal = customerInvoices.sumOf { it.invoice.grandTotal }
            val customerReceipts = allReceipts.filter { it.customerName.trim() == name }
            val customerReceiptsTotal = customerReceipts.sumOf { it.amount }
            val netBalance = customerInvoicesTotal - customerReceiptsTotal
            Triple(name, customerInvoices, netBalance)
        }
        .filter { query.isBlank() || it.first.contains(query, true) }
        .sortedByDescending { it.third } // Prioritize customers with outstanding balances
    }

    Scaffold(
        containerColor = NeuBackground,
        floatingActionButton = {
            NeuCircleButton(
                onClick = { showAddCustomerDialog = true },
                size = 46.dp,
                isPrimary = true
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "إضافة عميل جديد",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIconBox(size = 30.dp) {
                        Icon(AppIcons.Person, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("إدارة العملاء والحسابات", fontSize = 15.sp, fontWeight = FontWeight.Black, color = NeuTextPrimary)
                }
                NeuPillBadge(
                    text = "${customers.size.toEnglishString()} عميل",
                    fontSize = 11.sp,
                    textColor = NeuAccentBlue
                )
            }

            // Compact Sunken Search Box
            NeuInsetBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = NeuTextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = NeuTextPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NeuAccentBlue),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("ابحث عن اسم العميل...", fontSize = 12.sp, color = NeuTextMuted)
                            }
                            inner()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(AppIcons.Clear, contentDescription = "مسح", tint = NeuTextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (customers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Store, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("لا يوجد عملاء حالياً", fontSize = 13.sp, color = NeuTextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text("اضغط على زر (+) لإضافة عميل جديد", fontSize = 11.sp, color = NeuTextMuted)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            elevation = 3.dp,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NeuIconBox(
                                        size = 32.dp,
                                        shape = RoundedCornerShape(8.dp),
                                        backgroundColor = if (netBalance > 0) NeuAccentBlue.copy(alpha = 0.12f) else NeuSuccess.copy(alpha = 0.12f)
                                    ) {
                                        Icon(
                                            AppIcons.Person,
                                            contentDescription = null,
                                            tint = if (netBalance > 0) NeuAccentBlue else NeuSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            name,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeuTextPrimary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            NeuPillBadge(
                                                text = "${list.size.toEnglishString()} فاتورة",
                                                fontSize = 9.sp,
                                                textColor = NeuAccentBlue
                                            )
                                            NeuPillBadge(
                                                text = "${receiptsList.size.toEnglishString()} سندات",
                                                backgroundColor = NeuSuccess.copy(alpha = 0.12f),
                                                textColor = NeuSuccess,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${formatter.format(netBalance)} ${config?.currencySymbol ?: "ر.ي"}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (netBalance > 0) NeuAccentBlue else NeuSuccess
                                    )
                                    Text(
                                        if (netBalance > 0) "المتبقي عليه" else if (netBalance < 0) "له رصيد" else "خالص الحساب",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (netBalance > 0) NeuAccentBlue else NeuSuccess
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- ADD NEW CUSTOMER DIALOG ---
    if (showAddCustomerDialog) {
        var newCustomerName by remember { mutableStateOf("") }
        var initialBalanceStr by remember { mutableStateOf("") }
        var initialBalanceType by remember { mutableStateOf("مدين (عليه دين)") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            containerColor = NeuSurface,
            title = {
                Text("إضافة عميل جديد", fontWeight = FontWeight.Black, fontSize = 16.sp, color = NeuTextPrimary)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cleanName = newCustomerName.trim()
                    val isDuplicateName = remember(cleanName, invoices, allReceipts) {
                        cleanName.isNotBlank() && (invoices.map { it.invoice.customerName.trim() } +
                            allReceipts.map { it.customerName.trim() })
                            .any { it.equals(cleanName, ignoreCase = true) }
                    }

                    Text("اسم العميل:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        TextField(
                            value = newCustomerName,
                            onValueChange = { newCustomerName = it },
                            placeholder = { Text("مثال: محمد علي", fontSize = 12.sp) },
                            singleLine = true,
                            isError = isDuplicateName,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    if (isDuplicateName) {
                        Text(
                            text = "⚠️ اسم العميل مسجل مسبقاً! لا يُسمح بتكرار أسماء العملاء.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuError
                        )
                    }

                    Text("رصيد افتتاحي (اختياري):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NeuInsetBox(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            TextField(
                                value = initialBalanceStr,
                                onValueChange = { initialBalanceStr = it.toEnglishDigits() },
                                placeholder = { Text("0.0", fontSize = 12.sp) },
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

                        FilterChip(
                            selected = initialBalanceType == "مدين (عليه دين)",
                            onClick = { initialBalanceType = "مدين (عليه دين)" },
                            label = { Text("عليه", fontSize = 11.sp) }
                        )

                        FilterChip(
                            selected = initialBalanceType == "دائن (له رصيد)",
                            onClick = { initialBalanceType = "دائن (له رصيد)" },
                            label = { Text("له", fontSize = 11.sp) }
                        )
                    }

                    Text("رقم الهاتف أو ملاحظات:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    NeuInsetBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = { Text("ملاحظات إضافية...", fontSize = 12.sp) },
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
                NeuButton(
                    onClick = {
                        val cleanName = newCustomerName.trim()
                        if (cleanName.isNotBlank()) {
                            val duplicate = (invoices.map { it.invoice.customerName.trim() } +
                                allReceipts.map { it.customerName.trim() })
                                .any { it.equals(cleanName, ignoreCase = true) }
                            if (duplicate) {
                                Toast.makeText(context, "اسم العميل مسجل مسبقاً، اختره من القائمة", Toast.LENGTH_LONG).show()
                                return@NeuButton
                            }
                            val initBal = initialBalanceStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
                            if (initBal > 0) {
                                if (initialBalanceType == "مدين (عليه دين)") {
                                    // Save as an opening balance receipt/note
                                    viewModel.addCustomerReceipt(cleanName, -initBal, "رصيد افتتاحي (مدين)", notes)
                                } else {
                                    viewModel.addCustomerReceipt(cleanName, initBal, "رصيد افتتاحي (دائن)", notes)
                                }
                            } else {
                                // Register customer with zero-amount initial entry
                                viewModel.addCustomerReceipt(cleanName, 0.0, "تسجيل عميل جديد", notes)
                            }
                            showAddCustomerDialog = false
                            selectedCustomer = cleanName
                        } else {
                            Toast.makeText(context, "يرجى إدخال اسم العميل", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(4.dp),
                    isPrimary = true
                ) {
                    Text("إضافة العميل", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) {
                    Text("إلغاء", color = NeuTextSecondary)
                }
            }
        )
    }

    // --- CUSTOMER DETAIL & ACCOUNT STATEMENT DIALOG ---
    selectedCustomer?.let { name ->
        val customerInvoices = invoices.filter { it.invoice.customerName.trim() == name }.sortedByDescending { it.invoice.createdAtTimestamp }
        val customerReceipts = allReceipts.filter { it.customerName.trim() == name }.sortedByDescending { it.createdAtTimestamp }

        var amountStr by remember { mutableStateOf("") }
        var paymentTypeChoice by remember { mutableStateOf("سند قبض نقدي") }
        var receiptNotes by remember { mutableStateOf("") }

        val paymentTypes = listOf("سند قبض نقدي", "تسديد جزئي للفاتورة", "تسديد الفاتورة كاملة", "سند صرف (استرداد)")

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

        AlertDialog(
            onDismissRequest = { selectedCustomer = null },
            containerColor = NeuSurface,
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("حساب العميل: $name", fontWeight = FontWeight.Black, fontSize = 16.sp, color = NeuTextPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "إجمالي الفواتير: ${formatter.format(totalInvoices)} | المسدد: ${formatter.format(totalReceipts)} | المتبقي: ${formatter.format(remainingBalance)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuAccentBlue
                    )
                }
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- TOP QUICK INPUT ROW FOR RECEIPT / PAYMENT ---
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
                                paymentTypes.forEach { type ->
                                    FilterChip(
                                        selected = paymentTypeChoice == type,
                                        onClick = { paymentTypeChoice = type },
                                        label = { Text(type, fontSize = 9.5.sp) },
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

                                // Add Button
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("سجل العمليات والحركات:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeuTextPrimary)
                        Text("${combinedTransactions.size} حركة", fontSize = 11.sp, color = NeuTextSecondary)
                    }

                    // --- COMPACT TRANSACTIONS LIST WITH RUNNING BALANCE ---
                    if (combinedTransactions.isEmpty()) {
                        Text("لا توجد عمليات مسجلة لهذا العميل.", fontSize = 11.sp, color = NeuTextMuted)
                    } else {
                        var runningBalance = 0.0
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 240.dp)
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
                                        if (!tx.isDebit && tx.amount > 0) {
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

                    // --- STATEMENT EXPORT ACTIONS ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Share Statement as PDF
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
                                    customerName = name,
                                    statementRows = rows,
                                    totalDebit = totalInvoices,
                                    totalCredit = totalReceipts,
                                    netBalance = remainingBalance
                                )
                                if (uri != null) {
                                    val shareIntent = InvoicePdfHelper.createShareStatementPdfIntent(context, uri, name)
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة كشف حساب $name"))
                                } else {
                                    Toast.makeText(context, "تعذر تجهيز ملف كشف الحساب", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = true
                        ) {
                            Icon(AppIcons.Pdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("كشف حساب PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // 2. Share Statement via WhatsApp/Text
                        NeuButton(
                            onClick = {
                                val textMsg = "مرحباً يا $name،\nكشف حسابك لدى ${config?.storeNameArabic ?: "متجرنا"}:\n" +
                                        "• إجمالي المشتريات: ${formatter.format(totalInvoices)} ${config?.currencySymbol ?: "ر.ي"}\n" +
                                        "• إجمالي المدفوعات: ${formatter.format(totalReceipts)} ${config?.currencySymbol ?: "ر.ي"}\n" +
                                        "• الرصيد المتبقي: ${formatter.format(remainingBalance)} ${config?.currencySymbol ?: "ر.ي"}\n" +
                                        "شكراً لتعاملكم معنا."
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textMsg)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة الحساب عبر الواتساب"))
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("رسالة نصية / واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
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
    val rawType: String,
    val invoiceWithItems: com.example.data.local.InvoiceWithItems? = null,
    val receipt: com.example.data.local.CustomerReceiptEntity? = null
)
