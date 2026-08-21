package com.pos.lite.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

    // 整单打折与减免
    private var wholeDiscountRate = 1.0
    private var wholeDiscountDeduct = 0.0
    private var wholeDiscountNote = ""

    data class CartItemModel(
        val product: Product,
        var count: Int,
        var discountRate: Double = 1.0,
        var deductAmount: Double = 0.0,
        var discountNote: String = ""
    ) {
        val singlePrice: Double
            get() = Math.max(0.0, (product.price * discountRate) - deductAmount)
        val subtotal: Double
            get() = singlePrice * count
        val originalSubtotal: Double
            get() = product.price * count
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeTables()
        observeCategories()
    }

    private fun setupUI() {
        val staff = App.currentStaff
        val staffName = staff?.name ?: "未登录"
        val roleDesc = if (staff?.role == "ADMIN") "店长" else "收银员"
        binding.tvCashierInfo.text = "员工: $staffName ($roleDesc)"

        // 核心权限隔离：普通收银员完全隐藏后台管理
        if (staff?.role == "ADMIN") {
            binding.btnManage.visibility = View.VISIBLE
        } else {
            binding.btnManage.visibility = View.GONE
        }

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
            resetWholeDiscount()
            updateCartSummary()
        }

        // 整单打折/优惠
        binding.btnWholeDiscount.setOnClickListener {
            if (cartList.isEmpty()) {
                Toast.makeText(this, "购物车为空，无法打折", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showWholeDiscountDialog()
        }

        binding.btnSaveTableOrder.setOnClickListener {
            if (activeTable == null) {
                Toast.makeText(this, "快餐模式请直接点击【结账收款】", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartList.isEmpty()) {
                Toast.makeText(this, "请先添加菜品再下单", Toast.LENGTH_SHORT).show()
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

    private fun resetWholeDiscount() {
        wholeDiscountRate = 1.0
        wholeDiscountDeduct = 0.0
        wholeDiscountNote = ""
    }

    private fun showWholeDiscountDialog() {
        val options = arrayOf("全单 95 折", "全单 9 折", "全单 88 折", "全单 85 折", "全单 8 折", "自定义打折/立减", "恢复原价 (无折扣)")
        AlertDialog.Builder(this)
            .setTitle("选择整单优惠方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setWholeDiscount(0.95, 0.0, "全单95折")
                    1 -> setWholeDiscount(0.90, 0.0, "全单9折")
                    2 -> setWholeDiscount(0.88, 0.0, "全单88折")
                    3 -> setWholeDiscount(0.85, 0.0, "全单85折")
                    4 -> setWholeDiscount(0.80, 0.0, "全单8折")
                    5 -> showCustomWholeDiscountDialog()
                    6 -> resetWholeDiscount()
                }
                updateCartSummary()
            }.show()
    }

    private fun setWholeDiscount(rate: Double, deduct: Double, note: String) {
        wholeDiscountRate = rate
        wholeDiscountDeduct = deduct
        wholeDiscountNote = note
    }

    private fun showCustomWholeDiscountDialog() {
        val etRate = EditText(this).apply {
            hint = "打折比例 (如: 0.88 表示88折, 留空不打折)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etDeduct = EditText(this).apply {
            hint = "立减/抹零金额 (元，如: 10 表示立减10元)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(etRate)
            addView(etDeduct)
        }

        AlertDialog.Builder(this)
            .setTitle("自定义整单打折与立减")
            .setView(layout)
            .setPositiveButton("应用") { _, _ ->
                val rateInput = etRate.text.toString().trim().toDoubleOrNull()
                val deductInput = etDeduct.text.toString().trim().toDoubleOrNull() ?: 0.0

                val rate = if (rateInput != null && rateInput in 0.01..1.0) rateInput else 1.0
                var note = ""
                if (rate < 1.0) note += "${(rate * 10).toInt()}折 "
                if (deductInput > 0) note += "立减￥$deductInput"

                setWholeDiscount(rate, deductInput, note)
                updateCartSummary()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showItemDiscountDialog(item: CartItemModel) {
        val options = arrayOf("单品 9 折", "单品 85 折", "单品 8 折", "单品半价 (5折)", "单品立减金额...", "恢复单品原价")
        AlertDialog.Builder(this)
            .setTitle("【${item.product.name}】单品优惠")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { item.discountRate = 0.9; item.deductAmount = 0.0; item.discountNote = "9折" }
                    1 -> { item.discountRate = 0.85; item.deductAmount = 0.0; item.discountNote = "85折" }
                    2 -> { item.discountRate = 0.8; item.deductAmount = 0.0; item.discountNote = "8折" }
                    3 -> { item.discountRate = 0.5; item.deductAmount = 0.0; item.discountNote = "半价" }
                    4 -> showCustomItemDeductDialog(item)
                    5 -> { item.discountRate = 1.0; item.deductAmount = 0.0; item.discountNote = "" }
                }
                updateCartSummary()
            }.show()
    }

    private fun showCustomItemDeductDialog(item: CartItemModel) {
        val et = EditText(this).apply {
            hint = "每件立减金额 (元，例如: 5)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle("【${item.product.name}】单品立减")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val deduct = et.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (deduct > 0) {
                    item.discountRate = 1.0
                    item.deductAmount = deduct
                    item.discountNote = "减￥$deduct"
                }
                updateCartSummary()
            }.setNegativeButton("取消", null).show()
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
        resetWholeDiscount()
        binding.layoutTableOverview.visibility = View.VISIBLE
        binding.layoutOrderScreen.visibility = View.GONE
        binding.btnNavTables.setBackgroundColor(Color.parseColor("#1E2433"))
        binding.btnNavFastFood.setBackgroundColor(Color.parseColor("#FFFFFF"))
    }

    private fun openFastFoodOrder() {
        activeTable = null
        activeOrderId = 0
        cartList.clear()
        resetWholeDiscount()
        updateCartSummary()
        binding.tvOrderTableTitle.text = "模式: ⚡ 快餐直接收银"
        binding.btnSaveTableOrder.visibility = View.GONE
        binding.layoutTableOverview.visibility = View.GONE
        binding.layoutOrderScreen.visibility = View.VISIBLE
        binding.btnNavFastFood.setBackgroundColor(Color.parseColor("#1E2433"))
        binding.btnNavTables.setBackgroundColor(Color.parseColor("#FFFFFF"))
    }

    private fun startOrderForTable(table: DiningTable) {
        activeTable = table
        activeOrderId = 0
        cartList.clear()
        resetWholeDiscount()
        updateCartSummary()
        binding.tvOrderTableTitle.text = "桌台: ${table.name} (新开台)"
        binding.btnSaveTableOrder.visibility = View.VISIBLE
        binding.btnSaveTableOrder.text = "下单挂单"
        binding.layoutTableOverview.visibility = View.GONE
        binding.layoutOrderScreen.visibility = View.VISIBLE
    }

    private fun loadOccupiedTableOrder(table: DiningTable, onLoaded: (() -> Unit)? = null) {
        activeTable = table
        activeOrderId = table.currentOrderId
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val items = dao.getOrderItems(table.currentOrderId)
            val products = dao.getAllProducts().first()

            withContext(Dispatchers.Main) {
                cartList.clear()
                resetWholeDiscount()
                for (item in items) {
                    val p = products.find { prod -> prod.id == item.productId }
                        ?: Product(id = item.productId, categoryId = 0L, name = item.productName, price = item.originalPrice)
                    val discountRate = if (item.originalPrice > 0) item.price / item.originalPrice else 1.0
                    cartList.add(CartItemModel(p, item.quantity, discountRate = discountRate, discountNote = item.discountNote))
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
        val finalAmount = calculateFinalAmount()
        val originalAmount = cartList.sumOf { it.originalSubtotal }
        val discountAmount = Math.max(0.0, originalAmount - finalAmount)

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            var orderId = activeOrderId

            if (orderId == 0L) {
                val orderNo = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date()) + (100..999).random()
                val order = Order(
                    orderNo = orderNo,
                    originalAmount = originalAmount,
                    discountAmount = discountAmount,
                    totalAmount = finalAmount,
                    payType = "挂单未付",
                    cashierName = App.currentStaff?.name ?: "收银员",
                    tableId = table.id,
                    tableName = table.name,
                    status = "UNPAID",
                    discountNote = wholeDiscountNote
                )
                orderId = dao.insertOrder(order)
            } else {
                val order = dao.getOrderById(orderId)
                if (order != null) {
                    dao.updateOrder(order.copy(originalAmount = originalAmount, discountAmount = discountAmount, totalAmount = finalAmount, discountNote = wholeDiscountNote))
                }
                dao.deleteOrderItemsByOrderId(orderId)
            }

            val items = cartList.map {
                OrderItem(
                    orderId = orderId,
                    productId = it.product.id,
                    productName = it.product.name,
                    originalPrice = it.product.price,
                    price = it.singlePrice,
                    quantity = it.count,
                    discountNote = it.discountNote
                )
            }
            dao.insertOrderItems(items)

            table.status = "OCCUPIED"
            table.currentOrderId = orderId
            table.currentAmount = finalAmount
            table.openTime = if (table.openTime == 0L) System.currentTimeMillis() else table.openTime
            dao.updateTable(table)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "桌台 [${table.name}] 挂单成功！总额: ￥${String.format("%.2f", finalAmount)}", Toast.LENGTH_SHORT).show()
                showTableView()
            }
        }
    }

    private fun calculateFinalAmount(): Double {
        val sumAfterItemDiscount = cartList.sumOf { it.subtotal }
        val finalAfterWhole = (sumAfterItemDiscount * wholeDiscountRate) - wholeDiscountDeduct
        return Math.max(0.0, finalAfterWhole)
    }

    private fun showPaymentDialog() {
        val finalAmount = calculateFinalAmount()
        val originalAmount = cartList.sumOf { it.originalSubtotal }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val tableName = activeTable?.name ?: "快餐"
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvDialogAmount)
        if (originalAmount > finalAmount) {
            tvAmount.text = String.format("[%s] 应收: ￥%.2f (原价￥%.2f 让利￥%.2f)", tableName, finalAmount, originalAmount, originalAmount - finalAmount)
        } else {
            tvAmount.text = String.format("[%s] 应收: ￥%.2f", tableName, finalAmount)
        }

        val payActions = mapOf(
            R.id.btnPayCash to "现金支付",
            R.id.btnPayWechat to "微信支付",
            R.id.btnPayAlipay to "支付宝",
            R.id.btnPayCard to "银行卡"
        )

        for ((btnId, payName) in payActions) {
            dialogView.findViewById<Button>(btnId)?.setOnClickListener {
                completeOrderAndClearTable(payName, finalAmount, originalAmount)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun completeOrderAndClearTable(payType: String, totalAmount: Double, originalAmount: Double) {
        val table = activeTable
        val tableName = table?.name ?: "快餐"
        val discountAmount = Math.max(0.0, originalAmount - totalAmount)

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            val orderNo = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date()) + (100..999).random()

            val order = Order(
                orderNo = orderNo,
                originalAmount = originalAmount,
                discountAmount = discountAmount,
                totalAmount = totalAmount,
                payType = payType,
                cashierName = App.currentStaff?.name ?: "收银员",
                tableId = table?.id ?: 0,
                tableName = tableName,
                status = "PAID",
                discountNote = wholeDiscountNote
            )
            val orderId = dao.insertOrder(order)
            val items = cartList.map {
                OrderItem(
                    orderId = orderId,
                    productId = it.product.id,
                    productName = it.product.name,
                    originalPrice = it.product.price,
                    price = it.singlePrice,
                    quantity = it.count,
                    discountNote = it.discountNote
                )
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
                Toast.makeText(this@MainActivity, "[$tableName] 收款 ￥${String.format("%.2f", totalAmount)} 成功！已清台", Toast.LENGTH_SHORT).show()
                if (table != null) {
                    showTableView()
                } else {
                    cartList.clear()
                    resetWholeDiscount()
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
        val originalTotal = cartList.sumOf { it.originalSubtotal }
        val finalAmount = calculateFinalAmount()
        val totalDiscount = Math.max(0.0, originalTotal - finalAmount)

        binding.tvTotalCount.text = "共 $totalQty 件"
        binding.tvTotalAmount.text = String.format("￥%.2f", finalAmount)

        if (totalDiscount > 0.0) {
            binding.tvOriginalAmount.visibility = View.VISIBLE
            binding.tvOriginalAmount.text = String.format("原价:￥%.2f", originalTotal)
            binding.tvOriginalAmount.paintFlags = binding.tvOriginalAmount.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.tvDiscountInfo.text = "已优惠 ￥${String.format("%.2f", totalDiscount)} ($wholeDiscountNote)"
        } else {
            binding.tvOriginalAmount.visibility = View.GONE
            binding.tvDiscountInfo.text = ""
        }
    }

    // --- 桌台大厅适配器 ---
    inner class TableGridAdapter(private val list: List<DiningTable>) : RecyclerView.Adapter<TableGridAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: View = v.findViewById(R.id.layoutCardRoot)
            val tvName: TextView = v.findViewById(R.id.tvTableName)
            val tvCapacity: TextView = v.findViewById(R.id.tvTableCapacity)
            val tvStatusBadge: TextView = v.findViewById(R.id.tvStatusBadge)
            val tvTableInfo: TextView = v.findViewById(R.id.tvTableInfo)
            val tvTableAmount: TextView = v.findViewById(R.id.tvTableAmount)
            val btnAction1: Button = v.findViewById(R.id.btnCardAction1)
            val btnAction2: Button = v.findViewById(R.id.btnCardAction2)
            val btnAction3: Button = v.findViewById(R.id.btnCardAction3)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_table_grid, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val table = list[position]
            holder.tvName.text = table.name
            holder.tvCapacity.text = "(${table.capacity}人桌)"

            when (table.status) {
                "OCCUPIED" -> {
                    holder.root.setBackgroundColor(Color.parseColor("#FEF3C7"))
                    holder.tvStatusBadge.text = "🟠 就餐中"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#D97706"))
                    holder.tvTableInfo.text = "当前消费金额："
                    holder.tvTableAmount.visibility = View.VISIBLE
                    holder.tvTableAmount.text = String.format("￥%.2f", table.currentAmount)

                    holder.btnAction1.text = "➕ 加菜/查单"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#F59E0B"))
                    holder.btnAction1.setOnClickListener { loadOccupiedTableOrder(table) }

                    holder.btnAction2.text = "💰 结账收款"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#10B981"))
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
                    holder.root.setBackgroundColor(Color.parseColor("#EFF6FF"))
                    holder.tvStatusBadge.text = "🔵 已预定"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#2563EB"))
                    holder.tvTableInfo.text = "客户预定中"
                    holder.tvTableAmount.visibility = View.GONE

                    holder.btnAction1.text = "▶ 到店开台"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#059669"))
                    holder.btnAction1.setOnClickListener {
                        table.status = "IDLE"
                        startOrderForTable(table)
                    }

                    holder.btnAction2.text = "✖ 取消预定"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#6B7280"))
                    holder.btnAction2.setOnClickListener {
                        table.status = "IDLE"
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().updateTable(table)
                        }
                    }
                    holder.btnAction3.visibility = View.GONE
                }
                else -> { // 空闲
                    holder.root.setBackgroundColor(Color.parseColor("#E6F9F0"))
                    holder.tvStatusBadge.text = "🟢 空闲"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#059669"))
                    holder.tvTableInfo.text = "桌位空闲"
                    holder.tvTableAmount.visibility = View.GONE

                    holder.btnAction1.text = "🍽 开台点餐"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#059669"))
                    holder.btnAction1.setOnClickListener { startOrderForTable(table) }

                    holder.btnAction2.text = "📅 设为预定"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#2563EB"))
                    holder.btnAction2.setOnClickListener {
                        table.status = "RESERVED"
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().updateTable(table)
                        }
                    }
                    holder.btnAction3.visibility = View.GONE
                }
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
            val tvDiscountBtn: TextView = v.findViewById(R.id.tvItemDiscountBtn)
            val tvDiscountNote: TextView = v.findViewById(R.id.tvItemDiscountNote)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = cartList[position]
            holder.tvName.text = item.product.name
            holder.tvPrice.text = String.format("￥%.2f", item.subtotal)
            holder.tvCount.text = "${item.count}"

            if (item.discountNote.isNotEmpty()) {
                holder.tvDiscountNote.visibility = View.VISIBLE
                holder.tvDiscountNote.text = "单品优惠: ${item.discountNote}"
            } else {
                holder.tvDiscountNote.visibility = View.GONE
            }

            holder.tvDiscountBtn.setOnClickListener { showItemDiscountDialog(item) }

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