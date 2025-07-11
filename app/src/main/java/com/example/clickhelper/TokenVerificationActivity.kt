package com.example.clickhelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.clickhelper.util.TokenManager

class TokenVerificationActivity : AppCompatActivity() {

    private lateinit var etToken: EditText
    private lateinit var btnVerify: Button
    private lateinit var tvStatus: TextView
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_token_verification)

        // 初始化TokenManager
        tokenManager = TokenManager(this)
        
        // 检查是否已经有有效的Token
        if (tokenManager.isTokenValid()) {
            // Token有效，直接跳转到主界面
            startMainActivity()
            return
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etToken = findViewById(R.id.et_token)
        btnVerify = findViewById(R.id.btn_verify)
        tvStatus = findViewById(R.id.tv_status)
        
        // 设置初始状态
        updateStatusText()
    }

    private fun setupListeners() {
        // 监听文本变化，实时验证
        etToken.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateStatusText()
            }
        })

        // 验证按钮点击事件
        btnVerify.setOnClickListener {
            val inputToken = etToken.text.toString().trim()
            
            if (inputToken.isEmpty()) {
                showToast(getString(R.string.token_hint))
                return@setOnClickListener
            }
            
            verifyToken(inputToken)
        }
    }

    private fun verifyToken(token: String) {
        if (tokenManager.verifyToken(token)) {
            // Token验证成功
            tokenManager.saveToken(token)
            showToast(getString(R.string.token_verification_success))
            
            // 跳转到主界面
            startMainActivity()
        } else {
            // Token验证失败
            showToast(getString(R.string.token_verification_failed))
            etToken.selectAll()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun updateStatusText() {
        val inputToken = etToken.text.toString().trim()
        val remainingTime = tokenManager.getRemainingTime()
        
        when {
            tokenManager.isTokenValid() -> {
                tvStatus.text = "${getString(R.string.token_status_valid)} (${getString(R.string.token_remaining_time, formatTime(remainingTime))})"
                tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            }
            inputToken.isEmpty() -> {
                tvStatus.text = getString(R.string.token_verification_subtitle)
                tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            }
            else -> {
                tvStatus.text = "待验证..."
                tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}${getString(R.string.token_days)}${hours % 24}${getString(R.string.token_hours)}"
            hours > 0 -> "${hours}${getString(R.string.token_hours)}${minutes % 60}${getString(R.string.token_minutes)}"
            minutes > 0 -> "${minutes}${getString(R.string.token_minutes)}"
            else -> "${seconds}秒"
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        // 显示退出确认对话框
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_app_title))
            .setMessage(getString(R.string.exit_app_message))
            .setPositiveButton(getString(R.string.exit_app_confirm)) { _, _ ->
                super.onBackPressed()
                finishAffinity() // 完全退出应用
            }
            .setNegativeButton(getString(R.string.token_dialog_cancel), null)
            .show()
    }
} 