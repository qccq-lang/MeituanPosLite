package com.pos.lite.ui

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pos.lite.App
import com.pos.lite.data.Category
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
            val input = EditText(this).apply {
                hint = "输入分类名称 (如: 烧烤、主食、饮料)"
            }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
                addView(input)
            }

            AlertDialog.Builder(this)
                .setTitle("新增商品分类")
                .setView(container)
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
                .setNegativeButton("取消", null)
                .show()
        }

        // 2. 新增菜品
        binding.btnAddProduct.setOnClickListener {
            showAddProductDialog()
        }

        // 3. 新增员工
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

            val etName = EditText(this@ManageActivity).apply {
                hint = "菜品名称 (例如: 宫保鸡丁)"
            }
            val etPrice = EditText(this@ManageActivity).apply {
                hint = "单价 (元)"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            val btnCat = Button(this@ManageActivity).apply {
                text = "所属分类: " + catNames[0]
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
                .setTitle("新增商品/菜品")
                .setView(layout)
                .setPositiveButton("保存") { _, _ ->
                    val name = etName.text.toString().trim()
                    val price = etPrice.text.toString().trim().toDoubleOrNull() ?: 0.0

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
                    } else {
                        Toast.makeText(this@ManageActivity, "请输入正确的名称和单价", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showAddStaffDialog() {
        val etName = EditText(this).apply {
            hint = "员工姓名 (例如: 张三)"
        }
        val etPin = EditText(this).apply {
            hint = "登录PIN码 (4~6位数字)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(etName)
            addView(etPin)
        }

        AlertDialog.Builder(this)
            .setTitle("新增收银员")
            .setView(layout)
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
            .setNegativeButton("取消", null)
            .show()
    }
}
