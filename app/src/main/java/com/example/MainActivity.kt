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
    HISTORY("السجل", AppIcons.History, AppIcons.History),
    CUSTOMERS("العملاء", Icons.Default.Person, Icons.Default.Person),
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
    onSettings: () -> Unit
) {
    val invoices by viewModel.allInvoices.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val config by viewModel.storeConfig.collectAsState()
    val receipts by viewModel.allReceipts.collectAsState()
    val money = remember { DecimalFormat("#,##0.##") }
    val today = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date()) }
    val todayInvoices = invoices.filter { it.invoice.dateString == today }
    val todaySales = todayInvoices.sumOf { it.invoice.grandTotal }
    val todayReceived = todayInvoices.sumOf { it.invoice.paidAmount } +
        receipts.filter { it.dateString == today }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF1E3A8A),
            shape = RoundedCornerShape(18.dp),
            elevation = 3.dp,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Category, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text((config?.storeNameArabic ?: "بقالة العزي").ifBlank { "بقالة العزي" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("إدارة المبيعات والفواتير", fontSize = 13.sp, color = Color(0xFFDBEAFE))
                }
            }
            Spacer(Modifier.height(14.dp))
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

        Text("العمليات الرئيسية", color = NeuTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardAction("فاتورة جديدة", "بيع وتسجيل الواصل", AppIcons.Receipt, Color(0xFF2563EB), onNewInvoice, Modifier.weight(1f))
            DashboardAction("حسابات العملاء", "الأرصدة والحركات", AppIcons.Person, Color(0xFF0F9D78), onCustomers, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardAction("سجل الفواتير", "عرض وتعديل ومشاركة", AppIcons.History, Color(0xFF7C3AED), onHistory, Modifier.weight(1f))
            DashboardAction("الأصناف والمخزون", "الأسعار والبحث", AppIcons.Category, Color(0xFFB45309), onProducts, Modifier.weight(1f))
        }
        Text("الأدوات والإعدادات", color = NeuTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardAction("إعداد الطابعة", "حرارية 58 / 80 مم", AppIcons.Print, Color(0xFF475569), onPrinter, Modifier.weight(1f))
            DashboardAction("إعدادات المتجر", "النسخ الاحتياطي والبيانات", Icons.Default.Settings, Color(0xFFBE123C), onSettings, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("أحدث الفواتير", color = NeuTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
        modifier = modifier.heightIn(min = 94.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = NeuSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = NeuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
    val bottomScreens = listOf(NavigationScreen.HOME, NavigationScreen.NEW_INVOICE, NavigationScreen.HISTORY, NavigationScreen.CUSTOMERS, NavigationScreen.SETTINGS)
    val uiMessage by viewModel.uiEventMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept hardware/system back button
    BackHandler {
        if (currentTab != NavigationScreen.HOME) {
            currentTab = NavigationScreen.HOME
        } else {
            showExitDialog = true
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
            // Floating Clean Navigation Bar
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
                        val isSelected = currentTab == screen || (screen == NavigationScreen.HOME && currentTab in listOf(NavigationScreen.PRODUCTS, NavigationScreen.PRINTER))
                        val itemModifier = if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEFF6FF))
                                .border(
                                    1.dp,
                                    Color(0xFFBFDBFE),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { currentTab = screen }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
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
                                fontSize = 11.sp,
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
                    onSettings = { currentTab = NavigationScreen.SETTINGS }
                )
                NavigationScreen.NEW_INVOICE -> NewInvoiceScreen(
                    viewModel = viewModel,
                    onNavigateToPrinterSetup = { currentTab = NavigationScreen.PRINTER }
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
                NavigationScreen.CUSTOMERS -> CustomersScreen(
                    viewModel = viewModel,
                    onEditInvoice = { inv ->
                        viewModel.loadInvoiceForEditing(inv)
                        currentTab = NavigationScreen.NEW_INVOICE
                    }
                )
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
