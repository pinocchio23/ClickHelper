package com.example.clickhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.content.Intent
import android.os.PowerManager
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.annotation.RequiresApi
import com.example.clickhelper.MainActivity
import com.example.clickhelper.R
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.Display
import android.view.WindowManager
import android.util.DisplayMetrics
import com.example.clickhelper.util.TokenManager
import java.util.concurrent.Executor
import java.util.function.Consumer

class MyAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "accessibility_service_channel"
        var instance: MyAccessibilityService? = null
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    interface ScreenshotCallback {
        fun onSuccess(bitmap: Bitmap)
        fun onFailure(error: String)
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        Log.d(TAG, "AccessibilityService connected")
        
        // 验证Token
        val tokenManager = TokenManager(this)
        if (!tokenManager.isTokenValid()) {
            Log.d(TAG, "Token is invalid, stopping accessibility service")
            disableSelf()
            return
        }
        
        // 获取WakeLock防止系统休眠时关闭服务
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG:AccessibilityWakeLock"
        )
        wakeLock?.acquire()
        
        // Android 15+ 需要启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            startForegroundService()
        }
    }
    
    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi")
    }
    
    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("点击助手")
                .setContentText("无障碍服务正在运行")
                .setSmallIcon(R.drawable.ic_accessibility)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "无障碍服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "点击助手无障碍服务通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 处理无障碍事件
    }
    
    override fun onInterrupt() {
        // 服务被中断
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        return true // 返回true允许重新绑定
    }
    
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 释放WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        // 停止前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        
        instance = null
    }
    
    /**
     * 执行点击操作
     */
    fun performClick(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x, y)
            
            val gestureBuilder = GestureDescription.Builder()
            val gestureDescription = gestureBuilder
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            
            return dispatchGesture(gestureDescription, null, null)
        }
        return false
    }
    
    /**
     * 执行滑动操作
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            
            val gestureBuilder = GestureDescription.Builder()
            val gestureDescription = gestureBuilder
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            return dispatchGesture(gestureDescription, null, null)
        }
        return false
    }
    
    /**
     * 截取屏幕指定区域的回调接口
     */
    interface ScreenCaptureCallback {
        fun onSuccess(bitmap: Bitmap)
        fun onFailure(error: String)
    }
    
    /**
     * 截取屏幕指定区域
     */
    fun captureScreenRegion(
        left: Float, 
        top: Float, 
        right: Float, 
        bottom: Float, 
        callback: ScreenCaptureCallback
    ) {
        Log.d(TAG, "开始截取屏幕区域: ($left, $top) -> ($right, $bottom)")
        Log.d(TAG, "当前Android版本: ${Build.VERSION.SDK_INT}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用新的截图API
            Log.d(TAG, "使用Android 11+截图API")
            captureScreenWithNewAPI(left, top, right, bottom, callback)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9-10 使用旧的截图API
            Log.d(TAG, "使用Android 9-10截图API")
            captureScreenAndroid9to10(left, top, right, bottom, callback)
        } else {
            // Android 8及以下不支持
            Log.w(TAG, "Android版本过低，不支持无障碍截图")
            callback.onFailure("Android版本过低，不支持无障碍截图")
        }
    }
    
    /**
     * Android 11+ 截图API
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenWithNewAPI(
        left: Float, top: Float, right: Float, bottom: Float,
        callback: ScreenCaptureCallback
    ) {
        try {
            val screenshotCallback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    Log.d(TAG, "Android 11+ 截图API回调成功")
                    try {
                        // 使用反射获取bitmap，因为hardwareBitmap可能不直接可用
                        val bitmap = getBitmapFromScreenshotResult(screenshotResult)
                        if (bitmap != null) {
                            val croppedBitmap = cropBitmap(bitmap, left, top, right, bottom)
                            callback.onSuccess(croppedBitmap)
                        } else {
                            callback.onFailure("无法获取截图bitmap")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理截图结果失败", e)
                        callback.onFailure("处理截图结果失败: ${e.message}")
                    }
                }
                
                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Android 11+ 截图API失败, 错误码: $errorCode")
                    callback.onFailure("截图API失败，错误码: $errorCode")
                }
            }
            
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, screenshotCallback)
            
        } catch (e: Exception) {
            Log.e(TAG, "调用Android 11+ 截图API异常", e)
            callback.onFailure("调用截图API异常: ${e.message}")
        }
    }
    
    /**
     * 使用Android 9-10的截图API
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun captureScreenAndroid9to10(left: Float, top: Float, right: Float, bottom: Float, callback: ScreenCaptureCallback) {
        Log.d(TAG, "使用Android 9-10 截图API")
        
        try {
            // 使用反射调用隐藏的截图API
            val method = AccessibilityService::class.java.getDeclaredMethod(
                "takeScreenshot",
                Int::class.java,
                Executor::class.java,
                AccessibilityService.TakeScreenshotCallback::class.java
            )
            method.isAccessible = true
            
            val screenshotCallback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    Log.d(TAG, "Android 9-10 截图API回调成功")
                    try {
                        // 使用反射获取bitmap，因为hardwareBitmap可能不直接可用
                        val bitmap = getBitmapFromScreenshotResult(screenshotResult)
                        if (bitmap != null) {
                            val croppedBitmap = cropBitmap(bitmap, left, top, right, bottom)
                            callback.onSuccess(croppedBitmap)
                        } else {
                            callback.onFailure("无法获取截图bitmap")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理截图结果失败", e)
                        callback.onFailure("处理截图结果失败: ${e.message}")
                    }
                }
                
                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Android 9-10 截图API失败, 错误码: $errorCode")
                    callback.onFailure("截图API失败，错误码: $errorCode")
                }
            }
            
            method.invoke(this, Display.DEFAULT_DISPLAY, mainExecutor, screenshotCallback)
            
        } catch (e: Exception) {
            Log.e(TAG, "调用Android 9-10 截图API异常", e)
            callback.onFailure("调用截图API异常: ${e.message}")
        }
    }
    
    /**
     * 从ScreenshotResult中获取bitmap
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun getBitmapFromScreenshotResult(screenshotResult: AccessibilityService.ScreenshotResult): Bitmap? {
        Log.d(TAG, "开始从ScreenshotResult获取bitmap")
        
        // 方法1：Android 15+ 使用HardwareBuffer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "尝试Android 15+ HardwareBuffer方法")
            try {
                // 获取HardwareBuffer
                val getHardwareBufferMethod = screenshotResult.javaClass.getMethod("getHardwareBuffer")
                val hardwareBuffer = getHardwareBufferMethod.invoke(screenshotResult) as? android.hardware.HardwareBuffer
                
                if (hardwareBuffer != null) {
                    Log.d(TAG, "成功获取HardwareBuffer: ${hardwareBuffer.width}x${hardwareBuffer.height}")
                    
                    // 从HardwareBuffer创建Bitmap
                    val bitmap = createBitmapFromHardwareBuffer(hardwareBuffer)
                    if (bitmap != null) {
                        Log.d(TAG, "成功从HardwareBuffer创建bitmap: ${bitmap.width}x${bitmap.height}")
                        return bitmap
                    }
                } else {
                    Log.w(TAG, "HardwareBuffer为null")
                }
            } catch (e: Exception) {
                Log.w(TAG, "HardwareBuffer方法失败", e)
            }
            
            // 方法1.2：尝试直接访问hardwareBitmap字段（较老的Android 11-14）
            Log.d(TAG, "尝试Android 11-14的hardwareBitmap方法")
            try {
                val hardwareBitmapField = screenshotResult.javaClass.getDeclaredField("mHardwareBitmap")
                hardwareBitmapField.isAccessible = true
                val hardwareBitmap = hardwareBitmapField.get(screenshotResult) as? android.graphics.Bitmap
                
                if (hardwareBitmap != null) {
                    Log.d(TAG, "成功获取hardwareBitmap: ${hardwareBitmap.width}x${hardwareBitmap.height}")
                    val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    Log.d(TAG, "成功转换为软件bitmap: ${softwareBitmap.width}x${softwareBitmap.height}")
                    return softwareBitmap
                } else {
                    Log.w(TAG, "hardwareBitmap为null")
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取hardwareBitmap失败", e)
            }
            
            // 方法1.3：遍历所有字段寻找bitmap
            try {
                val fields = screenshotResult.javaClass.declaredFields
                for (field in fields) {
                    Log.d(TAG, "发现字段: ${field.name}, 类型: ${field.type}")
                    if (field.type == android.graphics.Bitmap::class.java) {
                        field.isAccessible = true
                        val bitmap = field.get(screenshotResult) as? Bitmap
                        if (bitmap != null) {
                            Log.d(TAG, "通过字段${field.name}成功获取bitmap: ${bitmap.width}x${bitmap.height}")
                            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "遍历字段获取bitmap失败", e)
            }
        }
        
        // 方法2：Android 9-10的bitmap字段
        Log.d(TAG, "尝试Android 9-10的bitmap方法")
        try {
            val bitmapField = screenshotResult.javaClass.getDeclaredField("mBitmap")
            bitmapField.isAccessible = true
            val bitmap = bitmapField.get(screenshotResult) as? Bitmap
            
            if (bitmap != null) {
                Log.d(TAG, "成功获取mBitmap: ${bitmap.width}x${bitmap.height}")
                return bitmap
            } else {
                Log.w(TAG, "mBitmap为null")
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取mBitmap失败", e)
        }
        
        // 方法3：尝试调用可能的方法
        Log.d(TAG, "尝试调用方法获取bitmap")
        try {
            val methods = screenshotResult.javaClass.declaredMethods
            for (method in methods) {
                Log.d(TAG, "发现方法: ${method.name}, 返回类型: ${method.returnType}")
                if (method.returnType == Bitmap::class.java && method.parameterCount == 0) {
                    method.isAccessible = true
                    val bitmap = method.invoke(screenshotResult) as? Bitmap
                    if (bitmap != null) {
                        Log.d(TAG, "通过方法${method.name}成功获取bitmap: ${bitmap.width}x${bitmap.height}")
                        return bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "调用方法获取bitmap失败", e)
        }
        
        // 方法4：尝试toString查看对象结构
        Log.d(TAG, "ScreenshotResult对象信息: ${screenshotResult}")
        Log.d(TAG, "ScreenshotResult类: ${screenshotResult.javaClass}")
        
        Log.e(TAG, "所有方法都无法获取bitmap")
        return null
    }
    
    /**
     * 从HardwareBuffer创建Bitmap（Android 15+）
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun createBitmapFromHardwareBuffer(hardwareBuffer: android.hardware.HardwareBuffer): Bitmap? {
        return try {
            Log.d(TAG, "尝试从HardwareBuffer创建Bitmap")
            
            // 方法1：尝试直接使用wrapHardwareBuffer（Android 29+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Log.d(TAG, "尝试使用wrapHardwareBuffer方法")
                    val wrapMethod = Bitmap::class.java.getMethod(
                        "wrapHardwareBuffer",
                        android.hardware.HardwareBuffer::class.java,
                        android.graphics.ColorSpace::class.java
                    )
                    
                    val colorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                    val bitmap = wrapMethod.invoke(null, hardwareBuffer, colorSpace) as? Bitmap
                    
                    if (bitmap != null) {
                        Log.d(TAG, "成功通过wrapHardwareBuffer创建bitmap: ${bitmap.width}x${bitmap.height}")
                        return bitmap
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "wrapHardwareBuffer方法失败", e)
                }
            }
            
            // 方法2：尝试createFromHardwareBuffer（如果存在）
            try {
                Log.d(TAG, "尝试使用createFromHardwareBuffer方法")
                val createMethod = Bitmap::class.java.getMethod(
                    "createFromHardwareBuffer", 
                    android.hardware.HardwareBuffer::class.java,
                    android.graphics.ColorSpace::class.java
                )
                
                val colorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                val bitmap = createMethod.invoke(null, hardwareBuffer, colorSpace) as? Bitmap
                
                if (bitmap != null) {
                    Log.d(TAG, "成功通过createFromHardwareBuffer创建bitmap: ${bitmap.width}x${bitmap.height}")
                    return bitmap
                }
            } catch (e: Exception) {
                Log.w(TAG, "createFromHardwareBuffer方法失败", e)
            }
            
            // 方法3：列出所有可用的静态方法
            Log.d(TAG, "列出Bitmap类的所有静态方法:")
            val methods = Bitmap::class.java.declaredMethods
            for (method in methods) {
                if (java.lang.reflect.Modifier.isStatic(method.modifiers) && 
                    method.returnType == Bitmap::class.java) {
                    Log.d(TAG, "静态方法: ${method.name}, 参数: ${method.parameterTypes.joinToString { it.simpleName }}")
                }
            }
            
            // 方法4：尝试使用Canvas绘制（备用方案）
            try {
                Log.d(TAG, "尝试使用Canvas备用方案")
                // 创建一个临时bitmap
                val tempBitmap = Bitmap.createBitmap(
                    hardwareBuffer.width, 
                    hardwareBuffer.height, 
                    Bitmap.Config.ARGB_8888
                )
                
                // 这里可能需要通过其他方式从HardwareBuffer获取像素数据
                // 但这需要更复杂的实现
                Log.w(TAG, "Canvas备用方案需要更多实现")
                
            } catch (e: Exception) {
                Log.w(TAG, "Canvas备用方案失败", e)
            }
            
            Log.w(TAG, "所有HardwareBuffer转换方法都失败了")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "从HardwareBuffer创建Bitmap失败", e)
            null
        }
    }
    
    /**
     * 裁剪bitmap到指定区域
     */
    private fun cropBitmap(
        fullBitmap: Bitmap,
        left: Float, top: Float, right: Float, bottom: Float
    ): Bitmap {
        val bitmapWidth = fullBitmap.width
        val bitmapHeight = fullBitmap.height
        
        Log.d(TAG, "原始bitmap尺寸: ${bitmapWidth}x${bitmapHeight}")
        Log.d(TAG, "请求裁剪区域: ($left, $top) -> ($right, $bottom)")
        
        // 获取屏幕实际尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        Log.d(TAG, "屏幕尺寸: ${screenWidth}x${screenHeight}")
        
        // 获取状态栏和导航栏高度
        val statusBarHeight = getStatusBarHeight()
        val navigationBarHeight = getNavigationBarHeight()
        Log.d(TAG, "状态栏高度: $statusBarHeight, 导航栏高度: $navigationBarHeight")
        
        // 计算实际可用的屏幕高度
        val usableScreenHeight = screenHeight - statusBarHeight - navigationBarHeight
        Log.d(TAG, "可用屏幕高度: $usableScreenHeight")
        
        // 智能判断bitmap是否包含状态栏
        val bitmapIncludesStatusBar = when {
            // 如果bitmap高度接近完整屏幕高度，说明包含状态栏和导航栏
            kotlin.math.abs(bitmapHeight - screenHeight) < 50 -> {
                Log.d(TAG, "bitmap包含完整屏幕（含状态栏和导航栏）")
                true
            }
            // 如果bitmap高度接近可用高度，说明不包含状态栏和导航栏
            kotlin.math.abs(bitmapHeight - usableScreenHeight) < 50 -> {
                Log.d(TAG, "bitmap只包含应用区域（不含状态栏和导航栏）")
                false
            }
            // 其他情况，根据比例判断
            else -> {
                val ratioFull = bitmapHeight.toFloat() / screenHeight.toFloat()
                val ratioUsable = bitmapHeight.toFloat() / usableScreenHeight.toFloat()
                val includesStatusBar = kotlin.math.abs(ratioFull - 1.0f) < kotlin.math.abs(ratioUsable - 1.0f)
                Log.d(TAG, "根据比例判断bitmap${if (includesStatusBar) "包含" else "不包含"}状态栏")
                includesStatusBar
            }
        }
        
        // 根据bitmap是否包含状态栏来调整坐标
        val (adjustedLeft, adjustedTop, adjustedRight, adjustedBottom) = if (bitmapIncludesStatusBar) {
            // bitmap包含状态栏，直接使用原始坐标
            Log.d(TAG, "使用原始坐标（bitmap包含状态栏）")
            arrayOf(left, top, right, bottom)
        } else {
            // bitmap不包含状态栏，需要调整Y坐标
            Log.d(TAG, "调整Y坐标（bitmap不包含状态栏）")
            arrayOf(left, top - statusBarHeight, right, bottom - statusBarHeight)
        }
        
        Log.d(TAG, "调整后的坐标: ($adjustedLeft, $adjustedTop) -> ($adjustedRight, $adjustedBottom)")
        
        // 计算bitmap与对应区域的缩放比例
        val targetScreenWidth = screenWidth.toFloat()
        val targetScreenHeight = if (bitmapIncludesStatusBar) screenHeight.toFloat() else usableScreenHeight.toFloat()
        
        val scaleX = bitmapWidth.toFloat() / targetScreenWidth
        val scaleY = bitmapHeight.toFloat() / targetScreenHeight
        Log.d(TAG, "缩放比例: scaleX=$scaleX, scaleY=$scaleY")
        
        // 将调整后的屏幕坐标转换为bitmap坐标
        val bitmapLeft = (adjustedLeft * scaleX).toInt()
        val bitmapTop = (adjustedTop * scaleY).toInt()
        val bitmapRight = (adjustedRight * scaleX).toInt()
        val bitmapBottom = (adjustedBottom * scaleY).toInt()
        
        Log.d(TAG, "转换后的bitmap坐标: ($bitmapLeft, $bitmapTop) -> ($bitmapRight, $bitmapBottom)")
        
        // 确保坐标在有效范围内
        val cropLeft = kotlin.math.max(0, bitmapLeft)
        val cropTop = kotlin.math.max(0, bitmapTop)
        val cropRight = kotlin.math.min(bitmapWidth, bitmapRight)
        val cropBottom = kotlin.math.min(bitmapHeight, bitmapBottom)
        
        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop
        
        Log.d(TAG, "最终裁剪区域: ($cropLeft, $cropTop) -> ($cropRight, $cropBottom), 尺寸: ${cropWidth}x${cropHeight}")
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.e(TAG, "裁剪区域无效: 宽度=$cropWidth, 高度=$cropHeight")
            throw IllegalArgumentException("裁剪区域无效: 宽度=$cropWidth, 高度=$cropHeight")
        }
        
        // 检查裁剪区域是否超出bitmap边界
        if (cropLeft < 0 || cropTop < 0 || cropRight > bitmapWidth || cropBottom > bitmapHeight) {
            Log.w(TAG, "警告：裁剪区域超出bitmap边界，将被调整")
        }
        
        return Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
    }
    
    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        return try {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                0
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法获取状态栏高度", e)
            0
        }
    }
    
    /**
     * 获取导航栏高度
     */
    private fun getNavigationBarHeight(): Int {
        return try {
            val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                0
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法获取导航栏高度", e)
            0
        }
    }
} 