package com.pos.lite.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
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
import com.pos.lite.data.*
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

    private var activeTable: DiningTable? = null
    private var activeOrderId: Long = 0
    private var expandedTableId: Long = -1 // 当前展开操作栏的桌台ID

    data class CartItemModel(val product: Product, var count: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeTables()
        observeCategories()
    }

    private fun setupUI() {
        val staffName = App.currentStaff?.name ?: "未登录"
        val roleDesc = if (App.currentStaff?.role == "ADMIN") "店长" else "收银员"
        binding.tvCashierInfo.text = "员工: $staffName ($roleDesc)"

        binding.rvTableGrid.layoutManager = GridLayoutManager(this, 4)
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = CartAdapter()
        binding.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvProducts.layoutManager = GridLayoutManager(this, 4)

        binding.btnNavTables.setOnClickListener { showTableView() }
        binding.btnNavFastFood.setOnClickListener { openFastFoodOrder() }
        binding.btnBackToTables.setOnClickListener { showTableView() }

        binding.btnClearCart.setOnClickListener {
            cartList.clear()
            updateCartSummary()
        }

        binding.btnSaveTableOrder.setOnClickListener {
            if (activeTable == null) {
                Toast.makeText(this, "快餐模式请直接点击【结账收款】", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartList.isEmpty()) {
                Toast.makeText(this, "请选择菜品后再下单", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveTableOrderAndReturn()
        }

        binding.btnPay.setOnClickListener {
            if (cartList.isEmpty()) {
                Toast.makeText(this, "购物车为空，无法结账", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPaymentDialog()
        }

        binding.btnManage.setOnClickListener { startActivity(Intent(this, ManageActivity::class.java)) }
        binding.btnReport.setOnClickListener { startActivity(Intent(this, ReportActivity::class.java)) }
        binding.btnLogout.setOnClickListener {
            App.currentStaff = null
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun observeTables() {
        lifecycleScope.launch {
            App.instance.database.posDao().getAllTables().collectLatest { tables ->
                binding.rvTableGrid.adapter = TableGridAdapter(tables)
            }
        }
    }

    private fun showTableView() {
        activeTable = null
        activeOrderId = 0
        cartList.clear()
        binding.layoutTableOverview.visibility = View.VISIBLE
        binding.layoutOrderScreen.visibility = View.GONE
        binding.btnNavTables.setBackgroundColor(Color.parseColor("#2C344B"))
        binding.btnNavFastFood.setBackgroundColor(Color.parseColor("#FFFFFF"))
    }

    private fun openFastFoodOrder() {
        activeTable = null
        activeOrderId = 0
        cartList.clear()
        updateCartSummary()
        binding.tvOrderTableTitle.text = "模式: ⚡ 快餐直接收银"
        binding.btnSaveTableOrder.visibility = View.GONE
        binding.layoutTableOverview.visibility = View.GONE
        binding.layoutOrderScreen.visibility = View.VISIBLE
    }

    // 进入开台点餐
    private fun startOrderForTable(table: DiningTable) {
        activeTable = table
        activeOrderId = 0
        cartList.clear()
        updateCartSummary()
        binding.tvOrderTableTitle.text = "桌台: ${table.name} (空闲·新开台)"
        binding.btnSaveTableOrder.visibility = View.VISIBLE
        binding.btnSaveTableOrder.text = "下单开台"
        binding.layoutTableOverview.visibility = View.GONE
        binding.layoutOrderScreen.visibility = View.VISIBLE
    }

    // 读取就餐中订单进行加菜或结账
    private fun loadOccupiedTableOrder(table: DiningTable, onLoaded: (() -> Unit)? = null) {
        activeTable = table
        activeOrderId = table.currentOrderId
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val items = dao.getOrderItems(table.currentOrderId)
            val products = dao.getAllProducts().first()

            withContext(Dispatchers.Main) {
                cartList.clear()
                for (item in items) {
                    val p = products.find { prod: Product -> prod.id == item.productId }
                        ?: Product(id = item.productId, categoryId = 0L, name = item.productName, price = item.price)
                    cartList.add(CartItemModel(p, item.quantity))
                }
                updateCartSummary()
                binding.tvOrderTableTitle.text = "桌台: ${table.name} (就餐中·已点${cartList.sumOf { it.count }}件)"
                binding.btnSaveTableOrder.visibility = View.VISIBLE
                binding.btnSaveTableOrder.text = "加菜入单"
                binding.layoutTableOverview.visibility = View.GONE
                binding.layoutOrderScreen.visibility = View.VISIBLE
                onLoaded?.invoke()
            }
        }
    }

    private fun saveTableOrderAndReturn() {
        val table = activeTable ?: return
        val totalAmount = cartList.sumOf { it.product.price * it.count }

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            var orderId = activeOrderId

            if (orderId == 0L) {
                val orderNo = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date()) + (100..999).random()
                val order = Order(
                    orderNo = orderNo,
                    totalAmount = totalAmount,
                    payType = "挂单未付",
                    cashierName = App.currentStaff?.name ?: "收银员",
                    tableId = table.id,
                    tableName = table.name,
                    status = "UNPAID"
                )
                orderId = dao.insertOrder(order)
            } else {
                val order = dao.getOrderById(orderId)
                if (order != null) {
                    dao.updateOrder(order.copy(totalAmount = totalAmount))
                }
                dao.deleteOrderItemsByOrderId(orderId)
            }

            val items = cartList.map {
                OrderItem(orderId = orderId, productId = it.product.id, productName = it.product.name, price = it.product.price, quantity = it.count)
            }
            dao.insertOrderItems(items)

            table.status = "OCCUPIED"
            table.currentOrderId = orderId
            table.currentAmount = totalAmount
            table.openTime = if (table.openTime == 0L) System.currentTimeMillis() else table.openTime
            dao.updateTable(table)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "桌台 [${table.name}] 挂单成功！总额: ￥$totalAmount", Toast.LENGTH_SHORT).show()
                showTableView()
            }
        }
    }

    private fun showPaymentDialog() {
        val totalAmount = cartList.sumOf { it.product.price * it.count }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val tableName = activeTable?.name ?: "快餐"
        dialogView.findViewById<TextView>(R.id.tvDialogAmount).text = String.format("[%s] 应收: ￥%.2f", tableName, totalAmount)

        val payActions = mapOf(
            R.id.btnPayCash to "现金支付",
            R.id.btnPayWechat to "微信支付",
            R.id.btnPayAlipay to "支付宝",
            R.id.btnPayCard to "银行卡"
        )

        for ((btnId, payName) in payActions) {
            dialogView.findViewById<Button>(btnId)?.setOnClickListener {
                completeOrderAndClearTable(payName, totalAmount)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun completeOrderAndClearTable(payType: String, totalAmount: Double) {
        val table = activeTable
        val tableName = table?.name ?: "快餐"

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val orderNo = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date()) + (100..999).random()

            val order = Order(
                orderNo = orderNo,
                totalAmount = totalAmount,
                payType = payType,
                cashierName = App.currentStaff?.name ?: "收银员",
                tableId = table?.id ?: 0,
                tableName = tableName,
                status = "PAID"
            )
            val orderId = dao.insertOrder(order)
            val items = cartList.map {
                OrderItem(orderId = orderId, productId = it.product.id, productName = it.product.name, price = it.product.price, quantity = it.count)
            }
            dao.insertOrderItems(items)

            if (table != null) {
                table.status = "IDLE"
                table.currentOrderId = 0
                table.currentAmount = 0.0
                table.openTime = 0
                dao.updateTable(table)
            }

            PosPrinterHelper.printReceipt(this@MainActivity, order, items)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "[$tableName] 结账成功！已清台", Toast.LENGTH_SHORT).show()
                if (table != null) {
                    showTableView()
                } else {
                    cartList.clear()
                    updateCartSummary()
                }
            }
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

    // --- 桌台内嵌式操作网格适配器 (点击在卡片内展开操作按钮) ---
    inner class TableGridAdapter(private val list: List<DiningTable>) : RecyclerView.Adapter<TableGridAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: View = v.findViewById(R.id.layoutCardRoot)
            val tvName: TextView = v.findViewById(R.id.tvTableName)
            val tvStatus: TextView = v.findViewById(R.id.tvTableStatus)
            val tvAmount: TextView = v.findViewById(R.id.tvTableAmount)
            val divider: View = v.findViewById(R.id.viewDivider)
            val layoutActions: View = v.findViewById(R.id.layoutActions)
            val btnAction1: Button = v.findViewById(R.id.btnAction1)
            val btnAction2: Button = v.findViewById(R.id.btnAction2)
            val btnAction3: Button = v.findViewById(R.id.btnAction3)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_table_grid, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val table = list[position]
            val isExpanded = (table.id == expandedTableId)

            holder.tvName.text = table.name

            when (table.status) {
                "OCCUPIED" -> {
                    holder.root.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    holder.tvStatus.text = "● 就餐中 (${table.capacity}人)"
                    holder.tvStatus.setTextColor(Color.parseColor("#E65100"))
                    holder.tvAmount.visibility = View.VISIBLE
                    holder.tvAmount.text = String.format("￥%.2f", table.currentAmount)

                    // 展开时设置就餐中的功能按键
                    holder.btnAction1.text = "加菜/查单"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#FF9800"))
                    holder.btnAction1.setOnClickListener { loadOccupiedTableOrder(table) }

                    holder.btnAction2.text = "结账收款"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#4CAF50"))
                    holder.btnAction2.setOnClickListener {
                        loadOccupiedTableOrder(table) { showPaymentDialog() }
                    }

                    holder.btnAction3.visibility = View.VISIBLE
                    holder.btnAction3.text = "清台"
                    holder.btnAction3.setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            table.status = "IDLE"
                            table.currentOrderId = 0
                            table.currentAmount = 0.0
                            App.instance.database.posDao().updateTable(table)
                        }
                    }
                }
                "RESERVED" -> {
                    holder.root.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    holder.tvStatus.text = "● 已预定"
                    holder.tvStatus.setTextColor(Color.parseColor("#1565C0"))
                    holder.tvAmount.visibility = View.GONE

                    holder.btnAction1.text = "到店开台"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#2E7D32"))
                    holder.btnAction1.setOnClickListener {
                        table.status = "IDLE"
                        startOrderForTable(table)
                    }

                    holder.btnAction2.text = "取消预定"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#78909C"))
                    holder.btnAction2.setOnClickListener {
                        table.status = "IDLE"
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().updateTable(table)
                        }
                    }
                    holder.btnAction3.visibility = View.GONE
                }
                else -> { // 空闲
                    holder.root.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    holder.tvStatus.text = "● 空闲 (${table.capacity}人)"
                    holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                    holder.tvAmount.visibility = View.GONE

                    holder.btnAction1.text = "开台点餐"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#2E7D32"))
                    holder.btnAction1.setOnClickListener { startOrderForTable(table) }

                    holder.btnAction2.text = "设为预定"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#1565C0"))
                    holder.btnAction2.setOnClickListener {
                        table.status = "RESERVED"
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().updateTable(table)
                        }
                    }
                    holder.btnAction3.visibility = View.GONE
                }
            }

            // 控制卡片下方操作栏展开/折叠
            holder.divider.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.layoutActions.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // 点击卡片切换展开状态
            holder.itemView.setOnClickListener {
                expandedTableId = if (isExpanded) -1 else table.id
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = list.size
    }

    inner class CategoryTabAdapter(private val list: List<Category>) : RecyclerView.Adapter<CategoryTabAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val btn: Button = v.findViewById(R.id.btnCategory)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
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
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
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
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
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
