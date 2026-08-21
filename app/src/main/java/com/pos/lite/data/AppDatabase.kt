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
    entities = [Category::class, Product::class, Order::class, OrderItem::class, Staff::class, DiningTable::class, DiscountConfig::class],
    version = 4,
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
                ).fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).posDao()

                            // 1. 初始员工 (唯一店长)
                            if (dao.getAdminCount() == 0) {
                                dao.insertStaff(Staff(name = "店长", pinCode = "888888", role = "ADMIN"))
                                dao.insertStaff(Staff(name = "收银员01", pinCode = "1234", role = "CASHIER"))
                            }

                            // 2. 初始桌台 (按大厅、包厢、露台分区)
                            for (i in 1..6) {
                                dao.insertTable(DiningTable(name = "A%02d".format(i), area = "大厅", capacity = 4))
                            }
                            dao.insertTable(DiningTable(name = "B01卡座", area = "卡座", capacity = 6))
                            dao.insertTable(DiningTable(name = "B02卡座", area = "卡座", capacity = 6))
                            dao.insertTable(DiningTable(name = "V01包厢", area = "包厢", capacity = 10))
                            dao.insertTable(DiningTable(name = "V02包厢", area = "包厢", capacity = 12))
                            dao.insertTable(DiningTable(name = "T01露台", area = "露台", capacity = 4))

                            // 3. 初始分类与商品
                            val cat1 = dao.insertCategory(Category(name = "招牌快餐"))
                            val cat2 = dao.insertCategory(Category(name = "特色炒菜"))
                            val cat3 = dao.insertCategory(Category(name = "酒水饮料"))

                            dao.insertProduct(Product(categoryId = cat1, name = "黄焖鸡米饭", price = 22.0))
                            dao.insertProduct(Product(categoryId = cat1, name = "台式卤肉饭", price = 20.0))
                            dao.insertProduct(Product(categoryId = cat2, name = "农家小炒肉", price = 38.0))
                            dao.insertProduct(Product(categoryId = cat2, name = "手撕包菜", price = 18.0))
                            dao.insertProduct(Product(categoryId = cat3, name = "冰镇酸梅汤", price = 6.0))

                            // 4. 常用折扣快捷按键库
                            dao.insertDiscountConfig(DiscountConfig(name = "95折", type = "RATE", value = 0.95, sortOrder = 1))
                            dao.insertDiscountConfig(DiscountConfig(name = "9折", type = "RATE", value = 0.90, sortOrder = 2))
                            dao.insertDiscountConfig(DiscountConfig(name = "88折", type = "RATE", value = 0.88, sortOrder = 3))
                            dao.insertDiscountConfig(DiscountConfig(name = "85折", type = "RATE", value = 0.85, sortOrder = 4))
                            dao.insertDiscountConfig(DiscountConfig(name = "8折", type = "RATE", value = 0.80, sortOrder = 5))
                            dao.insertDiscountConfig(DiscountConfig(name = "75折", type = "RATE", value = 0.75, sortOrder = 6))
                            dao.insertDiscountConfig(DiscountConfig(name = "立减￥5", type = "DEDUCT", value = 5.0, sortOrder = 7))
                            dao.insertDiscountConfig(DiscountConfig(name = "立减￥10", type = "DEDUCT", value = 10.0, sortOrder = 8))
                            dao.insertDiscountConfig(DiscountConfig(name = "立减￥20", type = "DEDUCT", value = 20.0, sortOrder = 9))
                            dao.insertDiscountConfig(DiscountConfig(name = "立减￥50", type = "DEDUCT", value = 50.0, sortOrder = 10))
                            dao.insertDiscountConfig(DiscountConfig(name = "自动抹零", type = "MOLING", value = 0.0, sortOrder = 11))
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}