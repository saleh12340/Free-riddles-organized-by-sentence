package com.example.data.backup

import android.content.Context
import com.example.data.local.*
import com.example.data.repository.InvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val context: Context,
    private val repository: InvoiceRepository
) {
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "Fatooraty Al-Ezzi")
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        root.put("timestamp", System.currentTimeMillis())

        // 1. Store Config
        val config = repository.storeConfig.first() ?: StoreConfigEntity()
        val configJson = JSONObject().apply {
            put("storeNameArabic", config.storeNameArabic)
            put("storeNameEnglish", config.storeNameEnglish)
            put("storeSubtitle", config.storeSubtitle)
            put("logoText", config.logoText)
            put("address", config.address)
            put("phone1", config.phone1)
            put("phone2", config.phone2)
            put("currencySymbol", config.currencySymbol)
            put("nextInvoiceNumber", config.nextInvoiceNumber)
            put("defaultDisclaimerNote", config.defaultDisclaimerNote)
        }
        root.put("storeConfig", configJson)

        // 2. Products Catalog
        val products = repository.allProducts.first()
        val productsArray = JSONArray()
        products.forEach { prod ->
            val pJson = JSONObject().apply {
                put("name", prod.name)
                put("defaultUnitPrice", prod.defaultUnitPrice)
                put("category", prod.category)
            }
            productsArray.put(pJson)
        }
        root.put("products", productsArray)

        // 3. Invoices With Items
        val invoices = repository.allInvoices.first()
        val invoicesArray = JSONArray()
        invoices.forEach { invWithItems ->
            val inv = invWithItems.invoice
            val invJson = JSONObject().apply {
                put("invoiceNumber", inv.invoiceNumber)
                put("dateString", inv.dateString)
                put("customerName", inv.customerName)
                put("paymentType", inv.paymentType)
                put("subtotal", inv.subtotal)
                put("discount", inv.discount)
                put("grandTotal", inv.grandTotal)
                put("notes", inv.notes)
                put("createdAtTimestamp", inv.createdAtTimestamp)

                val itemsArray = JSONArray()
                invWithItems.items.forEach { item ->
                    val itJson = JSONObject().apply {
                        put("description", item.description)
                        put("quantity", item.quantity)
                        put("unitPrice", item.unitPrice)
                        put("totalAmount", item.totalAmount)
                    }
                    itemsArray.put(itJson)
                }
                put("items", itemsArray)
            }
            invoicesArray.put(invJson)
        }
        root.put("invoices", invoicesArray)

        root.toString(2)
    }

    suspend fun restoreFromJson(jsonString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)

            // 1. Restore Store Config
            if (root.has("storeConfig")) {
                val cfg = root.getJSONObject("storeConfig")
                val current = repository.storeConfig.first() ?: StoreConfigEntity()
                val updatedConfig = current.copy(
                    storeNameArabic = cfg.optString("storeNameArabic", current.storeNameArabic),
                    storeNameEnglish = cfg.optString("storeNameEnglish", current.storeNameEnglish),
                    storeSubtitle = cfg.optString("storeSubtitle", current.storeSubtitle),
                    logoText = cfg.optString("logoText", current.logoText),
                    address = cfg.optString("address", current.address),
                    phone1 = cfg.optString("phone1", current.phone1),
                    phone2 = cfg.optString("phone2", current.phone2),
                    currencySymbol = cfg.optString("currencySymbol", current.currencySymbol),
                    nextInvoiceNumber = cfg.optInt("nextInvoiceNumber", current.nextInvoiceNumber),
                    defaultDisclaimerNote = cfg.optString("defaultDisclaimerNote", current.defaultDisclaimerNote)
                )
                repository.saveStoreConfig(updatedConfig)
            }

            // 2. Restore Products
            var restoredProductsCount = 0
            if (root.has("products")) {
                val productsArr = root.getJSONArray("products")
                for (i in 0 until productsArr.length()) {
                    val p = productsArr.getJSONObject(i)
                    val name = p.optString("name").trim()
                    if (name.isNotEmpty()) {
                        repository.saveProduct(
                            ProductCatalogEntity(
                                name = name,
                                defaultUnitPrice = p.optDouble("defaultUnitPrice", 0.0),
                                category = p.optString("category", "عام")
                            )
                        )
                        restoredProductsCount++
                    }
                }
            }

            // 3. Restore Invoices
            var restoredInvoicesCount = 0
            if (root.has("invoices")) {
                val invArr = root.getJSONArray("invoices")
                for (i in 0 until invArr.length()) {
                    val invJson = invArr.getJSONObject(i)
                    val invoice = InvoiceEntity(
                        invoiceNumber = invJson.optInt("invoiceNumber", 1001),
                        dateString = invJson.optString("dateString", ""),
                        customerName = invJson.optString("customerName", "عميل"),
                        paymentType = invJson.optString("paymentType", "نقداً"),
                        subtotal = invJson.optDouble("subtotal", 0.0),
                        discount = invJson.optDouble("discount", 0.0),
                        grandTotal = invJson.optDouble("grandTotal", 0.0),
                        notes = invJson.optString("notes", ""),
                        createdAtTimestamp = invJson.optLong("createdAtTimestamp", System.currentTimeMillis())
                    )

                    val itemsList = mutableListOf<InvoiceItemEntity>()
                    if (invJson.has("items")) {
                        val itemsArr = invJson.getJSONArray("items")
                        for (j in 0 until itemsArr.length()) {
                            val it = itemsArr.getJSONObject(j)
                            itemsList.add(
                                InvoiceItemEntity(
                                    invoiceId = 0,
                                    description = it.optString("description", ""),
                                    quantity = it.optDouble("quantity", 1.0),
                                    unitPrice = it.optDouble("unitPrice", 0.0),
                                    totalAmount = it.optDouble("totalAmount", 0.0)
                                )
                            )
                        }
                    }

                    repository.saveInvoice(invoice, itemsList)
                    restoredInvoicesCount++
                }
            }

            BackupRestoreResult.Success(
                invoicesCount = restoredInvoicesCount,
                productsCount = restoredProductsCount
            )
        } catch (e: Throwable) {
            BackupRestoreResult.Error(e.localizedMessage ?: "فشل في قراءة ملف النسخة الاحتياطية")
        }
    }
}

sealed class BackupRestoreResult {
    data class Success(val invoicesCount: Int, val productsCount: Int) : BackupRestoreResult()
    data class Error(val message: String) : BackupRestoreResult()
}
