package com.pos.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dining_tables")
data class DiningTable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val capacity: Int = 4,
    var status: String = "IDLE",     // "IDLE"(空闲), "OCCUPIED"(就餐中), "RESERVED"(已预定)
    var currentOrderId: Long = 0,    // 挂单ID
    var currentAmount: Double = 0.0, // 当前消费总金额
    var openTime: Long = 0           // 开台时间
)
