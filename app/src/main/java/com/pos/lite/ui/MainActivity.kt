package com.pos.lite.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
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
import com.pos.lite.utils.ImageUtil
import com.pos.lite.utils.LicenseGuard
import com.pos.lite.utils.PinyinUtil
import com.pos.lite.utils.PrinterSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cartList = mutableListOf<CartItemModel>()

    private var allProductsList = listOf<Product>()
    private var selectedCategoryId: Long = -1L
    private var searchKeyword: String = ""

    private var activeTable: DiningTable? = null
    private var activeOrderId: Long = 0
    private var isReservingMode: Boolean = false

    private var wholeDiscountRate = 1.0
    private var wholeDiscountDeduct = 0.0
    private var wholeDiscountNote = ""
    private var isAutoMoling = false

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
        if (LicenseGuard.verifyOrHalt(this)) return

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeTables()
        observeCategories()
        observeProducts()
    }

    override fun onResume() {
        super.onResume()
        if (LicenseGuard.verifyOrHalt(this)) return
    }

    private fun setupUI() {
        val staff = App.currentStaff
        val staffName = staff?.name ?: "未登录"
        val roleDesc = if (staff?.role == "ADMIN") "店长" else "收银员"
        binding.tvCashierInfo.text = "员工: $staffName ($roleDesc)"

        if (staff?.role == "ADMIN") {
            binding.btnManage.visibility = View.VISIBLE
            binding.btnReport.visibility = View.VISIBLE
        } else {
            binding.btnManage.visibility = View.GONE
            binding.btnReport.visibility = View.GONE
        }

        binding.rvTableGrid.layoutManager = GridLayoutManager(this, 4)
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = CartAdapter()
        binding.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvProducts.layoutManager = GridLayoutManager(this, 4)

        binding.btnNavTables.setOnClickListener { showTableView() }
        binding.btnNavFastFood.setOnClickListener { openFastFoodOrder() }
        binding.btnBackToTables.setOnClickListener { showTableView() }
        updateNavModeButtons(isTableMode = true)

        binding.cbAutoMoling.setOnCheckedChangeListener { _, isChecked ->
            isAutoMoling = isChecked
            updateCartSummary()
        }

        binding.etSearchDish.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchKeyword = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (searchKeyword.isNotEmpty()) View.VISIBLE else View.GONE
                applyProductFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener { binding.etSearchDish.setText("") }

        binding.btnClearCart.setOnClickListener {
            cartList.clear()
            resetWholeDiscount()
            updateCartSummary()
        }

        binding.btnWholeDiscount.setOnClickListener {
            if (cartList.isEmpty()) {
                Toast.makeText(this, "购物车为空，无法打折", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDiscountButtonGridDialog(isWholeOrder = true, targetItem = null)
        }

        binding.btnSaveTableOrder.setOnClickListener {
            if (activeTable == null) {
                Toast.makeText(this, "快餐模式请直接点击【结账收款】", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartList.isEmpty()) {
                Toast.makeText(this, "请先添加菜品再保存", Toast.LENGTH_SHORT).show()
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

    private fun updateNavModeButtons(isTableMode: Boolean) {
        if (isTableMode) {
            binding.btnNavTables.setBackgroundColor(Color.parseColor("#1E2433"))
            binding.btnNavTables.setTextColor(Color.parseColor("#FFFFFF"))
            binding.btnNavFastFood.setBackgroundColor(Color.parseColor("#FFFFFF"))
            binding.btnNavFastFood.setTextColor(Color.parseColor("#1E2433"))
        } else {
            binding.btnNavTables.setBackgroundColor(Color.parseColor("#FFFFFF"))
            binding.btnNavTables.setTextColor(Color.parseColor("#1E2433"))
            binding.btnNavFastFood.setBackgroundColor(Color.parseColor("#1E2433"))
            binding.btnNavFastFood.setTextColor(Color.parseColor("#FFFFFF"))
        }
    }

    private fun showTableView() {
        activeTable = null
        activeOrderId = 0
        isReservingMode = false
        cartList.clear()
        resetWholeDiscount()
        updateNavModeButtons(isTableMode = true)
        binding.layoutTableOverview.visibility = View.VISIBLE
        binding.layoutOrderScreen.visibility = View.GONE
    }

    private fun openFastFoodOrder() {
        activeTable = null
        activeOrderId = 0
        isReservingMode = false
        cartList.clear()
        resetWholeDiscount()
        updateCartSummary()
        updateNavModeButtons(isTableMode = false)
        binding.tvOrderTableTitle.text = "模式: ⚡ 快餐直接收银"
        binding.btnSaveTableOrder.visibility = View.GONE
        binding.layoutTableOverview.visibility = View.GONE
        binding.layoutOrderScreen.visibility = View.VISIBLE
    }

    private fun resetWholeDiscount() {
        wholeDiscountRate = 1.0
        wholeDiscountDeduct = 0.0
        wholeDiscountNote = ""
    }

    private fun showDiscountButtonGridDialog(isWholeOrder: Boolean, targetItem: CartItemModel?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            var configs = dao.getDiscountConfigsList()

            if (configs.isEmpty()) {
                configs = listOf(
                    DiscountConfig(name = "95折", type = "RATE", value = 0.95),
                    DiscountConfig(name = "9折", type = "RATE", value = 0.90),
                    DiscountConfig(name = "88折", type = "RATE", value = 0.88),
                    DiscountConfig(name = "85折", type = "RATE", value = 0.85),
                    DiscountConfig(name = "8折", type = "RATE", value = 0.80),
                    DiscountConfig(name = "75折", type = "RATE", value = 0.75),
                    DiscountConfig(name = "立减￥5", type = "DEDUCT", value = 5.0),
                    DiscountConfig(name = "立减￥10", type = "DEDUCT", value = 10.0),
                    DiscountConfig(name = "立减￥20", type = "DEDUCT", value = 20.0)
                )
            }

            withContext(Dispatchers.Main) {
                val dialogView = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(40, 24, 40, 24)
                }

                val title = if (isWholeOrder) "🏷 选择整单优惠打折" else "🏷 【${targetItem?.product?.name}】单品优惠"
                val tvTip = TextView(this@MainActivity).apply {
                    text = "点击下方快捷按钮立即应用优惠折扣："
                    textSize = 14f
                    setTextColor(Color.parseColor("#4B5563"))
                }
                dialogView.addView(tvTip)

                val grid = GridLayout(this@MainActivity).apply {
                    columnCount = 3
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 16
                        bottomMargin = 16
                    }
                }

                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setView(dialogView)
                    .setNegativeButton("关闭", null)
                    .create()

                for (cfg in configs) {
                    val btn = Button(this@MainActivity).apply {
                        text = cfg.name
                        textSize = 15f
                        setTextColor(Color.parseColor("#1E2433"))
                        setBackgroundColor(Color.parseColor("#F3F4F6"))
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                            setMargins(8, 8, 8, 8)
                            height = 110
                        }

                        setOnClickListener {
                            applyDiscountConfig(cfg, isWholeOrder, targetItem)
                            dialog.dismiss()
                        }
                    }
                    grid.addView(btn)
                }
                dialogView.addView(grid)

                val bottomActions = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val btnCustom = Button(this@MainActivity).apply {
                    text = "✏ 自定义减免..."
                    textSize = 13f
                    setTextColor(Color.parseColor("#2563EB"))
                    setBackgroundColor(Color.parseColor("#EFF6FF"))
                    layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { marginEnd = 8 }
                    setOnClickListener {
                        dialog.dismiss()
                        showCustomInputDiscountDialog(isWholeOrder, targetItem)
                    }
                }

                val btnReset = Button(this@MainActivity).apply {
                    text = "🔄 恢复原价"
                    textSize = 13f
                    setTextColor(Color.parseColor("#EF4444"))
                    setBackgroundColor(Color.parseColor("#FEE2E2"))
                    layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
                    setOnClickListener {
                        if (isWholeOrder) resetWholeDiscount()
                        else {
                            targetItem?.discountRate = 1.0
                            targetItem?.deductAmount = 0.0
                            targetItem?.discountNote = ""
                        }
                        updateCartSummary()
                        dialog.dismiss()
                    }
                }

                bottomActions.addView(btnCustom)
                bottomActions.addView(btnReset)
                dialogView.addView(bottomActions)

                dialog.show()
            }
        }
    }

    private fun showCustomInputDiscountDialog(isWholeOrder: Boolean, targetItem: CartItemModel?) {
        val et = EditText(this).apply {
            hint = "输入立减金额 (元，例如: 15)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle(if (isWholeOrder) "自定义整单立减" else "【${targetItem?.product?.name}】单品立减")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val deduct = et.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (deduct > 0) {
                    if (isWholeOrder) {
                        wholeDiscountRate = 1.0
                        wholeDiscountDeduct = deduct
                        wholeDiscountNote = "立减￥$deduct"
                    } else {
                        targetItem?.discountRate = 1.0
                        targetItem?.deductAmount = deduct
                        targetItem?.discountNote = "减￥$deduct"
                    }
                    updateCartSummary()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun applyDiscountConfig(cfg: DiscountConfig, isWholeOrder: Boolean, targetItem: CartItemModel?) {
        if (isWholeOrder) {
            when (cfg.type) {
                "RATE" -> {
                    wholeDiscountRate = cfg.value
                    wholeDiscountDeduct = 0.0
                    wholeDiscountNote = cfg.name
                }
                "DEDUCT" -> {
                    wholeDiscountRate = 1.0
                    wholeDiscountDeduct = cfg.value
                    wholeDiscountNote = cfg.name
                }
            }
        } else {
            targetItem?.let {
                when (cfg.type) {
                    "RATE" -> {
                        it.discountRate = cfg.value
                        it.deductAmount = 0.0
                        it.discountNote = cfg.name
                    }
                    "DEDUCT" -> {
                        it.discountRate = 1.0
                        it.deductAmount = cfg.value
                        it.discountNote = cfg.name
                    }
                }
            }
        }
        updateCartSummary()
    }

    private fun observeTables() {
        lifecycleScope.launch {
            App.instance.database.posDao().getAllTables().collectLatest { tables ->
                binding.rvTableGrid.adapter = TableGridAdapter(tables)
            }
        }
    }

    private fun startOrderForTable(table: DiningTable) {
        activeTable = table
        activeOrderId = 0
        isReservingMode = false
        cartList.clear()
        resetWholeDiscount()
        updateCartSummary()
        binding.tvOrderTableTitle.text = "桌台: ${table.name} (${table.area})"
        binding.btnSaveTableOrder.visibility = View.VISIBLE
        binding.btnSaveTableOrder.text = "下单开台"
        binding.layoutTableOverview.visibility = View.GONE
        binding.layoutOrderScreen.visibility = View.VISIBLE
    }

    private fun startPreOrderForReservation(table: DiningTable) {
        activeTable = table
        activeOrderId = 0
        isReservingMode = true
        cartList.clear()
        resetWholeDiscount()
        updateCartSummary()
        binding.tvOrderTableTitle.text = "【预定提前点菜】桌台: ${table.name}"
        binding.btnSaveTableOrder.visibility = View.VISIBLE
        binding.btnSaveTableOrder.text = "保存预定菜单"
        binding.layoutTableOverview.visibility = View.GONE
        binding.layoutOrderScreen.visibility = View.VISIBLE
    }

    private fun loadExistingTableOrder(table: DiningTable, isFromReservation: Boolean = false, onLoaded: (() -> Unit)? = null) {
        activeTable = table
        activeOrderId = table.currentOrderId
        isReservingMode = isFromReservation
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
                val statusText = if (isFromReservation) "预定预点·已选${cartList.sumOf { it.count }}件" else "就餐中·已点${cartList.sumOf { it.count }}件"
                binding.tvOrderTableTitle.text = "桌台: ${table.name} ($statusText)"
                binding.btnSaveTableOrder.visibility = View.VISIBLE
                binding.btnSaveTableOrder.text = if (isFromReservation) "更新预定菜单" else "加菜入单"
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
                    payType = if (isReservingMode) "预定挂单" else "挂单未付",
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

            table.status = if (isReservingMode) "RESERVED" else "OCCUPIED"
            table.currentOrderId = orderId
            table.currentAmount = finalAmount
            table.openTime = if (table.openTime == 0L) System.currentTimeMillis() else table.openTime
            dao.updateTable(table)

            withContext(Dispatchers.Main) {
                val msg = if (isReservingMode) "桌台 [${table.name}] 预定菜单已保存！预点总额: ￥${String.format("%.2f", finalAmount)}" else "桌台 [${table.name}] 挂单成功！总额: ￥${String.format("%.2f", finalAmount)}"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                showTableView()
            }
        }
    }

    private fun calculateFinalAmount(): Double {
        val sumAfterItemDiscount = cartList.sumOf { it.subtotal }
        var finalAfterWhole = (sumAfterItemDiscount * wholeDiscountRate) - wholeDiscountDeduct
        finalAfterWhole = Math.max(0.0, finalAfterWhole)

        if (isAutoMoling) {
            finalAfterWhole = floor(finalAfterWhole)
        }
        return finalAfterWhole
    }

    // 核心结算弹窗
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

        val cbPrint = dialogView.findViewById<CheckBox>(R.id.cbDialogPrint)
        val cbDrawer = dialogView.findViewById<CheckBox>(R.id.cbDialogDrawer)
        cbPrint?.isChecked = PrinterSettings.isDefaultPrintEnabled(this)
        cbDrawer?.isChecked = PrinterSettings.isDefaultDrawerEnabled(this)

        val payActions = mapOf(
            R.id.btnPayCash to "现金支付",
            R.id.btnPayWechat to "微信支付",
            R.id.btnPayAlipay to "支付宝",
            R.id.btnPayCard to "银行卡"
        )

        for ((btnId, payName) in payActions) {
            dialogView.findViewById<Button>(btnId)?.setOnClickListener {
                val needPrint = cbPrint?.isChecked ?: false
                val needDrawer = cbDrawer?.isChecked ?: false
                completeOrderAndClearTable(payName, finalAmount, originalAmount, needPrint, needDrawer)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun completeOrderAndClearTable(
        payType: String,
        totalAmount: Double,
        originalAmount: Double,
        needPrint: Boolean = false,
        needDrawer: Boolean = false
    ) {
        if (LicenseGuard.verifyOrHalt(this)) return

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
                discountNote = if (isAutoMoling) "$wholeDiscountNote 自动抹零".trim() else wholeDiscountNote
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

            PosPrinterHelper.executePrintAction(
                context = this@MainActivity,
                order = order,
                items = items,
                needPrint = needPrint,
                needKickDrawer = needDrawer
            )

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
                val allCategoryList = mutableListOf(Category(id = -1L, name = "全部", sortOrder = -1))
                allCategoryList.addAll(categories)
                binding.rvCategories.adapter = CategoryTabAdapter(allCategoryList)
            }
        }
    }

    private fun observeProducts() {
        lifecycleScope.launch {
            App.instance.database.posDao().getAllProducts().collectLatest { products ->
                allProductsList = products
                applyProductFilter()
            }
        }
    }

    private fun applyProductFilter() {
        var result = allProductsList

        if (selectedCategoryId != -1L) {
            result = result.filter { it.categoryId == selectedCategoryId }
        }
        if (searchKeyword.isNotEmpty()) {
            result = result.filter { PinyinUtil.matches(it.name, searchKeyword) }
        }

        binding.rvProducts.adapter = ProductGridAdapter(result)
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
            val note = if (isAutoMoling) "$wholeDiscountNote (含抹零)" else wholeDiscountNote
            binding.tvDiscountInfo.text = "已优惠 ￥${String.format("%.2f", totalDiscount)} $note"
        } else {
            binding.tvOriginalAmount.visibility = View.GONE
            binding.tvDiscountInfo.text = ""
        }
    }

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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_table_grid, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val table = list[position]
            holder.tvName.text = table.name
            holder.tvCapacity.text = "(${table.area}·${table.capacity}人)"

            when (table.status) {
                "OCCUPIED" -> {
                    holder.root.setBackgroundColor(Color.parseColor("#FEF3C7"))
                    holder.tvStatusBadge.text = "🟠 就餐中"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#D97706"))
                    holder.tvTableInfo.text = "当前消费金额："
                    holder.tvTableAmount.visibility = View.VISIBLE
                    holder.tvTableAmount.text = String.format("￥%.2f", table.currentAmount)

                    holder.btnAction1.text = "加菜"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#F59E0B"))
                    holder.btnAction1.setOnClickListener { loadExistingTableOrder(table, isFromReservation = false) }

                    holder.btnAction2.text = "结账"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#10B981"))
                    holder.btnAction2.setOnClickListener {
                        loadExistingTableOrder(table, isFromReservation = false) { showPaymentDialog() }
                    }

                    holder.btnAction3.visibility = View.VISIBLE
                    holder.btnAction3.text = "清台"
                    holder.btnAction3.setBackgroundColor(Color.parseColor("#EF4444"))
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
                    val hasPreOrder = (table.currentAmount > 0)
                    holder.tvStatusBadge.text = if (hasPreOrder) "🔵 已预定 (有预点)" else "🔵 已预定"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#2563EB"))

                    if (hasPreOrder) {
                        holder.tvTableInfo.text = "预定预点总额："
                        holder.tvTableAmount.visibility = View.VISIBLE
                        holder.tvTableAmount.text = String.format("￥%.2f", table.currentAmount)
                    } else {
                        holder.tvTableInfo.text = "客户预定中，尚未预点菜品"
                        holder.tvTableAmount.visibility = View.GONE
                    }

                    holder.btnAction1.text = "开台"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#059669"))
                    holder.btnAction1.setOnClickListener {
                        table.status = "OCCUPIED"
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().updateTable(table)
                            withContext(Dispatchers.Main) {
                                if (hasPreOrder) {
                                    loadExistingTableOrder(table, isFromReservation = false)
                                } else {
                                    startOrderForTable(table)
                                }
                            }
                        }
                    }

                    holder.btnAction2.text = if (hasPreOrder) "改预点" else "补预点"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#2563EB"))
                    holder.btnAction2.setOnClickListener {
                        if (hasPreOrder) {
                            loadExistingTableOrder(table, isFromReservation = true)
                        } else {
                            startPreOrderForReservation(table)
                        }
                    }

                    holder.btnAction3.visibility = View.VISIBLE
                    holder.btnAction3.text = "退订"
                    holder.btnAction3.setBackgroundColor(Color.parseColor("#6B7280"))
                    holder.btnAction3.setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            table.status = "IDLE"
                            table.currentOrderId = 0
                            table.currentAmount = 0.0
                            App.instance.database.posDao().updateTable(table)
                        }
                    }
                }
                else -> {
                    val areaBgColor = when (table.area) {
                        "包厢" -> "#FEF9C3"
                        "卡座" -> "#FCE7F3"
                        "露台" -> "#CCFBF1"
                        else -> "#E6F9F0"
                    }
                    holder.root.setBackgroundColor(Color.parseColor(areaBgColor))
                    holder.tvStatusBadge.text = "🟢 空闲"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#059669"))
                    holder.tvTableInfo.text = "桌位空闲就绪"
                    holder.tvTableAmount.visibility = View.GONE

                    holder.btnAction1.text = "开台"
                    holder.btnAction1.setBackgroundColor(Color.parseColor("#059669"))
                    holder.btnAction1.setOnClickListener { startOrderForTable(table) }

                    holder.btnAction2.text = "预定"
                    holder.btnAction2.setBackgroundColor(Color.parseColor("#2563EB"))
                    holder.btnAction2.setOnClickListener {
                        table.status = "RESERVED"
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().updateTable(table)
                        }
                    }

                    holder.btnAction3.visibility = View.VISIBLE
                    holder.btnAction3.text = "预点"
                    holder.btnAction3.setBackgroundColor(Color.parseColor("#8B5CF6"))
                    holder.btnAction3.setOnClickListener {
                        startPreOrderForReservation(table)
                    }
                }
            }
        }
        override fun getItemCount() = list.size
    }

    inner class CategoryTabAdapter(private val list: List<Category>) : RecyclerView.Adapter<CategoryTabAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: View = v.findViewById(R.id.layoutCategoryRoot)
            val tvName: TextView = v.findViewById(R.id.tvCategoryName)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name
            val isSelected = (item.id == selectedCategoryId)

            if (isSelected) {
                holder.root.setBackgroundColor(Color.parseColor("#FFC300"))
                holder.tvName.setTextColor(Color.parseColor("#111827"))
            } else {
                holder.root.setBackgroundColor(Color.parseColor("#FFFFFF"))
                holder.tvName.setTextColor(Color.parseColor("#4B5563"))
            }

            holder.root.setOnClickListener {
                selectedCategoryId = item.id
                notifyDataSetChanged()
                applyProductFilter()
            }
        }
        override fun getItemCount() = list.size
    }

    inner class ProductGridAdapter(private val list: List<Product>) : RecyclerView.Adapter<ProductGridAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvProductName)
            val tvPrice: TextView = v.findViewById(R.id.tvProductPrice)
            val ivThumb: ImageView = v.findViewById(R.id.ivProductThumb)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = list[position]
            holder.tvName.text = p.name
            holder.tvPrice.text = String.format("￥%.2f", p.price)

            if (p.imageUri.isNotEmpty()) {
                holder.ivThumb.visibility = View.VISIBLE
                ImageUtil.loadSafeImage(this@MainActivity, p.imageUri, holder.ivThumb)
            } else {
                holder.ivThumb.visibility = View.GONE
            }

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
                holder.tvDiscountNote.text = "优惠: ${item.discountNote}"
            } else {
                holder.tvDiscountNote.visibility = View.GONE
            }

            holder.tvDiscountBtn.setOnClickListener {
                showDiscountButtonGridDialog(isWholeOrder = false, targetItem = item)
            }

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