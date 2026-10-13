package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProductCatalogEntity
import com.example.ui.theme.*
import com.example.ui.viewmodels.InvoiceViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.toEnglishDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsCatalogScreen(viewModel: InvoiceViewModel) {
    val allProducts by viewModel.allProducts.collectAsState()
    val storeConfigState by viewModel.storeConfig.collectAsState()
    val storeConfig = storeConfigState ?: com.example.data.local.StoreConfigEntity()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductCatalogEntity?>(null) }
    var productNameInput by remember { mutableStateOf("") }
    var productPriceInput by remember { mutableStateOf("") }
    var productCategoryInput by remember { mutableStateOf("عام") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }

    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }
    val defaultCategories = listOf("عام", "مواد غذائية", "مشروبات", "معلبات", "منظفات", "حلويات", "أخرى")

    val filteredList = remember(allProducts, searchQuery, selectedCategoryFilter) {
        allProducts.filter { product ->
            val matchesSearch = if (searchQuery.isBlank()) true else product.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = if (selectedCategoryFilter == "الكل") true else product.category == selectedCategoryFilter
            matchesSearch && matchesCategory
        }
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "دليل المنتجات والأصناف",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NeuTextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NeuInsetBg
                    ) {
                        Text(
                            text = "${allProducts.size} صنف",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuAccentBlue
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            NeuCircleButton(
                onClick = {
                    productNameInput = ""
                    productPriceInput = ""
                    productCategoryInput = "عام"
                    editingProduct = null
                    showAddDialog = true
                },
                size = 56.dp,
                isPrimary = true
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "إضافة منتج جديد",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sunken Neumorphic Search Bar
            NeuInsetBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = NeuTextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.5.sp, color = NeuTextPrimary),
                        cursorBrush = SolidColor(NeuAccentBlue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("بحث عن صنف في الدليل...", fontSize = 13.5.sp, color = NeuTextMuted)
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = NeuTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Category Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("الكل", "عام", "مواد غذائية", "مشروبات", "معلبات").forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat, fontSize = 10.sp, fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "لا توجد أصناف مطابقة للبحث.",
                            color = NeuTextMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اضغط على زر (+) في الأسفل لإضافة صنف جديد",
                            color = NeuTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.productId }) { product ->
                        NeuCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingProduct = product
                                    productNameInput = product.name
                                    productPriceInput = if (product.defaultUnitPrice > 0) product.defaultUnitPrice.toString() else ""
                                    productCategoryInput = product.category
                                    showAddDialog = true
                                },
                            shape = RoundedCornerShape(18.dp),
                            elevation = 6.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = NeuTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "القسم: ${product.category}",
                                        fontSize = 11.5.sp,
                                        color = NeuTextSecondary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = NeuInsetBg
                                    ) {
                                        Text(
                                            text = "${formatter.format(product.defaultUnitPrice)} ${storeConfig.currencySymbol}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = NeuAccentBlue
                                        )
                                    }

                                    // Quick Edit
                                    NeuCircleButton(
                                        onClick = {
                                            editingProduct = product
                                            productNameInput = product.name
                                            productPriceInput = if (product.defaultUnitPrice > 0) product.defaultUnitPrice.toString() else ""
                                            productCategoryInput = product.category
                                            showAddDialog = true
                                        },
                                        size = 32.dp
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "تعديل الصنف",
                                            tint = NeuTextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    // Delete
                                    NeuCircleButton(
                                        onClick = { viewModel.deleteCatalogProduct(product) },
                                        size = 32.dp
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "حذف الصنف",
                                            tint = NeuError,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Product Modal
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = NeuSurface,
            title = {
                Text(
                    text = if (editingProduct != null) "تعديل بيانات الصنف" else "إضافة صنف جديد للدليل",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NeuTextPrimary
                )
            },
            confirmButton = {
                NeuButton(
                    onClick = {
                        val name = productNameInput.trim()
                        val price = productPriceInput.toDoubleOrNull() ?: 0.0
                        if (name.isNotEmpty()) {
                            if (editingProduct != null) {
                                viewModel.addCatalogProduct(name, price, productCategoryInput)
                            } else {
                                viewModel.addCatalogProduct(name, price, productCategoryInput)
                            }
                            productNameInput = ""
                            productPriceInput = ""
                            editingProduct = null
                            showAddDialog = false
                        }
                    },
                    isPrimary = true
                ) {
                    Text(if (editingProduct != null) "حفظ التعديل" else "إضافة الصنف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                NeuButton(
                    onClick = {
                        showAddDialog = false
                        editingProduct = null
                    },
                    isPrimary = false
                ) {
                    Text("إلغاء", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("اسم الصنف:", fontSize = 12.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                    NeuInsetBox(modifier = Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = productNameInput,
                            onValueChange = { productNameInput = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = NeuTextPrimary),
                            cursorBrush = SolidColor(NeuAccentBlue),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (productNameInput.isEmpty()) {
                                    Text("مثال: عصير مانجو كبير", color = NeuTextMuted, fontSize = 13.5.sp)
                                }
                                inner()
                            }
                        )
                    }

                    Text("سعر البيع الافتراضي:", fontSize = 12.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                    NeuInsetBox(modifier = Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = productPriceInput,
                            onValueChange = { productPriceInput = it.toEnglishDigits() },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = NeuTextPrimary),
                            cursorBrush = SolidColor(NeuAccentBlue),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (productPriceInput.isEmpty()) {
                                    Text("0.0", color = NeuTextMuted, fontSize = 13.5.sp)
                                }
                                inner()
                            }
                        )
                    }

                    Text("فئة / قسم الصنف:", fontSize = 12.sp, color = NeuTextSecondary, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("عام", "مواد غذائية", "مشروبات", "معلبات").forEach { cat ->
                            FilterChip(
                                selected = productCategoryInput == cat,
                                onClick = { productCategoryInput = cat },
                                label = { Text(cat, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        )
    }
}
