package com.pos.lite.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pos.lite.App
import com.pos.lite.R
import com.pos.lite.data.Category
import com.pos.lite.data.DiningTable
import com.pos.lite.data.Product
import com.pos.lite.data.Staff
import com.pos.lite.databinding.ActivityManageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 新增菜品分类
        binding.btnAddCategory.setOnClickListener {
            val input = EditText(this)
            input.hint = "例如: 烧烤、主食、饮料"
            AlertDialog.Builder(this)
                .setTitle("新增商品分类")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().insertCategory(Category(name = name))
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ManageActivity, "分类 [$name] 添加成功", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("取消", null).show()
        }

        // 2. 新增菜品
        binding.btnAddProduct.setOnClickListener {
            showAddProductDialog()
        }

        // 3. 新增员工 (仅店长有权)
        binding.btnAddStaff.setOnClickListener {
            if (App.currentStaff?.role != "ADMIN") {
                Toast.makeText(this, "权限不足: 仅店长可添加员工", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddStaffDialog()
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun showAddProductDialog() {
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                App.instance.database.posDao().getAllCategories().first()
            }
            if (categories.isEmpty()) {
                Toast.makeText(this@ManageActivity, "请先添加至少一个商品分类", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val catNames = categories.map { it.name }.toTypedArray()
            var selectedIndex = 0

            val view = LayoutInflater.from(this@ManageActivity).inflate(R.layout.dialog_add_product, null, false)
            val etName = view.findViewById<EditText>(R.id.etProductName)
            val etPrice = view.findViewById<EditText>(R.id.etProductPrice)
            val btnSelectCat = view.findViewById<android.widget.Button>(R.id.btnChooseCategory)
            btnSelectCat.text = "分类: " + catNames[0]

            btnSelectCat.setOnClickListener {
                AlertDialog.Builder(this@ManageActivity)
                    .setTitle("选择分类")
                    .setSingleChoiceItems(catNames, selectedIndex) { d, which ->
                        selectedIndex = which
                        btnSelectCat.text = "分类: " + catNames[which]
                        d.dismiss()
                    }.show()
            }

            AlertDialog.Builder(this@ManageActivity)
                .setTitle("新增商品/菜品")
                .setView(view)
                .setPositiveButton("保存") { _, _ ->
                    val name = etName.text.toString().trim()
                    val priceStr = etPrice.text.toString().trim()
                    val price = priceStr.toDoubleOrNull() ?: 0.0

                    if (name.isNotEmpty() && price > 0) {
                        val chosenCat = categories[selectedIndex]
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().insertProduct(
                                Product(categoryId = chosenCat.id, name = name, price = price)
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ManageActivity, "菜品 [$name] 添加成功", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("取消", null).show()
        }
    }

    private fun showAddStaffDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_staff, null, false)
        val etName = view.findViewById<EditText>(R.id.etStaffName)
        val etPin = view.findViewById<EditText>(R.id.etStaffPin)

        AlertDialog.Builder(this)
            .setTitle("新增收银员")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val pin = etPin.text.toString().trim()
                if (name.isNotEmpty() && pin.length >= 4) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        App.instance.database.posDao().insertStaff(
                            Staff(name = name, pinCode = pin, role = "CASHIER")
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ManageActivity, "员工 [$name] 添加成功, PIN: $pin", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "PIN码至少需要4位数字", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null).show()
    }
}
