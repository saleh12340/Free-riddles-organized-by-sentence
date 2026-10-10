package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.StoreConfigEntity
import com.example.printer.PrinterConnectionState
import com.example.ui.components.ReceiptBitmapHelper
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun PrinterSetupScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerState by viewModel.printerState.collectAsState()
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val config = storeConfigState ?: StoreConfigEntity()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedPaperWidthDots by remember { mutableStateOf(384) } // 384 = 58mm POS, 576 = 80mm POS

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            pairedDevices = viewModel.bluetoothPrinterManager.getPairedDevices()
        } else {
            Toast.makeText(context, "تتطلب الطباعة منح صلاحية البلوتوث", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        } else {
            pairedDevices = viewModel.bluetoothPrinterManager.getPairedDevices()
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermissions()
    }

    Scaffold(
        containerColor = NeuBackground,
        topBar = {
            Surface(
                color = NeuSurfaceRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إعدادات طابعة الفواتير (بلوتوث)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NeuTextPrimary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // STATUS BANNER (Neumorphic Card with Central Badge like the lock in screenshot)
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Soft UI Icon Badge
                    NeuBadge(size = 52.dp) {
                        Icon(
                            AppIcons.Bluetooth,
                            contentDescription = null,
                            tint = when (printerState) {
                                is PrinterConnectionState.Connected -> NeuSuccess
                                is PrinterConnectionState.Connecting -> NeuAccentBlue
                                else -> NeuTextMuted
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "حالة الاتصال بالطابعة:",
                            fontSize = 11.5.sp,
                            color = NeuTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (val state = printerState) {
                                is PrinterConnectionState.Connected -> "متصل بالطابعة: ${state.deviceName}"
                                is PrinterConnectionState.Connecting -> "جاري الاتصال بالطابعة..."
                                is PrinterConnectionState.Error -> state.message
                                else -> "غير متصل بالطابعة"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = when (printerState) {
                                is PrinterConnectionState.Connected -> NeuSuccess
                                is PrinterConnectionState.Error -> NeuError
                                else -> NeuTextPrimary
                            }
                        )
                    }

                    if (printerState is PrinterConnectionState.Connected) {
                        NeuButton(
                            onClick = { viewModel.bluetoothPrinterManager.disconnect() },
                            isPrimary = false
                        ) {
                            Text("قطع", fontSize = 11.sp, color = NeuError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // PAPER SIZE SELECTOR (58mm vs 80mm POS)
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "عرض ورق الطابعة الحرارية:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = NeuTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NeuButton(
                        onClick = { selectedPaperWidthDots = 384 },
                        modifier = Modifier.weight(1f),
                        isPrimary = selectedPaperWidthDots == 384
                    ) {
                        Text(
                            text = "ورق 58 مم (POS-58)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedPaperWidthDots == 384) Color.White else NeuTextPrimary
                        )
                    }

                    NeuButton(
                        onClick = { selectedPaperWidthDots = 576 },
                        modifier = Modifier.weight(1f),
                        isPrimary = selectedPaperWidthDots == 576
                    ) {
                        Text(
                            text = "ورق 80 مم (POS-80)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedPaperWidthDots == 576) Color.White else NeuTextPrimary
                        )
                    }
                }
            }

            // TEST PRINT BUTTON
            NeuButton(
                onClick = {
                    val testBitmap = ReceiptBitmapHelper.createReceiptBitmap(
                        context = context,
                        storeConfig = config,
                        invoiceNumber = 9999,
                        dateString = "2026/10/08",
                        customerName = "تجربة طباعة الفاتورة",
                        paymentType = "نقداً",
                        dualRows = listOf(
                            com.example.ui.viewmodels.DualReceiptRow(
                                rightDescription = "صنف تجريبي 1",
                                rightQuantityStr = "1",
                                rightTotalAmountStr = "500",
                                leftDescription = "صنف تجريبي 2",
                                leftQuantityStr = "2",
                                leftTotalAmountStr = "1000"
                            )
                        ),
                        widthPx = selectedPaperWidthDots
                    )
                    viewModel.printInvoiceAsBitmap(testBitmap)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = printerState is PrinterConnectionState.Connected,
                isPrimary = true
            ) {
                Icon(AppIcons.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("طباعة فاتورة تجريبية كصورة", fontWeight = FontWeight.Bold, color = Color.White)
            }

            // DEVICE LIST HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الأجهزة المقترنة بالهاتف (${pairedDevices.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NeuTextPrimary
                )

                NeuCircleButton(
                    onClick = { pairedDevices = viewModel.bluetoothPrinterManager.getPairedDevices() },
                    size = 36.dp
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = NeuAccentBlue, modifier = Modifier.size(16.dp))
                }
            }

            if (pairedDevices.isEmpty()) {
                NeuCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "لا توجد أجهزة بلوتوث مقترنة. يرجى التوجه لإعدادات الهاتف، وتشغيل البلوتوث والاقتران بالطابعة أولاً.",
                        color = NeuTextSecondary,
                        fontSize = 12.5.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pairedDevices) { device ->
                        NeuCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        viewModel.bluetoothPrinterManager.connectToDevice(device)
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            elevation = 4.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    NeuBadge(size = 40.dp) {
                                        Icon(AppIcons.Print, contentDescription = null, tint = NeuAccentBlue, modifier = Modifier.size(18.dp))
                                    }

                                    Column {
                                        Text(
                                            text = device.name ?: "طابعة غير معروفة",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = NeuTextPrimary
                                        )
                                        Text(
                                            text = device.address,
                                            fontSize = 11.5.sp,
                                            color = NeuTextMuted
                                        )
                                    }
                                }

                                NeuButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.bluetoothPrinterManager.connectToDevice(device)
                                        }
                                    },
                                    isPrimary = false
                                ) {
                                    Text("اتصال", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeuAccentBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
