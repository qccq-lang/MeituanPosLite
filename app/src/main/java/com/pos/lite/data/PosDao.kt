package com.pos.lite.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {
    // 员工
    @Query("SELECT * FROM staffs")
    fun getAllStaffs(): Flow<List<Staff>>

    @Query("SELECT * FROM staffs WHERE pinCode = :pin LIMIT 1")
    suspend fun loginWithPin(pin: String): Staff?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: Staff): Long

    // 桌台
    @Query("SELECT * FROM dining_tables ORDER BY id ASC")
    fun getAllTables(): Flow<List<DiningTable>>

    @Query("SELECT * FROM dining_tables WHERE id = :id")
    suspend fun getTableById(id: Long): DiningTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: DiningTable): Long

    @Update
    suspend fun updateTable(table: DiningTable)

    // 菜品与分类
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM products WHERE categoryId = :catId")
    fun getProductsByCategory(catId: Long): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    // 订单与明细 (挂单/结账)
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

    @Query("SELECT * FROM orders WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getOrdersByDateRange(start: Long, end: Long): List<Order>

    @Query("SELECT SUM(totalAmount) FROM orders WHERE status = 'PAID' AND timestamp BETWEEN :start AND :end")
    suspend fun getTotalSales(start: Long, end: Long): Double?
}
