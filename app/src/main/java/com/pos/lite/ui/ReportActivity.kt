package com.pos.lite.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pos.lite.App
import com.pos.lite.R
import com.pos.lite.data.Order
import com.pos.lite.databinding.ActivityReportBinding
import com.pos.lite.print.PosPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private var currentFilter = "TODAY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvOrderHistory.layoutManager = LinearLayoutManager(this)
        binding.rvOrderHistory.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        binding.btnBack.setOnClickListener { finish() }

        binding.btnFilterToday.setOnClickListener {
            currentFilter = "TODAY"
            updateFilterButtons()
            loadReportData()
        }
        binding.btnFilterMonth.setOnClickListener {
            currentFilter = "MONTH"
            updateFilterButtons()
            loadReportData()
        }
        binding.btnFilterYear.setOnClickListener {
            currentFilter = "YEAR"
            updateFilterButtons()
            loadReportData()
        }

        updateFilterButtons()
        loadReportData()
    }

    private fun updateFilterButtons() {
        binding.btnFilterToday.setBackgroundColor(if (currentFilter == "TODAY") Color.parseColor("#1E2433") else Color.parseColor("#E5E7EB"))
        binding.btnFilterToday.setTextColor(if (currentFilter == "TODAY") Color.WHITE else Color.parseColor("#111827"))

        binding.btnFilterMonth.setBackgroundColor(if (currentFilter == "MONTH") Color.parseColor("#1E2433") else Color.parseColor("#E5E7EB"))
        binding.btnFilterMonth.setTextColor(if (currentFilter == "MONTH") Color.WHITE else Color.parseColor("#111827"))

        binding.btnFilterYear.setBackgroundColor(if (currentFilter == "YEAR") Color.parseColor("#1E2433") else Color.parseColor("#E5E7EB"))
        binding.btnFilterYear.setTextColor(if (currentFilter == "YEAR") Color.WHITE else Color.parseColor("#111827"))
    }

    private fun loadReportData() {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val startTime: Long

        when (currentFilter) {
            "TODAY" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                startTime = cal.timeInMillis
                binding.tvReportPeriod.text = "统计周期: 今日 (" + SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()) + ")"
            }
            "MONTH" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                startTime = cal.timeInMillis
                binding.tvReportPeriod.text = "统计周期: 本月 (" + SimpleDateFormat("yyyy年MM月", Locale.CHINA).format(Date()) + ")"
            }
            else -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                startTime = cal.timeInMillis
                binding.tvReportPeriod.text = "统计周期: 本年 (" + SimpleDateFormat("yyyy年", Locale.CHINA).format(Date()) + ")"
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val orders = dao.getPaidOrdersByRange(startTime, now)

            val totalSales = orders.sumOf { it.totalAmount }
            val totalOriginal = orders.sumOf { it.originalAmount }
            val totalDiscount = orders.sumOf { it.discountAmount }
            val orderCount = orders.size
            val avgPerOrder = if (orderCount > 0) totalSales / orderCount else 0.0

            val cashierMap = orders.groupBy { it.cashierName }
            val cashierSb = StringBuilder()
            for ((name, list) in cashierMap) {
                val sum = list.sumOf { it.totalAmount }
                cashierSb.append("• $name: ${list.size} 笔 (实收 ￥${String.format("%.2f", sum)})\n")
            }

            val tableOrders = orders.filter { it.tableId > 0 }
            val fastFoodOrders = orders.filter { it.tableId == 0L }

            val payMap = orders.groupBy { it.payType }
            val paySb = StringBuilder()
            for ((payType, list) in payMap) {
                val sum = list.sumOf { it.totalAmount }
                paySb.append("• $payType: ${list.size} 笔 (￥${String.format("%.2f", sum)})\n")
            }

            withContext(Dispatchers.Main) {
                binding.tvTotalSales.text = String.format("￥%.2f", totalSales)
                binding.tvOriginalAndDiscount.text = String.format("原价总额: ￥%.2f | 优惠让利: ￥%.2f", totalOriginal, totalDiscount)
                binding.tvOrderCountAndAvg.text = "有效单量: $orderCount 笔 | 客单价: ￥${String.format("%.2f", avgPerOrder)}"

                binding.tvCashierDistribution.text = if (cashierSb.isNotEmpty()) cashierSb.toString().trim() else "暂无收银数据"

                binding.tvTypeDistribution.text = "• 🪑 堂食桌台: ${tableOrders.size} 笔 (￥${String.format("%.2f", tableOrders.sumOf { it.totalAmount })})\n• ⚡ 快餐外带: ${fastFoodOrders.size} 笔 (￥${String.format("%.2f", fastFoodOrders.sumOf { it.totalAmount })})"
                binding.tvPayDistribution.text = if (paySb.isNotEmpty()) paySb.toString().trim() else "暂无支付明细"

                binding.rvOrderHistory.adapter = OrderHistoryAdapter(orders)
            }
        }
    }

    private fun showOrderDetailDialog(order: Order) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val items = dao.getOrderItems(order.orderId)

            withContext(Dispatchers.Main) {
                val sb = StringBuilder()
                sb.append("单号: ${order.orderNo}\n")
                sb.append("桌号/场景: ${order.tableName}\n")
                sb.append("收款账号: ${order.cashierName}\n")
                sb.append("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(order.timestamp))}\n")
                sb.append("支付方式: ${order.payType}\n")
                if (order.discountNote.isNotEmpty()) sb.append("优惠说明: ${order.discountNote}\n")
                sb.append("--------------------------------\n")
                sb.append(String.format("%-12s %-4s %-6s\n", "菜品", "数量", "金额"))
                for (it in items) {
                    val note = if (it.discountNote.isNotEmpty()) "(${it.discountNote})" else ""
                    sb.append(String.format("%-12s %-4d ￥%-6.2f %s\n", it.productName, it.quantity, it.price * it.quantity, note))
                }
                sb.append("--------------------------------\n")
                sb.append(String.format("原价总计: ￥%.2f\n", order.originalAmount))
                sb.append(String.format("优惠减免: ￥%.2f\n", order.discountAmount))
                sb.append(String.format("实收金额: ￥%.2f\n", order.totalAmount))

                AlertDialog.Builder(this@ReportActivity)
                    .setTitle("单据小票明细 (收款人: ${order.cashierName})")
                    .setMessage(sb.toString())
                    .setPositiveButton("补打小票") { _, _ ->
                        PosPrinterHelper.printReceipt(this@ReportActivity, order, items)
                    }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }

    inner class OrderHistoryAdapter(private val list: List<Order>) : RecyclerView.Adapter<OrderHistoryAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTable: TextView = v.findViewById(R.id.tvOrderTable)
            val tvPayType: TextView = v.findViewById(R.id.tvOrderPayType)
            val tvCashierBadge: TextView = v.findViewById(R.id.tvOrderCashierBadge)
            val tvDiscountBadge: TextView = v.findViewById(R.id.tvOrderDiscountBadge)
            val tvNoAndTime: TextView = v.findViewById(R.id.tvOrderNoAndTime)
            val tvAmount: TextView = v.findViewById(R.id.tvOrderAmount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_order_history, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val order = list[position]
            holder.tvTable.text = order.tableName
            holder.tvPayType.text = "[${order.payType}]"
            holder.tvCashierBadge.text = "收款: ${order.cashierName}"
            holder.tvAmount.text = String.format("￥%.2f", order.totalAmount)

            if (order.discountAmount > 0) {
                holder.tvDiscountBadge.visibility = View.VISIBLE
                holder.tvDiscountBadge.text = if (order.discountNote.isNotEmpty()) order.discountNote else "优惠￥${order.discountAmount}"
            } else {
                holder.tvDiscountBadge.visibility = View.GONE
            }

            val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(order.timestamp))
            holder.tvNoAndTime.text = "时间: $timeStr | 单号: ${order.orderNo}"

            holder.itemView.setOnClickListener { showOrderDetailDialog(order) }
        }
        override fun getItemCount() = list.size
    }
}