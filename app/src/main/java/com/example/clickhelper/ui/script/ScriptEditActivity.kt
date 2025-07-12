package com.example.clickhelper.ui.script

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clickhelper.R
import com.example.clickhelper.storage.ScriptStorage
import kotlinx.coroutines.launch
import com.example.clickhelper.executor.ScriptExecutor
import com.example.clickhelper.model.EventType
import com.example.clickhelper.model.ExecutionMode
import com.example.clickhelper.model.Script
import com.example.clickhelper.model.ScriptEvent
import com.example.clickhelper.util.TokenManager
import com.example.clickhelper.TokenVerificationActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ScriptEditActivity : AppCompatActivity() {
    
    private lateinit var script: Script
    private lateinit var eventAdapter: EventAdapter
    private val scriptExecutor by lazy { ScriptExecutor(this) }
    private lateinit var scriptStorage: ScriptStorage
    private lateinit var tokenManager: TokenManager
    
    companion object {
        const val REQUEST_CODE_RECORD_EVENT = 1001
        const val REQUEST_CODE_EDIT_EVENT = 2000
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
        
        setContentView(R.layout.activity_script_edit)

        // 设置自定义Toolbar作为ActionBar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // 获取脚本对象
        script = intent.getSerializableExtra("script") as? Script ?: return
        
        // 设置当前正在编辑的脚本ID（用于工具栏编辑）
        com.example.clickhelper.service.FloatingToolbarService.setCurrentEditingScriptId(script.id)
        
        // 初始化存储
        scriptStorage = ScriptStorage(this)
        
        // 设置标题和返回按钮
        supportActionBar?.apply {
            title = script.name
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        
        // 初始化事件列表
        initEventList()
        
        // 添加事件按钮
        findViewById<FloatingActionButton>(R.id.fab_add_event).setOnClickListener {
            showAddEventDialog()
        }
        
        // 初始化执行模式选择
        initExecutionModeSelection()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.script_edit_menu, menu)
        
        // 根据执行模式显示不同的菜单项
        val stopItem = menu?.findItem(R.id.action_stop_script)
        val runItem = menu?.findItem(R.id.action_run_script)
        
        if (script.executionMode == ExecutionMode.REPEAT) {
            // 重复执行模式：显示播放和停止按钮
            stopItem?.isVisible = true
            runItem?.isVisible = true
        } else {
            // 执行一次模式：只显示播放按钮
            stopItem?.isVisible = false
            runItem?.isVisible = true
        }
        
        return true
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
        reloadScriptData()
    }
    
    /**
     * 重新加载脚本数据，确保与存储同步
     */
    private fun reloadScriptData() {
        lifecycleScope.launch {
            android.util.Log.d("ScriptEditActivity", "重新加载脚本数据...")
            val scripts = scriptStorage.loadScripts()
            val updatedScript = scripts.find { it.id == script.id }
            if (updatedScript != null) {
                val oldEventCount = script.events.size
                val newEventCount = updatedScript.events.size
                
                android.util.Log.d("ScriptEditActivity", "脚本数据已更新: 事件数量 $oldEventCount -> $newEventCount")
                
                // 比较事件是否有变化
                var hasChanges = oldEventCount != newEventCount
                if (!hasChanges && oldEventCount == newEventCount) {
                    for (i in 0 until oldEventCount) {
                        if (script.events[i].params != updatedScript.events[i].params) {
                            hasChanges = true
                            android.util.Log.d("ScriptEditActivity", "检测到事件 $i 参数变化")
                            android.util.Log.d("ScriptEditActivity", "原参数: ${script.events[i].params}")
                            android.util.Log.d("ScriptEditActivity", "新参数: ${updatedScript.events[i].params}")
                            break
                        }
                    }
                }
                
                if (hasChanges) {
                    android.util.Log.d("ScriptEditActivity", "检测到数据变化，更新UI")
                    script = updatedScript
                    // 重新初始化事件列表以确保数据同步
                    initEventList()
                    // 刷新执行模式选择
                    refreshExecutionModeSelection()
                    // 刷新菜单
                    invalidateOptionsMenu()
                } else {
                    android.util.Log.d("ScriptEditActivity", "数据无变化，保持现有UI")
                }
            } else {
                android.util.Log.w("ScriptEditActivity", "警告：无法找到脚本ID ${script.id}")
            }
        }
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
        val hasOcrEvent = script.events.any { it.type == EventType.OCR }
        if (hasOcrEvent) {
            Toast.makeText(this, "已有识别文本节点，无法添加新节点。请先删除识别文本节点。", Toast.LENGTH_LONG).show()
            return
        }
        
        val items = arrayOf("点击", "滑动", "等待", "识别文本")
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
                        // 识别文本事件 - 启动录制Activity
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
            android.util.Log.d("ScriptEditActivity", "编辑页面保存脚本: ${script.name}")
            android.util.Log.d("ScriptEditActivity", "编辑页面保存的事件数量: ${script.events.size}")
            script.events.forEachIndexed { index, event ->
                android.util.Log.d("ScriptEditActivity", "编辑页面保存事件 $index: ${event.type} - ${event.params}")
            }
            
            scriptStorage.saveScript(script)
            android.util.Log.d("ScriptEditActivity", "编辑页面脚本保存完成")
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

        if (scriptExecutor.isExecuting()) {
            Toast.makeText(this, "脚本正在执行中", Toast.LENGTH_SHORT).show()
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
        // 在执行前先保存脚本，确保数据同步
        lifecycleScope.launch {
            scriptStorage.saveScript(script)
            android.util.Log.d("ScriptEditActivity", "脚本已保存，开始执行: ${script.name}")
            android.util.Log.d("ScriptEditActivity", "执行的脚本事件数量: ${script.events.size}")
            script.events.forEachIndexed { index, event ->
                android.util.Log.d("ScriptEditActivity", "事件 $index: ${event.type} - ${event.params}")
            }
            
            // 设置全局的ScriptExecutor实例，以便停止按钮可以停止脚本
            com.example.clickhelper.service.FloatingToolbarService.setGlobalScriptExecutor(scriptExecutor)
            
            scriptExecutor.executeScript(script, object : ScriptExecutor.ExecutionCallback {
                override fun onExecutionStart() {
                    runOnUiThread {
                        Toast.makeText(this@ScriptEditActivity, "开始执行脚本", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onExecutionComplete() {
                    runOnUiThread {
                        Toast.makeText(this@ScriptEditActivity, "脚本执行完成", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onExecutionError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@ScriptEditActivity, "执行失败: $error", Toast.LENGTH_LONG).show()
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
                    }
                }

                override fun onNumberRecognitionSuccess(recognizedNumber: Double, targetNumber: Double, comparisonType: String) {
                    runOnUiThread {
                        Toast.makeText(this@ScriptEditActivity, "识别成功！识别到数字: $recognizedNumber, 条件: $comparisonType $targetNumber", Toast.LENGTH_LONG).show()
                    }
                }
                
                override fun onTextRecognitionSuccess(recognizedText: String, targetText: String, comparisonType: String) {
                    runOnUiThread {
                        Toast.makeText(this@ScriptEditActivity, "识别成功！识别到文字: $recognizedText, 条件: $comparisonType $targetText", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }
    
    private fun stopScript() {
        scriptExecutor.stopScript()
    }
} 