package com.example.clickhelper.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class TokenManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "TokenManager"
        private const val PREF_NAME = "token_prefs"
        private const val KEY_TOKEN_HASH = "token_hash"
        private const val KEY_SAVE_TIME = "save_time"
        private const val KEY_LAST_ACTIVITY = "last_activity"
        
        // Token有效期（3天）
        private const val TOKEN_VALIDITY_PERIOD = 3 * 24 * 60 * 60 * 1000L // 3天（毫秒）
        
        // 预设的有效Token列表（实际应用中应该从服务器获取）
        private val VALID_TOKENS = listOf(
            "ClickHelper2024",
            "AutoClick@2024",
            "Helper#2024",
            "CH2024@Token",
            "SmartClick2024"
        )
    }

    /**
     * 验证Token是否有效
     */
    fun verifyToken(token: String): Boolean {
        val isValid = VALID_TOKENS.contains(token)
        Log.d(TAG, "Token验证结果: $isValid")
        return isValid
    }

    /**
     * 保存Token
     */
    fun saveToken(token: String) {
        val tokenHash = hashToken(token)
        val currentTime = System.currentTimeMillis()
        
        sharedPreferences.edit()
            .putString(KEY_TOKEN_HASH, tokenHash)
            .putLong(KEY_SAVE_TIME, currentTime)
            .putLong(KEY_LAST_ACTIVITY, currentTime)
            .apply()
        
        Log.d(TAG, "Token已保存，保存时间: ${formatTimestamp(currentTime)}")
    }

    /**
     * 检查当前Token是否有效
     */
    fun isTokenValid(): Boolean {
        val tokenHash = sharedPreferences.getString(KEY_TOKEN_HASH, null)
        val saveTime = sharedPreferences.getLong(KEY_SAVE_TIME, 0)
        val lastActivity = sharedPreferences.getLong(KEY_LAST_ACTIVITY, 0)
        
        if (tokenHash.isNullOrEmpty() || saveTime == 0L) {
            Log.d(TAG, "没有保存的Token")
            return false
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceSave = currentTime - saveTime
        val timeSinceLastActivity = currentTime - lastActivity
        
        // 检查Token是否过期
        if (timeSinceSave > TOKEN_VALIDITY_PERIOD) {
            Log.d(TAG, "Token已过期，保存时间: ${formatTimestamp(saveTime)}")
            clearToken()
            return false
        }
        
        // 更新最后活动时间
        updateLastActivity()
        
        Log.d(TAG, "Token有效，剩余时间: ${formatDuration(TOKEN_VALIDITY_PERIOD - timeSinceSave)}")
        return true
    }

    /**
     * 获取Token剩余有效时间（毫秒）
     */
    fun getRemainingTime(): Long {
        val saveTime = sharedPreferences.getLong(KEY_SAVE_TIME, 0)
        if (saveTime == 0L) {
            return 0L
        }
        
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - saveTime
        val remaining = TOKEN_VALIDITY_PERIOD - elapsed
        
        return if (remaining > 0) remaining else 0L
    }

    /**
     * 更新最后活动时间
     */
    fun updateLastActivity() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .apply()
    }

    /**
     * 清除Token
     */
    fun clearToken() {
        sharedPreferences.edit()
            .remove(KEY_TOKEN_HASH)
            .remove(KEY_SAVE_TIME)
            .remove(KEY_LAST_ACTIVITY)
            .apply()
        
        Log.d(TAG, "Token已清除")
    }

    /**
     * 获取Token状态信息
     */
    fun getTokenStatus(): TokenStatus {
        val tokenHash = sharedPreferences.getString(KEY_TOKEN_HASH, null)
        val saveTime = sharedPreferences.getLong(KEY_SAVE_TIME, 0)
        val lastActivity = sharedPreferences.getLong(KEY_LAST_ACTIVITY, 0)
        
        return TokenStatus(
            hasToken = !tokenHash.isNullOrEmpty(),
            isValid = isTokenValid(),
            saveTime = saveTime,
            lastActivity = lastActivity,
            remainingTime = getRemainingTime()
        )
    }

    /**
     * 强制Token过期
     */
    fun expireToken() {
        val saveTime = sharedPreferences.getLong(KEY_SAVE_TIME, 0)
        if (saveTime > 0) {
            // 将保存时间设置为过期时间之前
            val expiredTime = System.currentTimeMillis() - TOKEN_VALIDITY_PERIOD - 1000
            sharedPreferences.edit()
                .putLong(KEY_SAVE_TIME, expiredTime)
                .apply()
            
            Log.d(TAG, "Token已强制过期")
        }
    }

    /**
     * 获取所有有效Token列表（仅用于调试）
     */
    fun getValidTokens(): List<String> {
        return VALID_TOKENS
    }

    /**
     * 对Token进行哈希处理
     */
    private fun hashToken(token: String): String {
        return try {
            val bytes = token.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Token哈希处理失败", e)
            token // 降级到原始Token
        }
    }

    /**
     * 格式化时间戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 格式化时间长度
     */
    private fun formatDuration(duration: Long): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}天${hours % 24}小时"
            hours > 0 -> "${hours}小时${minutes % 60}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "${seconds}秒"
        }
    }

    /**
     * Token状态数据类
     */
    data class TokenStatus(
        val hasToken: Boolean,
        val isValid: Boolean,
        val saveTime: Long,
        val lastActivity: Long,
        val remainingTime: Long
    )
} 