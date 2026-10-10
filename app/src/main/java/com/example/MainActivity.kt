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
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel

enum class NavigationScreen(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    NEW_INVOICE("جديدة", AppIcons.Receipt, AppIcons.Receipt),
    HISTORY("السجل", AppIcons.History, AppIcons.History),
    PRODUCTS("الأصناف", AppIcons.Category, AppIcons.Category),
    CUSTOMERS("العملاء", AppIcons.Store, AppIcons.Store),
    PRINTER("الطابعة", AppIcons.Print, AppIcons.Print),
    SETTINGS("المتجر", AppIcons.Store, AppIcons.Store)
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
    var currentTab by remember { mutableStateOf(NavigationScreen.NEW_INVOICE) }
    val uiMessage by viewModel.uiEventMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept hardware/system back button
    BackHandler {
        if (currentTab != NavigationScreen.NEW_INVOICE) {
            currentTab = NavigationScreen.NEW_INVOICE
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
            // Floating Soft UI Neumorphic Navigation Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = NeuDarkShadow.copy(alpha = 0.5f),
                        spotColor = NeuDarkShadow.copy(alpha = 0.65f)
                    )
                    .clip(RoundedCornerShape(26.dp)),
                color = NeuSurfaceRaised
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationScreen.entries.forEach { screen ->
                        val isSelected = currentTab == screen
                        val itemModifier = if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(NeuInsetBg)
                                .border(
                                    1.dp,
                                    NeuInsetBorder.copy(alpha = 0.6f),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
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
                NavigationScreen.CUSTOMERS -> CustomersScreen(viewModel = viewModel)
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
