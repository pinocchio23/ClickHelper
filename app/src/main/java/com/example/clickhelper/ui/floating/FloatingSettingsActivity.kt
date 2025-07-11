package com.example.clickhelper.ui.floating

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.clickhelper.R
import com.example.clickhelper.model.EventType
import com.example.clickhelper.ui.script.EventRecordActivity
import com.example.clickhelper.util.TokenManager
import com.example.clickhelper.TokenVerificationActivity

class FloatingSettingsActivity : AppCompatActivity() {
    
    private lateinit var tokenManager: TokenManager
    
    companion object {
        private const val REQUEST_CODE_RECORD_EVENT = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化TokenManager并验证Token
        tokenManager = TokenManager(this)
        if (!tokenManager.isTokenValid()) {
            // Token无效，跳转到验证页面
            val intent = Intent(this, TokenVerificationActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        setContentView(R.layout.activity_floating_settings)
        
        supportActionBar?.title = "悬浮操作"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setupEventButtons()
    }
    
    private fun setupEventButtons() {
        findViewById<LinearLayout>(R.id.btn_add_click).setOnClickListener {
            startEventRecord(EventType.CLICK)
        }
        
        findViewById<LinearLayout>(R.id.btn_add_swipe).setOnClickListener {
            startEventRecord(EventType.SWIPE)
        }
        
        findViewById<LinearLayout>(R.id.btn_add_wait).setOnClickListener {
            // TODO: 显示等待时间输入对话框
        }
        
        findViewById<LinearLayout>(R.id.btn_add_ocr).setOnClickListener {
            startEventRecord(EventType.OCR)
        }
    }
    
    private fun startEventRecord(eventType: EventType) {
        val intent = Intent(this, EventRecordActivity::class.java)
        intent.putExtra("event_type", eventType.name)
        intent.putExtra("floating_mode", true)
        startActivityForResult(intent, REQUEST_CODE_RECORD_EVENT)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_RECORD_EVENT && resultCode == Activity.RESULT_OK) {
            // 事件录制完成，关闭设置界面
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 