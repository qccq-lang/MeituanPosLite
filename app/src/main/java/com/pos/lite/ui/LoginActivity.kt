package com.pos.lite.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pos.lite.App
import com.pos.lite.R
import com.pos.lite.data.Staff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val pinBuilder = StringBuilder()
    private lateinit var tvPinDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tvPinDisplay = findViewById(R.id.tvPinDisplay)
        setupPinKeypad()
    }

    private fun setupPinKeypad() {
        // 显式数字配置：彻底杜绝主题样式将字符变白
        val keyConfig = listOf(
            R.id.btn1 to "1", R.id.btn2 to "2", R.id.btn3 to "3",
            R.id.btn4 to "4", R.id.btn5 to "5", R.id.btn6 to "6",
            R.id.btn7 to "7", R.id.btn8 to "8", R.id.btn9 to "9",
            R.id.btnClear to "清空", R.id.btn0 to "0", R.id.btnDelete to "⌫"
        )

        for ((id, textVal) in keyConfig) {
            val tv = findViewById<TextView>(id)
            tv.text = textVal
            if (id == R.id.btnClear) {
                tv.setTextColor(Color.parseColor("#EF4444")) // 红色清空
            } else {
                tv.setTextColor(Color.parseColor("#111827")) // 纯黑高对比度
            }

            tv.setOnClickListener {
                when (id) {
                    R.id.btnClear -> clearDigits()
                    R.id.btnDelete -> deleteDigit()
                    else -> appendDigit(textVal)
                }
            }
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener { submitLogin() }
    }

    private fun appendDigit(d: String) {
        if (pinBuilder.length < 8) {
            pinBuilder.append(d)
            updatePinText()
        }
    }

    private fun deleteDigit() {
        if (pinBuilder.isNotEmpty()) {
            pinBuilder.deleteCharAt(pinBuilder.length - 1)
            updatePinText()
        }
    }

    private fun clearDigits() {
        pinBuilder.clear()
        updatePinText()
    }

    private fun updatePinText() {
        if (pinBuilder.isEmpty()) {
            tvPinDisplay.text = ""
        } else {
            tvPinDisplay.text = "● ".repeat(pinBuilder.length).trim()
        }
    }

    private fun submitLogin() {
        val pin = pinBuilder.toString()
        if (pin.isEmpty()) {
            Toast.makeText(this, "请输入PIN码", Toast.LENGTH_SHORT).show()
            return
        }
        performLogin(pin)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> appendDigit("0")
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> appendDigit("1")
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> appendDigit("2")
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> appendDigit("3")
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> appendDigit("4")
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> appendDigit("5")
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> appendDigit("6")
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> appendDigit("7")
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> appendDigit("8")
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> appendDigit("9")
            KeyEvent.KEYCODE_DEL -> deleteDigit()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> submitLogin()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun performLogin(pin: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = App.instance.database.posDao()
            var staff = dao.loginWithPin(pin)

            if (staff == null) {
                if (pin == "888888") {
                    staff = Staff(name = "店长", pinCode = "888888", role = "ADMIN")
                    dao.insertStaff(staff)
                } else if (pin == "1234") {
                    staff = Staff(name = "收银员01", pinCode = "1234", role = "CASHIER")
                    dao.insertStaff(staff)
                }
            }

            withContext(Dispatchers.Main) {
                if (staff != null) {
                    App.currentStaff = staff
                    Toast.makeText(this@LoginActivity, "欢迎: ${staff.name}", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "PIN码错误 (店长:888888, 收银员:1234)", Toast.LENGTH_LONG).show()
                    clearDigits()
                }
            }
        }
    }
}
