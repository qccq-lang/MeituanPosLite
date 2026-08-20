package com.pos.lite.print

import android.content.Context
import android.util.Log
import com.pos.lite.data.Order
import com.pos.lite.data.OrderItem
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object PosPrinterHelper {
    fun printReceipt(context: Context, order: Order, items: List<OrderItem>, os: OutputStream? = null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val timeStr = sdf.format(Date(order.timestamp))

        val sb = StringBuilder()
        sb.append("================================\n")
        sb.append("         美团收银小票           \n")
        sb.append("================================\n")
        sb.append("单号: ${order.orderNo}\n")
        sb.append("桌台/类型: ${order.tableName}\n")
        sb.append("时间: $timeStr\n")
        sb.append("收银员: ${order.cashierName}\n")
        sb.append("--------------------------------\n")
        sb.append(String.format("%-14s %-4s %-6s\n", "商品", "数量", "金额"))
        for (item in items) {
            val name = if (item.productName.length > 7) item.productName.substring(0, 7) else item.productName
            sb.append(String.format("%-14s %-4d %-6.2f\n", name, item.quantity, item.price * item.quantity))
        }
        sb.append("--------------------------------\n")
        sb.append(String.format("总计: ￥%.2f\n", order.totalAmount))
        sb.append("支付方式: ${order.payType}\n")
        sb.append("================================\n")
        sb.append("      谢谢惠顾，欢迎再次光临     \n\n\n\n")

        Log.d("PosPrinter", "\n" + sb.toString())
    }
}
