package com.pos.lite.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {
    @Query("SELECT * FROM staffs")
    fun getAllStaffs(): Flow<List<Staff>>

    @Query("SELECT * FROM staffs WHERE pinCode = :pin LIMIT 1")
    suspend fun loginWithPin(pin: String): Staff?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: Staff): Long

    @Query("SELECT * FROM dining_tables")
    fun getAllTables(): Flow<List<DiningTable>>

    @Query("SELECT * FROM dining_tables WHERE id = :id")
    suspend fun getTableById(id: Long): DiningTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: DiningTable): Long

    @Update
    suspend fun updateTable(table: DiningTable)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM products WHERE categoryId = :catId")
    fun getProductsByCategory(catId: Long): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert
    suspend fun insertOrder(order: Order): Long

    @Insert
    suspend fun insertOrderItems(items: List<OrderItem>)

    @Query("SELECT * FROM orders WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getOrdersByDateRange(start: Long, end: Long): List<Order>

    @Query("SELECT SUM(totalAmount) FROM orders WHERE status = 'PAID' AND timestamp BETWEEN :start AND :end")
    suspend fun getTotalSales(start: Long, end: Long): Double?
}
