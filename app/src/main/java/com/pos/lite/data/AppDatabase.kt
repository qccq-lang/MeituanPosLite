package com.pos.lite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Category::class, Product::class, Order::class, OrderItem::class, Staff::class, DiningTable::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meituan_pos_lite.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).posDao()
                            dao.insertStaff(Staff(name = "店长", pinCode = "888888", role = "ADMIN"))
                            dao.insertStaff(Staff(name = "收银员01", pinCode = "1234", role = "CASHIER"))

                            for (i in 1..12) {
                                dao.insertTable(DiningTable(name = "A%02d".format(i), capacity = 4))
                            }

                            val cat1 = dao.insertCategory(Category(name = "招牌快餐"))
                            val cat2 = dao.insertCategory(Category(name = "精选小吃"))
                            dao.insertProduct(Product(categoryId = cat1, name = "黄焖鸡米饭", price = 22.0))
                            dao.insertProduct(Product(categoryId = cat1, name = "台式卤肉饭", price = 20.0))
                            dao.insertProduct(Product(categoryId = cat2, name = "脆皮炸鸡翅", price = 12.0))
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
