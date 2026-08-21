package com.pos.lite.ui

import android.content.DialogInterface
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pos.lite.App
import com.pos.lite.R
import com.pos.lite.data.*
import com.pos.lite.databinding.ActivityManageBinding
import com.pos.lite.utils.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageBinding
    private var currentTab = "PRODUCTS" // PRODUCTS / CATEGORIES / STAFFS / TABLES / DISCOUNTS

    // 当前编辑菜品时选中的图片 URI
    private var tempSelectedImageUri: String = ""
    private var dialogPreviewImageView: ImageView? = null

    // 相册选图注册器
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempSelectedImageUri = it.toString()
            dialogPreviewImageView?.let { iv ->
                iv.visibility = View.VISIBLE
                ImageUtil.loadSafeImage(this, tempSelectedImageUri, iv)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvManageList.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }

        binding.btnTabProducts.setOnClickListener { switchTab("PRODUCTS") }
        binding.btnTabCategories.setOnClickListener { switchTab("CATEGORIES") }
        binding.btnTabStaffs.setOnClickListener { switchTab("STAFFS") }
        binding.btnTabTables.setOnClickListener { switchTab("TABLES") }
        binding.btnTabDiscounts.setOnClickListener { switchTab("DISCOUNTS") }

        binding.btnAddNewItem.setOnClickListener {
            when (currentTab) {
                "PRODUCTS" -> showEditProductDialog(null)
                "CATEGORIES" -> showEditCategoryDialog(null)
                "STAFFS" -> showEditStaffDialog(null)
                "TABLES" -> showEditTableDialog(null)
                "DISCOUNTS" -> showEditDiscountDialog(null)
            }
        }

        switchTab("PRODUCTS")
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        val tabs = mapOf(
            "PRODUCTS" to binding.btnTabProducts,
            "CATEGORIES" to binding.btnTabCategories,
            "STAFFS" to binding.btnTabStaffs,
            "TABLES" to binding.btnTabTables,
            "DISCOUNTS" to binding.btnTabDiscounts
        )

        for ((k, btn) in tabs) {
            val isSelected = (k == tab)
            btn.setBackgroundColor(if (isSelected) Color.parseColor("#1E2433") else Color.parseColor("#E5E7EB"))
            btn.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#111827"))
        }

        when (tab) {
            "PRODUCTS" -> binding.btnAddNewItem.text = "➕ 新增菜品"
            "CATEGORIES" -> binding.btnAddNewItem.text = "➕ 新增分类"
            "STAFFS" -> binding.btnAddNewItem.text = "➕ 新增员工"
            "TABLES" -> binding.btnAddNewItem.text = "➕ 新增桌台"
            "DISCOUNTS" -> binding.btnAddNewItem.text = "➕ 新增快捷折扣"
        }

        loadListData()
    }

    private fun loadListData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            when (currentTab) {
                "PRODUCTS" -> {
                    val products = dao.getAllProducts().first()
                    val categories = dao.getAllCategories().first()
                    withContext(Dispatchers.Main) {
                        binding.tvManageHeaderInfo.text = "菜品总数: ${products.size} 种 | 在售: ${products.count { it.isAvailable }}"
                        binding.rvManageList.adapter = ProductListAdapter(products, categories)
                    }
                }
                "CATEGORIES" -> {
                    val categories = dao.getAllCategories().first()
                    withContext(Dispatchers.Main) {
                        binding.tvManageHeaderInfo.text = "分类总数: ${categories.size} 个"
                        binding.rvManageList.adapter = CategoryListAdapter(categories)
                    }
                }
                "STAFFS" -> {
                    val staffs = dao.getAllStaffs().first()
                    withContext(Dispatchers.Main) {
                        binding.tvManageHeaderInfo.text = "员工总数: ${staffs.size} 人 (店长: 1, 收银员: ${staffs.size - 1})"
                        binding.rvManageList.adapter = StaffListAdapter(staffs)
                    }
                }
                "TABLES" -> {
                    val tables = dao.getAllTables().first()
                    withContext(Dispatchers.Main) {
                        binding.tvManageHeaderInfo.text = "桌台总数: ${tables.size} 张 | 容纳总客量: ${tables.sumOf { it.capacity }} 人"
                        binding.rvManageList.adapter = TableListAdapter(tables)
                    }
                }
                "DISCOUNTS" -> {
                    val discounts = dao.getAllDiscountConfigs().first()
                    withContext(Dispatchers.Main) {
                        binding.tvManageHeaderInfo.text = "收银台快捷折扣按钮: ${discounts.size} 个"
                        binding.rvManageList.adapter = DiscountConfigAdapter(discounts)
                    }
                }
            }
        }
    }

    // 1. 菜品增改弹窗 (支持选图/拍照)
    private fun showEditProductDialog(product: Product?) {
        tempSelectedImageUri = product?.imageUri ?: ""

        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { App.instance.database.posDao().getAllCategories().first() }
            if (categories.isEmpty()) {
                Toast.makeText(this@ManageActivity, "请先添加至少一个商品分类", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val catNames = categories.map { it.name }.toTypedArray()
            var selectedIndex = if (product != null) categories.indexOfFirst { it.id == product.categoryId }.coerceAtLeast(0) else 0

            val etName = EditText(this@ManageActivity).apply {
                hint = "菜品名称 (如: 秘制红烧肉)"
                setText(product?.name ?: "")
            }
            val etPrice = EditText(this@ManageActivity).apply {
                hint = "单价 (元)"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(if (product != null) product.price.toString() else "")
            }
            val btnCat = Button(this@ManageActivity).apply {
                text = "所属分类: " + catNames[selectedIndex]
            }

            btnCat.setOnClickListener {
                AlertDialog.Builder(this@ManageActivity)
                    .setTitle("选择分类")
                    .setSingleChoiceItems(catNames, selectedIndex) { d: DialogInterface, which: Int ->
                        selectedIndex = which
                        btnCat.text = "所属分类: " + catNames[which]
                        d.dismiss()
                    }.show()
            }

            // 图片预览与选择控件
            val ivPreview = ImageView(this@ManageActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200).apply {
                    topMargin = 12
                    bottomMargin = 12
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#F3F4F6"))
            }
            dialogPreviewImageView = ivPreview

            if (tempSelectedImageUri.isNotEmpty()) {
                ivPreview.visibility = View.VISIBLE
                ImageUtil.loadSafeImage(this@ManageActivity, tempSelectedImageUri, ivPreview)
            } else {
                ivPreview.visibility = View.GONE
            }

            val btnPickPhoto = Button(this@ManageActivity).apply {
                text = "📸 从相册选择菜品封面图片"
                setBackgroundColor(Color.parseColor("#E5E7EB"))
                setTextColor(Color.parseColor("#111827"))
                setOnClickListener {
                    pickImageLauncher.launch("image/*")
                }
            }

            val layout = LinearLayout(this@ManageActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 30, 50, 20)
                addView(etName)
                addView(etPrice)
                addView(btnCat)
                addView(ivPreview)
                addView(btnPickPhoto)
            }

            AlertDialog.Builder(this@ManageActivity)
                .setTitle(if (product == null) "➕ 新增菜品" else "✏ 编辑菜品")
                .setView(layout)
                .setPositiveButton("保存") { _, _ ->
                    val name = etName.text.toString().trim()
                    val price = etPrice.text.toString().trim().toDoubleOrNull() ?: 0.0

                    if (name.isNotEmpty() && price > 0) {
                        val chosenCat = categories[selectedIndex]
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dao = App.instance.database.posDao()
                            if (product == null) {
                                dao.insertProduct(Product(categoryId = chosenCat.id, name = name, price = price, imageUri = tempSelectedImageUri))
                            } else {
                                dao.updateProduct(product.copy(categoryId = chosenCat.id, name = name, price = price, imageUri = tempSelectedImageUri))
                            }
                            withContext(Dispatchers.Main) {
                                loadListData()
                            }
                        }
                    }
                }
                .setNegativeButton("取消", null).show()
        }
    }

    // 2. 分类增改
    private fun showEditCategoryDialog(cat: Category?) {
        val et = EditText(this).apply {
            hint = "分类名称 (如: 烧烤热炒、酒水饮料)"
            setText(cat?.name ?: "")
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(et)
        }

        AlertDialog.Builder(this)
            .setTitle(if (cat == null) "➕ 新增分类" else "✏ 编辑分类")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = App.instance.database.posDao()
                        if (cat == null) dao.insertCategory(Category(name = name))
                        else dao.updateCategory(cat.copy(name = name))
                        withContext(Dispatchers.Main) { loadListData() }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    // 3. 员工增改
    private fun showEditStaffDialog(staff: Staff?) {
        val etName = EditText(this).apply {
            hint = "员工姓名 (如: 张三)"
            setText(staff?.name ?: "")
        }
        val etPin = EditText(this).apply {
            hint = "登录PIN码 (4~6位数字)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setText(staff?.pinCode ?: "")
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(etName)
            addView(etPin)
        }

        AlertDialog.Builder(this)
            .setTitle(if (staff == null) "➕ 新增收银员工" else "✏ 编辑员工信息")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val pin = etPin.text.toString().trim()
                if (name.isNotEmpty() && pin.length >= 4) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = App.instance.database.posDao()
                        if (staff == null) dao.insertStaff(Staff(name = name, pinCode = pin, role = "CASHIER"))
                        else dao.updateStaff(staff.copy(name = name, pinCode = pin))
                        withContext(Dispatchers.Main) { loadListData() }
                    }
                } else {
                    Toast.makeText(this, "PIN码至少需要4位数字", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("取消", null).show()
    }

    // 4. 桌台增改 (支持区域)
    private fun showEditTableDialog(table: DiningTable?) {
        val areas = arrayOf("大厅", "包厢", "露台", "卡座")
        var selectedAreaIdx = if (table != null) areas.indexOf(table.area).coerceAtLeast(0) else 0

        val etName = EditText(this).apply {
            hint = "桌台名称 (如: A08, 包厢V8)"
            setText(table?.name ?: "")
        }
        val btnArea = Button(this).apply {
            text = "所属区域: " + areas[selectedAreaIdx]
        }
        btnArea.setOnClickListener {
            AlertDialog.Builder(this@ManageActivity)
                .setTitle("选择所属区域")
                .setSingleChoiceItems(areas, selectedAreaIdx) { d, which ->
                    selectedAreaIdx = which
                    btnArea.text = "所属区域: " + areas[which]
                    d.dismiss()
                }.show()
        }

        val etCapacity = EditText(this).apply {
            hint = "容纳人数 (如: 4)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (table != null) table.capacity.toString() else "4")
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(etName)
            addView(btnArea)
            addView(etCapacity)
        }

        AlertDialog.Builder(this)
            .setTitle(if (table == null) "➕ 新增桌台" else "✏ 编辑桌台")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val cap = etCapacity.text.toString().trim().toIntOrNull() ?: 4
                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = App.instance.database.posDao()
                        if (table == null) dao.insertTable(DiningTable(name = name, area = areas[selectedAreaIdx], capacity = cap))
                        else dao.updateTable(table.copy(name = name, area = areas[selectedAreaIdx], capacity = cap))
                        withContext(Dispatchers.Main) { loadListData() }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    // 5. 新增：折扣快捷按键配置增改
    private fun showEditDiscountDialog(config: DiscountConfig?) {
        val types = arrayOf("比例打折 (如9折填0.9)", "固定立减 (如减10元填10)", "自动抹零")
        var selectedTypeIdx = if (config?.type == "DEDUCT") 1 else if (config?.type == "MOLING") 2 else 0

        val etName = EditText(this).apply {
            hint = "按键名称 (如: 9折, 88折, 减5元, 抹零)"
            setText(config?.name ?: "")
        }
        val btnType = Button(this).apply {
            text = "折扣类型: " + types[selectedTypeIdx]
        }
        val etValue = EditText(this).apply {
            hint = "数值 (如: 0.9 或 10, 抹零可填0)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (config != null) config.value.toString() else "")
        }

        btnType.setOnClickListener {
            AlertDialog.Builder(this@ManageActivity)
                .setTitle("选择类型")
                .setSingleChoiceItems(types, selectedTypeIdx) { d, which ->
                    selectedTypeIdx = which
                    btnType.text = "折扣类型: " + types[which]
                    d.dismiss()
                }.show()
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(etName)
            addView(btnType)
            addView(etValue)
        }

        AlertDialog.Builder(this)
            .setTitle(if (config == null) "➕ 新增常用折扣按键" else "✏ 编辑折扣按键")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val value = etValue.text.toString().trim().toDoubleOrNull() ?: 0.0
                val typeKey = if (selectedTypeIdx == 1) "DEDUCT" else if (selectedTypeIdx == 2) "MOLING" else "RATE"

                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = App.instance.database.posDao()
                        if (config == null) dao.insertDiscountConfig(DiscountConfig(name = name, type = typeKey, value = value))
                        else dao.updateDiscountConfig(config.copy(name = name, type = typeKey, value = value))
                        withContext(Dispatchers.Main) { loadListData() }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    // --- 各模块适配器 ---
    inner class ProductListAdapter(private val list: List<Product>, private val categories: List<Category>) : RecyclerView.Adapter<ProductListAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivThumb: ImageView = v.findViewById(R.id.ivRowThumb)
            val tvTitle: TextView = v.findViewById(R.id.tvRowTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvRowSubtitle)
            val btnEdit: Button = v.findViewById(R.id.btnRowEdit)
            val btnDelete: Button = v.findViewById(R.id.btnRowDelete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_manage_row, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = list[position]
            val catName = categories.find { it.id == p.categoryId }?.name ?: "未分类"
            holder.tvTitle.text = p.name
            holder.tvSubtitle.text = "单价: ￥${String.format("%.2f", p.price)} | 分类: $catName"

            if (p.imageUri.isNotEmpty()) {
                holder.ivThumb.visibility = View.VISIBLE
                ImageUtil.loadSafeImage(this@ManageActivity, p.imageUri, holder.ivThumb)
            } else {
                holder.ivThumb.visibility = View.GONE
            }

            holder.btnEdit.setOnClickListener { showEditProductDialog(p) }
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@ManageActivity)
                    .setTitle("确认删除")
                    .setMessage("确定删除菜品【${p.name}】吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().deleteProductById(p.id)
                            withContext(Dispatchers.Main) { loadListData() }
                        }
                    }.setNegativeButton("取消", null).show()
            }
        }
        override fun getItemCount() = list.size
    }

    inner class CategoryListAdapter(private val list: List<Category>) : RecyclerView.Adapter<CategoryListAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvRowTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvRowSubtitle)
            val btnEdit: Button = v.findViewById(R.id.btnRowEdit)
            val btnDelete: Button = v.findViewById(R.id.btnRowDelete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_manage_row, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = list[position]
            holder.tvTitle.text = cat.name
            holder.tvSubtitle.text = "分类编号: #${cat.id}"

            holder.btnEdit.setOnClickListener { showEditCategoryDialog(cat) }
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@ManageActivity)
                    .setTitle("确认删除")
                    .setMessage("确定删除分类【${cat.name}】吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().deleteCategoryById(cat.id)
                            withContext(Dispatchers.Main) { loadListData() }
                        }
                    }.setNegativeButton("取消", null).show()
            }
        }
        override fun getItemCount() = list.size
    }

    inner class StaffListAdapter(private val list: List<Staff>) : RecyclerView.Adapter<StaffListAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvRowTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvRowSubtitle)
            val btnEdit: Button = v.findViewById(R.id.btnRowEdit)
            val btnDelete: Button = v.findViewById(R.id.btnRowDelete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_manage_row, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val staff = list[position]
            holder.tvTitle.text = staff.name + (if (staff.role == "ADMIN") " (店长·管理员)" else " (收银员)")
            holder.tvSubtitle.text = "PIN登录密码: ${staff.pinCode}"

            if (staff.role == "ADMIN") {
                holder.btnDelete.visibility = View.GONE
            } else {
                holder.btnDelete.visibility = View.VISIBLE
            }

            holder.btnEdit.setOnClickListener { showEditStaffDialog(staff) }
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@ManageActivity)
                    .setTitle("确认删除")
                    .setMessage("确定删除员工【${staff.name}】吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().deleteStaffById(staff.id)
                            withContext(Dispatchers.Main) { loadListData() }
                        }
                    }.setNegativeButton("取消", null).show()
            }
        }
        override fun getItemCount() = list.size
    }

    inner class TableListAdapter(private val list: List<DiningTable>) : RecyclerView.Adapter<TableListAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvRowTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvRowSubtitle)
            val btnEdit: Button = v.findViewById(R.id.btnRowEdit)
            val btnDelete: Button = v.findViewById(R.id.btnRowDelete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_manage_row, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val table = list[position]
            holder.tvTitle.text = "${table.name} (${table.area})"
            holder.tvSubtitle.text = "容纳: ${table.capacity}人桌 | 状态: ${if(table.status=="OCCUPIED")"就餐中" else if(table.status=="RESERVED")"已预定" else "空闲"}"

            holder.btnEdit.setOnClickListener { showEditTableDialog(table) }
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@ManageActivity)
                    .setTitle("确认删除")
                    .setMessage("确定删除桌台【${table.name}】吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().deleteTableById(table.id)
                            withContext(Dispatchers.Main) { loadListData() }
                        }
                    }.setNegativeButton("取消", null).show()
            }
        }
        override fun getItemCount() = list.size
    }

    inner class DiscountConfigAdapter(private val list: List<DiscountConfig>) : RecyclerView.Adapter<DiscountConfigAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvRowTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvRowSubtitle)
            val btnEdit: Button = v.findViewById(R.id.btnRowEdit)
            val btnDelete: Button = v.findViewById(R.id.btnRowDelete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_manage_row, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cfg = list[position]
            holder.tvTitle.text = cfg.name
            val desc = when (cfg.type) {
                "RATE" -> "按比例打折 (${(cfg.value * 10).toInt()}折)"
                "DEDUCT" -> "固定金额立减 (减￥${cfg.value})"
                else -> "去分去角自动抹零"
            }
            holder.tvSubtitle.text = "按键参数: $desc"

            holder.btnEdit.setOnClickListener { showEditDiscountDialog(cfg) }
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@ManageActivity)
                    .setTitle("确认删除")
                    .setMessage("确定删除快捷折扣【${cfg.name}】吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().deleteDiscountConfigById(cfg.id)
                            withContext(Dispatchers.Main) { loadListData() }
                        }
                    }.setNegativeButton("取消", null).show()
            }
        }
        override fun getItemCount() = list.size
    }
}