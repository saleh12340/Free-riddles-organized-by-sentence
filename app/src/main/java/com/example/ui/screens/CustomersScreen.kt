package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceWithItems
import com.example.ui.theme.*
import com.example.ui.util.toEnglishDigits
import com.example.ui.util.toEnglishString
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class CustomerFilterType {
    ALL,
    DEBT_ONLY,
    CREDIT_OR_SETTLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: InvoiceViewModel,
    onNavigateToCustomerStatement: ((String) -> Unit)? = null,
    onEditInvoice: ((InvoiceWithItems) -> Unit)? = null,
    onNewInvoiceForCustomer: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val allReceipts by viewModel.allReceipts.collectAsState()
    val config by viewModel.storeConfig.collectAsState()

    var query by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(CustomerFilterType.ALL) }
    var selectedCustomer by remember { mutableStateOf<String?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    // When customer is selected, open dedicated statement screen
    selectedCustomer?.let { customerName ->
        CustomerAccountStatementScreen(
            customerName = customerName,
            viewModel = viewModel,
            onBack = { selectedCustomer = null },
            onEditInvoice = onEditInvoice,
            onNewInvoiceForCustomer = onNewInvoiceForCustomer
        )
        return
    }

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    // Aggregate unique customer metrics
    val allCustomers = remember(invoices, allReceipts) {
        val invoiceNames = invoices.map { it.invoice.customerName.trim() }
        val receiptNames = allReceipts.map { it.customerName.trim() }
        val allNames = (invoiceNames + receiptNames)
            .filter { it.isNotBlank() && it != "عميل نقدي" }
            .distinct()

        allNames.map { name ->
            val customerInvoices = invoices.filter { it.invoice.customerName.trim() == name }
            val customerInvoicesTotal = customerInvoices.sumOf { it.invoice.grandTotal }
            val customerReceipts = allReceipts.filter { it.customerName.trim() == name }
            val debitReceiptsTotal = customerReceipts.filter { it.isDebit || it.paymentType.contains("عليه") || it.paymentType.contains("قيد دين") }.sumOf { it.amount }
            val creditReceiptsTotal = customerReceipts.filter { !(it.isDebit || it.paymentType.contains("عليه") || it.paymentType.contains("قيد دين")) }.sumOf { it.amount }
            val netBalance = (customerInvoicesTotal + debitReceiptsTotal) - creditReceiptsTotal
            val phone = viewModel.getCustomerPhone(name)
            CustomerSummary(
                name = name,
                invoicesCount = customerInvoices.size,
                receiptsCount = customerReceipts.size,
                netBalance = netBalance,
                phone = phone
            )
        }
    }

    // Filter and search
    val filteredCustomers = remember(allCustomers, query, filterType) {
        allCustomers
            .filter { c ->
                val matchesQuery = query.isBlank() ||
                        c.name.contains(query, ignoreCase = true) ||
                        c.phone.contains(query)
                val matchesFilter = when (filterType) {
                    CustomerFilterType.ALL -> true
                    CustomerFilterType.DEBT_ONLY -> c.netBalance > 0.05
                    CustomerFilterType.CREDIT_OR_SETTLED -> c.netBalance <= 0.05
                }
                matchesQuery && matchesFilter
            }
            .sortedByDescending { it.netBalance } // Outstanding debts first
    }

    val totalDebtAmount = remember(allCustomers) {
        allCustomers.filter { it.netBalance > 0 }.sumOf { it.netBalance }
    }
    val inDebtCount = remember(allCustomers) {
        allCustomers.count { it.netBalance > 0 }
    }

    Scaffold(
        containerColor = NeuBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddCustomerDialog = true },
                containerColor = NavyPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(bottom = 60.dp, end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "إضافة عميل جديد",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "عميل جديد",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Top Summary Banner: Total Debts & Customers KPI
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "حسابات العملاء والديون",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeuTextPrimary
                            )
                            Text(
                                text = "${allCustomers.size.toEnglishString()} عميل مسجل • $inDebtCount عليهم ديون",
                                fontSize = 11.sp,
                                color = NeuTextSecondary
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "إجمالي الديون القائمة",
                            fontSize = 10.sp,
                            color = NeuTextSecondary
                        )
                        Text(
                            text = "${formatter.format(totalDebtAmount)} ${config?.currencySymbol ?: "ر.ي"}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            // 2. Fixed Search Bar (أنيق وسهل الوصول)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeuTextPrimary
                        ),
                        cursorBrush = SolidColor(NavyPrimary),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "ابحث بالاسم أو رقم الهاتف...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "مسح",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Filter Chips Row (تصفية سريعة)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )

                FilterChip(
                    selected = filterType == CustomerFilterType.ALL,
                    onClick = { filterType = CustomerFilterType.ALL },
                    label = { Text("الكل (${allCustomers.size})", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NavyPrimary,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                FilterChip(
                    selected = filterType == CustomerFilterType.DEBT_ONLY,
                    onClick = { filterType = CustomerFilterType.DEBT_ONLY },
                    label = { Text("عليهم ديون فقط ($inDebtCount)", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFDC2626),
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                FilterChip(
                    selected = filterType == CustomerFilterType.CREDIT_OR_SETTLED,
                    onClick = { filterType = CustomerFilterType.CREDIT_OR_SETTLED },
                    label = { Text("خالص / دائن", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF059669),
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // 4. Customer Flat Cards List (الاستغلال الأمثل للمساحة والتركيز البصري)
            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = if (query.isNotBlank()) "لا توجد نتائج مطابقة لبحثك" else "لا يوجد عملاء في هذه القائمة",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuTextSecondary
                        )
                        Text(
                            text = "اضغط على زر (عميل جديد) لإضافة عميل",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredCustomers, key = { it.name }) { customer ->
                        val isDebit = customer.netBalance > 0.05
                        val isCredit = customer.netBalance < -0.05
                        val isSettled = !isDebit && !isCredit

                        // Color rules: Red for debt, Green for credit, Slate for settled
                        val balanceColor = when {
                            isDebit -> Color(0xFFDC2626) // Alert red for debt
                            isCredit -> Color(0xFF059669) // Emerald green for credit
                            else -> Color(0xFF64748B) // Slate for zero balance
                        }

                        val statusLabel = when {
                            isDebit -> "متبقي عليه (دين)"
                            isCredit -> "له رصيد (دائن)"
                            else -> "خالص الحساب"
                        }

                        // Flat Card with soft shadow and crisp border
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCustomer = customer.name },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(
                                0.8.dp,
                                if (isDebit) Color(0xFFFECACA) else Color(0xFFE2E8F0)
                            ),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Right Side: Avatar + Customer Details
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Customer Initial Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isDebit) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = customer.name.firstOrNull()?.toString() ?: "ع",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isDebit) Color(0xFFDC2626) else NavyPrimary
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        // Customer Name: Bold, Prominent, Clear
                                        Text(
                                            text = customer.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeuTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (customer.phone.isNotBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Phone,
                                                        contentDescription = null,
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Text(
                                                        text = customer.phone,
                                                        fontSize = 10.5.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                                Text("•", fontSize = 9.sp, color = Color(0xFFCBD5E1))
                                            }

                                            Text(
                                                text = "${customer.invoicesCount.toEnglishString()} فاتورة",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF64748B)
                                            )

                                            if (customer.receiptsCount > 0) {
                                                Text("•", fontSize = 9.sp, color = Color(0xFFCBD5E1))
                                                Text(
                                                    text = "${customer.receiptsCount.toEnglishString()} سندات",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF059669)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Left Side: Outstanding Balance (Bold & Colored) + Status
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    // Balance Amount: Prominent & Distinct
                                    Text(
                                        text = "${formatter.format(if (customer.netBalance < 0) -customer.netBalance else customer.netBalance)} ${config?.currencySymbol ?: "ر.ي"}",
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = balanceColor
                                    )

                                    // Status Badge: Clear Pill Indicator
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when {
                                            isDebit -> Color(0xFFFEF2F2)
                                            isCredit -> Color(0xFFECFDF5)
                                            else -> Color(0xFFF1F5F9)
                                        },
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = balanceColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(70.dp)) // Space for FAB
                    }
                }
            }
        }
    }

    // --- ADD NEW CUSTOMER DIALOG ---
    if (showAddCustomerDialog) {
        var newCustomerName by remember { mutableStateOf("") }
        var customerPhoneInput by remember { mutableStateOf("") }
        var initialBalanceStr by remember { mutableStateOf("") }
        var initialBalanceType by remember { mutableStateOf("مدين (عليه دين)") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "إضافة عميل جديد",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NeuTextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cleanName = newCustomerName.trim()
                    val isDuplicateName = remember(cleanName, allCustomers) {
                        cleanName.isNotBlank() && allCustomers.any { it.name.equals(cleanName, ignoreCase = true) }
                    }

                    Text("اسم العميل *", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isDuplicateName) Color(0xFFDC2626) else Color(0xFFCBD5E1))
                    ) {
                        BasicTextField(
                            value = newCustomerName,
                            onValueChange = { newCustomerName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            decorationBox = { inner ->
                                if (newCustomerName.isEmpty()) {
                                    Text("مثال: عبد الرحمن محمد", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                }
                                inner()
                            }
                        )
                    }

                    if (isDuplicateName) {
                        Text(
                            text = "⚠️ اسم العميل مسجل مسبقاً في النظام!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }

                    Text("رقم الهاتف (اختياري)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        BasicTextField(
                            value = customerPhoneInput,
                            onValueChange = { customerPhoneInput = it.toEnglishDigits() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            textStyle = TextStyle(fontSize = 13.sp, color = NeuTextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            decorationBox = { inner ->
                                if (customerPhoneInput.isEmpty()) {
                                    Text("مثال: 771234567", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                }
                                inner()
                            }
                        )
                    }

                    Text("رصيد افتتاحي سابق (اختياري)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = initialBalanceStr,
                                    onValueChange = { initialBalanceStr = it.toEnglishDigits() },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary),
                                    decorationBox = { inner ->
                                        if (initialBalanceStr.isEmpty()) {
                                            Text("0.0", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        }
                                        inner()
                                    }
                                )
                            }
                        }

                        FilterChip(
                            selected = initialBalanceType == "مدين (عليه دين)",
                            onClick = { initialBalanceType = "مدين (عليه دين)" },
                            label = { Text("عليه دين", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDC2626),
                                selectedLabelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = initialBalanceType == "دائن (له رصيد)",
                            onClick = { initialBalanceType = "دائن (له رصيد)" },
                            label = { Text("له رصيد", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF059669),
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Text("ملاحظات إضافية", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        BasicTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = NeuTextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            decorationBox = { inner ->
                                if (notes.isEmpty()) {
                                    Text("العنوان أو أي بيانات أخرى...", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                                }
                                inner()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = newCustomerName.trim()
                        if (cleanName.isNotBlank()) {
                            val duplicate = allCustomers.any { it.name.equals(cleanName, ignoreCase = true) }
                            if (duplicate) {
                                Toast.makeText(context, "اسم العميل مسجل مسبقاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Save phone number if entered
                            if (customerPhoneInput.isNotBlank()) {
                                viewModel.saveCustomerPhone(cleanName, customerPhoneInput)
                            }

                            val initBal = initialBalanceStr.toEnglishDigits().toDoubleOrNull() ?: 0.0
                            if (initBal > 0) {
                                if (initialBalanceType == "مدين (عليه دين)") {
                                    viewModel.addCustomerReceipt(cleanName, -initBal, "رصيد افتتاحي (مدين)", notes)
                                } else {
                                    viewModel.addCustomerReceipt(cleanName, initBal, "رصيد افتتاحي (دائن)", notes)
                                }
                            } else {
                                viewModel.addCustomerReceipt(cleanName, 0.0, "تسجيل عميل جديد", notes)
                            }
                            showAddCustomerDialog = false
                            selectedCustomer = cleanName
                        } else {
                            Toast.makeText(context, "يرجى كتابة اسم العميل أولاً", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ العميل", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddCustomerDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إلغاء", color = NeuTextSecondary, fontSize = 12.sp)
                }
            }
        )
    }
}

private data class CustomerSummary(
    val name: String,
    val invoicesCount: Int,
    val receiptsCount: Int,
    val netBalance: Double,
    val phone: String
)
