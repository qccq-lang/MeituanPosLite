package com.pos.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val originalPrice: Double, // 原单价
    val price: Double,         // 实收单价
    val quantity: Int,
    val discountNote: String = "" // 如: "85折" 或 "-￥5"
)