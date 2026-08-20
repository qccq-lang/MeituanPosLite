package com.pos.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dining_tables")
data class DiningTable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val capacity: Int = 4,
    var status: String = "IDLE",
    var currentOrderId: Long = 0
)
