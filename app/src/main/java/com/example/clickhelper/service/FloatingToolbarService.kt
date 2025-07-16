package com.example.clickhelper.service

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.clickhelper.R
import com.example.clickhelper.model.EventType
import com.example.clickhelper.model.ScriptEvent
import com.example.clickhelper.model.Script
import com.example.clickhelper.storage.ScriptStorage
import com.example.clickhelper.util.TokenManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.clickhelper.executor.ScriptExecutor
import android.text.Editable
import android.text.TextWatcher

class FloatingToolbarService : Service() {
    
    // 自定义绘制覆盖层
    class DrawingOverlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {
        
        private val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        private val pointPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            isAntiAlias = true
        }
        
        private var eventType: EventType? = null
        private var clickX = 0f
        private var clickY = 0f
        private var swipeStartX = 0f
        private var swipeStartY = 0f
        private var swipeEndX = 0f
        private var swipeEndY = 0f
        private var ocrLeft = 0f
        private var ocrTop = 0f
        private var ocrRight = 0f
        private var ocrBottom = 0f
        
        fun setClickPoint(x: Float, y: Float) {
            eventType = EventType.CLICK
            clickX = x
            clickY = y
            invalidate()
        }
        
        fun setSwipePath(startX: Float, startY: Float, endX: Float, endY: Float) {
            eventType = EventType.SWIPE
            swipeStartX = startX
            swipeStartY = startY
            swipeEndX = endX
            swipeEndY = endY
            invalidate()
        }
        
        fun setOcrRect(left: Float, top: Float, right: Float, bottom: Float) {
            eventType = EventType.OCR
            ocrLeft = left
            ocrTop = top
            ocrRight = right
            ocrBottom = bottom
            invalidate()
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            when (eventType) {
                EventType.CLICK -> {
                    // 绘制点击点
                    canvas.drawCircle(clickX, clickY, 30f, pointPaint)
                    canvas.drawCircle(clickX, clickY, 30f, paint)
                    canvas.drawText("点击", clickX - 30f, clickY - 50f, textPaint)
                }
                EventType.SWIPE -> {
                    // 绘制滑动轨迹
                    canvas.drawLine(swipeStartX, swipeStartY, swipeEndX, swipeEndY, paint)
                    // 绘制起点
                    canvas.drawCircle(swipeStartX, swipeStartY, 20f, pointPaint)
                    canvas.drawText("起点", swipeStartX - 30f, swipeStartY - 30f, textPaint)
                    // 绘制终点
                    canvas.drawCircle(swipeEndX, swipeEndY, 20f, pointPaint)
                    canvas.drawText("终点", swipeEndX - 30f, swipeEndY - 30f, textPaint)
                    
                    // 绘制箭头
                    val arrowLength = 40f
                    val arrowOffset = 25f // 箭头相对于终点的偏移距离，避免与终点圆圈重叠
                    val angle = Math.atan2((swipeEndY - swipeStartY).toDouble(), (swipeEndX - swipeStartX).toDouble())
                    val arrowAngle1 = angle + Math.PI / 6
                    val arrowAngle2 = angle - Math.PI / 6
                    
                    // 计算箭头起始位置，向起点方向偏移一定距离
                    val arrowStartX = swipeEndX - arrowOffset * Math.cos(angle).toFloat()
                    val arrowStartY = swipeEndY - arrowOffset * Math.sin(angle).toFloat()
                    
                    val arrowX1 = arrowStartX - arrowLength * Math.cos(arrowAngle1).toFloat()
                    val arrowY1 = arrowStartY - arrowLength * Math.sin(arrowAngle1).toFloat()
                    val arrowX2 = arrowStartX - arrowLength * Math.cos(arrowAngle2).toFloat()
                    val arrowY2 = arrowStartY - arrowLength * Math.sin(arrowAngle2).toFloat()
                    
                    canvas.drawLine(arrowStartX, arrowStartY, arrowX1, arrowY1, paint)
                    canvas.drawLine(arrowStartX, arrowStartY, arrowX2, arrowY2, paint)
                }
                EventType.OCR -> {
                    // 绘制OCR识别区域
                    canvas.drawRect(ocrLeft, ocrTop, ocrRight, ocrBottom, paint)
                    canvas.drawText("识别区域", ocrLeft, ocrTop - 10f, textPaint)
                }
                else -> {}
            }
        }
    }
    
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var isExpanded = false
    private var currentEventType: EventType? = null
    private var isRecordingEvent = false
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        const val ACTION_SHOW_TOOLBAR = "SHOW_TOOLBAR"
        const val ACTION_HIDE_TOOLBAR = "HIDE_TOOLBAR"
        private const val TAG = "FloatingToolbarService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "floating_toolbar_channel"
        
        // 全局的ScriptExecutor实例
        private var globalScriptExecutor: ScriptExecutor? = null
        
        // 当前正在编辑的脚本ID（用于工具栏编辑）
        private var currentEditingScriptId: String? = null
        
        fun getGlobalScriptExecutor(): ScriptExecutor? {
            return globalScriptExecutor
        }
        
        fun setGlobalScriptExecutor(executor: ScriptExecutor?) {
            globalScriptExecutor = executor
        }
        
        fun setCurrentEditingScriptId(scriptId: String?) {
            currentEditingScriptId = scriptId
        }
        
        fun getCurrentEditingScriptId(): String? {
            return currentEditingScriptId
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingToolbarService onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        
        // 验证Token
        val tokenManager = TokenManager(this)
        if (!tokenManager.isTokenValid()) {
            Log.d(TAG, "Token is invalid, stopping service")
            Toast.makeText(this, getString(R.string.token_expired_message), Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        when (intent?.action) {
            ACTION_SHOW_TOOLBAR -> {
                Log.d(TAG, "Showing floating toolbar")
                showFloatingToolbar()
            }
            ACTION_HIDE_TOOLBAR -> {
                Log.d(TAG, "Hiding floating toolbar")
                hideFloatingToolbar()
            }
            else -> {
                Log.d(TAG, "No action specified, showing toolbar by default")
                showFloatingToolbar()
            }
        }
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮工具栏",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮工具栏服务"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮工具栏")
            .setContentText("悬浮工具栏正在运行")
            .setSmallIcon(R.drawable.ic_floating_toolbar)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun showFloatingToolbar() {
        Log.d(TAG, "showFloatingToolbar called, current floatingView: $floatingView")
        if (floatingView != null) {
            Log.d(TAG, "FloatingView already exists, returning")
            return
        }
        
        try {
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_toolbar, null)
            Log.d(TAG, "FloatingView inflated successfully")
            
            val layoutParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                x = 0 // 贴左边缘
                y = 0 // 垂直居中
            }
            
            Log.d(TAG, "LayoutParams configured: type=${layoutParams.type}, flags=${layoutParams.flags}")
            
            setupToolbarButtons()
            setupDragFunctionality(layoutParams)
            
            windowManager.addView(floatingView, layoutParams)
            Log.d(TAG, "FloatingView added to WindowManager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating toolbar", e)
        }
    }
    
    private fun setupToolbarButtons() {
        floatingView?.let { view ->
            Log.d(TAG, "Setting up toolbar buttons")
            val btnPlay = view.findViewById<ImageButton>(R.id.btn_play)
            val btnStop = view.findViewById<ImageButton>(R.id.btn_stop)
            val btnSettings = view.findViewById<ImageButton>(R.id.btn_settings)
            val btnClose = view.findViewById<ImageButton>(R.id.btn_close)
            
            // 根据脚本执行状态更新按钮状态
            updateButtonStates(btnPlay, btnStop)
            
            btnPlay.setOnClickListener {
                Log.d(TAG, "Play button clicked")
                // 检查是否有脚本正在执行
                if (ScriptExecutor.isAnyScriptExecuting()) {
                    Toast.makeText(this@FloatingToolbarService, "已有脚本正在执行中，请等待完成", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // 执行脚本
                executeCurrentScript()
            }
            
            btnStop.setOnClickListener {
                Log.d(TAG, "Stop button clicked")
                // 停止脚本
                if (ScriptExecutor.isAnyScriptExecuting()) {
                    globalScriptExecutor?.stopScript()
                    // 更新按钮状态
                    updateButtonStates(btnPlay, btnStop)
                } else {
                    Toast.makeText(this@FloatingToolbarService, "没有脚本正在执行", Toast.LENGTH_SHORT).show()
                }
            }
            
            btnSettings.setOnClickListener {
                Log.d(TAG, "Settings button clicked")
                // 编辑现有事件
                editCurrentEvent()
            }
            
            btnClose.setOnClickListener {
                Log.d(TAG, "Close button clicked")
                // 关闭悬浮工具栏
                val intent = Intent(this, FloatingToolbarService::class.java)
                intent.action = ACTION_HIDE_TOOLBAR
                startService(intent)
            }
            
            Log.d(TAG, "Toolbar buttons setup completed")
        }
    }
    
    /**
     * 根据脚本执行状态更新按钮状态
     */
    private fun updateButtonStates(btnPlay: ImageButton, btnStop: ImageButton) {
        val isExecuting = ScriptExecutor.isAnyScriptExecuting()
        
        // 执行按钮：脚本未执行时启用，执行时禁用
        btnPlay.isEnabled = !isExecuting
        btnPlay.alpha = if (isExecuting) 0.5f else 1.0f
        
        // 停止按钮：脚本执行时启用，未执行时禁用
        btnStop.isEnabled = isExecuting
        btnStop.alpha = if (isExecuting) 1.0f else 0.5f
        
        Log.d(TAG, "按钮状态更新: 执行中=$isExecuting, 播放按钮=${if (btnPlay.isEnabled) "启用" else "禁用"}, 停止按钮=${if (btnStop.isEnabled) "启用" else "禁用"}")
    }
    
    private fun setupDragFunctionality(layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        
        // 创建智能的拖拽触摸监听器，支持点击和拖拽共存
        val dragTouchListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false // 不消费DOWN事件，让点击事件能够正常开始
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val distance = kotlin.math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())
                    
                    // 只有当移动距离较大时才开始拖拽
                    if (!isDragging && distance > 30) { // 增加拖拽阈值
                        isDragging = true
                        // 取消按钮的按下状态，防止点击事件触发
                        if (view is ImageButton) {
                            view.isPressed = false
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    
                    if (isDragging) {
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        
                        // 限制在屏幕边界内
                        val displayMetrics = resources.displayMetrics
                        val maxX = displayMetrics.widthPixels - (floatingView?.width ?: 0)
                        val maxY = displayMetrics.heightPixels - (floatingView?.height ?: 0)
                        
                        layoutParams.x = layoutParams.x.coerceIn(0, maxX)
                        layoutParams.y = layoutParams.y.coerceIn(0, maxY)
                        
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        true // 消费拖拽事件
                    } else {
                        false // 不消费事件，让点击事件继续
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val wasDragging = isDragging
                    isDragging = false
                    if (wasDragging) {
                        // 拖拽结束，取消按钮状态
                        if (view is ImageButton) {
                            view.isPressed = false
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        true // 消费拖拽结束事件
                    } else {
                        false // 不消费事件，让点击事件正常触发
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    if (view is ImageButton) {
                        view.isPressed = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                else -> false
            }
        }
        
        // 为整个浮动视图设置拖拽监听器
        floatingView?.setOnTouchListener(dragTouchListener)
        
        // 为每个按钮也设置拖拽监听器，这样点击按钮也能拖拽
        floatingView?.let { view ->
            val btnPlay = view.findViewById<ImageButton>(R.id.btn_play)
            val btnStop = view.findViewById<ImageButton>(R.id.btn_stop)
            val btnSettings = view.findViewById<ImageButton>(R.id.btn_settings)
            val btnClose = view.findViewById<ImageButton>(R.id.btn_close)
            
            btnPlay.setOnTouchListener(dragTouchListener)
            btnStop.setOnTouchListener(dragTouchListener)
            btnSettings.setOnTouchListener(dragTouchListener)
            btnClose.setOnTouchListener(dragTouchListener)
        }
    }
    
    private fun showEventTypeDialog() {
        val items = arrayOf("点击", "滑动", "等待", "识别数字")
        val icons = arrayOf("👆", "👉", "⏱️", "👁️")
        val displayItems = items.mapIndexed { index, item -> "${icons[index]} $item" }.toTypedArray()
        
        val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("选择事件类型")
            .setItems(displayItems) { dialog, which ->
                when (which) {
                    0 -> startEventRecording(EventType.CLICK)
                    1 -> startEventRecording(EventType.SWIPE)
                    2 -> showWaitEventDialog()
                    3 -> startEventRecording(EventType.OCR)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create()
        
        // 设置对话框为系统级别
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        alertDialog.show()
    }
    
    private fun startEventRecording(eventType: EventType) {
        currentEventType = eventType
        isRecordingEvent = true
        
        when (eventType) {
            EventType.CLICK -> {
                Toast.makeText(this, "请点击屏幕上的位置", Toast.LENGTH_SHORT).show()
                createEventOverlay()
            }
            EventType.SWIPE -> {
                Toast.makeText(this, "请在屏幕上滑动", Toast.LENGTH_SHORT).show()
                createEventOverlay()
            }
            EventType.OCR -> {
                Toast.makeText(this, "请拖拽选择要识别数字的区域", Toast.LENGTH_SHORT).show()
                createEventOverlay()
            }
            else -> {
                // 其他类型事件
            }
        }
    }
    
    private fun createEventOverlay() {
        // 创建透明的全屏覆盖层用于捕获触摸事件
        overlayView = View(this).apply {
            setBackgroundColor(0x20000000) // 半透明黑色
        }
        
        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            format = PixelFormat.TRANSLUCENT
            x = 0
            y = 0
            gravity = Gravity.TOP or Gravity.START
        }
        
        var startX = 0f
        var startY = 0f
        var endX = 0f
        var endY = 0f
        
        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 保存屏幕坐标（用于执行）
                    startX = event.rawX
                    startY = event.rawY
                    Log.d(TAG, "记录起始坐标: ($startX, $startY)")
                    Log.d(TAG, "View坐标: (${event.x}, ${event.y})")
                }
                MotionEvent.ACTION_UP -> {
                    // 保存屏幕坐标（用于执行）
                    endX = event.rawX
                    endY = event.rawY
                    Log.d(TAG, "记录结束坐标: ($endX, $endY)")
                    Log.d(TAG, "View坐标: (${event.x}, ${event.y})")
                    
                    // 处理事件
                    handleEventCapture(startX, startY, endX, endY)
                    
                    // 移除覆盖层
                    removeEventOverlay()
                }
            }
            true
        }
        
        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event overlay", e)
        }
    }
    
    private fun removeEventOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing event overlay", e)
            }
        }
        overlayView = null
        isRecordingEvent = false
    }
    
    private fun handleEventCapture(startX: Float, startY: Float, endX: Float, endY: Float) {
        when (currentEventType) {
            EventType.CLICK -> {
                val event = ScriptEvent(EventType.CLICK, mapOf(
                    "x" to startX,
                    "y" to startY
                ))
                Toast.makeText(this, "点击事件已记录: (${startX.toInt()}, ${startY.toInt()})", Toast.LENGTH_SHORT).show()
                // TODO: 保存事件到当前脚本
            }
            EventType.SWIPE -> {
                val distance = kotlin.math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
                if (distance < 50) {
                    Toast.makeText(this, "滑动距离太短，请重新操作", Toast.LENGTH_SHORT).show()
                } else {
                    val event = ScriptEvent(EventType.SWIPE, mapOf(
                        "startX" to startX,
                        "startY" to startY,
                        "endX" to endX,
                        "endY" to endY
                    ))
                    Toast.makeText(this, "滑动事件已记录", Toast.LENGTH_SHORT).show()
                    // TODO: 保存事件到当前脚本
                }
            }
            EventType.OCR -> {
                val width = kotlin.math.abs(endX - startX)
                val height = kotlin.math.abs(endY - startY)
                if (width < 50 || height < 50) {
                    Toast.makeText(this, "选择区域太小，请重新操作", Toast.LENGTH_SHORT).show()
                } else {
                    showNumberRecognitionDialog(startX, startY, endX, endY)
                }
            }
            else -> {
                // 其他事件类型
            }
        }
    }
    
    private fun showWaitEventDialog() {
        val editText = EditText(this).apply {
            hint = "等待时间（毫秒）"
            setText("1000")
        }
        
        val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("等待事件")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val duration = editText.text.toString().toIntOrNull() ?: 1000
                val event = ScriptEvent(EventType.WAIT, mapOf("duration" to duration))
                Toast.makeText(this, "等待事件已记录: ${duration}ms", Toast.LENGTH_SHORT).show()
                // TODO: 保存事件到当前脚本
            }
            .setNegativeButton("取消", null)
            .create()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        alertDialog.show()
    }
    
    private fun showNumberRecognitionDialog(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_target_number, null)
        val editTextNumber = dialogView.findViewById<EditText>(R.id.et_target_number)
        val radioGroupComparison = dialogView.findViewById<RadioGroup>(R.id.rg_comparison)
        val radioLessThan = dialogView.findViewById<RadioButton>(R.id.rb_less_than)
        val radioEquals = dialogView.findViewById<RadioButton>(R.id.rb_equals)
        
        editTextNumber.hint = "请输入目标数字"
        
        // 默认选择小于
        radioLessThan.isChecked = true
        
        // 监听输入变化，动态调整选项
        var lastInputType: String? = null // 记录上次输入的类型
        editTextNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: ""
                val currentInputType = when {
                    input.isEmpty() -> "empty"
                    input[0].isDigit() -> "number"
                    else -> "text"
                }
                
                // 只在输入类型改变时才调整选项和默认选择
                if (lastInputType != currentInputType) {
                    when (currentInputType) {
                        "number" -> {
                            // 数字输入，显示等于和小于选项
                            radioLessThan.visibility = View.VISIBLE
                            radioEquals.visibility = View.VISIBLE
                            
                            // 只在从文字类型切换到数字类型时设置默认选择
                            if (lastInputType == "text") {
                                // 如果之前是文字输入，默认选择小于
                                radioLessThan.isChecked = true
                            } else if (lastInputType == "empty") {
                                // 如果之前是空输入，只在没有选择时设置默认值
                                if (!radioLessThan.isChecked && !radioEquals.isChecked) {
                                    radioLessThan.isChecked = true
                                }
                            }
                            // 如果之前已经是数字输入，保持用户的选择不变
                        }
                        "text" -> {
                            // 文字输入，只显示等于选项
                            radioLessThan.visibility = View.GONE
                            radioEquals.visibility = View.VISIBLE
                            
                            // 文字输入强制选择等于
                            radioEquals.isChecked = true
                        }
                        "empty" -> {
                            // 空输入，显示所有选项，但不改变用户的选择
                            radioLessThan.visibility = View.VISIBLE
                            radioEquals.visibility = View.VISIBLE
                            // 不重置用户的选择状态
                        }
                    }
                    lastInputType = currentInputType
                }
            }
        })
        
        val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("设置文本识别条件")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val targetText = editTextNumber.text.toString().trim()
                if (targetText.isNotEmpty()) {
                    val comparisonType = when {
                        radioLessThan.isChecked -> "小于"
                        radioEquals.isChecked -> "等于"
                        else -> "小于"
                    }
                    
                    val left = kotlin.math.min(startX, endX)
                    val top = kotlin.math.min(startY, endY)
                    val right = kotlin.math.max(startX, endX)
                    val bottom = kotlin.math.max(startY, endY)
                    val width = right - left
                    val height = bottom - top
                    
                    // 判断是数字还是文字
                    val targetNumber = targetText.toDoubleOrNull()
                    val event = if (targetNumber != null) {
                        // 数字识别
                        ScriptEvent(EventType.OCR, mapOf(
                            "left" to left,
                            "top" to top,
                            "right" to right,
                            "bottom" to bottom,
                            "targetNumber" to targetNumber,
                            "comparisonType" to comparisonType
                        ))
                    } else {
                        // 文字识别（只支持等于）
                        ScriptEvent(EventType.OCR, mapOf(
                            "left" to left,
                            "top" to top,
                            "right" to right,
                            "bottom" to bottom,
                            "targetText" to targetText,
                            "comparisonType" to "等于"
                        ))
                    }
                    
                    Log.d(TAG, "OCR事件已记录 - 区域: ($left, $top) -> ($right, $bottom), 尺寸: ${width}x${height}")
                    Toast.makeText(this, "文本识别事件已记录: $comparisonType $targetText\n区域: ${width.toInt()}x${height.toInt()}", Toast.LENGTH_LONG).show()
                    // TODO: 保存事件到当前脚本
                } else {
                    Toast.makeText(this, "目标文本不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        alertDialog.show()
    }
    
    private fun editCurrentEvent() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val scriptStorage = ScriptStorage(this@FloatingToolbarService)
                val scripts = scriptStorage.loadScripts()
                
                if (scripts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingToolbarService, "没有找到脚本", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // 获取用户选择的脚本
                val selectedScriptId = scriptStorage.loadSelectedScriptId()
                val script = when {
                    // 优先使用当前正在编辑的脚本ID
                    currentEditingScriptId != null -> {
                        scripts.find { it.id == currentEditingScriptId } ?: scripts.firstOrNull()
                    }
                    // 其次使用用户在主界面选择的脚本
                    selectedScriptId != null -> {
                        scripts.find { it.id == selectedScriptId } ?: scripts.firstOrNull()
                    }
                    // 最后才使用第一个脚本作为默认值
                    else -> scripts.firstOrNull()
                }
                
                if (script == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingToolbarService, "没有找到有效的脚本", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                if (script.events.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingToolbarService, "当前脚本没有事件", Toast.LENGTH_SHORT).show()
                        // 显示事件类型选择对话框
                        showEventTypeDialog()
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    showEventSelectionDialog(script)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading script for editing", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingToolbarService, "加载脚本失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showEventSelectionDialog(script: Script) {
        val eventDescriptions = script.events.mapIndexed { index, event ->
            when (event.type) {
                EventType.CLICK -> {
                    val x = (event.params["x"] as? Number)?.toInt() ?: 0
                    val y = (event.params["y"] as? Number)?.toInt() ?: 0
                    "${index + 1}. 👆 点击 ($x, $y)"
                }
                EventType.SWIPE -> {
                    val startX = (event.params["startX"] as? Number)?.toInt() ?: 0
                    val startY = (event.params["startY"] as? Number)?.toInt() ?: 0
                    val endX = (event.params["endX"] as? Number)?.toInt() ?: 0
                    val endY = (event.params["endY"] as? Number)?.toInt() ?: 0
                    "${index + 1}. 👉 滑动 ($startX,$startY) → ($endX,$endY)"
                }
                EventType.WAIT -> {
                    val duration = (event.params["duration"] as? Number)?.toInt() ?: 0
                    "${index + 1}. ⏱️ 等待 ${duration}ms"
                }
                EventType.OCR -> {
                    val targetNumber = (event.params["targetNumber"] as? Number)?.toDouble()
                    val targetText = event.params["targetText"] as? String
                    val comparisonType = event.params["comparisonType"] as? String ?: "小于"
                    
                    if (targetNumber != null) {
                        // 数字识别
                        val displayNumber = if (targetNumber == targetNumber.toInt().toDouble()) {
                            targetNumber.toInt().toString()
                        } else {
                            targetNumber.toString()
                        }
                        "${index + 1}. 👁️ 识别数字 $comparisonType $displayNumber"
                    } else if (targetText != null) {
                        // 文字识别
                        "${index + 1}. 👁️ 识别文字 $comparisonType $targetText"
                    } else {
                        // 兼容旧数据
                        "${index + 1}. 👁️ 识别文本"
                    }
                }
            }
        }.toTypedArray()
        
        val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("选择要编辑的节点")
            .setItems(eventDescriptions) { dialog, which ->
                val selectedEvent = script.events[which]
                showEditEventDialog(selectedEvent, script, which)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        alertDialog.show()
    }
    
    private fun showEditEventDialog(event: ScriptEvent, script: Script, eventIndex: Int) {
        when (event.type) {
            EventType.CLICK -> {
                Toast.makeText(this, "请点击屏幕上的新位置", Toast.LENGTH_SHORT).show()
                startEventEditing(EventType.CLICK, event, script, eventIndex)
            }
            EventType.SWIPE -> {
                Toast.makeText(this, "请在屏幕上滑动设置新路径", Toast.LENGTH_SHORT).show()
                startEventEditing(EventType.SWIPE, event, script, eventIndex)
            }
            EventType.OCR -> {
                Toast.makeText(this, "请拖拽选择新的识别区域", Toast.LENGTH_SHORT).show()
                startEventEditing(EventType.OCR, event, script, eventIndex)
            }
            EventType.WAIT -> {
                showEditWaitDialog(event, script, eventIndex)
            }
        }
    }
    
    private fun showEditWaitDialog(event: ScriptEvent, script: Script, eventIndex: Int) {
        val editText = EditText(this).apply {
            hint = "等待时间（毫秒）"
            setText(((event.params["duration"] as? Number)?.toInt() ?: 1000).toString())
        }
        
        val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("编辑等待事件")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val duration = editText.text.toString().toIntOrNull() ?: 1000
                val newEvent = ScriptEvent(EventType.WAIT, mapOf("duration" to duration))
                script.events[eventIndex] = newEvent
                
                // 保存脚本
                GlobalScope.launch(Dispatchers.IO) {
                    val scriptStorage = ScriptStorage(this@FloatingToolbarService)
                    scriptStorage.saveScript(script)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingToolbarService, "等待事件已更新: ${duration}ms", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        alertDialog.show()
    }
    
    private fun startEventEditing(eventType: EventType, originalEvent: ScriptEvent, script: Script, eventIndex: Int) {
        currentEventType = eventType
        isRecordingEvent = true
        
        // 创建编辑覆盖层
        createEditEventOverlay(originalEvent, script, eventIndex)
    }
    
    private fun createEditEventOverlay(originalEvent: ScriptEvent, script: Script, eventIndex: Int) {
        // 创建带绘制功能的全屏覆盖层
        val drawingOverlay = DrawingOverlayView(this).apply {
            setBackgroundColor(0x20000000) // 半透明黑色
        }
        
        // 显示原有事件数据作为参考
        // 由于原有事件使用屏幕坐标保存，需要转换为View坐标显示
        when (originalEvent.type) {
            EventType.CLICK -> {
                val screenX = (originalEvent.params["x"] as? Number)?.toFloat() ?: 0f
                val screenY = (originalEvent.params["y"] as? Number)?.toFloat() ?: 0f
                // 由于FLAG_FULLSCREEN，View坐标系与屏幕坐标系应该一致
                drawingOverlay.setClickPoint(screenX, screenY)
                Log.d(TAG, "显示原有点击事件: 屏幕坐标($screenX, $screenY)")
            }
            EventType.SWIPE -> {
                val screenStartX = (originalEvent.params["startX"] as? Number)?.toFloat() ?: 0f
                val screenStartY = (originalEvent.params["startY"] as? Number)?.toFloat() ?: 0f
                val screenEndX = (originalEvent.params["endX"] as? Number)?.toFloat() ?: 0f
                val screenEndY = (originalEvent.params["endY"] as? Number)?.toFloat() ?: 0f
                // 由于FLAG_FULLSCREEN，View坐标系与屏幕坐标系应该一致
                drawingOverlay.setSwipePath(screenStartX, screenStartY, screenEndX, screenEndY)
                Log.d(TAG, "显示原有滑动事件: 屏幕坐标($screenStartX, $screenStartY) -> ($screenEndX, $screenEndY)")
            }
            EventType.OCR -> {
                val screenLeft = (originalEvent.params["left"] as? Number)?.toFloat() ?: 0f
                val screenTop = (originalEvent.params["top"] as? Number)?.toFloat() ?: 0f
                val screenRight = (originalEvent.params["right"] as? Number)?.toFloat() ?: 0f
                val screenBottom = (originalEvent.params["bottom"] as? Number)?.toFloat() ?: 0f
                // 由于FLAG_FULLSCREEN，View坐标系与屏幕坐标系应该一致
                drawingOverlay.setOcrRect(screenLeft, screenTop, screenRight, screenBottom)
                Log.d(TAG, "显示原有OCR事件: 屏幕坐标($screenLeft, $screenTop) -> ($screenRight, $screenBottom)")
            }
            else -> {}
        }
        
        overlayView = drawingOverlay
        
        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            format = PixelFormat.TRANSLUCENT
            x = 0
            y = 0
            gravity = Gravity.TOP or Gravity.START
        }
        
        var startX = 0f
        var startY = 0f
        var endX = 0f
        var endY = 0f
        var viewStartX = 0f
        var viewStartY = 0f
        var isDrawing = false
        
        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 保存屏幕坐标（用于执行）
                    startX = event.rawX
                    startY = event.rawY
                    // 保存View坐标（用于绘制）
                    viewStartX = event.x
                    viewStartY = event.y
                    isDrawing = true
                    Log.d(TAG, "编辑模式记录起始坐标: 屏幕($startX, $startY), View($viewStartX, $viewStartY)")
                    
                    when (currentEventType) {
                        EventType.CLICK -> {
                            (drawingOverlay as? DrawingOverlayView)?.setClickPoint(viewStartX, viewStartY)
                        }
                        EventType.SWIPE -> {
                            (drawingOverlay as? DrawingOverlayView)?.setSwipePath(viewStartX, viewStartY, viewStartX, viewStartY)
                        }
                        EventType.OCR -> {
                            (drawingOverlay as? DrawingOverlayView)?.setOcrRect(viewStartX, viewStartY, viewStartX, viewStartY)
                        }
                        else -> {}
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDrawing) {
                        // 保存屏幕坐标（用于执行）
                        endX = event.rawX
                        endY = event.rawY
                        // 获取当前View坐标（用于绘制）
                        val viewEndX = event.x
                        val viewEndY = event.y
                        
                        Log.d(TAG, "编辑模式移动坐标: 屏幕($endX, $endY), View($viewEndX, $viewEndY)")
                        
                        when (currentEventType) {
                            EventType.SWIPE -> {
                                (drawingOverlay as? DrawingOverlayView)?.setSwipePath(viewStartX, viewStartY, viewEndX, viewEndY)
                            }
                            EventType.OCR -> {
                                (drawingOverlay as? DrawingOverlayView)?.setOcrRect(
                                    kotlin.math.min(viewStartX, viewEndX),
                                    kotlin.math.min(viewStartY, viewEndY),
                                    kotlin.math.max(viewStartX, viewEndX),
                                    kotlin.math.max(viewStartY, viewEndY)
                                )
                            }
                            else -> {}
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // 保存屏幕坐标（用于执行）
                    endX = event.rawX
                    endY = event.rawY
                    val viewEndX = event.x
                    val viewEndY = event.y
                    isDrawing = false
                    Log.d(TAG, "编辑模式记录结束坐标: 屏幕($endX, $endY), View($viewEndX, $viewEndY)")
                    
                    // 处理事件编辑
                    handleEventEdit(startX, startY, endX, endY, originalEvent, script, eventIndex)
                    
                    // 延迟移除覆盖层，让用户看到最终结果
                    handler.postDelayed({
                        removeEventOverlay()
                    }, 1000)
                }
            }
            true
        }
        
        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating edit event overlay", e)
        }
    }
    
    private fun handleEventEdit(startX: Float, startY: Float, endX: Float, endY: Float, 
                               originalEvent: ScriptEvent, script: Script, eventIndex: Int) {
        when (currentEventType) {
            EventType.CLICK -> {
                val newEvent = ScriptEvent(EventType.CLICK, mapOf(
                    "x" to startX,
                    "y" to startY
                ))
                script.events[eventIndex] = newEvent
                Toast.makeText(this, "点击事件已更新: (${startX.toInt()}, ${startY.toInt()})", Toast.LENGTH_SHORT).show()
                saveEditedScript(script)
            }
            EventType.SWIPE -> {
                val distance = kotlin.math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
                if (distance < 50) {
                    Toast.makeText(this, "滑动距离太短，请重新操作", Toast.LENGTH_SHORT).show()
                } else {
                    val newEvent = ScriptEvent(EventType.SWIPE, mapOf(
                        "startX" to startX,
                        "startY" to startY,
                        "endX" to endX,
                        "endY" to endY
                    ))
                    script.events[eventIndex] = newEvent
                    Toast.makeText(this, "滑动事件已更新", Toast.LENGTH_SHORT).show()
                    saveEditedScript(script)
                }
            }
            EventType.OCR -> {
                val width = kotlin.math.abs(endX - startX)
                val height = kotlin.math.abs(endY - startY)
                if (width < 50 || height < 50) {
                    Toast.makeText(this, "选择区域太小，请重新操作", Toast.LENGTH_SHORT).show()
                } else {
                    showEditNumberRecognitionDialog(startX, startY, endX, endY, originalEvent, script, eventIndex)
                }
            }
            else -> {
                // 其他事件类型
            }
        }
    }
    
    private fun showEditNumberRecognitionDialog(startX: Float, startY: Float, endX: Float, endY: Float,
                                               originalEvent: ScriptEvent, script: Script, eventIndex: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_target_number, null)
        val editTextNumber = dialogView.findViewById<EditText>(R.id.et_target_number)
        val radioGroupComparison = dialogView.findViewById<RadioGroup>(R.id.rg_comparison)
        val radioLessThan = dialogView.findViewById<RadioButton>(R.id.rb_less_than)
        val radioEquals = dialogView.findViewById<RadioButton>(R.id.rb_equals)
        
        editTextNumber.hint = "请输入目标数字"
        
        // 预填充原有数据
        val originalNumber = (originalEvent.params["targetNumber"] as? Number)?.toDouble()
        val originalText = originalEvent.params["targetText"] as? String
        val originalComparison = originalEvent.params["comparisonType"] as? String ?: "小于"
        
        if (originalNumber != null) {
            editTextNumber.setText(if (originalNumber == originalNumber.toInt().toDouble()) {
                originalNumber.toInt().toString()
            } else {
                originalNumber.toString()
            })
        } else if (originalText != null) {
            editTextNumber.setText(originalText)
        }
        
        // 监听输入变化，动态调整选项
        var lastInputType: String? = null // 记录上次输入的类型
        editTextNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: ""
                val currentInputType = when {
                    input.isEmpty() -> "empty"
                    input[0].isDigit() -> "number"
                    else -> "text"
                }
                
                // 只在输入类型改变时才调整选项和默认选择
                if (lastInputType != currentInputType) {
                    when (currentInputType) {
                        "number" -> {
                            // 数字输入，显示等于和小于选项
                            radioLessThan.visibility = View.VISIBLE
                            radioEquals.visibility = View.VISIBLE
                            
                            // 在编辑模式下，保持用户的选择
                            // 只有当用户还没有做出选择时才设置默认值
                            if (lastInputType == "text" && !radioLessThan.isChecked && !radioEquals.isChecked) {
                                radioLessThan.isChecked = true
                            } else if (lastInputType == "empty" && !radioLessThan.isChecked && !radioEquals.isChecked) {
                                radioLessThan.isChecked = true
                            }
                        }
                        "text" -> {
                            // 文字输入，只显示等于选项
                            radioLessThan.visibility = View.GONE
                            radioEquals.visibility = View.VISIBLE
                            
                            // 文字输入强制选择等于
                            radioEquals.isChecked = true
                        }
                        "empty" -> {
                            // 空输入，显示所有选项，但不改变用户的选择
                            radioLessThan.visibility = View.VISIBLE
                            radioEquals.visibility = View.VISIBLE
                            // 不重置用户的选择状态
                        }
                    }
                    lastInputType = currentInputType
                }
            }
        })
        
        Log.d(TAG, "编辑OCR事件，原始比较类型: $originalComparison")
        
        // 清除所有选中状态，然后设置正确的选中状态
        radioGroupComparison.clearCheck()
        when (originalComparison) {
            "小于" -> {
                radioLessThan.isChecked = true
                Log.d(TAG, "设置小于选项为选中状态")
            }
            "等于" -> {
                radioEquals.isChecked = true
                Log.d(TAG, "设置等于选项为选中状态")
            }
            else -> {
                // 默认选择小于
                radioLessThan.isChecked = true
                Log.d(TAG, "使用默认选项：小于")
            }
        }
        
        val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("编辑文本识别条件")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val targetText = editTextNumber.text.toString().trim()
                if (targetText.isNotEmpty()) {
                    val comparisonType = when {
                        radioLessThan.isChecked -> "小于"
                        radioEquals.isChecked -> "等于"
                        else -> "小于"
                    }
                    Log.d(TAG, "保存OCR事件，新的比较类型: $comparisonType")
                    
                    val left = kotlin.math.min(startX, endX)
                    val top = kotlin.math.min(startY, endY)
                    val right = kotlin.math.max(startX, endX)
                    val bottom = kotlin.math.max(startY, endY)
                    val width = right - left
                    val height = bottom - top
                    
                    // 判断是数字还是文字
                    val targetNumber = targetText.toDoubleOrNull()
                    val newEvent = if (targetNumber != null) {
                        // 数字识别
                        ScriptEvent(EventType.OCR, mapOf(
                            "left" to left,
                            "top" to top,
                            "right" to right,
                            "bottom" to bottom,
                            "targetNumber" to targetNumber,
                            "comparisonType" to comparisonType
                        ))
                    } else {
                        // 文字识别（只支持等于）
                        ScriptEvent(EventType.OCR, mapOf(
                            "left" to left,
                            "top" to top,
                            "right" to right,
                            "bottom" to bottom,
                            "targetText" to targetText,
                            "comparisonType" to "等于"
                        ))
                    }
                    
                    script.events[eventIndex] = newEvent
                    Log.d(TAG, "OCR事件已更新 - 区域: ($left, $top) -> ($right, $bottom), 尺寸: ${width}x${height}")
                    Toast.makeText(this, "文本识别事件已更新: $comparisonType $targetText\n区域: ${width.toInt()}x${height.toInt()}", Toast.LENGTH_LONG).show()
                    saveEditedScript(script)
                } else {
                    Toast.makeText(this, "目标文本不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        alertDialog.show()
    }
    
    private fun saveEditedScript(script: Script) {
        GlobalScope.launch(Dispatchers.IO) {
            val scriptStorage = ScriptStorage(this@FloatingToolbarService)
            val success = scriptStorage.saveScript(script)
            Log.d(TAG, "Script saved after editing: $success")
            
            // 保存成功后，发送广播通知其他组件刷新数据
            if (success) {
                val intent = Intent("com.example.clickhelper.SCRIPT_UPDATED")
                intent.putExtra("script_id", script.id)
                sendBroadcast(intent)
                Log.d(TAG, "Script update broadcast sent for script: ${script.id}")
            }
        }
    }
    
    private fun hideFloatingToolbar() {
        Log.d(TAG, "hideFloatingToolbar called")
        floatingView?.let {
            try {
                windowManager.removeView(it)
                Log.d(TAG, "FloatingView removed from WindowManager")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view", e)
            }
        }
        floatingView = null
        
        // 同时移除事件覆盖层（如果存在）
        removeEventOverlay()
    }

    private fun executeCurrentScript() {
        Log.d(TAG, "executeCurrentScript called")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val scriptStorage = ScriptStorage(this@FloatingToolbarService)
                val scripts = scriptStorage.loadScripts()
                Log.d(TAG, "Loaded ${scripts.size} scripts")

                if (scripts.isEmpty()) {
                    Log.d(TAG, "No scripts found")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingToolbarService, "没有找到脚本", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 获取用户选择的脚本
                val selectedScriptId = scriptStorage.loadSelectedScriptId()
                val script = when {
                    // 优先使用当前正在编辑的脚本ID
                    currentEditingScriptId != null -> {
                        scripts.find { it.id == currentEditingScriptId } ?: scripts.firstOrNull()
                    }
                    // 其次使用用户在主界面选择的脚本
                    selectedScriptId != null -> {
                        scripts.find { it.id == selectedScriptId } ?: scripts.firstOrNull()
                    }
                    // 最后才使用第一个脚本作为默认值
                    else -> scripts.firstOrNull()
                }
                
                if (script == null) {
                    Log.d(TAG, "No valid script found")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingToolbarService, "没有找到有效的脚本", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                Log.d(TAG, "Using script: ${script.name} (ID: ${script.id}) with ${script.events.size} events")
                
                // 打印所有事件的详细信息
                script.events.forEachIndexed { index, event ->
                    Log.d(TAG, "Event $index: ${event.type} with params: ${event.params}")
                }
                
                if (script.events.isEmpty()) {
                    Log.d(TAG, "Script has no events")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingToolbarService, "当前脚本没有事件", Toast.LENGTH_SHORT).show()
                        // 显示事件类型选择对话框
                        showEventTypeDialog()
                    }
                    return@launch
                }

                // 执行脚本
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Starting script execution on main thread")
                    
                    // 重用现有的ScriptExecutor实例，如果没有则创建新的
                    val scriptExecutor = globalScriptExecutor ?: ScriptExecutor(this@FloatingToolbarService)
                    // 设置全局的ScriptExecutor实例
                    setGlobalScriptExecutor(scriptExecutor)
                    scriptExecutor.executeScript(script, object : ScriptExecutor.ExecutionCallback {
                        override fun onExecutionStart() {
                            Toast.makeText(this@FloatingToolbarService, "开始执行脚本", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Script execution started")
                            // 更新按钮状态
                            floatingView?.let { view ->
                                val btnPlay = view.findViewById<ImageButton>(R.id.btn_play)
                                val btnStop = view.findViewById<ImageButton>(R.id.btn_stop)
                                updateButtonStates(btnPlay, btnStop)
                            }
                        }

                        override fun onExecutionComplete() {
                            Toast.makeText(this@FloatingToolbarService, "脚本执行完成", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Script execution completed")
                            // 更新按钮状态
                            floatingView?.let { view ->
                                val btnPlay = view.findViewById<ImageButton>(R.id.btn_play)
                                val btnStop = view.findViewById<ImageButton>(R.id.btn_stop)
                                updateButtonStates(btnPlay, btnStop)
                            }
                        }

                        override fun onExecutionError(error: String) {
                            Toast.makeText(this@FloatingToolbarService, "脚本执行失败: $error", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Script execution error: $error")
                            // 更新按钮状态
                            floatingView?.let { view ->
                                val btnPlay = view.findViewById<ImageButton>(R.id.btn_play)
                                val btnStop = view.findViewById<ImageButton>(R.id.btn_stop)
                                updateButtonStates(btnPlay, btnStop)
                            }
                        }

                        override fun onEventExecuted(event: ScriptEvent, index: Int) {
                            Log.d(TAG, "Event executed: ${event.type} at index $index")
                        }
                        
                        override fun onExecutionStopped() {
                            Toast.makeText(this@FloatingToolbarService, "脚本执行已停止", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Script execution stopped")
                            // 更新按钮状态
                            floatingView?.let { view ->
                                val btnPlay = view.findViewById<ImageButton>(R.id.btn_play)
                                val btnStop = view.findViewById<ImageButton>(R.id.btn_stop)
                                updateButtonStates(btnPlay, btnStop)
                            }
                        }

                        override fun onNumberRecognitionSuccess(recognizedNumber: Double, targetNumber: Double, comparisonType: String) {
                            val displayRecognized = if (recognizedNumber == recognizedNumber.toInt().toDouble()) {
                                recognizedNumber.toInt().toString()
                            } else {
                                recognizedNumber.toString()
                            }
                            val displayTarget = if (targetNumber == targetNumber.toInt().toDouble()) {
                                targetNumber.toInt().toString()
                            } else {
                                targetNumber.toString()
                            }
                            Toast.makeText(this@FloatingToolbarService, "数字识别成功: $displayRecognized $comparisonType $displayTarget", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Number recognition success: $displayRecognized $comparisonType $displayTarget")
                        }
                        
                        override fun onTextRecognitionSuccess(recognizedText: String, targetText: String, comparisonType: String) {
                            Toast.makeText(this@FloatingToolbarService, "文字识别成功: $recognizedText $comparisonType $targetText", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Text recognition success: $recognizedText $comparisonType $targetText")
                        }
                        
                        override fun onTokenExpired() {
                            Toast.makeText(this@FloatingToolbarService, "Token已过期，请重新验证", Toast.LENGTH_LONG).show()
                            Log.w(TAG, "Token expired during script execution")
                            // 可以在这里跳转到token验证页面
                            val intent = Intent(this@FloatingToolbarService, com.example.clickhelper.TokenVerificationActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing script", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingToolbarService, "脚本执行失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingToolbarService onDestroy")
        hideFloatingToolbar()
        stopForeground(true)
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
} 