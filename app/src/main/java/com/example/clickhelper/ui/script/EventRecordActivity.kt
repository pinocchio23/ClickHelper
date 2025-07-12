package com.example.clickhelper.ui.script

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.clickhelper.R
import com.example.clickhelper.model.EventType
import com.example.clickhelper.model.ScriptEvent
import com.example.clickhelper.ui.view.OverlayView
import com.example.clickhelper.util.TokenManager
import com.example.clickhelper.TokenVerificationActivity

class EventRecordActivity : AppCompatActivity() {
    
    private lateinit var overlayView: OverlayView
    private lateinit var instructionText: TextView
    private lateinit var tokenManager: TokenManager
    private var eventType: EventType = EventType.CLICK
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var viewStartX = 0f
    private var viewStartY = 0f
    private var viewEndX = 0f
    private var viewEndY = 0f
    private var isRecording = false
    private var isEditMode = false
    private var editPosition = -1

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
        
        // 设置全屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.activity_event_record)

        // 获取事件类型
        eventType = when (intent.getStringExtra("event_type")) {
            "CLICK" -> EventType.CLICK
            "SWIPE" -> EventType.SWIPE
            "OCR" -> EventType.OCR
            else -> EventType.CLICK
        }
        
        // 检查是否是编辑模式
        isEditMode = intent.getBooleanExtra("edit_mode", false)
        editPosition = intent.getIntExtra("edit_position", -1)
        


        initViews()
        setupTouchListener()
        
        // 如果是编辑模式，加载现有事件数据
        if (isEditMode) {
            loadExistingEventData()
        }
    }

    private fun initViews() {
        overlayView = findViewById(R.id.overlay_view)
        instructionText = findViewById(R.id.tv_instruction)
        
        // 设置提示文本
        instructionText.text = when (eventType) {
            EventType.CLICK -> "请点击屏幕上的某个位置"
            EventType.SWIPE -> "请在屏幕上滑动"
            EventType.OCR -> "请拖拽选择要识别文本的矩形区域"
            else -> "请操作屏幕"
        }
        
        // 设置操作按钮
        findViewById<View>(R.id.btn_confirm).setOnClickListener {
            confirmEvent()
        }
        
        findViewById<View>(R.id.btn_cancel).setOnClickListener {
            finish()
        }
        
        findViewById<View>(R.id.btn_clear).setOnClickListener {
            clearRecording()
        }
    }
    
    private fun loadExistingEventData() {
        val existingEvent = intent.getSerializableExtra("existing_event") as? ScriptEvent
        if (existingEvent != null) {
            when (existingEvent.type) {
                EventType.CLICK -> {
                    // 加载屏幕坐标
                    startX = (existingEvent.params["x"] as? Number)?.toFloat() ?: 0f
                    startY = (existingEvent.params["y"] as? Number)?.toFloat() ?: 0f
                    endX = startX
                    endY = startY
                    // 对于绘制，我们需要转换为View坐标，但对于CLICK事件，通常屏幕坐标和View坐标相同（全屏Activity）
                    viewStartX = startX
                    viewStartY = startY
                    viewEndX = endX
                    viewEndY = endY
                    isRecording = true
                    
                    overlayView.setClickPoint(viewStartX, viewStartY)
                    overlayView.invalidate()
                    updateInstructionText()
                }
                EventType.SWIPE -> {
                    // 加载屏幕坐标
                    startX = (existingEvent.params["startX"] as? Number)?.toFloat() ?: 0f
                    startY = (existingEvent.params["startY"] as? Number)?.toFloat() ?: 0f
                    endX = (existingEvent.params["endX"] as? Number)?.toFloat() ?: 0f
                    endY = (existingEvent.params["endY"] as? Number)?.toFloat() ?: 0f
                    // 对于绘制，屏幕坐标和View坐标相同（全屏Activity）
                    viewStartX = startX
                    viewStartY = startY
                    viewEndX = endX
                    viewEndY = endY
                    isRecording = true
                    
                    overlayView.setSwipePath(viewStartX, viewStartY, viewEndX, viewEndY)
                    overlayView.invalidate()
                    updateInstructionText()
                }
                EventType.OCR -> {
                    // 加载屏幕坐标
                    startX = (existingEvent.params["left"] as? Number)?.toFloat() ?: 0f
                    startY = (existingEvent.params["top"] as? Number)?.toFloat() ?: 0f
                    endX = (existingEvent.params["right"] as? Number)?.toFloat() ?: 0f
                    endY = (existingEvent.params["bottom"] as? Number)?.toFloat() ?: 0f
                    // 对于绘制，屏幕坐标和View坐标相同（全屏Activity）
                    viewStartX = startX
                    viewStartY = startY
                    viewEndX = endX
                    viewEndY = endY
                    isRecording = true
                    
                    android.util.Log.d("EventRecordActivity", "加载OCR区域: 屏幕坐标($startX, $startY) -> ($endX, $endY)")
                    
                    overlayView.setOcrRect(viewStartX, viewStartY, viewEndX, viewEndY)
                    overlayView.invalidate()
                    updateInstructionText()
                }
                else -> {
                    // 未知事件类型，不处理
                }
            }
        }
    }

    private fun setupTouchListener() {
        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 保存屏幕绝对坐标（用于执行）
                    startX = event.rawX
                    startY = event.rawY
                    // 保存View坐标（用于绘制）
                    viewStartX = event.x
                    viewStartY = event.y
                    isRecording = true
                    
                    android.util.Log.d("EventRecordActivity", "记录起始坐标: 屏幕($startX, $startY), View($viewStartX, $viewStartY)")
                    
                    if (eventType == EventType.CLICK) {
                        // 点击事件立即显示
                        overlayView.setClickPoint(viewStartX, viewStartY)
                        overlayView.invalidate()
                    }
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (isRecording) {
                        // 保存屏幕绝对坐标（用于执行）
                        endX = event.rawX
                        endY = event.rawY
                        // 保存View坐标（用于绘制）
                        viewEndX = event.x
                        viewEndY = event.y
                        
                        when (eventType) {
                            EventType.SWIPE -> {
                                overlayView.setSwipePath(viewStartX, viewStartY, viewEndX, viewEndY)
                                overlayView.invalidate()
                            }
                            EventType.OCR -> {
                                overlayView.setOcrRect(viewStartX, viewStartY, viewEndX, viewEndY)
                                overlayView.invalidate()
                            }
                            EventType.CLICK, EventType.WAIT -> {
                                // 点击和等待事件不需要处理移动
                            }
                        }
                    }
                }
                
                MotionEvent.ACTION_UP -> {
                    if (isRecording) {
                        // 保存屏幕绝对坐标（用于执行）
                        endX = event.rawX
                        endY = event.rawY
                        // 保存View坐标（用于绘制）
                        viewEndX = event.x
                        viewEndY = event.y
                        
                        android.util.Log.d("EventRecordActivity", "记录结束坐标: 屏幕($endX, $endY), View($viewEndX, $viewEndY)")
                        
                        when (eventType) {
                            EventType.SWIPE -> {
                                overlayView.setSwipePath(viewStartX, viewStartY, viewEndX, viewEndY)
                                overlayView.invalidate()
                            }
                            EventType.OCR -> {
                                overlayView.setOcrRect(viewStartX, viewStartY, viewEndX, viewEndY)
                                overlayView.invalidate()
                            }
                            EventType.CLICK, EventType.WAIT -> {
                                // 点击和等待事件在ACTION_DOWN时已处理
                            }
                        }
                        
                        // 更新提示文本
                        updateInstructionText()
                    }
                }
            }
            true
        }
    }

    private fun updateInstructionText() {
        instructionText.text = when (eventType) {
            EventType.CLICK -> "点击位置: (${startX.toInt()}, ${startY.toInt()})\n点击下方按钮确认或取消"
            EventType.SWIPE -> {
                val distance = kotlin.math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
                if (distance < 50) {
                    "请滑动至少50像素的距离"
                } else {
                    "滑动轨迹: (${startX.toInt()}, ${startY.toInt()}) → (${endX.toInt()}, ${endY.toInt()})\n点击下方按钮确认或取消"
                }
            }
            EventType.OCR -> {
                val width = kotlin.math.abs(endX - startX)
                val height = kotlin.math.abs(endY - startY)
                if (width < 50 || height < 50) {
                    "请选择至少50x50像素的矩形区域"
                } else {
                    "文本识别区域: (${kotlin.math.min(startX, endX).toInt()}, ${kotlin.math.min(startY, endY).toInt()}) - (${kotlin.math.max(startX, endX).toInt()}, ${kotlin.math.max(startY, endY).toInt()})\n点击下方按钮确认或取消"
                }
            }
            else -> "操作完成，请确认"
        }
    }

    private fun confirmEvent() {
        if (!isRecording) {
            return
        }
        
        // 验证滑动事件的距离（使用屏幕坐标）
        if (eventType == EventType.SWIPE) {
            val distance = kotlin.math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
            if (distance < 50) {
                Toast.makeText(this, "滑动距离太短，请重新滑动", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // 验证OCR区域大小（使用屏幕坐标）
        if (eventType == EventType.OCR) {
            val width = kotlin.math.abs(endX - startX)
            val height = kotlin.math.abs(endY - startY)
            if (width < 50 || height < 50) {
                Toast.makeText(this, "识别区域太小，请重新选择", Toast.LENGTH_SHORT).show()
                return
            }
            
            android.util.Log.d("EventRecordActivity", "确认OCR区域: 屏幕坐标(${kotlin.math.min(startX, endX)}, ${kotlin.math.min(startY, endY)}) -> (${kotlin.math.max(startX, endX)}, ${kotlin.math.max(startY, endY)})")
            
            // 显示目标数字输入对话框
            showTargetTextDialog()
            return
        }
        
        val params = when (eventType) {
            EventType.CLICK -> mapOf(
                "x" to startX,
                "y" to startY
            )
            EventType.SWIPE -> mapOf(
                "startX" to startX,
                "startY" to startY,
                "endX" to endX,
                "endY" to endY
            )
            EventType.WAIT -> mapOf(
                "duration" to 1000 // 默认等待1秒，实际应该通过对话框输入
            )
            EventType.OCR -> emptyMap() // OCR事件在showTargetTextDialog中处理
        }
        
        val event = ScriptEvent(eventType, params)
        
        val resultIntent = Intent()
        resultIntent.putExtra("event", event)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun clearRecording() {
        isRecording = false
        overlayView.clear()
        overlayView.invalidate()
        
        instructionText.text = when (eventType) {
            EventType.CLICK -> "请点击屏幕上的某个位置"
            EventType.SWIPE -> "请在屏幕上滑动"
            EventType.OCR -> "请拖拽选择要识别文本的矩形区域"
            else -> "请操作屏幕"
        }
    }
    
    private fun showTargetTextDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_target_number, null)
        val editTextNumber = dialogView.findViewById<EditText>(R.id.et_target_number)
        val radioGroupComparison = dialogView.findViewById<android.widget.RadioGroup>(R.id.rg_comparison)
        val radioLessThan = dialogView.findViewById<android.widget.RadioButton>(R.id.rb_less_than)
        val radioEquals = dialogView.findViewById<android.widget.RadioButton>(R.id.rb_equals)
        
        // 添加"包含"选项
        val radioContains = android.widget.RadioButton(this)
        radioContains.text = "包含"
        radioContains.id = View.generateViewId()
        radioGroupComparison.addView(radioContains)
        
        editTextNumber.hint = "请输入目标文本或数字"
        
        // 初始状态：所有选项都隐藏，无默认选择
        radioLessThan.visibility = View.GONE
        radioEquals.visibility = View.GONE
        radioContains.visibility = View.GONE
        radioGroupComparison.clearCheck()
        
        // 监听输入变化，动态调整选项
        editTextNumber.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val input = s?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    val firstChar = input[0]
                    if (firstChar.isDigit()) {
                        // 数字输入，显示等于和小于选项，默认选择等于
                        radioEquals.visibility = View.VISIBLE
                        radioLessThan.visibility = View.VISIBLE
                        radioContains.visibility = View.GONE
                        
                        // 默认选择等于
                        radioEquals.isChecked = true
                    } else {
                        // 文字输入，只显示包含选项
                        radioEquals.visibility = View.GONE
                        radioLessThan.visibility = View.GONE
                        radioContains.visibility = View.VISIBLE
                        
                        // 默认选择包含
                        radioContains.isChecked = true
                    }
                } else {
                    // 空输入，隐藏所有选项
                    radioEquals.visibility = View.GONE
                    radioLessThan.visibility = View.GONE
                    radioContains.visibility = View.GONE
                    radioGroupComparison.clearCheck()
                }
            }
        })
        
        // 如果是编辑模式，预填充现有数据
        if (isEditMode) {
            val existingEvent = intent.getSerializableExtra("existing_event") as? ScriptEvent
            val existingTargetNumber = existingEvent?.params?.get("targetNumber") as? Number
            val existingTargetText = existingEvent?.params?.get("targetText") as? String
            val existingComparisonType = existingEvent?.params?.get("comparisonType") as? String
            
            if (existingTargetNumber != null) {
                editTextNumber.setText(existingTargetNumber.toString())
            } else if (existingTargetText != null) {
                editTextNumber.setText(existingTargetText)
            }
            
            // 编辑模式下根据现有数据设置选项
            when (existingComparisonType) {
                "小于" -> {
                    radioEquals.visibility = View.VISIBLE
                    radioLessThan.visibility = View.VISIBLE
                    radioContains.visibility = View.GONE
                    radioLessThan.isChecked = true
                }
                "等于" -> {
                    radioEquals.visibility = View.VISIBLE
                    radioLessThan.visibility = View.VISIBLE
                    radioContains.visibility = View.GONE
                    radioEquals.isChecked = true
                }
                "包含" -> {
                    radioEquals.visibility = View.GONE
                    radioLessThan.visibility = View.GONE
                    radioContains.visibility = View.VISIBLE
                    radioContains.isChecked = true
                }
                else -> {
                    // 保持初始状态
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("设置文本识别条件")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val targetText = editTextNumber.text.toString().trim()
                if (targetText.isNotEmpty()) {
                    val comparisonType = when {
                        radioEquals.isChecked -> "等于"
                        radioLessThan.isChecked -> "小于"
                        radioContains.isChecked -> "包含"
                        else -> {
                            // 如果没有选择，根据输入类型自动选择
                            if (targetText[0].isDigit()) "等于" else "包含"
                        }
                    }
                    
                    val left = kotlin.math.min(startX, endX)
                    val top = kotlin.math.min(startY, endY)
                    val right = kotlin.math.max(startX, endX)
                    val bottom = kotlin.math.max(startY, endY)
                    
                    android.util.Log.d("EventRecordActivity", "保存OCR节点: targetText=$targetText, comparisonType=$comparisonType")
                    android.util.Log.d("EventRecordActivity", "保存OCR坐标: 屏幕($left, $top) -> ($right, $bottom)")
                    
                    val params = if (comparisonType == "包含") {
                        // 文字识别
                        mapOf(
                            "left" to left,
                            "top" to top,
                            "right" to right,
                            "bottom" to bottom,
                            "targetText" to targetText,
                            "comparisonType" to comparisonType
                        )
                    } else {
                        // 数字识别
                        val targetNumber = targetText.toDoubleOrNull()
                        if (targetNumber == null) {
                            Toast.makeText(this, "数字格式不正确", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        mapOf(
                            "left" to left,
                            "top" to top,
                            "right" to right,
                            "bottom" to bottom,
                            "targetNumber" to targetNumber,
                            "comparisonType" to comparisonType
                        )
                    }
                    
                    android.util.Log.d("EventRecordActivity", "保存的OCR参数: $params")
                    val event = ScriptEvent(EventType.OCR, params)
                    
                    val resultIntent = Intent()
                    resultIntent.putExtra("event", event)
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                } else {
                    Toast.makeText(this, "目标文本不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
} 