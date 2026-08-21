package com.pos.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val orderId: Long = 0,
    val orderNo: String,
    val originalAmount: Double = 0.0, // 原价总额
    val discountAmount: Double = 0.0, // 优惠让利金额
    val totalAmount: Double,          // 实收金额
    val payType: String,              // 支付方式
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "收银员",
    val tableId: Long = 0,
    val tableName: String = "快餐",    // 桌台名称或快餐
    val status: String = "PAID",      // PAID / UNPAID
    val discountNote: String = ""      // 优惠备注 (如: 全单9折 / 立减￥10)
)