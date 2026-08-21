package com.pos.lite.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pos.lite.App
import com.pos.lite.R
import com.pos.lite.data.Category
import com.pos.lite.data.DiningTable
import com.pos.lite.data.Order
import com.pos.lite.data.OrderItem
import com.pos.lite.data.Product
import com.pos.lite.databinding.ActivityMainBinding
import com.pos.lite.print.PosPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cartList = mutableListOf<CartItemModel>()
    private var selectedCategoryId: Long = -1
    private var currentDiningTable: DiningTable? = null // null 为快餐模式

    data class CartItemModel(val product: Product, var count: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeCategories()
    }

    private fun setupUI() {
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = CartAdapter()

        binding.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvProducts.layoutManager = GridLayoutManager(this, 4)

        val staffName = App.currentStaff?.name ?: "未登录"
        val roleDesc = if (App.currentStaff?.role == "ADMIN") "店长" else "收银员"
        binding.tvCashierInfo.text = "当前员工: $staffName ($roleDesc)"

        // 切换堂食桌台
        binding.btnSwitchTable.setOnClickListener {
            showTableSelectDialog()
        }

        binding.btnClearCart.setOnClickListener {
            cartList.clear()
            updateCartSummary()
        }

        binding.btnPay.setOnClickListener {
            if (cartList.isEmpty()) {
                Toast.makeText(this, "购物车为空，请先选择菜品", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPaymentDialog()
        }

        binding.btnManage.setOnClickListener {
            startActivity(Intent(this, ManageActivity::class.java))
        }

        binding.btnReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            App.currentStaff = null
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun showTableSelectDialog() {
        lifecycleScope.launch {
            val tables = withContext(Dispatchers.IO) {
                App.instance.database.posDao().getAllTables().first()
            }
            val tableNames = mutableListOf("【快餐外带】(无需桌号)")
            tableNames.addAll(tables.map { "${it.name} (${it.capacity}人)" })

            AlertDialog.Builder(this@MainActivity)
                .setTitle("选择就餐模式 / 堂食桌台")
                .setItems(tableNames.toTypedArray()) { _, which ->
                    if (which == 0) {
                        currentDiningTable = null
                        binding.tvCurrentMode.text = "[ 快餐外带 ]"
                    } else {
                        val selectedTable = tables[which - 1]
                        currentDiningTable = selectedTable
                        binding.tvCurrentMode.text = "[ 堂食: ${selectedTable.name} ]"
                    }
                }.show()
        }
    }

    private fun observeCategories() {
        lifecycleScope.launch {
            App.instance.database.posDao().getAllCategories().collectLatest { categories ->
                if (categories.isNotEmpty() && selectedCategoryId == -1L) {
                    selectedCategoryId = categories[0].id
                }
                binding.rvCategories.adapter = CategoryTabAdapter(categories)
                if (selectedCategoryId != -1L) {
                    loadProducts(selectedCategoryId)
                }
            }
        }
    }

    private fun loadProducts(catId: Long) {
        lifecycleScope.launch {
            App.instance.database.posDao().getProductsByCategory(catId).collectLatest { products ->
                binding.rvProducts.adapter = ProductGridAdapter(products)
            }
        }
    }

    private fun addToCart(product: Product) {
        val existing = cartList.find { it.product.id == product.id }
        if (existing != null) {
            existing.count++
        } else {
            cartList.add(CartItemModel(product, 1))
        }
        updateCartSummary()
    }

    private fun updateCartSummary() {
        binding.rvCart.adapter?.notifyDataSetChanged()
        val totalQty = cartList.sumOf { it.count }
        val totalAmount = cartList.sumOf { it.product.price * it.count }
        binding.tvTotalCount.text = "共 $totalQty 件"
        binding.tvTotalAmount.text = String.format("￥%.2f", totalAmount)
    }

    private fun showPaymentDialog() {
        val totalAmount = cartList.sumOf { it.product.price * it.count }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val tableName = currentDiningTable?.name ?: "快餐"
        dialogView.findViewById<TextView>(R.id.tvDialogAmount).text = String.format("[%s] 应收: ￥%.2f", tableName, totalAmount)

        val payActions = mapOf(
            R.id.btnPayCash to "现金支付",
            R.id.btnPayWechat to "微信支付",
            R.id.btnPayAlipay to "支付宝",
            R.id.btnPayCard to "银行卡/记账"
        )

        for ((btnId, payName) in payActions) {
            dialogView.findViewById<Button>(btnId)?.setOnClickListener {
                completeOrder(payName, totalAmount)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun completeOrder(payType: String, totalAmount: Double) {
        val orderNo = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date()) + (100..999).random()
        val tableName = currentDiningTable?.name ?: "快餐"
        val order = Order(
            orderNo = orderNo,
            totalAmount = totalAmount,
            payType = payType,
            cashierName = App.currentStaff?.name ?: "收银员",
            tableId = currentDiningTable?.id ?: 0,
            tableName = tableName
        )

        val items = cartList.map {
            OrderItem(orderId = 0, productId = it.product.id, productName = it.product.name, price = it.product.price, quantity = it.count)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val orderId = dao.insertOrder(order)
            val itemsWithId = items.map { it.copy(orderId = orderId) }
            dao.insertOrderItems(itemsWithId)

            PosPrinterHelper.printReceipt(this@MainActivity, order, itemsWithId)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "收款成功 [$tableName] ￥$totalAmount", Toast.LENGTH_SHORT).show()
                cartList.clear()
                updateCartSummary()
            }
        }
    }

    inner class CategoryTabAdapter(private val list: List<Category>) : RecyclerView.Adapter<CategoryTabAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val btn: Button = v.findViewById(R.id.btnCategory)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.btn.text = item.name
            holder.btn.isSelected = (item.id == selectedCategoryId)
            holder.btn.setOnClickListener {
                selectedCategoryId = item.id
                notifyDataSetChanged()
                loadProducts(selectedCategoryId)
            }
        }
        override fun getItemCount() = list.size
    }

    inner class ProductGridAdapter(private val list: List<Product>) : RecyclerView.Adapter<ProductGridAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvProductName)
            val tvPrice: TextView = v.findViewById(R.id.tvProductPrice)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = list[position]
            holder.tvName.text = p.name
            holder.tvPrice.text = String.format("￥%.2f", p.price)
            holder.itemView.setOnClickListener { addToCart(p) }
        }
        override fun getItemCount() = list.size
    }

    inner class CartAdapter : RecyclerView.Adapter<CartAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvCartItemName)
            val tvPrice: TextView = v.findViewById(R.id.tvCartItemPrice)
            val tvCount: TextView = v.findViewById(R.id.tvCartItemCount)
            val btnPlus: Button = v.findViewById(R.id.btnPlus)
            val btnMinus: Button = v.findViewById(R.id.btnMinus)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = cartList[position]
            holder.tvName.text = item.product.name
            holder.tvPrice.text = String.format("￥%.2f", item.product.price * item.count)
            holder.tvCount.text = "${item.count}"
            holder.btnPlus.setOnClickListener {
                item.count++
                updateCartSummary()
            }
            holder.btnMinus.setOnClickListener {
                item.count--
                if (item.count <= 0) cartList.removeAt(position)
                updateCartSummary()
            }
        }
        override fun getItemCount() = cartList.size
    }
}
