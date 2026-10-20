package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceWithItems
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NavigationScreen(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("الرئيسية", AppIcons.Store, AppIcons.Store),
    NEW_INVOICE("فاتورة", AppIcons.Receipt, AppIcons.Receipt),
    CUSTOMERS("العملاء", Icons.Default.Person, Icons.Default.Person),
    REPORTS("التقارير", AppIcons.BarChart, AppIcons.BarChart),
    MORE("المزيد", AppIcons.MoreHoriz, AppIcons.MoreHoriz),
    HISTORY("السجل", AppIcons.History, AppIcons.History),
    SETTINGS("الإعدادات", Icons.Default.Settings, Icons.Default.Settings),
    PRODUCTS("الأصناف", AppIcons.Category, AppIcons.Category),
    PRINTER("الطابعة", AppIcons.Print, AppIcons.Print)
}


@Composable
private fun HomeDashboardScreen(
    viewModel: InvoiceViewModel,
    onNewInvoice: () -> Unit,
    onCustomers: () -> Unit,
    onHistory: () -> Unit,
    onProducts: () -> Unit,
    onPrinter: () -> Unit,
    onSettings: () -> Unit,
    onOpenCustomer: (String) -> Unit
) {
    val invoices by viewModel.allInvoices.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val config by viewModel.storeConfig.collectAsState()
    val receipts by viewModel.allReceipts.collectAsState()
    val money = remember { DecimalFormat("#,##0.##") }
    val customerRows = remember(invoices, receipts) {
        val names = (invoices.map { it.invoice.customerName.trim() } + receipts.map { it.customerName.trim() })
            .filter { it.isNotBlank() && it != "عميل نقدي" }.distinct()
        names.map { name ->
            val customerInvoices = invoices.filter { it.invoice.customerName.trim() == name }
            val customerReceipts = receipts.filter { it.customerName.trim() == name }
            val debits = customerReceipts.filter { it.isDebit || it.paymentType.contains("عليه") || it.paymentType.contains("قيد دين") }.sumOf { it.amount }
            val credits = customerReceipts.filterNot { it.isDebit || it.paymentType.contains("عليه") || it.paymentType.contains("قيد دين") }.sumOf { it.amount }
            Triple(name, customerInvoices.size + customerReceipts.size, customerInvoices.sumOf { it.invoice.grandTotal } + debits - credits)
        }.sortedByDescending { it.third }
    }
    val today = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date()) }
    val todayInvoices = invoices.filter { it.invoice.dateString == today }
    val todaySales = todayInvoices.sumOf { it.invoice.grandTotal }
    val todayReceived = todayInvoices.sumOf { it.invoice.paidAmount } +
        receipts.filter { it.dateString == today }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF1E3A8A),
            shape = RoundedCornerShape(12.dp),
            elevation = 2.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Category, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text((config?.storeNameArabic ?: "بقالة العزي").ifBlank { "بقالة العزي" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Text("إدارة المبيعات والفواتير", fontSize = 9.sp, color = Color(0xFFDBEAFE), maxLines = 1)
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardMetric("مبيعات اليوم", money.format(todaySales), config?.currencySymbol ?: "ر.ي", Modifier.weight(1f))
                DashboardMetric("الواصل اليوم", money.format(todayReceived), config?.currencySymbol ?: "ر.ي", Modifier.weight(1f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardSmallMetric("الفواتير اليوم", todayInvoices.size.toString(), Modifier.weight(1f))
            DashboardSmallMetric("إجمالي الفواتير", invoices.size.toString(), Modifier.weight(1f))
            DashboardSmallMetric("الأصناف", products.size.toString(), Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("العملاء وحساباتهم", color = NeuTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onCustomers, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("كل العملاء", fontSize = 10.sp) }
        }
        if (customerRows.isEmpty()) {
            Text("ستظهر حسابات العملاء هنا بعد تسجيل أول حركة", color = NeuTextSecondary, fontSize = 10.sp)
        } else {
            customerRows.take(8).forEach { item ->
                val (name, count, balance) = item
                val owing = balance > 0.05
                val tint = if (owing) Color(0xFFB91C1C) else Color(0xFF047857)
                val rowBg = if (owing) Color(0xFFFFF1F2) else Color(0xFFECFDF5)
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(rowBg)
                        .border(1.dp, if (owing) Color(0xFFFCA5A5) else Color(0xFF86EFAC), RoundedCornerShape(5.dp))
                        .clickable { onOpenCustomer(name) }.padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(name, modifier = Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("$count حركة", fontSize = 9.sp, color = NeuTextSecondary, maxLines = 1)
                    Text(if (kotlin.math.abs(balance) < 0.05) "خالص" else "${money.format(kotlin.math.abs(balance))} ${if (owing) "عليه" else "له"}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = tint, maxLines = 1)
                }
            }
        }
        Text("العمليات الرئيسية", color = NeuTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardAction("فاتورة جديدة", "بيع وتسجيل الواصل", AppIcons.Receipt, NeuAccentBlue, onNewInvoice, Modifier.weight(1f))
            DashboardAction("حسابات العملاء", "الأرصدة والحركات", AppIcons.Person, NeuAccentBlue, onCustomers, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardAction("سجل الفواتير", "عرض وتعديل ومشاركة", AppIcons.History, NeuAccentBlue, onHistory, Modifier.weight(1f))
            DashboardAction("الأصناف والمخزون", "الأسعار والبحث", AppIcons.Category, NeuAccentBlue, onProducts, Modifier.weight(1f))
        }
        Text("الأدوات والإعدادات", color = NeuTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardAction("إعداد الطابعة", "حرارية 58 / 80 مم", AppIcons.Print, Color(0xFF475569), onPrinter, Modifier.weight(1f))
            DashboardAction("إعدادات المتجر", "النسخ الاحتياطي والبيانات", Icons.Default.Settings, Color(0xFF475569), onSettings, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("أحدث الفواتير", color = NeuTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onHistory, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("عرض السجل", color = NeuAccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        val latestInvoices = invoices.sortedByDescending { it.invoice.createdAtTimestamp }.take(3)
        if (latestInvoices.isEmpty()) {
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NeuSurface,
                elevation = 1.dp,
                contentPadding = PaddingValues(12.dp)
            ) {
                Text("لا توجد فواتير بعد", color = NeuTextSecondary, fontSize = 13.sp)
            }
        } else {
            latestInvoices.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeuSurface)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .clickable(onClick = onHistory)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Receipt, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(19.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("فاتورة #" + entry.invoice.invoiceNumber, color = NeuTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(entry.invoice.customerName.ifBlank { "عميل نقدي" }, color = NeuTextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money.format(entry.invoice.grandTotal), color = NeuTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(entry.invoice.paymentType, color = if (entry.invoice.paymentType == "أجل") Color(0xFFD97706) else NeuSuccess, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (index < latestInvoices.lastIndex) Spacer(Modifier.height(5.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DashboardMetric(label: String, amount: String, currency: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(10.dp)
    ) {
        Text(label, color = Color(0xFFDBEAFE), fontSize = 12.sp)
        Text(amount, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(currency, color = Color(0xFFBFDBFE), fontSize = 11.sp)
    }
}

@Composable
private fun DashboardSmallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    NeuCard(modifier = modifier, backgroundColor = NeuSurface, elevation = 1.dp, contentPadding = PaddingValues(10.dp)) {
        Text(value, color = NeuAccentBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = NeuTextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun DashboardAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 86.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = NeuSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = NeuTextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = NeuTextSecondary, fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}


class MainActivity : ComponentActivity() {

    private val viewModel: InvoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Ensure native Arabic RTL layout
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationScreen.HOME) }
    var customerToOpen by remember { mutableStateOf<String?>(null) }
    val bottomScreens = listOf(
        NavigationScreen.HOME,
        NavigationScreen.NEW_INVOICE,
        NavigationScreen.CUSTOMERS,
        NavigationScreen.REPORTS,
        NavigationScreen.MORE
    )
    val uiMessage by viewModel.uiEventMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept hardware/system back button
    BackHandler {
        when (currentTab) {
            NavigationScreen.HISTORY, NavigationScreen.PRODUCTS, NavigationScreen.PRINTER, NavigationScreen.SETTINGS -> {
                currentTab = NavigationScreen.MORE
            }
            NavigationScreen.MORE, NavigationScreen.REPORTS, NavigationScreen.CUSTOMERS, NavigationScreen.NEW_INVOICE -> {
                currentTab = NavigationScreen.HOME
            }
            NavigationScreen.HOME -> {
                showExitDialog = true
            }
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    Scaffold(
        containerColor = NeuBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Floating Clean Navigation Bar (5 Primary Tabs)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color(0x14000000),
                        spotColor = Color(0x1E000000)
                    )
                    .clip(RoundedCornerShape(22.dp)),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomScreens.forEach { screen ->
                        val isSelected = currentTab == screen || (screen == NavigationScreen.MORE && currentTab in listOf(NavigationScreen.HISTORY, NavigationScreen.PRODUCTS, NavigationScreen.PRINTER, NavigationScreen.SETTINGS))
                        val itemModifier = if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEFF6FF))
                                .border(
                                    1.dp,
                                    Color(0xFFBFDBFE),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { currentTab = screen }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        }

                        Column(
                            modifier = itemModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                                tint = if (isSelected) NeuAccentBlue else NeuTextSecondary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = screen.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) NeuAccentBlue else NeuTextSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NeuBackground)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationScreen.HOME -> HomeDashboardScreen(
                    viewModel = viewModel,
                    onNewInvoice = { currentTab = NavigationScreen.NEW_INVOICE },
                    onCustomers = { currentTab = NavigationScreen.CUSTOMERS },
                    onHistory = { currentTab = NavigationScreen.HISTORY },
                    onProducts = { currentTab = NavigationScreen.PRODUCTS },
                    onPrinter = { currentTab = NavigationScreen.PRINTER },
                    onSettings = { currentTab = NavigationScreen.SETTINGS },
                    onOpenCustomer = { name ->
                        customerToOpen = name
                        currentTab = NavigationScreen.CUSTOMERS
                    }
                )
                NavigationScreen.NEW_INVOICE -> NewInvoiceScreen(
                    viewModel = viewModel,
                    onNavigateToPrinterSetup = { currentTab = NavigationScreen.PRINTER }
                )
                NavigationScreen.CUSTOMERS -> CustomersScreen(
                    viewModel = viewModel,
                    initialCustomerName = customerToOpen,
                    onEditInvoice = { inv ->
                        viewModel.loadInvoiceForEditing(inv)
                        currentTab = NavigationScreen.NEW_INVOICE
                    },
                    onNewInvoiceForCustomer = { customerName ->
                        viewModel.resetForm()
                        viewModel.customerName = customerName
                        viewModel.paymentType = "أجل"
                        currentTab = NavigationScreen.NEW_INVOICE
                    }
                )
                NavigationScreen.REPORTS -> FinancialReportsScreen(viewModel = viewModel)
                NavigationScreen.MORE -> MoreHubScreen(
                    viewModel = viewModel,
                    onNavigateToHistory = { currentTab = NavigationScreen.HISTORY },
                    onNavigateToProducts = { currentTab = NavigationScreen.PRODUCTS },
                    onNavigateToPrinter = { currentTab = NavigationScreen.PRINTER },
                    onNavigateToSettings = { currentTab = NavigationScreen.SETTINGS }
                )
                NavigationScreen.HISTORY -> InvoiceHistoryScreen(
                    viewModel = viewModel,
                    onEditInvoice = { inv ->
                        viewModel.loadInvoiceForEditing(inv)
                        currentTab = NavigationScreen.NEW_INVOICE
                    },
                    onNavigateToPrinterSetup = { currentTab = NavigationScreen.PRINTER }
                )
                NavigationScreen.PRODUCTS -> ProductsCatalogScreen(viewModel = viewModel)
                NavigationScreen.PRINTER -> PrinterSetupScreen(viewModel = viewModel)
                NavigationScreen.SETTINGS -> StoreSettingsScreen(viewModel = viewModel)
            }
        }
    }

    // Exit Confirmation Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("تأكيد الخروج من التطبيق", fontWeight = FontWeight.Bold, color = NeuTextPrimary) },
            text = { Text("هل أنت متأكد من رغبتك في إغلاق التطبيق؟ سيتم حفظ جميع بياناتك بأمان.", color = NeuTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finishAffinity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuError)
                ) {
                    Text("نعم، خروج", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text("إلغاء", color = NeuTextPrimary)
                }
            },
            containerColor = NeuSurface
        )
    }
}
