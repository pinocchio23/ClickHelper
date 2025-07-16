package com.example.clickhelper.ui.script

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clickhelper.MainActivity
import com.example.clickhelper.R
import com.example.clickhelper.model.Script
import com.example.clickhelper.model.ScriptEvent
import com.example.clickhelper.model.EventType
import com.example.clickhelper.model.ExecutionMode
import com.example.clickhelper.storage.ScriptStorage
import com.example.clickhelper.executor.ScriptExecutor
import com.example.clickhelper.util.TokenManager
import com.example.clickhelper.TokenVerificationActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class ScriptEditActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ScriptEditActivity"
        private const val REQUEST_CODE_RECORD_EVENT = 1001
        private const val REQUEST_CODE_EDIT_EVENT = 2000
    }
    
    private lateinit var script: Script
    private lateinit var eventAdapter: EventAdapter
    private lateinit var scriptExecutor: ScriptExecutor
    private lateinit var scriptStorage: ScriptStorage
    private lateinit var tokenManager: TokenManager
    
    // 广播接收器，用于接收脚本更新通知
    private val scriptUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val scriptId = intent?.getStringExtra("script_id")
            Log.d(TAG, "Received script update broadcast for script: $scriptId")
            
            if (scriptId == script.id) {
                // 当前脚本被更新，重新加载数据
                Log.d(TAG, "Current script was updated, reloading data")
                lifecycleScope.launch {
                    reloadScriptData()
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_edit)
        
        // 初始化组件
        scriptStorage = ScriptStorage(this)
        scriptExecutor = ScriptExecutor(this)
        tokenManager = TokenManager(this)
        
        // 从Intent获取脚本数据
        script = intent.getSerializableExtra("script") as Script
        
        // 设置当前正在编辑的脚本ID
        com.example.clickhelper.service.FloatingToolbarService.setCurrentEditingScriptId(script.id)
          // 设置自定义Toolbar作为ActionBar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // 设置标题和返回按钮
        supportActionBar?.apply {
            title = script.name
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        // 初始化UI
        initEventList()
        initExecutionModeSelection()
        
        // 设置添加事件按钮
        findViewById<FloatingActionButton>(R.id.fab_add_event).setOnClickListener {
            showAddEventDialog()
        }
        
        // 注册广播接收器
        val filter = IntentFilter("com.example.clickhelper.SCRIPT_UPDATED")
        registerReceiver(scriptUpdateReceiver, filter)
        Log.d(TAG, "Script update receiver registered")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.script_edit_menu, menu)
        
        // 根据执行模式和执行状态更新菜单项
        updateMenuItems(menu)
        
        return true
    }
    
    /**
     * 根据执行模式和执行状态更新菜单项
     */
    private fun updateMenuItems(menu: Menu?) {
        val stopItem = menu?.findItem(R.id.action_stop_script)
        val runItem = menu?.findItem(R.id.action_run_script)
        val isExecuting = ScriptExecutor.isAnyScriptExecuting()
        
        if (script.executionMode == ExecutionMode.REPEAT) {
            // 重复执行模式：显示播放和停止按钮
            stopItem?.isVisible = true
            runItem?.isVisible = true
            
            // 根据执行状态启用/禁用按钮
            runItem?.isEnabled = !isExecuting
            stopItem?.isEnabled = isExecuting
        } else {
            // 执行一次模式：只显示播放按钮
            stopItem?.isVisible = false
            runItem?.isVisible = true
            
            // 根据执行状态启用/禁用按钮
            runItem?.isEnabled = !isExecuting
        }
        
        Log.d(TAG, "菜单项状态更新: 执行中=$isExecuting, 运行按钮=${runItem?.isEnabled}, 停止按钮=${stopItem?.isEnabled}")
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        // 每次显示菜单前更新菜单项状态
        updateMenuItems(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // 处理返回按钮
                finish()
                true
            }
            R.id.action_run_script -> {
                runScript()
                true
            }
            R.id.action_stop_script -> {
                stopScript()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        
        // 检查Token是否仍然有效
        if (!tokenManager.isTokenValid()) {
            // Token已过期或无效，跳转到验证页面
            val intent = Intent(this, TokenVerificationActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // 更新Token活动时间
        tokenManager.updateLastActivity()
        
        // 在Activity恢复时重新加载脚本数据（以防被工具栏编辑）
        lifecycleScope.launch {
            reloadScriptData()
        }
        
        // 更新菜单项状态
        invalidateOptionsMenu()
    }

    override fun onPause() {
        super.onPause()
        // 在Activity暂停时保存脚本
        saveScript()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 在Activity销毁时保存脚本
        saveScript()
        // 清除当前正在编辑的脚本ID
        com.example.clickhelper.service.FloatingToolbarService.setCurrentEditingScriptId(null)
        // 取消广播接收器
        unregisterReceiver(scriptUpdateReceiver)
        Log.d(TAG, "Script update receiver unregistered")
    }

    private fun initEventList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_events)
        eventAdapter = EventAdapter(script.events, { event, position ->
            showEventOptions(event, position)
        }, {
            // 拖拽排序后保存脚本
            saveScript()
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = eventAdapter
        
        // 附加拖拽功能到RecyclerView
        eventAdapter.attachToRecyclerView(recyclerView)
    }
    
    private fun initExecutionModeSelection() {
        val radioGroup = findViewById<RadioGroup>(R.id.rg_execution_mode)
        val radioOnce = findViewById<RadioButton>(R.id.rb_execute_once)
        val radioRepeat = findViewById<RadioButton>(R.id.rb_execute_repeat)
        
        // 设置当前选择
        when (script.executionMode) {
            ExecutionMode.ONCE -> radioOnce.isChecked = true
            ExecutionMode.REPEAT -> radioRepeat.isChecked = true
        }
        
        // 监听选择变化
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_execute_once -> {
                    script.executionMode = ExecutionMode.ONCE
                    saveScript()
                    invalidateOptionsMenu() // 刷新菜单
                }
                R.id.rb_execute_repeat -> {
                    script.executionMode = ExecutionMode.REPEAT
                    saveScript()
                    invalidateOptionsMenu() // 刷新菜单
                }
            }
        }
    }
    
    private fun refreshExecutionModeSelection() {
        val radioGroup = findViewById<RadioGroup>(R.id.rg_execution_mode)
        val radioOnce = findViewById<RadioButton>(R.id.rb_execute_once)
        val radioRepeat = findViewById<RadioButton>(R.id.rb_execute_repeat)
        
        // 清除监听器，避免触发不必要的保存
        radioGroup.setOnCheckedChangeListener(null)
        
        // 设置当前选择
        when (script.executionMode) {
            ExecutionMode.ONCE -> radioOnce.isChecked = true
            ExecutionMode.REPEAT -> radioRepeat.isChecked = true
        }
        
        // 重新设置监听器
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_execute_once -> {
                    script.executionMode = ExecutionMode.ONCE
                    saveScript()
                    invalidateOptionsMenu() // 刷新菜单
                }
                R.id.rb_execute_repeat -> {
                    script.executionMode = ExecutionMode.REPEAT
                    saveScript()
                    invalidateOptionsMenu() // 刷新菜单
                }
            }
        }
    }

    private fun showAddEventDialog() {
        // 检查是否已经有OCR节点
        val hasOcrNode = script.events.any { it.type == EventType.OCR }
        if (hasOcrNode) {
            Toast.makeText(this, "已有识别数字节点，无法添加新节点。请先删除识别数字节点。", Toast.LENGTH_LONG).show()
            return
        }
        
        val items = arrayOf("点击", "滑动", "等待", "识别数字")
        val icons = arrayOf("👆", "👉", "⏱️", "👁️")
        val displayItems = items.mapIndexed { index, item -> "${icons[index]} $item" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("选择事件类型")
            .setItems(displayItems) { _, which ->
                when (which) {
                    0 -> {
                        // 点击事件 - 启动录制Activity
                        val intent = Intent(this, EventRecordActivity::class.java)
                        intent.putExtra("event_type", "CLICK")
                        startActivityForResult(intent, REQUEST_CODE_RECORD_EVENT)
                    }
                    1 -> {
                        // 滑动事件 - 启动录制Activity
                        val intent = Intent(this, EventRecordActivity::class.java)
                        intent.putExtra("event_type", "SWIPE")
                        startActivityForResult(intent, REQUEST_CODE_RECORD_EVENT)
                    }
                    2 -> {
                        // 等待事件 - 直接弹窗输入时间
                        showWaitEventDialog()
                    }
                    3 -> {
                        // 识别数字事件 - 启动录制Activity
                        val intent = Intent(this, EventRecordActivity::class.java)
                        intent.putExtra("event_type", "OCR")
                        startActivityForResult(intent, REQUEST_CODE_RECORD_EVENT)
                    }
                }
            }
            .show()
    }

    private fun showWaitEventDialog() {
        val editText = EditText(this)
        editText.hint = "等待时间（毫秒）"
        editText.setText("1000")
        
        AlertDialog.Builder(this)
            .setTitle("等待事件")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val duration = editText.text.toString().toIntOrNull() ?: 1000
                val event = ScriptEvent(EventType.WAIT, mapOf("duration" to duration))
                script.events.add(event)
                eventAdapter.notifyItemInserted(script.events.size - 1)
                saveScript() // 自动保存
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEventOptions(event: ScriptEvent, position: Int) {
        val options = arrayOf("编辑", "删除")
        
        AlertDialog.Builder(this)
            .setTitle("事件操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 编辑事件
                        when (event.type) {
                            EventType.CLICK -> editClickEvent(event, position)
                            EventType.SWIPE -> editSwipeEvent(event, position)
                            EventType.WAIT -> showEditWaitDialog(event, position)
                            EventType.OCR -> editOcrEvent(event, position)
                        }
                    }
                    1 -> {
                        // 删除事件
                        script.events.removeAt(position)
                        eventAdapter.notifyItemRemoved(position)
                        saveScript()
                    }
                }
            }
            .show()
    }

    private fun showEditWaitDialog(event: ScriptEvent, position: Int) {
        val editText = EditText(this)
        editText.hint = "等待时间（毫秒）"
        editText.setText(((event.params["duration"] as? Number)?.toInt() ?: 1000).toString())
        
        AlertDialog.Builder(this)
            .setTitle("编辑等待事件")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val duration = editText.text.toString().toIntOrNull() ?: 1000
                val newEvent = ScriptEvent(EventType.WAIT, mapOf("duration" to duration))
                script.events[position] = newEvent
                eventAdapter.notifyItemChanged(position)
                saveScript()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editClickEvent(event: ScriptEvent, position: Int) {
        val intent = Intent(this, EventRecordActivity::class.java)
        intent.putExtra("event_type", "CLICK")
        intent.putExtra("edit_mode", true)
        intent.putExtra("edit_position", position)
        intent.putExtra("existing_event", event)
        startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT + position)
    }

    private fun editSwipeEvent(event: ScriptEvent, position: Int) {
        val intent = Intent(this, EventRecordActivity::class.java)
        intent.putExtra("event_type", "SWIPE")
        intent.putExtra("edit_mode", true)
        intent.putExtra("edit_position", position)
        intent.putExtra("existing_event", event)
        startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT + position)
    }

    private fun editOcrEvent(event: ScriptEvent, position: Int) {
        val intent = Intent(this, EventRecordActivity::class.java)
        intent.putExtra("event_type", "OCR")
        intent.putExtra("edit_mode", true)
        intent.putExtra("edit_position", position)
        intent.putExtra("existing_event", event)
        startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT + position)
    }

    private fun saveScript() {
        lifecycleScope.launch {
            scriptStorage.saveScript(script)
        }
    }

    private fun reloadScriptData() {
        Log.d(TAG, "Reloading script data for script ID: ${script.id}")
        lifecycleScope.launch {
            val scripts = scriptStorage.loadScripts()
            val updatedScript = scripts.find { it.id == script.id }
            if (updatedScript != null) {
                script = updatedScript
                // 在主线程中更新UI
                runOnUiThread {
                    // 重新初始化事件列表以确保数据同步
                    initEventList()
                    // 刷新执行模式选择
                    refreshExecutionModeSelection()
                    // 刷新菜单
                    invalidateOptionsMenu()
                    Log.d(TAG, "Script data reloaded successfully.")
                }
            } else {
                Log.e(TAG, "Script with ID ${script.id} not found in storage.")
                // 在主线程中处理UI跳转
                runOnUiThread {
                    Toast.makeText(this@ScriptEditActivity, "脚本已被删除", Toast.LENGTH_SHORT).show()
                    // 跳转到主页面
                    val intent = Intent(this@ScriptEditActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK && data != null) {
            val event = data.getSerializableExtra("event") as? ScriptEvent
            if (event != null) {
                if (requestCode == REQUEST_CODE_RECORD_EVENT) {
                    // 新建事件
                    script.events.add(event)
                    eventAdapter.notifyItemInserted(script.events.size - 1)
                } else if (requestCode >= REQUEST_CODE_EDIT_EVENT) {
                    // 编辑事件
                    val position = requestCode - REQUEST_CODE_EDIT_EVENT
                    
                    // 检查位置是否有效
                    if (position >= 0 && position < script.events.size) {
                        script.events[position] = event
                        eventAdapter.notifyItemChanged(position)
                    } else {
                        // 如果位置无效，作为新事件添加
                        script.events.add(event)
                        eventAdapter.notifyItemInserted(script.events.size - 1)
                    }
                }
                saveScript()
            }
        }
    }

    private fun runScript() {
        if (script.events.isEmpty()) {
            Toast.makeText(this, "脚本中没有事件", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查是否有任何脚本正在执行（全局状态检查）
        if (ScriptExecutor.isAnyScriptExecuting()) {
            Toast.makeText(this, "已有脚本正在执行中，请等待完成", Toast.LENGTH_SHORT).show()
            return
        }

        if (scriptExecutor.isExecuting()) {
            Toast.makeText(this, "当前执行器正在执行脚本", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("执行脚本")
            .setMessage("确定要执行脚本 \"${script.name}\" 吗？")
            .setPositiveButton("执行") { _, _ ->
                executeScript()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeScript() {
        // 设置全局的ScriptExecutor实例，以便停止按钮可以停止脚本
        com.example.clickhelper.service.FloatingToolbarService.setGlobalScriptExecutor(scriptExecutor)
        
        scriptExecutor.executeScript(script, object : ScriptExecutor.ExecutionCallback {
                                    override fun onExecutionStart() {
                            runOnUiThread {
                                Toast.makeText(this@ScriptEditActivity, "开始执行脚本", Toast.LENGTH_SHORT).show()
                                // 更新菜单项状态
                                invalidateOptionsMenu()
                            }
                        }

                        override fun onExecutionComplete() {
                            runOnUiThread {
                                Toast.makeText(this@ScriptEditActivity, "脚本执行完成", Toast.LENGTH_SHORT).show()
                                // 更新菜单项状态
                                invalidateOptionsMenu()
                            }
                        }

                        override fun onExecutionError(error: String) {
                            runOnUiThread {
                                Toast.makeText(this@ScriptEditActivity, "执行失败: $error", Toast.LENGTH_LONG).show()
                                // 更新菜单项状态
                                invalidateOptionsMenu()
                            }
                        }

            override fun onEventExecuted(event: ScriptEvent, index: Int) {
                runOnUiThread {
                    // 可以在这里更新UI显示当前执行的事件
                }
            }
            
                                    override fun onExecutionStopped() {
                            runOnUiThread {
                                Toast.makeText(this@ScriptEditActivity, "脚本执行已停止", Toast.LENGTH_SHORT).show()
                                // 更新菜单项状态
                                invalidateOptionsMenu()
                            }
                        }

            override fun onNumberRecognitionSuccess(recognizedNumber: Double, targetNumber: Double, comparisonType: String) {
                runOnUiThread {
                    // Toast.makeText(this@ScriptEditActivity, "识别成功！识别到数字: $recognizedNumber, 条件: $comparisonType $targetNumber", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onTextRecognitionSuccess(recognizedText: String, targetText: String, comparisonType: String) {
                runOnUiThread {
                    // Toast.makeText(this@ScriptEditActivity, "识别成功！识别到文字: $recognizedText, 条件: $comparisonType $targetText", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onTokenExpired() {
                runOnUiThread {
                    Toast.makeText(this@ScriptEditActivity, "Token已过期，请重新验证", Toast.LENGTH_LONG).show()
                    // 跳转到token验证页面
                    val intent = Intent(this@ScriptEditActivity, com.example.clickhelper.TokenVerificationActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }
    
    private fun stopScript() {
        scriptExecutor.stopScript()
    }
} 