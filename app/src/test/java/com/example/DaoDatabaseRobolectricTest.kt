package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoDatabaseRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var invoiceDao: InvoiceDao
    private lateinit var productDao: ProductDao
    private lateinit var receiptDao: CustomerReceiptDao
    private lateinit var configDao: StoreConfigDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        invoiceDao = db.invoiceDao()
        productDao = db.productDao()
        receiptDao = db.customerReceiptDao()
        configDao = db.storeConfigDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testStoreConfigInsertAndRead() = runBlocking {
        val config = StoreConfigEntity(
            id = 1,
            storeNameArabic = "بقالة النجاح",
            currencySymbol = "ر.ي"
        )
        configDao.insertOrUpdate(config)

        val retrieved = configDao.getStoreConfigSync()
        assertNotNull(retrieved)
        assertEquals("بقالة النجاح", retrieved?.storeNameArabic)
        assertEquals("ر.ي", retrieved?.currencySymbol)
    }

    @Test
    fun testSaveFullInvoiceWithItems() = runBlocking {
        val invoice = InvoiceEntity(
            invoiceNumber = 101,
            dateString = "2026/10/17",
            customerName = "علي صالح",
            paymentType = "نقداً",
            subtotal = 5000.0,
            grandTotal = 5000.0
        )
        val items = listOf(
            InvoiceItemEntity(
                invoiceId = 0,
                description = "كرتون ماء",
                quantity = 2.0,
                unitPrice = 2500.0,
                totalAmount = 5000.0
            )
        )

        val invoiceId = invoiceDao.saveFullInvoice(invoice, items)
        assertTrue(invoiceId > 0)

        val retrieved = invoiceDao.getInvoiceById(invoiceId)
        assertNotNull(retrieved)
        assertEquals(101, retrieved?.invoice?.invoiceNumber)
        assertEquals("علي صالح", retrieved?.invoice?.customerName)
        assertEquals(1, retrieved?.items?.size)
        assertEquals("كرتون ماء", retrieved?.items?.first()?.description)
        assertEquals(5000.0, retrieved?.items?.first()?.totalAmount ?: 0.0, 0.01)
    }

    @Test
    fun testProductCatalogAndStock() = runBlocking {
        val product = ProductCatalogEntity(
            name = "حليب الممتاز",
            defaultUnitPrice = 800.0,
            category = "ألبان",
            stockQuantity = 50.0,
            minStockAlert = 10.0
        )
        val id = productDao.insertProduct(product)
        assertTrue(id > 0)

        val retrieved = productDao.getProductByName("حليب الممتاز")
        assertNotNull(retrieved)
        assertEquals(800.0, retrieved?.defaultUnitPrice ?: 0.0, 0.01)
        assertEquals(50.0, retrieved?.stockQuantity ?: 0.0, 0.01)

        // Test stock update
        val updated = retrieved!!.copy(stockQuantity = 45.0)
        productDao.insertProduct(updated)

        val afterUpdate = productDao.getProductByName("حليب الممتاز")
        assertEquals(45.0, afterUpdate?.stockQuantity ?: 0.0, 0.01)
    }

    @Test
    fun testCustomerReceiptsAndAggregation() = runBlocking {
        val receipt1 = CustomerReceiptEntity(
            customerName = "عمر خالد",
            amount = 1500.0,
            dateString = "2026/10/17",
            paymentType = "سند قبض"
        )
        val receipt2 = CustomerReceiptEntity(
            customerName = "عمر خالد",
            amount = 2500.0,
            dateString = "2026/10/17",
            paymentType = "سند قبض"
        )

        receiptDao.insertReceipt(receipt1)
        receiptDao.insertReceipt(receipt2)

        val receipts = receiptDao.getReceiptsForCustomer("عمر خالد").first()
        assertEquals(2, receipts.size)
        val sum = receipts.sumOf { it.amount }
        assertEquals(4000.0, sum, 0.01)
    }

    @Test
    fun testMaxInvoiceNumber() = runBlocking {
        assertEquals(null, invoiceDao.getMaxInvoiceNumber())

        invoiceDao.insertInvoice(
            InvoiceEntity(
                invoiceNumber = 1005,
                dateString = "2026/10/17",
                customerName = "عميل 1",
                paymentType = "نقداً",
                subtotal = 100.0,
                grandTotal = 100.0
            )
        )
        assertEquals(1005, invoiceDao.getMaxInvoiceNumber())

        invoiceDao.insertInvoice(
            InvoiceEntity(
                invoiceNumber = 1020,
                dateString = "2026/10/17",
                customerName = "عميل 2",
                paymentType = "نقداً",
                subtotal = 200.0,
                grandTotal = 200.0
            )
        )
        assertEquals(1020, invoiceDao.getMaxInvoiceNumber())
    }
}
