package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StoreConfigEntity
import com.example.ui.viewmodels.DualReceiptRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.example.ui.util.NumberUtils
import com.example.ui.util.toEnglishDigits

val ReceiptInkNavy = Color(0xFF1E3A8A)
val ReceiptInkBlueAccent = Color(0xFF1D4ED8)
val ReceiptInkRed = Color(0xFFDC2626)
val ReceiptPaperWhite = Color(0xFFFFFFFF)

@Composable
fun AutoSelectBasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    cursorBrush: Brush = SolidColor(ReceiptInkNavy),
    placeholder: String = "",
    textAlign: TextAlign = TextAlign.Start,
    focusRequester: FocusRequester? = null
) {
    var isFocusedState by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Initial state: cursor at end, NO selection (so opening new invoice never selects all)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    // Crucial: When focused, select all existing text after Compose's touch event settles
    LaunchedEffect(isFocusedState) {
        if (isFocusedState && textFieldValue.text.isNotEmpty()) {
            delay(50)
            textFieldValue = textFieldValue.copy(
                selection = TextRange(0, textFieldValue.text.length)
            )
        }
    }

    var baseModifier = modifier
        .onFocusChanged { focusState ->
            val justGainedFocus = focusState.isFocused && !isFocusedState
            isFocusedState = focusState.isFocused
            onFocusChange(focusState.isFocused)

            if (justGainedFocus && textFieldValue.text.isNotEmpty()) {
                coroutineScope.launch {
                    delay(50)
                    textFieldValue = textFieldValue.copy(
                        selection = TextRange(0, textFieldValue.text.length)
                    )
                }
            } else if (!focusState.isFocused) {
                // Clear selection highlight when losing focus
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(textFieldValue.text.length)
                )
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    focusRequester?.requestFocus()
                    if (textFieldValue.text.isNotEmpty()) {
                        coroutineScope.launch {
                            delay(50)
                            textFieldValue = textFieldValue.copy(
                                selection = TextRange(0, textFieldValue.text.length)
                            )
                        }
                    }
                }
            )
        }

    if (focusRequester != null) {
        baseModifier = baseModifier.focusRequester(focusRequester)
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newVal ->
            val converted = newVal.text.toEnglishDigits()
            val cleanVal = if (converted != newVal.text) {
                newVal.copy(text = converted)
            } else {
                newVal
            }
            textFieldValue = cleanVal
            onValueChange(converted)
        },
        modifier = baseModifier,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        cursorBrush = cursorBrush,
        decorationBox = { innerTextField ->
            if (textFieldValue.text.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = textStyle.fontSize,
                    color = Color.Gray.copy(alpha = 0.5f),
                    textAlign = textAlign
                )
            }
            innerTextField()
        }
    )
}

@Composable
fun InteractiveReceiptView(
    storeConfig: StoreConfigEntity,
    invoiceNumber: Int,
    onInvoiceNumberChange: (Int) -> Unit,
    dateString: String,
    onDateStringChange: (String) -> Unit,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    paymentType: String,
    onPaymentTypeChange: (String) -> Unit,
    dualRows: List<DualReceiptRow>,
    onUpdateRightDescription: (index: Int, desc: String) -> Unit,
    onUpdateRightQuantity: (index: Int, qty: String) -> Unit,
    onUpdateRightTotalAmount: (index: Int, total: String) -> Unit,
    onUpdateLeftDescription: (index: Int, desc: String) -> Unit,
    onUpdateLeftQuantity: (index: Int, qty: String) -> Unit,
    onUpdateLeftTotalAmount: (index: Int, total: String) -> Unit,
    onRemoveRow: (index: Int) -> Unit,
    onAddManualRow: () -> Unit,
    customerSuggestions: List<String> = emptyList(),
    onCustomerSuggestionClick: (String) -> Unit = {},
    isEditable: Boolean = true,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)) }

    val rightSubtotal = dualRows.sumOf { it.rightTotal }
    val leftSubtotal = dualRows.sumOf { it.leftTotal }
    val grandTotal = rightSubtotal + leftSubtotal

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.50f),
                spotColor = Color(0xFFA3B1C6).copy(alpha = 0.65f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(ReceiptPaperWhite)
            .border(1.dp, Color(0xFFCBD5E1).copy(alpha = 0.70f), RoundedCornerShape(18.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. ULTRA-COMPACT STORE HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ReceiptInkNavy.copy(alpha = 0.05f))
                    .border(0.8.dp, ReceiptInkNavy.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Store Name & Phone
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = storeConfig.storeNameArabic.ifEmpty { "بقالة العزي" },
                        fontWeight = FontWeight.Black,
                        fontSize = 12.5.sp,
                        color = ReceiptInkBlueAccent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ج: ${storeConfig.phone1.ifEmpty { "772437314" }}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                }

                // Payment Type Cash/Credit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .border(0.8.dp, ReceiptInkNavy.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.clickable(enabled = isEditable) { onPaymentTypeChange("نقداً") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (paymentType == "نقداً") "[✓] نقداً" else "[ ] نقداً",
                            fontSize = 8.sp,
                            color = if (paymentType == "نقداً") ReceiptInkBlueAccent else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(
                        modifier = Modifier.clickable(enabled = isEditable) { onPaymentTypeChange("أجل") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (paymentType == "أجل") "[✓] أجل" else "[ ] أجل",
                            fontSize = 8.sp,
                            color = if (paymentType == "أجل") ReceiptInkRed else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Invoice Number & Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "رقم: ", fontSize = 8.sp, color = ReceiptInkNavy, fontWeight = FontWeight.Bold)
                    if (isEditable) {
                        Box(
                            modifier = Modifier
                                .border(0.6.dp, ReceiptInkRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 2.dp)
                        ) {
                            AutoSelectBasicTextField(
                                value = invoiceNumber.toString(),
                                onValueChange = { onInvoiceNumberChange(it.toIntOrNull() ?: invoiceNumber) },
                                textStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = ReceiptInkRed, textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.width(40.dp)
                            )
                        }
                    } else {
                        Text(text = "$invoiceNumber", fontSize = 10.5.sp, color = ReceiptInkRed, fontWeight = FontWeight.Black)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(text = "التاريخ: ", fontSize = 8.sp, color = ReceiptInkNavy, fontWeight = FontWeight.Bold)
                    if (isEditable) {
                        Box(
                            modifier = Modifier
                                .border(0.6.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 2.dp)
                        ) {
                            AutoSelectBasicTextField(
                                value = dateString,
                                onValueChange = onDateStringChange,
                                textStyle = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.width(58.dp)
                            )
                        }
                    } else {
                        Text(text = dateString, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 2. CUSTOMER NAME INPUT BAR ("المطلوب من الأخ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                     .border(0.8.dp, ReceiptInkNavy.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "",
                    fontSize = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReceiptInkNavy
                )
                if (isEditable) {
                    AutoSelectBasicTextField(
                        value = customerName,
                        onValueChange = onCustomerNameChange,
                        textStyle = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                        singleLine = true,
                        placeholder = "العميل",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        cursorBrush = SolidColor(ReceiptInkNavy),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = customerName.ifEmpty { "عميل نقدي" },
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            if (isEditable && customerSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    customerSuggestions.take(4).forEach { suggestion ->
                        Surface(
                            modifier = Modifier.clickable { onCustomerSuggestionClick(suggestion) },
                            shape = RoundedCornerShape(10.dp),
                            color = ReceiptInkNavy.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = suggestion,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = ReceiptInkBlueAccent,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            // 3. INVOICE-LIKE ITEM GRID: TRUE DUAL-COLUMN SIDE-BY-SIDE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.8.dp, ReceiptInkNavy.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .background(ReceiptInkNavy)
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right Half Headers (الشق الأيمن: القيمة الإجمالية -> العدد -> التفاصيل -> سعر الواحدة)
                HeaderCell("الإجمالي", 0.80f)
                HeaderCell("العدد", 0.45f)
                HeaderCell("التفاصيل", 1.45f)
                HeaderCell("سعر الواحدة", 0.70f)

                // Central Vertical Divider Line
                Box(Modifier.width(1.5.dp).height(20.dp).background(Color.White))

                // Left Half Headers (الشق الأيسر: القيمة الإجمالية -> العدد -> التفاصيل -> سعر الواحدة)
                HeaderCell("الإجمالي", 0.80f)
                HeaderCell("العدد", 0.45f)
                HeaderCell("التفاصيل", 1.45f)
                HeaderCell("سعر الواحدة", 0.70f)
            }

            dualRows.forEachIndexed { index, row ->
                val rightQty = (row.rightQuantityStr.toEnglishDigits().toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                val rightTot = row.rightTotal
                val rightUnitPrice = if (rightQty > 0.0 && rightTot > 0.0) rightTot / rightQty else 0.0
                val leftQty = (row.leftQuantityStr.toEnglishDigits().toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                val leftTot = row.leftTotal
                val leftUnitPrice = if (leftQty > 0.0 && leftTot > 0.0) leftTot / leftQty else 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            0.6.dp,
                            if (row.hasRightData || row.hasLeftData) ReceiptInkNavy.copy(alpha = 0.35f) else Color(0xFFE2E8F0),
                            RoundedCornerShape(6.dp)
                        )
                        .background(if (index % 2 == 0) Color.White else Color(0xFFF8FAFC)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // --- RIGHT HALF (الشق الأيمن) ---
                    // 1. القيمة الإجمالية
                    DataCell(
                        value = row.rightTotalAmountStr.toEnglishDigits(),
                        onValueChange = { onUpdateRightTotalAmount(index, it.toEnglishDigits()) },
                        isEditable = isEditable,
                        weight = 0.80f,
                        isNumeric = true,
                        textColor = ReceiptInkNavy,
                        fontWeight = FontWeight.Bold,
                        placeholder = if (isEditable && row.isCompletelyEmpty) "0" else ""
                    )
                    // 2. العدد
                    DataCell(
                        value = row.rightQuantityStr.toEnglishDigits(),
                        onValueChange = { onUpdateRightQuantity(index, it.toEnglishDigits()) },
                        isEditable = isEditable,
                        weight = 0.45f,
                        isNumeric = true
                    )
                    // 3. التفاصيل
                    DataCell(
                        value = row.rightDescription,
                        onValueChange = { onUpdateRightDescription(index, it) },
                        isEditable = isEditable,
                        weight = 1.45f,
                        align = TextAlign.Start,
                        placeholder = if (isEditable && row.isCompletelyEmpty) "صنف" else ""
                    )
                    // 4. سعر الواحدة (يستنتج ويعرض)
                    DataCell(
                        value = if (rightUnitPrice > 0.0) formatter.format(rightUnitPrice) else "",
                        onValueChange = {},
                        isEditable = false,
                        weight = 0.70f,
                        isNumeric = true,
                        textColor = ReceiptInkBlueAccent,
                        fontWeight = FontWeight.Bold
                    )

                    // --- CENTRAL VERTICAL DIVIDER ---
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(ReceiptInkNavy.copy(alpha = 0.35f))
                    )

                    // --- LEFT HALF (الشق الأيسر) ---
                    // 1. القيمة الإجمالية
                    DataCell(
                        value = row.leftTotalAmountStr.toEnglishDigits(),
                        onValueChange = { onUpdateLeftTotalAmount(index, it.toEnglishDigits()) },
                        isEditable = isEditable,
                        weight = 0.80f,
                        isNumeric = true,
                        textColor = ReceiptInkNavy,
                        fontWeight = FontWeight.Bold,
                        placeholder = ""
                    )
                    // 2. العدد
                    DataCell(
                        value = row.leftQuantityStr.toEnglishDigits(),
                        onValueChange = { onUpdateLeftQuantity(index, it.toEnglishDigits()) },
                        isEditable = isEditable,
                        weight = 0.45f,
                        isNumeric = true
                    )
                    // 3. التفاصيل
                    DataCell(
                        value = row.leftDescription,
                        onValueChange = { onUpdateLeftDescription(index, it) },
                        isEditable = isEditable,
                        weight = 1.45f,
                        align = TextAlign.Start,
                        placeholder = ""
                    )
                    // 4. سعر الواحدة (يستنتج ويعرض)
                    DataCell(
                        value = if (leftUnitPrice > 0.0) formatter.format(leftUnitPrice) else "",
                        onValueChange = {},
                        isEditable = false,
                        weight = 0.70f,
                        isNumeric = true,
                        textColor = ReceiptInkBlueAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))

            // 5. TOTALS SECTION:
            // Part A: Subtotals side-by-side (كل شيء إجمالي لحاله)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Right Subtotal Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ReceiptInkNavy.copy(alpha = 0.08f))
                        .border(0.8.dp, ReceiptInkNavy.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "إجمالي اليمين: ${formatter.format(rightSubtotal)} ${storeConfig.currencySymbol}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ReceiptInkBlueAccent,
                        textAlign = TextAlign.Center
                    )
                }

                // Left Subtotal Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ReceiptInkNavy.copy(alpha = 0.08f))
                        .border(0.8.dp, ReceiptInkNavy.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "إجمالي اليسار: ${formatter.format(leftSubtotal)} ${storeConfig.currencySymbol}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ReceiptInkBlueAccent,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(1.5.dp))

            // Part B: Grand Total Directly Underneath (وكذلك إجمالي عام للكل تحته)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(ReceiptInkNavy)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المبلغ الإجمالي العام (Grand Total):",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${formatter.format(grandTotal)} ${storeConfig.currencySymbol}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFEF08A) // Soft gold highlight
                )
            }

            // 6. ULTRA-COMPACT SIGNATURES (تقليص توقيع المشتري وتوقيع البائع)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ت.المشتري: ......",
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Text(
                    text = storeConfig.defaultDisclaimerNote.take(30),
                    fontSize = 6.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "ت.البائع: ......",
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RowScope.DataCell(
    value: String,
    onValueChange: (String) -> Unit,
    isEditable: Boolean,
    weight: Float,
    isNumeric: Boolean = false,
    align: TextAlign = TextAlign.Center,
    textColor: Color = Color.Black,
    fontWeight: FontWeight = FontWeight.Normal,
    placeholder: String = "",
    subBadge: String? = null,
    autoFocus: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus && isEditable) {
            delay(80)
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(if (subBadge != null) 34.dp else 28.dp)
            .padding(horizontal = 0.5.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isFocused) Color(0xFFEFF6FF)
                else if (isNumeric && value.isNotBlank()) Color(0xFFF8FAFC)
                else Color.White
            )
            .border(
                width = if (isFocused) 1.2.dp else 0.5.dp,
                color = if (isFocused) ReceiptInkBlueAccent else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(5.dp)
            )
            .padding(horizontal = 2.dp),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = when (align) {
                TextAlign.Start -> Alignment.Start
                TextAlign.End -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
        ) {
            if (isEditable) {
                AutoSelectBasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    onFocusChange = { isFocused = it },
                    textStyle = TextStyle(
                        fontSize = 10.5.sp,
                        fontWeight = if (isFocused) FontWeight.Bold else fontWeight,
                        color = if (isFocused) ReceiptInkBlueAccent else textColor,
                        textAlign = align
                    ),
                    keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                                      else KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    singleLine = true,
                    placeholder = placeholder,
                    textAlign = align,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = value,
                    fontSize = 10.5.sp,
                    fontWeight = fontWeight,
                    color = textColor,
                    textAlign = align,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!subBadge.isNullOrBlank()) {
                Text(
                    text = subBadge,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReceiptInkBlueAccent,
                    maxLines = 1,
                    lineHeight = 9.sp
                )
            }
        }
    }
}
