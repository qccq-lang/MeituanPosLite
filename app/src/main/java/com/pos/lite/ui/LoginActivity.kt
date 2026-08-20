package com.pos.lite.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pos.lite.App
import com.pos.lite.R
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
        val numIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )
        for (id in numIds) {
            findViewById<Button>(id).setOnClickListener {
                if (pinBuilder.length < 8) {
                    pinBuilder.append((it as Button).text)
                    updatePinText()
                }
            }
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            if (pinBuilder.isNotEmpty()) {
                pinBuilder.deleteCharAt(pinBuilder.length - 1)
                updatePinText()
            }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            pinBuilder.clear()
            updatePinText()
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val pin = pinBuilder.toString()
            if (pin.isEmpty()) {
                Toast.makeText(this, "请输入PIN码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performLogin(pin)
        }
    }

    private fun updatePinText() {
        tvPinDisplay.text = "●".repeat(pinBuilder.length)
    }

    private fun performLogin(pin: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val staff = App.instance.database.posDao().loginWithPin(pin)
            withContext(Dispatchers.Main) {
                if (staff != null) {
                    App.currentStaff = staff
                    Toast.makeText(this@LoginActivity, "欢迎: ${staff.name}", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "PIN码错误 (初始店长:888888, 收银:1234)", Toast.LENGTH_LONG).show()
                    pinBuilder.clear()
                    updatePinText()
                }
            }
        }
    }
}
