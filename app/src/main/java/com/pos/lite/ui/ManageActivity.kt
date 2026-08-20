package com.pos.lite.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pos.lite.App
import com.pos.lite.data.Category
import com.pos.lite.data.Staff
import com.pos.lite.databinding.ActivityManageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAddCategory.setOnClickListener {
            val input = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("新增分类")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.database.posDao().insertCategory(Category(name = name))
                        }
                    }
                }.setNegativeButton("取消", null).show()
        }

        binding.btnAddStaff.setOnClickListener {
            if (App.currentStaff?.role != "ADMIN") {
                Toast.makeText(this, "仅店长有权添加员工", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 简单添加
            lifecycleScope.launch(Dispatchers.IO) {
                App.instance.database.posDao().insertStaff(Staff(name = "新收银员", pinCode = "0000"))
            }
            Toast.makeText(this, "已添加测试员工(PIN:0000)", Toast.LENGTH_SHORT).show()
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
