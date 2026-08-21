package com.pos.lite.ui

import android.content.DialogInterface
import android.graphics.Color
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pos.lite.App
import com.pos.lite.R
import com.pos.lite.data.*
import com.pos.lite.databinding.ActivityManageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageBinding
    private var currentTab = "PRODUCTS" // PRODUCTS / CATEGORIES / STAFFS / TABLES

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

        binding.btnAddNewItem.setOnClickListener {
            when (currentTab) {
                "PRODUCTS" -> showEditProductDialog(null)
                "CATEGORIES" -> showEditCategoryDialog(null)
                "STAFFS" -> showEditStaffDialog(null)
                "TABLES" -> showEditTableDialog(null)
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
            "TABLES" to binding.btnTabTables
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
                        binding.rvManageList.adapter = ProductListAdapter(products, categories)
                    }
                }
                "CATEGORIES" -> {
                    val categories = dao.getAllCategories().first()
                    withContext(Dispatchers.Main) {
                        binding.rvManageList.adapter = CategoryListAdapter(categories)
                    }
                }
                "STAFFS" -> {
                    val staffs = dao.getAllStaffs().first()
                    withContext(Dispatchers.Main) {
                        binding.rvManageList.adapter = StaffListAdapter(staffs)
                    }
                }
                "TABLES" -> {
                    val tables = dao.getAllTables().first()
                    withContext(Dispatchers.Main) {
                        binding.rvManageList.adapter = TableListAdapter(tables)
                    }
                }
            }
        }
    }

    // 1. 菜品增改弹窗
    private fun showEditProductDialog(product: Product?) {
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { App.instance.database.posDao().getAllCategories().first() }
            if (categories.isEmpty()) {
                Toast.makeText(this@ManageActivity, "请先添加至少一个商品分类", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val catNames = categories.map { it.name }.toTypedArray()
            var selectedIndex = if (product != null) categories.indexOfFirst { it.id == product.categoryId }.coerceAtLeast(0) else 0

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

            val layout = LinearLayout(this@ManageActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 20)
                addView(etName)
                addView(etPrice)
                addView(btnCat)
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
                                dao.insertProduct(Product(categoryId = chosenCat.id, name = name, price = price))
                            } else {
                                dao.updateProduct(product.copy(categoryId = chosenCat.id, name = name, price = price))
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

    // 2. 分类增改弹窗
    private fun showEditCategoryDialog(cat: Category?) {
        val et = EditText(this).apply {
            hint = "分类名称 (如: 招牌炒菜、酒水饮料)"
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

    // 3. 员工增改弹窗
    private fun showEditStaffDialog(staff: Staff?) {
        val etName = EditText(this).apply {
            hint = "员工姓名"
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
            .setTitle(if (staff == null) "➕ 新增收银员" else "✏ 编辑员工信息")
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
                    Toast.makeText(this, "PIN码至少4位数字", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("取消", null).show()
    }

    // 4. 桌台增改弹窗
    private fun showEditTableDialog(table: DiningTable?) {
        val etName = EditText(this).apply {
            hint = "桌台名称 (如: A08, 包厢V8)"
            setText(table?.name ?: "")
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
                        if (table == null) dao.insertTable(DiningTable(name = name, capacity = cap))
                        else dao.updateTable(table.copy(name = name, capacity = cap))
                        withContext(Dispatchers.Main) { loadListData() }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    // --- 各子模块列表适配器 ---
    inner class ProductListAdapter(private val list: List<Product>, private val categories: List<Category>) : RecyclerView.Adapter<ProductListAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
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
                holder.btnDelete.visibility = View.GONE // 店长不可删除
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
            val statusDesc = when (table.status) {
                "OCCUPIED" -> "就餐中 (挂单 ￥${table.currentAmount})"
                "RESERVED" -> "已预定"
                else -> "空闲"
            }
            holder.tvTitle.text = table.name
            holder.tvSubtitle.text = "容纳: ${table.capacity}人桌 | 状态: $statusDesc"

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
}