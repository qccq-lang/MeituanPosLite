package com.pos.lite.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {
    // 员工
    @Query("SELECT * FROM staffs ORDER BY id ASC")
    fun getAllStaffs(): Flow<List<Staff>>

    @Query("SELECT * FROM staffs WHERE pinCode = :pin LIMIT 1")
    suspend fun loginWithPin(pin: String): Staff?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: Staff): Long

    @Update
    suspend fun updateStaff(staff: Staff)

    @Query("DELETE FROM staffs WHERE id = :id")
    suspend fun deleteStaffById(id: Long)

    // 桌台
    @Query("SELECT * FROM dining_tables ORDER BY id ASC")
    fun getAllTables(): Flow<List<DiningTable>>

    @Query("SELECT * FROM dining_tables WHERE id = :id")
    suspend fun getTableById(id: Long): DiningTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: DiningTable): Long

    @Update
    suspend fun updateTable(table: DiningTable)

    @Query("DELETE FROM dining_tables WHERE id = :id")
    suspend fun deleteTableById(id: Long)

    // 分类
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

    // 菜品
    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE categoryId = :catId ORDER BY id DESC")
    fun getProductsByCategory(catId: Long): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    // 折扣配置 (常用预设)
    @Query("SELECT * FROM discount_configs ORDER BY sortOrder ASC, id ASC")
    fun getAllDiscountConfigs(): Flow<List<DiscountConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscountConfig(config: DiscountConfig): Long

    @Update
    suspend fun updateDiscountConfig(config: DiscountConfig)

    @Query("DELETE FROM discount_configs WHERE id = :id")
    suspend fun deleteDiscountConfigById(id: Long)

    // 订单与流水
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order): Long

    @Update
    suspend fun updateOrder(order: Order)

    @Query("SELECT * FROM orders WHERE orderId = :orderId")
    suspend fun getOrderById(orderId: Long): Order?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItem>)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItemsByOrderId(orderId: Long)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItems(orderId: Long): List<OrderItem>

    @Query("SELECT * FROM orders WHERE status = 'PAID' AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getPaidOrdersByRange(start: Long, end: Long): List<Order>
}