package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        StoreConfigEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        ProductCatalogEntity::class,
        CustomerReceiptEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeConfigDao(): StoreConfigDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun productDao(): ProductDao
    abstract fun customerReceiptDao(): CustomerReceiptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fatooraty_database.db"
                )
                .addCallback(DatabaseCallback(context.applicationContext))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val appDb = getDatabase(context)
                    appDb.storeConfigDao().insertOrUpdate(StoreConfigEntity())
                    
                    // Seed initial sample products for quick testing
                    appDb.productDao().insertProduct(ProductCatalogEntity(name = "أكياس بيضاء 50كجم", defaultUnitPrice = 1200.0))
                    appDb.productDao().insertProduct(ProductCatalogEntity(name = "أكياس مرحلة 25كجم", defaultUnitPrice = 900.0))
                    appDb.productDao().insertProduct(ProductCatalogEntity(name = "كرتون شاي ممتاز", defaultUnitPrice = 45000.0))
                    appDb.productDao().insertProduct(ProductCatalogEntity(name = "كيس سكر 10كجم", defaultUnitPrice = 15000.0))
                    appDb.productDao().insertProduct(ProductCatalogEntity(name = "كيس أرز 20كجم", defaultUnitPrice = 28000.0))
                }
            }
        }
    }
}
