package com.pos.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val orderId: Long = 0,
    val orderNo: String,
    val totalAmount: Double,
    val payType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "收银员",
    val tableId: Long = 0,
    val tableName: String = "快餐",
    val status: String = "PAID"
)
