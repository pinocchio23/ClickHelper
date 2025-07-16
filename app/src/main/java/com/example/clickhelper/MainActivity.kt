package com.example.clickhelper

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clickhelper.model.Script
import com.example.clickhelper.service.FloatingToolbarService
import com.example.clickhelper.service.MyAccessibilityService
import com.example.clickhelper.storage.ScriptStorage
import com.example.clickhelper.ui.script.ScriptAdapter
import com.example.clickhelper.ui.script.ScriptEditActivity
import com.example.clickhelper.util.PermissionHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import android.os.Build
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.clickhelper.util.TokenManager

class MainActivity : AppCompatActivity() {

    private lateinit var scriptAdapter: ScriptAdapter
    private val scripts = mutableListOf<Script>()
    private lateinit var scriptStorage: ScriptStorage
    private lateinit var tokenManager: TokenManager
    
    // 添加标志来避免在权限授权后立即重新检查
    private var justCompletedPermissionRequest = false
    private var lastPermissionRequestTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化TokenManager
        tokenManager = TokenManager(this)
        
        // 检查Token是否有效
        if (!tokenManager.isTokenValid()) {
            // Token无效，跳转到验证页面
            val intent = Intent(this, TokenVerificationActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        // 设置自定义Toolbar作为ActionBar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // 初始化存储
        scriptStorage = ScriptStorage(this)
        
        // 初始化脚本列表
        initScriptList()
        
        // 加载脚本数据
        loadScripts()
        
        // 设置添加按钮
        findViewById<FloatingActionButton>(R.id.fab_add_script).setOnClickListener {
            showAddScriptDialog()
        }
        
        // 权限检查将在onResume()中进行，避免重复检查
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // 处理其他Activity结果
        // MediaProjection权限处理已移除，现在使用UniversalOCRHelper
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
        
        // 重新加载脚本数据，确保显示最新的修改
        loadScripts()
        
        // 检查是否刚刚完成权限请求
        val timeSincePermissionRequest = System.currentTimeMillis() - lastPermissionRequestTime
        if (justCompletedPermissionRequest && timeSincePermissionRequest < 5000) {
            Log.d("MainActivity", "刚刚完成权限请求，跳过权限检查 (${timeSincePermissionRequest}ms ago)")
            // 延迟清除标志，给服务一些时间初始化
            findViewById<RecyclerView>(R.id.recycler_view_scripts).postDelayed({
                justCompletedPermissionRequest = false
                Log.d("MainActivity", "权限请求标志已清除")
            }, 3000)
            return
        }
        
        // 延迟检查权限，给服务一些时间初始化
        findViewById<RecyclerView>(R.id.recycler_view_scripts).postDelayed({
            checkPermissions()
        }, 500) // 延迟500ms
    }

    private fun checkPermissions() {
        val permissionStatus = PermissionHelper.checkAllPermissions(this)
        
        // 只有在有权限缺失时才显示对话框
        if (!permissionStatus.allGranted) {
            val missingPermissions = mutableListOf<String>()
            
            if (!permissionStatus.hasAccessibility) {
                missingPermissions.add("• 无障碍服务权限（用于自动点击和滑动）")
            }
            if (!permissionStatus.hasOverlay) {
                missingPermissions.add("• 悬浮窗权限（用于显示控制界面）")
            }
            if (!permissionStatus.hasIgnoreBatteryOptimization) {
                missingPermissions.add("• 电池优化忽略（确保服务持续运行）")
            }
            
            // 只有在有实际权限缺失时才显示对话框
            if (missingPermissions.isNotEmpty()) {
                val message = buildString {
                    append("检测到以下权限未授予：\n\n")
                    missingPermissions.forEach { append("$it\n") }
                    
                    append("\n⚠️ 为确保应用正常运行，完成上述权限设置后还需要：\n")
                    append("1. 开启自启动管理/应用启动管理\n")
                    append("2. 关闭省电策略/电池优化\n")
                    append("3. 允许后台弹出界面/后台活动\n")
                    append("4. 设置为高性能模式（部分设备）\n")
                    append("\n点击'去设置'将逐步引导您完成所有配置。")
                    append("\n\n如果您认为权限已正确设置，请点击'刷新检测'。")
                }
                
                AlertDialog.Builder(this)
                    .setTitle("权限配置")
                    .setMessage(message)
                    .setPositiveButton("去设置") { _, _ ->
                        startPermissionGuidance()
                    }
                    .setNeutralButton("刷新检测") { _, _ ->
                        // 延迟重新检测，给系统时间更新状态
                        findViewById<RecyclerView>(R.id.recycler_view_scripts).postDelayed({
                            checkPermissions()
                        }, 1000)
                    }
                    .setNegativeButton("稍后设置", null)
                    .setCancelable(false)
                    .show()
            }
        } else {
            // 基本权限已授予，但仍需检查系统级设置
            checkSystemSettings()
        }
    }
    
    private fun startPermissionGuidance() {
        val permissionStatus = PermissionHelper.checkAllPermissions(this)
        
        // 优先处理最重要的权限
        when {
            !permissionStatus.hasAccessibility -> {
                showPermissionGuidanceDialog(
                    "无障碍服务权限",
                    "即将打开无障碍服务设置页面，请找到\"点击助手\"并开启服务。",
                    "打开设置"
                ) {
                    PermissionHelper.openAccessibilitySettings(this)
                }
            }
            
            !permissionStatus.hasOverlay -> {
                showPermissionGuidanceDialog(
                    "悬浮窗权限",
                    "即将打开悬浮窗权限设置页面，请为\"点击助手\"开启悬浮窗权限。",
                    "打开设置"
                ) {
                    PermissionHelper.requestOverlayPermission(this, null)
                }
            }
            !permissionStatus.hasIgnoreBatteryOptimization -> {
                showPermissionGuidanceDialog(
                    "电池优化设置",
                    "即将打开电池优化设置页面，请为\"点击助手\"选择\"不优化\"或\"允许\"。",
                    "打开设置"
                ) {
                    PermissionHelper.requestIgnoreBatteryOptimizations(this)
                }
            }
            else -> {
                // 所有基本权限都已授予，引导系统级设置
                showSystemSettingsGuidance()
            }
        }
    }
    
    private fun checkSystemSettings() {
        // 所有基本权限都已授予，检查是否需要系统级设置提示
        val manufacturer = Build.MANUFACTURER.lowercase()
        val needsSystemSettings = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> true
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> true
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> true
            manufacturer.contains("vivo") -> true
            manufacturer.contains("oneplus") -> true
            else -> false
        }
        
        if (needsSystemSettings) {
            // 检查用户是否已经完成了系统级设置
            val prefs = getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            val hasCompletedSystemSettings = prefs.getBoolean("completed_system_settings", false)
            
            if (!hasCompletedSystemSettings) {
                // 主动显示系统设置引导对话框
                showSystemSettingsGuidance()
            } else {
                Toast.makeText(this, "✅ 所有权限已正确配置", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "✅ 所有权限已正确配置", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showSystemSettingsGuidance() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        val (title, message, settingsAction) = when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") -> {
                Triple(
                    "小米设备系统设置",
                    "基本权限已配置完成！\n\n为确保服务稳定运行，请进入以下设置：\n\n" +
                    "1. 设置 → 应用设置 → 授权管理 → 自启动管理 → 点击助手 → 开启\n" +
                    "2. 设置 → 省电与电池 → 应用智能省电 → 点击助手 → 无限制\n" +
                    "3. 设置 → 应用设置 → 权限管理 → 后台弹出界面 → 点击助手 → 允许",
                    { openXiaomiSettings() }
                )
            }
            manufacturer.contains("huawei") || brand.contains("huawei") || brand.contains("honor") -> {
                Triple(
                    "华为设备系统设置",
                    "基本权限已配置完成！\n\n为确保服务稳定运行，请进入以下设置：\n\n" +
                    "1. 设置 → 电池 → 应用启动管理 → 点击助手 → 手动管理 → 全部开启\n" +
                    "2. 设置 → 应用和服务 → 应用管理 → 点击助手 → 电池 → 高性能\n" +
                    "3. 手机管家 → 应用启动管理 → 点击助手 → 开启",
                    { openHuaweiSettings() }
                )
            }
            manufacturer.contains("oppo") || brand.contains("oppo") || brand.contains("realme") -> {
                Triple(
                    "OPPO设备系统设置",
                    "基本权限已配置完成！\n\n为确保服务稳定运行，请进入以下设置：\n\n" +
                    "1. 设置 → 电池 → 应用启动管理 → 点击助手 → 允许\n" +
                    "2. 设置 → 应用管理 → 点击助手 → 权限 → 后台活动 → 允许\n" +
                    "3. 手机管家 → 权限隐私 → 应用启动管理 → 点击助手 → 允许",
                    { openOppoSettings() }
                )
            }
            manufacturer.contains("vivo") -> {
                Triple(
                    "vivo设备系统设置",
                    "基本权限已配置完成！\n\n为确保服务稳定运行，请进入以下设置：\n\n" +
                    "1. 设置 → 电池 → 后台高耗电 → 点击助手 → 允许\n" +
                    "2. 设置 → 应用与权限 → 权限管理 → 后台弹出界面 → 点击助手 → 允许\n" +
                    "3. i管家 → 应用管理 → 自启动管理 → 点击助手 → 开启",
                    { openVivoSettings() }
                )
            }
            manufacturer.contains("oneplus") -> {
                Triple(
                    "一加设备系统设置",
                    "基本权限已配置完成！\n\n为确保服务稳定运行，请进入以下设置：\n\n" +
                    "1. 设置 → 电池 → 电池优化 → 点击助手 → 不优化\n" +
                    "2. 设置 → 应用管理 → 点击助手 → 应用权限 → 后台活动 → 允许\n" +
                    "3. 设置 → 应用管理 → 应用启动管理 → 点击助手 → 允许",
                    { openOnePlusSettings() }
                )
            }
            else -> {
                Triple(
                    "系统设置建议",
                    "基本权限已配置完成！\n\n为确保服务稳定运行，建议检查：\n\n" +
                    "1. 应用启动管理/自启动管理\n" +
                    "2. 电池优化设置\n" +
                    "3. 后台活动权限\n\n" +
                    "具体设置路径可能因设备而异，请在系统设置中查找相关选项。",
                    { openGeneralSettings() }
                )
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                settingsAction()
            }
            .setNeutralButton("已完成") { _, _ ->
                // 用户确认已完成系统设置
                val prefs = getSharedPreferences("system_settings", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("completed_system_settings", true).apply()
                Toast.makeText(this, "✅ 系统设置已标记为完成", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("稍后设置", null)
            .show()
    }
    
    private fun showPermissionGuidanceDialog(title: String, message: String, buttonText: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText) { _, _ ->
                action()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun openXiaomiSettings() {
        val xiaomiIntents = listOf(
            // 尝试打开自启动管理
            Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            },
            // 尝试打开应用权限管理
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", packageName)
            },
            // 尝试打开安全中心
            Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.securitycenter.Main")
            },
            // 尝试打开应用管理
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").apply {
                setClassName("com.miui.securitycenter", "com.miui.powercenter.PowerHideApps")
            }
        )
        
        var success = false
        for (intent in xiaomiIntents) {
            try {
                startActivity(intent)
                success = true
                Toast.makeText(this, "已打开小米设置页面，请找到\"点击助手\"进行配置", Toast.LENGTH_LONG).show()
                break
            } catch (e: Exception) {
                // 继续尝试下一个Intent
            }
        }
        
        if (!success) {
            showManualSettingsGuide("小米设备", listOf(
                "方法1: 设置 → 应用设置 → 授权管理 → 自启动管理",
                "方法2: 安全中心 → 应用管理 → 权限",
                "方法3: 设置 → 省电与电池 → 应用智能省电"
            ))
        }
    }
    
    private fun openHuaweiSettings() {
        val huaweiIntents = listOf(
            // 尝试打开应用启动管理
            Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            },
            // 尝试打开手机管家
            Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.MainActivity")
            },
            // 尝试打开电池优化
            Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity")
            },
            // 尝试华为设置
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings")
            }
        )
        
        var success = false
        for (intent in huaweiIntents) {
            try {
                startActivity(intent)
                success = true
                Toast.makeText(this, "已打开华为设置页面，请找到\"点击助手\"进行配置", Toast.LENGTH_LONG).show()
                break
            } catch (e: Exception) {
                // 继续尝试下一个Intent
            }
        }
        
        if (!success) {
            showManualSettingsGuide("华为设备", listOf(
                "方法1: 设置 → 电池 → 应用启动管理",
                "方法2: 手机管家 → 应用启动管理",
                "方法3: 设置 → 应用和服务 → 应用管理 → 点击助手 → 电池"
            ))
        }
    }
    
    private fun openOppoSettings() {
        val oppoIntents = listOf(
            // 尝试打开应用启动管理
            Intent().apply {
                setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            },
            // 尝试打开手机管家
            Intent().apply {
                setClassName("com.coloros.safecenter", "com.coloros.safecenter.MainActivity")
            },
            // 尝试打开应用权限管理
            Intent().apply {
                setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.PermissionManagerActivity")
            },
            // 尝试打开电池优化
            Intent().apply {
                setClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
            }
        )
        
        var success = false
        for (intent in oppoIntents) {
            try {
                startActivity(intent)
                success = true
                Toast.makeText(this, "已打开OPPO设置页面，请找到\"点击助手\"进行配置", Toast.LENGTH_LONG).show()
                break
            } catch (e: Exception) {
                // 继续尝试下一个Intent
            }
        }
        
        if (!success) {
            showManualSettingsGuide("OPPO设备", listOf(
                "方法1: 设置 → 电池 → 应用启动管理",
                "方法2: 手机管家 → 权限隐私 → 应用启动管理",
                "方法3: 设置 → 应用管理 → 点击助手 → 权限"
            ))
        }
    }
    
    private fun openVivoSettings() {
        val vivoIntents = listOf(
            // 尝试打开白名单管理
            Intent().apply {
                setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            },
            // 尝试打开i管家
            Intent().apply {
                setClassName("com.iqoo.secure", "com.iqoo.secure.MainActivity")
            },
            // 尝试打开应用管理
            Intent().apply {
                setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
            },
            // 尝试打开后台高耗电
            Intent().apply {
                setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgAppManagerActivity")
            }
        )
        
        var success = false
        for (intent in vivoIntents) {
            try {
                startActivity(intent)
                success = true
                Toast.makeText(this, "已打开vivo设置页面，请找到\"点击助手\"进行配置", Toast.LENGTH_LONG).show()
                break
            } catch (e: Exception) {
                // 继续尝试下一个Intent
            }
        }
        
        if (!success) {
            showManualSettingsGuide("vivo设备", listOf(
                "方法1: 设置 → 电池 → 后台高耗电",
                "方法2: i管家 → 应用管理 → 自启动管理",
                "方法3: 设置 → 应用与权限 → 权限管理"
            ))
        }
    }
    
    private fun openOnePlusSettings() {
        val onePlusIntents = listOf(
            // 尝试打开应用启动管理
            Intent().apply {
                setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            },
            // 尝试打开一加安全中心
            Intent().apply {
                setClassName("com.oneplus.security", "com.oneplus.security.MainActivity")
            },
            // 尝试打开电池优化
            Intent().apply {
                setClassName("com.oneplus.security", "com.oneplus.security.power.PowerManagerActivity")
            },
            // 尝试打开应用管理
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.applications.InstalledAppDetailsTop")
                putExtra("com.android.settings.ApplicationPkgName", packageName)
            }
        )
        
        var success = false
        for (intent in onePlusIntents) {
            try {
                startActivity(intent)
                success = true
                Toast.makeText(this, "已打开一加设置页面，请找到\"点击助手\"进行配置", Toast.LENGTH_LONG).show()
                break
            } catch (e: Exception) {
                // 继续尝试下一个Intent
            }
        }
        
        if (!success) {
            showManualSettingsGuide("一加设备", listOf(
                "方法1: 设置 → 电池 → 电池优化",
                "方法2: 设置 → 应用管理 → 应用启动管理",
                "方法3: 一加安全中心 → 应用管理"
            ))
        }
    }
    
    private fun openGeneralSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "请手动在系统设置中配置应用权限", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showManualSettingsGuide(deviceType: String, methods: List<String>) {
        val message = buildString {
            append("无法自动打开设置页面，请手动进行配置：\n\n")
            append("📱 $deviceType 设置方法：\n\n")
            methods.forEachIndexed { index, method ->
                append("${index + 1}. $method\n")
            }
            append("\n请在设置页面中找到\"点击助手\"并进行相应配置。")
        }
        
        AlertDialog.Builder(this)
            .setTitle("手动设置指南")
            .setMessage(message)
            .setPositiveButton("打开应用详情") { _, _ ->
                openGeneralSettings()
            }
            .setNegativeButton("知道了", null)
            .show()
    }
    
    private fun showDebugInfo() {
        val permissionStatus = PermissionHelper.checkAllPermissions(this)
        val prefs = getSharedPreferences("system_settings", Context.MODE_PRIVATE)
        val hasCompletedSystemSettings = prefs.getBoolean("completed_system_settings", false)
        
        val debugInfo = buildString {
            append("🔍 详细权限检测信息\n\n")
            append("设备信息：\n")
            append("${permissionStatus.deviceInfo}\n\n")
            
            append("权限状态：\n")
            append("• 悬浮窗权限: ${if (permissionStatus.hasOverlay) "✅ 已授予" else "❌ 未授予"}\n")
            append("• 无障碍权限: ${if (permissionStatus.hasAccessibility) "✅ 已启用" else "❌ 未启用"}\n")
            append("• 电池优化忽略: ${if (permissionStatus.hasIgnoreBatteryOptimization) "✅ 已忽略" else "❌ 未忽略"}\n")
            append("• 系统级设置: ${if (hasCompletedSystemSettings) "✅ 已完成" else "❌ 未完成"}\n\n")
            
            append("应用信息：\n")
            append("• 包名: ${packageName}\n")
            append("• 版本: ${packageManager.getPackageInfo(packageName, 0).versionName}\n\n")
            
            append("服务状态：\n")
            val serviceInstance = com.example.clickhelper.service.MyAccessibilityService.instance
            append("• 服务实例: ${if (serviceInstance != null) "✅ 存在" else "❌ 不存在"}\n")
            
            val manufacturer = Build.MANUFACTURER.lowercase()
            val needsSystemSettings = when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> true
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> true
                manufacturer.contains("oppo") || manufacturer.contains("realme") -> true
                manufacturer.contains("vivo") -> true
                manufacturer.contains("oneplus") -> true
                else -> false
            }
            
            if (needsSystemSettings) {
                append("\n⚠️ 系统级设置提示：\n")
                append("当前设备需要额外的系统级权限设置，\n")
                append("如自启动管理、省电策略等。")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("权限调试信息")
            .setMessage(debugInfo)
            .setPositiveButton("复制到剪贴板") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("权限调试信息", debugInfo)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "调试信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("重置系统设置") { _, _ ->
                // 重置系统设置状态
                prefs.edit().putBoolean("completed_system_settings", false).apply()
                Toast.makeText(this, "系统设置状态已重置", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun initScriptList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_scripts)
        scriptAdapter = ScriptAdapter(
            scripts = scripts,
            onItemClick = { script ->
                val intent = Intent(this, ScriptEditActivity::class.java)
                intent.putExtra("script", script)
                startActivity(intent)
            },
            onDeleteClick = { script ->
                showDeleteScriptDialog(script)
            },
            onSelectionChange = { script ->
                // 保存选择的脚本
                lifecycleScope.launch {
                    scriptStorage.saveSelectedScriptId(script.id)
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = scriptAdapter
    }

    private fun showAddScriptDialog() {
        val editText = EditText(this)
        editText.hint = "输入脚本名称"
        
        AlertDialog.Builder(this)
            .setTitle("新建脚本")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newScript = Script(
                        id = System.currentTimeMillis().toString(),
                        name = name,
                        events = mutableListOf(),
                        executionMode = com.example.clickhelper.model.ExecutionMode.ONCE
                    )
                    scripts.add(newScript)
                    scriptAdapter.notifyItemInserted(scripts.size - 1)
                    
                    // 如果这是第一个脚本，自动选择它
                    if (scripts.size == 1) {
                        scriptAdapter.setSelectedScriptId(newScript.id)
                        lifecycleScope.launch {
                            scriptStorage.saveSelectedScriptId(newScript.id)
                        }
                    }
                    
                    // 保存到本地存储
                    lifecycleScope.launch {
                        scriptStorage.saveScript(newScript)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun loadScripts() {
        lifecycleScope.launch {
            val savedScripts = scriptStorage.loadScripts()
            scripts.clear()
            scripts.addAll(savedScripts)
            scriptAdapter.notifyDataSetChanged()
            
            // 加载选择的脚本
            val selectedScriptId = scriptStorage.loadSelectedScriptId()
            if (selectedScriptId != null && scripts.any { it.id == selectedScriptId }) {
                scriptAdapter.setSelectedScriptId(selectedScriptId)
            } else if (scripts.isNotEmpty()) {
                // 如果没有选择的脚本或选择的脚本不存在，则默认选择第一个
                scriptAdapter.setSelectedScriptId(scripts.first().id)
                scriptStorage.saveSelectedScriptId(scripts.first().id)
            }
        }
    }
    
    private fun showDeleteScriptDialog(script: Script) {
        AlertDialog.Builder(this)
            .setTitle("删除脚本")
            .setMessage("确定要删除脚本 \"${script.name}\" 吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteScript(script)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun deleteScript(script: Script) {
        lifecycleScope.launch {
            val success = scriptStorage.deleteScript(script.id)
            if (success) {
                val index = scripts.indexOf(script)
                if (index != -1) {
                    scripts.removeAt(index)
                    scriptAdapter.notifyItemRemoved(index)
                    
                    // 如果删除的是当前选择的脚本，重新选择
                    if (scriptAdapter.getSelectedScriptId() == script.id) {
                        if (scripts.isNotEmpty()) {
                            // 选择第一个脚本
                            scriptAdapter.setSelectedScriptId(scripts.first().id)
                            scriptStorage.saveSelectedScriptId(scripts.first().id)
                        } else {
                            // 没有脚本了，清除选择
                            scriptAdapter.setSelectedScriptId(null)
                            scriptStorage.saveSelectedScriptId(null)
                        }
                    }
                    
                    Toast.makeText(this@MainActivity, "脚本已删除", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@MainActivity, "删除脚本失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 获取当前选择的脚本
     */
    fun getSelectedScript(): Script? {
        val selectedScriptId = scriptAdapter.getSelectedScriptId()
        return scripts.find { it.id == selectedScriptId }
    }
     
         override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
     
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_floating_toolbar -> {
                Log.d("MainActivity", "启动悬浮工具栏菜单被点击")
                if (PermissionHelper.hasOverlayPermission(this)) {
                    Log.d("MainActivity", "悬浮窗权限已授予，启动服务")
                    val intent = Intent(this, FloatingToolbarService::class.java)
                    intent.action = FloatingToolbarService.ACTION_SHOW_TOOLBAR
                    startService(intent)
                    Toast.makeText(this, "悬浮工具栏已启动", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("MainActivity", "悬浮窗权限未授予，请求权限")
                    Toast.makeText(this, "需要悬浮窗权限才能启动工具栏", Toast.LENGTH_LONG).show()
                    PermissionHelper.requestOverlayPermission(this, null)
                }
                true
            }
            R.id.action_system_settings -> {
                showSystemSettingsGuidance()
                true
            }
            R.id.action_check_permissions -> {
                checkPermissions()
                true
            }
//            R.id.action_token_status -> {
//                showTokenStatus()
//                true
//            }
//            R.id.action_clear_token -> {
//                showClearTokenDialog()
//                true
//            }
            else -> super.onOptionsItemSelected(item)
        }
    }
     

     
    /**
     * 显示Token状态信息
     */
    private fun showTokenStatus() {
        val tokenStatus = tokenManager.getTokenStatus()
        val currentTime = System.currentTimeMillis()
        
        val statusMessage = buildString {
            append("🔐 Token状态信息\n\n")
            
            if (tokenStatus.hasToken) {
                append("状态: ${if (tokenStatus.isValid) "✅ 有效" else "❌ 已过期"}\n")
                
                if (tokenStatus.saveTime > 0) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    append("保存时间: ${sdf.format(java.util.Date(tokenStatus.saveTime))}\n")
                }
                
                if (tokenStatus.lastActivity > 0) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    append("最后活动: ${sdf.format(java.util.Date(tokenStatus.lastActivity))}\n")
                }
                
                if (tokenStatus.isValid && tokenStatus.remainingTime > 0) {
                    append("剩余时间: ${formatRemainingTime(tokenStatus.remainingTime)}\n")
                } else if (!tokenStatus.isValid) {
                    append("剩余时间: 已过期\n")
                }
            } else {
                append("状态: ❌ 无Token\n")
            }
            
            append("\n💡 Token说明:\n")
            append("• Token有效期为3天\n")
            append("• 应用使用期间会自动更新活动时间\n")
            append("• Token过期后需要重新验证")
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.token_menu_status))
            .setMessage(statusMessage)
            .setPositiveButton("刷新状态") { _, _ ->
                // 重新检查Token状态
                if (!tokenManager.isTokenValid()) {
                    Toast.makeText(this, getString(R.string.token_expired_message), Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, TokenVerificationActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    showTokenStatus() // 重新显示状态
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    /**
     * 显示清除Token确认对话框
     */
    private fun showClearTokenDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.token_clear_confirm_title))
            .setMessage(getString(R.string.token_clear_confirm_message))
            .setPositiveButton(getString(R.string.token_dialog_ok)) { _, _ ->
                tokenManager.clearToken()
                Toast.makeText(this, getString(R.string.token_clear_success), Toast.LENGTH_SHORT).show()
                
                // 跳转到验证页面
                val intent = Intent(this, TokenVerificationActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.token_dialog_cancel), null)
            .show()
    }
    
    /**
     * 格式化剩余时间
     */
    private fun formatRemainingTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}${getString(R.string.token_days)}${hours % 24}${getString(R.string.token_hours)}${minutes % 60}${getString(R.string.token_minutes)}"
            hours > 0 -> "${hours}${getString(R.string.token_hours)}${minutes % 60}${getString(R.string.token_minutes)}"
            minutes > 0 -> "${minutes}${getString(R.string.token_minutes)}${seconds % 60}秒"
            else -> "${seconds}秒"
        }
    }

    // 权限处理现在通过设置页面完成，不再需要onRequestPermissionsResult
}