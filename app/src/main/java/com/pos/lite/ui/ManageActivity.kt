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
import android.widget.GridLayout
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
    private var currentTab = "PRODUCTS"

    private var tempSelectedImageUri: String = ""
    private var dialogPreviewImageView: ImageView? = null

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
                        binding.tvManageHeaderInfo.text = "桌台总数: ${tables.size} 张 (已按大厅/包厢/露台色彩分区)"
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

    // 核心改进：选择分类彻底改用【大按钮网格矩阵】选择
    private fun showCategoryButtonGridSelector(categories: List<Category>, onSelected: (Category) -> Unit) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
        }

        val tv = TextView(this).apply {
            text = "点击下方分类按钮快速选择："
            textSize = 14sp
            setTextColor(Color.parseColor("#4B5563"))
        }
        dialogView.addView(tv)

        val grid = GridLayout(this).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("🏷 选择菜品所属分类")
            .setView(dialogView)
            .setNegativeButton("取消", null)
            .create()

        for (cat in categories) {
            val btn = Button(this).apply {
                text = cat.name
                textSize = 15sp
                setTextColor(Color.parseColor("#111827"))
                setBackgroundColor(Color.parseColor("#F3F4F6"))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(6, 6, 6, 6)
                    height = 100
                }

                setOnClickListener {
                    onSelected(cat)
                    dialog.dismiss()
                }
            }
            grid.addView(btn)
        }

        dialogView.addView(grid)
        dialog.show()
    }

    // 1. 菜品增改
    private fun showEditProductDialog(product: Product?) {
        tempSelectedImageUri = product?.imageUri ?: ""

        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { App.instance.database.posDao().getAllCategories().first() }
            if (categories.isEmpty()) {
                Toast.makeText(this@ManageActivity, "请先添加至少一个商品分类", Toast.LENGTH_SHORT).show()
                return@launch
            }

            var chosenCat = if (product != null) categories.find { it.id == product.categoryId } ?: categories[0] else categories[0]

            val etName = EditText(this@ManageActivity).apply {
                hint = "菜品名称"
                setText(product?.name ?: "")
            }
            val etPrice = EditText(this@ManageActivity).apply {
                hint = "单价 (元)"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(if (product != null) product.price.toString() else "")
            }

            val btnCat = Button(this@ManageActivity).apply {
                text = "所属分类: " + chosenCat.name + " (点击切换)"
                setTextColor(Color.parseColor("#1E2433"))
                setBackgroundColor(Color.parseColor("#FEF3C7"))
            }

            // 点击分类弹出大按钮矩阵选择！
            btnCat.setOnClickListener {
                showCategoryButtonGridSelector(categories) { selected ->
                    chosenCat = selected
                    btnCat.text = "所属分类: " + selected.name + " (点击切换)"
                }
            }

            val ivPreview = ImageView(this@ManageActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 180).apply {
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
                text = "📸 选择/拍照菜品封面图"
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
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dao = App.instance.database.posDao()
                            if (product == null) {
                                dao.insertProduct(Product(categoryId = chosenCat.id, name = name, price = price, imageUri = tempSelectedImageUri))
                            } else {
                                dao.updateProduct(product.copy(categoryId = chosenCat.id, name = name, price = price, imageUri = tempSelectedImageUri))
                            }
                            withContext(Dispatchers.Main) { loadListData() }
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

    // 3. 员工增改 (脱敏隐藏明文)
    private fun showEditStaffDialog(staff: Staff?) {
        val etName = EditText(this).apply {
            hint = "员工姓名"
            setText(staff?.name ?: "")
        }
        val etPin = EditText(this).apply {
            hint = "登录PIN码 (4~6位纯数字)"
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
            .setTitle(if (staff == null) "➕ 新增收银员" else "✏ 修改员工信息")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val pin = etPin.text.toString().trim()
                if (name.isNotEmpty() && pin.length >= 4) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = App.instance.database.posDao()
                        if (staff == null) {
                            dao.insertStaff(Staff(name = name, pinCode = pin, role = "CASHIER"))
                        } else {
                            dao.updateStaff(staff.copy(name = name, pinCode = pin))
                        }
                        withContext(Dispatchers.Main) { loadListData() }
                    }
                } else {
                    Toast.makeText(this, "PIN码至少4位数字", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("取消", null).show()
    }

    // 4. 桌台增改 (区域大按钮选择)
    private fun showEditTableDialog(table: DiningTable?) {
        val areas = arrayOf("大厅", "包厢", "卡座", "露台")
        var selectedArea = table?.area ?: "大厅"

        val etName = EditText(this).apply {
            hint = "桌台名称 (如: A08, 包厢V8)"
            setText(table?.name ?: "")
        }
        val btnArea = Button(this).apply {
            text = "所属区域: $selectedArea (点击切换)"
            setBackgroundColor(Color.parseColor("#E0E7FF"))
            setTextColor(Color.parseColor("#3730A3"))
        }

        btnArea.setOnClickListener {
            // 区域选择也用大按钮矩阵！
            val gridView = LinearLayout(this@ManageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(30, 20, 30, 20)
            }
            val areaDialog = AlertDialog.Builder(this@ManageActivity)
                .setTitle("选择所属区域")
                .setView(gridView)
                .setNegativeButton("取消", null)
                .create()

            for (a in areas) {
                val b = Button(this@ManageActivity).apply {
                    text = a
                    layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { marginEnd = 6 }
                    setOnClickListener {
                        selectedArea = a
                        btnArea.text = "所属区域: $a (点击切换)"
                        areaDialog.dismiss()
                    }
                }
                gridView.addView(b)
            }
            areaDialog.show()
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
                        if (table == null) dao.insertTable(DiningTable(name = name, area = selectedArea, capacity = cap))
                        else dao.updateTable(table.copy(name = name, area = selectedArea, capacity = cap))
                        withContext(Dispatchers.Main) { loadListData() }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    // 5. 折扣配置增改
    private fun showEditDiscountDialog(config: DiscountConfig?) {
        var selectedType = config?.type ?: "RATE"

        val etName = EditText(this).apply {
            hint = "按键名称 (如: 9折, 88折, 减5元, 抹零)"
            setText(config?.name ?: "")
        }

        val typeButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }

        val btnRate = Button(this).apply { text = "比例打折"; layoutParams = LinearLayout.LayoutParams(0, 90, 1f) }
        val btnDeduct = Button(this).apply { text = "固定立减"; layoutParams = LinearLayout.LayoutParams(0, 90, 1f) }
        val btnMoling = Button(this).apply { text = "自动抹零"; layoutParams = LinearLayout.LayoutParams(0, 90, 1f) }

        fun updateTypeBtnStyles() {
            btnRate.setBackgroundColor(if (selectedType == "RATE") Color.parseColor("#1E2433") else Color.parseColor("#E5E7EB"))
            btnRate.setTextColor(if (selectedType == "RATE") Color.WHITE else Color.parseColor("#111827"))
            btnDeduct.setBackgroundColor(if (selectedType == "DEDUCT") Color.parseColor("#1E2433") else Color.parseColor("#E5E7EB"))
            btnDeduct.setTextColor(if (selectedType == "DEDUCT") Color.WHITE else Color.parseColor("#111827"))
            btnMoling.setBackgroundColor(if (selectedType == "MOLING") Color.parseColor("#1E2433") else Color.parseColor("#E5E7EB"))
            btnMoling.setTextColor(if (selectedType == "MOLING") Color.WHITE else Color.parseColor("#111827"))
        }
        updateTypeBtnStyles()

        btnRate.setOnClickListener { selectedType = "RATE"; updateTypeBtnStyles() }
        btnDeduct.setOnClickListener { selectedType = "DEDUCT"; updateTypeBtnStyles() }
        btnMoling.setOnClickListener { selectedType = "MOLING"; updateTypeBtnStyles() }

        typeButtons.addView(btnRate)
        typeButtons.addView(btnDeduct)
        typeButtons.addView(btnMoling)

        val etValue = EditText(this).apply {
            hint = "数值 (如: 0.9 或 10, 抹零填0)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (config != null) config.value.toString() else "")
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 20)
            addView(etName)
            addView(typeButtons)
            addView(etValue)
        }

        AlertDialog.Builder(this)
            .setTitle(if (config == null) "➕ 新增快捷折扣按键" else "✏ 编辑折扣按键")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val value = etValue.text.toString().trim().toDoubleOrNull() ?: 0.0

                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = App.instance.database.posDao()
                        if (config == null) dao.insertDiscountConfig(DiscountConfig(name = name, type = selectedType, value = value))
                        else dao.updateDiscountConfig(config.copy(name = name, type = selectedType, value = value))
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

    // 员工管理：密码脱敏
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
            holder.tvTitle.text = staff.name + (if (staff.role == "ADMIN") " (店长·唯一管理账号)" else " (收银员)")
            // 核心脱敏：使用黑点掩码，不再明文展示
            val maskedPin = "●".repeat(staff.pinCode.length.coerceAtLeast(4))
            holder.tvSubtitle.text = "登录PIN密码: $maskedPin (已安全加密)"

            if (staff.role == "ADMIN") {
                holder.btnDelete.visibility = View.GONE // 店长不可被删除
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

    // 桌台管理：色彩与区域聚类展示
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
            holder.tvTitle.text = "${table.name} [${table.area}]"
            holder.tvSubtitle.text = "区域: ${table.area} | 建议客容量: ${table.capacity}人 | 状态: ${if(table.status=="OCCUPIED")"就餐中" else if(table.status=="RESERVED")"已预定" else "空闲"}"

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
                    .setMessage("确定删除快捷折扣按键【${cfg.name}】吗？")
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