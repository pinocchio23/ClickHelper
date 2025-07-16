package com.example.clickhelper.executor

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import com.example.clickhelper.model.Script
import com.example.clickhelper.model.ScriptEvent
import com.example.clickhelper.model.EventType
import com.example.clickhelper.service.MyAccessibilityService
import com.example.clickhelper.util.UniversalOCRHelper
import com.example.clickhelper.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import com.example.clickhelper.util.PermissionHelper

class ScriptExecutor(private val context: Context) {
    companion object {
        private const val TAG = "ScriptExecutor"
        
        // 全局执行状态，确保同一时间只能执行一个脚本
        @Volatile
        private var globalIsExecuting = false
        
        /**
         * 检查是否有任何脚本正在执行
         */
        fun isAnyScriptExecuting(): Boolean {
            return globalIsExecuting
        }
        
        /**
         * 设置全局执行状态
         */
        private fun setGlobalExecuting(executing: Boolean) {
            globalIsExecuting = executing
        }
    }
    
    private var isExecuting = false
    private var currentScript: Script? = null
    private var executionCallback: ExecutionCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var isRepeating = false
    private var repeatRunnable: Runnable? = null
    private val tokenManager = TokenManager(context)
    
    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            
            // 初始化振动器
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ToneGenerator or Vibrator", e)
        }
    }
    
    interface ExecutionCallback {
        fun onExecutionStart()
        fun onExecutionComplete()
        fun onExecutionError(error: String)
        fun onEventExecuted(event: ScriptEvent, index: Int)
        fun onNumberRecognitionSuccess(recognizedNumber: Double, targetNumber: Double, comparisonType: String)
        fun onExecutionStopped()
        fun onTextRecognitionSuccess(recognizedText: String, targetText: String, comparisonType: String)
        fun onTokenExpired()
    }
    
    fun executeScript(script: Script, callback: ExecutionCallback?) {
        Log.d(TAG, "开始执行脚本: ${script.name}, 事件数量: ${script.events.size}")
        
        // 首先检查Token是否有效
        if (!tokenManager.isTokenValid()) {
            Log.w(TAG, "Token已过期或无效，拒绝执行脚本")
            callback?.onTokenExpired()
            callback?.onExecutionError("Token已过期，请重新验证后再试")
            return
        }
        
        Log.d(TAG, "Token验证通过，继续执行脚本")
        
        // 检查全局执行状态，确保同一时间只能执行一个脚本
        if (globalIsExecuting) {
            Log.w(TAG, "已有脚本正在执行中，拒绝启动新的脚本执行")
            callback?.onExecutionError("已有脚本正在执行中，请等待当前脚本完成")
            return
        }
        
        if (isExecuting) {
            callback?.onExecutionError("当前执行器正在执行脚本")
            return
        }
        
        val accessibilityService = MyAccessibilityService.instance
        if (accessibilityService == null) {
            callback?.onExecutionError("无障碍服务未启用")
            return
        }
        
        currentScript = script
        executionCallback = callback
        isExecuting = true
        isRepeating = (script.executionMode == com.example.clickhelper.model.ExecutionMode.REPEAT)
        
        // 设置全局执行状态
        setGlobalExecuting(true)
        
        // 更新token活动时间
        tokenManager.updateLastActivity()
        
        callback?.onExecutionStart()
        
        executeEventsSequentially(script.events, 0)
    }
    
    private fun executeEventsSequentially(events: List<ScriptEvent>, index: Int) {
        if (!isExecuting) {
            // 脚本已被停止
            return
        }
        
        // 在每个事件执行前再次检查Token有效性（特别是重复执行模式）
        if (!tokenManager.isTokenValid()) {
            Log.w(TAG, "执行过程中Token已过期，停止脚本执行")
            isExecuting = false
            setGlobalExecuting(false)
            executionCallback?.onTokenExpired()
            executionCallback?.onExecutionError("Token已过期，脚本执行已停止")
            return
        }
        
        if (index >= events.size) {
            // 所有事件执行完毕
            Log.d(TAG, "所有事件执行完毕")
            
            if (isRepeating) {
                // 重复执行模式，等待1秒后重新开始
                Log.d(TAG, "重复执行模式，等待1秒后重新开始")
                repeatRunnable = Runnable {
                    if (isExecuting && isRepeating) {
                        executeEventsSequentially(events, 0)
                    }
                }
                handler.postDelayed(repeatRunnable!!, 1000)
            } else {
                // 单次执行模式，结束
                isExecuting = false
                setGlobalExecuting(false)
                executionCallback?.onExecutionComplete()
            }
            return
        }
        
        val event = events[index]
        Log.d(TAG, "执行事件 ${index + 1}/${events.size}: ${event.type}")
        executeEvent(event) { success ->
            if (success && isExecuting) {
                executionCallback?.onEventExecuted(event, index)
                
                // 继续执行下一个事件
                executeEventsSequentially(events, index + 1)
            } else if (!isExecuting) {
                // 脚本已被停止
                return@executeEvent
            } else {
                isExecuting = false
                setGlobalExecuting(false)
                executionCallback?.onExecutionError("事件执行失败: ${event.type}")
            }
        }
    }
    
    private fun executeEvent(event: ScriptEvent, callback: (Boolean) -> Unit) {
        val accessibilityService = MyAccessibilityService.instance
        if (accessibilityService == null) {
            callback(false)
            return
        }
        
        when (event.type) {
            EventType.CLICK -> {
                val x = (event.params["x"] as? Number)?.toFloat() ?: 0f
                val y = (event.params["y"] as? Number)?.toFloat() ?: 0f
                
                val success = accessibilityService.performClick(x, y)
                
                // 点击后稍微延迟再执行下一个事件
                handler.postDelayed({
                    callback(success)
                }, 500)
            }
            
            EventType.SWIPE -> {
                val startX = (event.params["startX"] as? Number)?.toFloat() ?: 0f
                val startY = (event.params["startY"] as? Number)?.toFloat() ?: 0f
                val endX = (event.params["endX"] as? Number)?.toFloat() ?: 0f
                val endY = (event.params["endY"] as? Number)?.toFloat() ?: 0f
                
                val success = accessibilityService.performSwipe(startX, startY, endX, endY, 500L)
                
                // 滑动后稍微延迟再执行下一个事件
                handler.postDelayed({
                    callback(success)
                }, 800)
            }
            
            EventType.WAIT -> {
                val duration = (event.params["duration"] as? Number)?.toInt() ?: 1000
                
                handler.postDelayed({
                    callback(true)
                }, duration.toLong())
            }
            
            EventType.OCR -> {
                Log.d(TAG, "执行OCR事件，开始文字识别...")
                // 执行真实的文字识别（支持数字和文字）
                performRealTextRecognition(event, callback)
            }
        }
    }
    
    private fun performRealTextRecognition(event: ScriptEvent, callback: (Boolean) -> Unit) {
        val left = (event.params["left"] as? Number)?.toFloat() ?: 0f
        val top = (event.params["top"] as? Number)?.toFloat() ?: 0f
        val right = (event.params["right"] as? Number)?.toFloat() ?: 0f
        val bottom = (event.params["bottom"] as? Number)?.toFloat() ?: 0f
        val comparisonType = event.params["comparisonType"] as? String ?: "小于"
        
        Log.d(TAG, "开始执行真实文字识别...")
        Log.d(TAG, "参数解析完成: left=$left, top=$top, right=$right, bottom=$bottom")
        Log.d(TAG, "比较类型: $comparisonType")
        
        // 根据比较类型判断是数字识别还是文字识别
        if (comparisonType == "包含") {
            // 文字识别
            val targetText = event.params["targetText"] as? String ?: ""
            Log.d(TAG, "目标文字: $targetText")
            
            // 显示Toast提示开始识别
            handler.post {
                showSystemToast("开始文字识别...", Toast.LENGTH_SHORT)
            }
            
            // 使用UniversalOCRHelper进行文字识别
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d(TAG, "使用通用OCR助手进行文字识别")
                    UniversalOCRHelper.recognizeTextInRegion(
                        context,
                        left,
                        top,
                        right,
                        bottom,
                        targetText,
                        comparisonType,
                        object : UniversalOCRHelper.TextOCRCallback {
                            override fun onSuccess(recognizedText: String) {
                                Log.d(TAG, "文字识别成功: $recognizedText")
                                handler.post {
                                    showSystemToast("✅ 识别成功: $recognizedText", Toast.LENGTH_LONG)
                                    playSuccessSound()
                                    vibrate()
                                }
                                executionCallback?.onTextRecognitionSuccess(recognizedText, targetText, comparisonType)
                                callback(true)
                            }
                            
                            override fun onFailure(error: String) {
                                Log.e(TAG, "文字识别失败: $error")
                                handler.post {
                                    showSystemToast("❌ 识别失败: $error", Toast.LENGTH_LONG)
                                }
                                callback(true) // 继续执行下一个事件
                            }
                        }
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "文字识别过程中出现异常", e)
                    handler.post {
                        showSystemToast("❌ 识别过程异常: ${e.message}", Toast.LENGTH_LONG)
                    }
                    callback(true)
                }
            }
        } else {
            // 数字识别
            val targetNumber = (event.params["targetNumber"] as? Number)?.toDouble() ?: 0.0
            Log.d(TAG, "目标数字: $targetNumber")
            
            // 显示Toast提示开始识别
            handler.post {
                // showSystemToast("开始数字识别...", Toast.LENGTH_SHORT)
            }
            
            // 使用UniversalOCRHelper进行数字识别
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d(TAG, "使用通用OCR助手进行数字识别")
                    UniversalOCRHelper.recognizeNumberInRegion(
                        context,
                        left,
                        top,
                        right,
                        bottom,
                        targetNumber,
                        comparisonType,
                        object : UniversalOCRHelper.OCRCallback {
                            override fun onSuccess(recognizedNumber: Double) {
                                Log.d(TAG, "数字识别成功: $recognizedNumber")
                                handler.post {
                                    showSystemToast("✅ 识别成功: $recognizedNumber", Toast.LENGTH_LONG)
                                    playSuccessSound()
                                    vibrate()
                                }
                                executionCallback?.onNumberRecognitionSuccess(recognizedNumber, targetNumber, comparisonType)
                                callback(true)
                            }
                            
                            override fun onFailure(error: String) {
                                Log.e(TAG, "数字识别失败: $error")
                                handler.post {
                                    showSystemToast("❌ 识别失败: $error", Toast.LENGTH_LONG)
                                }
                                callback(true) // 继续执行下一个事件
                            }
                        }
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "数字识别过程中出现异常", e)
                    handler.post {
                        showSystemToast("❌ 识别过程异常: ${e.message}", Toast.LENGTH_LONG)
                    }
                    callback(true)
                }
            }
        }
    }
    
    private fun playSuccessSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {
            Log.e(TAG, "播放提示音失败", e)
        }
    }
    
    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (e: Exception) {
            Log.e(TAG, "振动失败", e)
        }
    }
    
    fun stopExecution() {
        if (isExecuting) {
            isExecuting = false
            setGlobalExecuting(false)
            handler.removeCallbacksAndMessages(null)
            executionCallback?.onExecutionError("用户取消执行")
        }
    }
    
    fun isExecuting(): Boolean {
        return isExecuting
    }
    
    fun stopScript() {
        Log.d(TAG, "停止脚本执行")
        isExecuting = false
        isRepeating = false
        
        // 清除全局执行状态
        setGlobalExecuting(false)
        
        // 取消延迟的重复执行任务
        repeatRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            repeatRunnable = null
        }
        
        // 清理所有挂起的Handler回调
        handler.removeCallbacksAndMessages(null)
        
        currentScript = null
        executionCallback?.onExecutionStopped()
        executionCallback = null
    }
    
    fun cleanup() {
        try {
            toneGenerator?.release()
            vibrator?.cancel() // 取消振动
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup resources", e)
        }
    }

    /**
     * 显示系统级别的Toast，即使应用不在前台也能显示
     */
    private fun showSystemToast(message: CharSequence, duration: Int) {
        try {
            // 方法1：尝试使用应用上下文显示Toast
            val toast = Toast.makeText(context.applicationContext, message, duration)
            toast.show()
            
            // 方法2：如果应用有悬浮窗权限，创建一个临时的系统级提示
            if (PermissionHelper.hasOverlayPermission(context)) {
                showOverlayToast(message.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示系统Toast失败", e)
            // 降级到日志输出
            Log.i(TAG, "OCR结果: $message")
        }
    }
    
    /**
     * 使用悬浮窗显示Toast效果的提示
     */
    private fun showOverlayToast(message: String) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 创建Toast样式的悬浮窗
            val toastView = object : View(context) {
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    // 绘制半透明背景
                    val bgPaint = Paint().apply {
                        color = Color.argb(200, 0, 0, 0) // 半透明黑色
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 20f, 20f, bgPaint)
                    
                    // 绘制文字
                    val textPaint = Paint().apply {
                        color = Color.WHITE
                        textSize = 48f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    
                    val textY = height / 2f + textPaint.textSize / 3f
                    canvas.drawText(message, width / 2f, textY, textPaint)
                }
            }
            
            val layoutParams = WindowManager.LayoutParams().apply {
                width = 800
                height = 200
                
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.CENTER
            }
            
            windowManager.addView(toastView, layoutParams)
            
            // 3秒后自动移除
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(toastView)
                } catch (e: Exception) {
                    Log.w(TAG, "移除悬浮Toast失败", e)
                }
            }, 3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮Toast失败", e)
        }
    }
} 