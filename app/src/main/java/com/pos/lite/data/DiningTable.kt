package com.pos.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dining_tables")
data class DiningTable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val area: String = "大厅",     // "大厅", "包厢", "露台", "卡座"
    val capacity: Int = 4,
    var status: String = "IDLE",   // "IDLE"(空闲), "OCCUPIED"(就餐中), "RESERVED"(已预定)
    var currentOrderId: Long = 0,
    var currentAmount: Double = 0.0,
    var openTime: Long = 0
)