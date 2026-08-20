package com.pos.lite.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pos.lite.App
import com.pos.lite.databinding.ActivityReportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        loadTodaySummary()
    }

    private fun loadTodaySummary() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startOfDay = cal.timeInMillis
        val endOfDay = System.currentTimeMillis()

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val orders = dao.getOrdersByDateRange(startOfDay, endOfDay)
            val totalSales = dao.getTotalSales(startOfDay, endOfDay) ?: 0.0

            withContext(Dispatchers.Main) {
                binding.tvReportDate.text = "日期: " + SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                binding.tvTotalOrders.text = "今日有效订单数: ${orders.size} 笔"
                binding.tvTotalSales.text = String.format("营业总额: ￥%.2f", totalSales)

                val payMap = orders.groupBy { it.payType }
                val sb = StringBuilder()
                for ((payType, list) in payMap) {
                    val sum = list.sumOf { it.totalAmount }
                    sb.append("$payType: ${list.size} 笔，合计: ￥${String.format("%.2f", sum)}\n")
                }
                binding.tvPaySummary.text = sb.toString()
            }
        }
    }
}
