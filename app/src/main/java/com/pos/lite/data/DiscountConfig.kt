package com.pos.lite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discount_configs")
data class DiscountConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,        // 按钮显示名称，如: "9折", "88折", "立减10元", "自动抹零"
    val type: String,        // "RATE"(比例打折,如0.9), "DEDUCT"(固定立减,如10.0), "MOLING"(抹零)
    val value: Double = 0.0, // 折扣值 (0.9 或 10.0)
    val sortOrder: Int = 0
)