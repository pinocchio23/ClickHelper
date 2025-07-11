package com.example.clickhelper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher

object PermissionHelper {
    
    private const val TAG = "PermissionHelper"
    
    /**
     * 检测是否为小米设备
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi")
    }
    
    /**
     * 检测是否为华为设备
     */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("huawei") || brand.contains("huawei") || brand.contains("honor")
    }
    
    /**
     * 检查是否有悬浮窗权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission(context: Context, launcher: ActivityResultLauncher<Intent>?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            
            if (launcher != null) {
                launcher.launch(intent)
            } else {
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * 检查无障碍服务是否启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // 第一步：检查系统无障碍服务总开关
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            ) == 1
        } catch (e: Exception) {
            Log.e(TAG, "检查无障碍服务总开关失败", e)
            false
        }
        
        if (!accessibilityEnabled) {
            return false
        }
        
        // 第二步：获取已启用的服务列表
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        // 第三步：检查我们的服务是否在列表中
        val packageName = context.packageName
        val serviceName = "MyAccessibilityService"
        
        // 可能的服务名称格式（增加更多可能的格式）
        val possibleServiceNames = listOf(
            "$packageName/.service.MyAccessibilityService",
            "$packageName/com.example.clickhelper.service.MyAccessibilityService",
            "com.example.clickhelper/.service.MyAccessibilityService",
            "com.example.clickhelper/com.example.clickhelper.service.MyAccessibilityService",
            "$packageName/MyAccessibilityService",
            "com.example.clickhelper/MyAccessibilityService",
            // 添加更多可能的格式
            "$packageName:com.example.clickhelper.service.MyAccessibilityService",
            "com.example.clickhelper:com.example.clickhelper.service.MyAccessibilityService"
        )
        
        // 检查是否有匹配的服务
        var isEnabled = false
        var matchedServiceName = ""
        
        if (enabledServices != null) {
            for (serviceName in possibleServiceNames) {
                if (enabledServices.contains(serviceName)) {
                    isEnabled = true
                    matchedServiceName = serviceName
                    break
                }
            }
        }
        
        // 第四步：通过服务实例检查（如果可用）
        val serviceInstance = try {
            com.example.clickhelper.service.MyAccessibilityService.instance
        } catch (e: Exception) {
            null
        }
        
        val hasServiceInstance = serviceInstance != null
        
        // 第五步：尝试直接检查服务状态（备用方法）
        var directCheck = false
        try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            for (serviceInfo in enabledServices) {
                val serviceId = serviceInfo.id
                if (serviceId.contains("com.example.clickhelper") && serviceId.contains("MyAccessibilityService")) {
                    directCheck = true
                    break
                }
            }
        } catch (e: Exception) {
            // 忽略错误，继续其他检查
        }
        
        // 综合判断（任一方法检测到都认为已启用）
        val finalResult = isEnabled || hasServiceInstance || directCheck
        
        return finalResult
    }
    
    /**
     * 打开无障碍服务设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    }
    
    /**
     * 检查是否忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }
    
    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "请求忽略电池优化失败", e)
                // fallback to general battery optimization settings
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "打开电池优化设置失败", e2)
                }
            }
        }
    }
    
    /**
     * 打开小米自启动管理设置
     */
    fun openXiaomiAutoStartSettings(context: Context) {
        try {
            // 尝试打开小米的自启动管理
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // 备用方案：打开应用信息页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "无法打开应用设置", e2)
            }
        }
    }
    
    /**
     * 打开小米省电策略设置
     */
    fun openXiaomiPowerSettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 备用方案：打开电池设置
            try {
                val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "无法打开电池设置", e2)
            }
        }
    }
    
    /**
     * 检查所有必要权限
     */
    fun checkAllPermissions(context: Context): PermissionStatus {
        val overlay = hasOverlayPermission(context)
        val accessibility = isAccessibilityServiceEnabled(context)
        val battery = isIgnoringBatteryOptimizations(context)
        val deviceInfo = getDeviceInfo()
        
        return PermissionStatus(
            hasOverlay = overlay,
            hasAccessibility = accessibility,
            hasIgnoreBatteryOptimization = battery,
            deviceInfo = deviceInfo
        )
    }
    
    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
    }
    
    data class PermissionStatus(
        val hasOverlay: Boolean,
        val hasAccessibility: Boolean,
        val hasIgnoreBatteryOptimization: Boolean,
        val deviceInfo: String
    ) {
        val allGranted: Boolean
            get() = hasOverlay && hasAccessibility && hasIgnoreBatteryOptimization
    }
} 