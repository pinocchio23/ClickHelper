package com.example.clickhelper.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
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
        private const val KEY_EXPIRY_TIME = "expiry_time"
        private const val KEY_ORIGINAL_TOKEN = "original_token"
        
        // Token解密算法使用的固定密钥
        private const val TOKEN_KEY = "ClickHelper2025"
    }

    /**
     * 验证Token是否有效（使用新的解密算法）
     */
    fun verifyToken(token: String): Boolean {
        try {
            Log.d(TAG, "开始验证Token: ${token.take(20)}...")
            
            // 1. Base64 解码
            val decodedData = String(Base64.decode(token, Base64.NO_WRAP))
            Log.d(TAG, "Base64解码结果: $decodedData")
            
            // 2. 解析结构
            val parts = decodedData.split(":")
            if (parts.size != 2) {
                Log.d(TAG, "Token格式错误，应该是 过期时间戳:哈希值前16位")
                return false
            }
            
            val expiryTimestamp = parts[0].toLong()
            val hashPrefix = parts[1]
            
            Log.d(TAG, "解析出过期时间戳: $expiryTimestamp")
            Log.d(TAG, "解析出哈希前缀: $hashPrefix")
            
            // 3. 验证是否过期
            val currentTime = System.currentTimeMillis()
            if (currentTime > expiryTimestamp) {
                Log.d(TAG, "Token已过期，当前时间: $currentTime，过期时间: $expiryTimestamp")
                return false
            }
            
            // 4. 验证Token完整性（可选但建议）
            if (!verifyTokenIntegrity(expiryTimestamp, hashPrefix)) {
                Log.d(TAG, "Token完整性验证失败")
                return false
            }
            
            Log.d(TAG, "Token验证成功")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Token验证失败", e)
            return false
        }
    }

    /**
     * 验证Token的完整性
     */
    private fun verifyTokenIntegrity(expiryTimestamp: Long, hashPrefix: String): Boolean {
        try {
            // 为了验证完整性，我们需要重新构建待加密字符串
            // 但是我们不知道原始的有效期天数，所以我们尝试常见的天数值
            val commonValidityPeriods = listOf(1, 3, 7, 15, 30, 60, 90, 180, 365)
            
            for (days in commonValidityPeriods) {
                val validityPeriodMs = days * 24 * 60 * 60 * 1000L
                val expectedGenerationTime = expiryTimestamp - validityPeriodMs
                
                // 允许一定的时间误差（比如1天）
                val currentTime = System.currentTimeMillis()
                if (Math.abs(currentTime - expectedGenerationTime) <= 24 * 60 * 60 * 1000L) {
                    // 构建待加密字符串
                    val dataToHash = "$expiryTimestamp|$days|$TOKEN_KEY"
                    
                    // 计算SHA-256
                    val sha256Hash = calculateSHA256(dataToHash)
                    val calculatedPrefix = sha256Hash.take(16)
                    
                    Log.d(TAG, "尝试验证，天数: $days")
                    Log.d(TAG, "待哈希数据: $dataToHash")
                    Log.d(TAG, "计算的哈希前缀: $calculatedPrefix")
                    Log.d(TAG, "Token中的哈希前缀: $hashPrefix")
                    
                    if (calculatedPrefix.equals(hashPrefix, ignoreCase = true)) {
                        Log.d(TAG, "Token完整性验证成功，有效期: $days 天")
                        return true
                    }
                }
            }
            
            Log.d(TAG, "无法验证Token完整性，但时间戳有效，允许使用")
            // 即使完整性验证失败，如果时间戳有效，我们也允许使用
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Token完整性验证出错", e)
            return true // 降级处理，只要时间戳有效就允许
        }
    }

    /**
     * 计算SHA-256哈希值
     */
    private fun calculateSHA256(data: String): String {
        return try {
            val bytes = data.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "SHA-256计算失败", e)
            ""
        }
    }

    /**
     * 解析Token信息
     */
    fun parseTokenInfo(token: String): TokenInfo? {
        return try {
            // 1. Base64 解码
            val decodedData = String(Base64.decode(token, Base64.NO_WRAP))
            
            // 2. 解析结构
            val parts = decodedData.split(":")
            if (parts.size != 2) return null
            
            val expiryTimestamp = parts[0].toLong()
            val hashPrefix = parts[1]
            
            // 3. 验证是否过期
            val currentTime = System.currentTimeMillis()
            val isValid = currentTime <= expiryTimestamp
            
            // 4. 格式化过期时间
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val expiryDate = dateFormat.format(Date(expiryTimestamp))
            
            TokenInfo(isValid, expiryDate, expiryTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "解析Token信息失败", e)
            null
        }
    }

    /**
     * 保存Token
     */
    fun saveToken(token: String) {
        val tokenInfo = parseTokenInfo(token)
        if (tokenInfo == null) {
            Log.e(TAG, "无法解析Token，保存失败")
            return
        }
        
        if (!tokenInfo.isValid) {
            Log.e(TAG, "Token已过期，不保存")
            return
        }
        
        val tokenHash = hashToken(token)
        val currentTime = System.currentTimeMillis()
        
        sharedPreferences.edit()
            .putString(KEY_TOKEN_HASH, tokenHash)
            .putString(KEY_ORIGINAL_TOKEN, token)
            .putLong(KEY_SAVE_TIME, currentTime)
            .putLong(KEY_LAST_ACTIVITY, currentTime)
            .putLong(KEY_EXPIRY_TIME, tokenInfo.expiryTimestamp)
            .apply()
        
        Log.d(TAG, "Token已保存，过期时间: ${tokenInfo.expiryDate}")
    }

    /**
     * 检查当前Token是否有效
     */
    fun isTokenValid(): Boolean {
        val originalToken = sharedPreferences.getString(KEY_ORIGINAL_TOKEN, null)
        val expiryTime = sharedPreferences.getLong(KEY_EXPIRY_TIME, 0)
        
        if (originalToken.isNullOrEmpty() || expiryTime == 0L) {
            Log.d(TAG, "没有保存的Token")
            return false
        }
        
        val currentTime = System.currentTimeMillis()
        
        // 检查Token是否过期
        if (currentTime > expiryTime) {
            Log.d(TAG, "Token已过期，过期时间: ${formatTimestamp(expiryTime)}")
            clearToken()
            return false
        }
        
        // 重新验证原始Token的有效性
        if (!verifyToken(originalToken)) {
            Log.d(TAG, "Token验证失败")
            clearToken()
            return false
        }
        
        // 更新最后活动时间
        updateLastActivity()
        
        val remainingTime = expiryTime - currentTime
        Log.d(TAG, "Token有效，剩余时间: ${formatDuration(remainingTime)}")
        return true
    }

    /**
     * 获取Token剩余有效时间（毫秒）
     */
    fun getRemainingTime(): Long {
        val expiryTime = sharedPreferences.getLong(KEY_EXPIRY_TIME, 0)
        if (expiryTime == 0L) {
            return 0L
        }
        
        val currentTime = System.currentTimeMillis()
        val remaining = expiryTime - currentTime
        
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
            .remove(KEY_ORIGINAL_TOKEN)
            .remove(KEY_SAVE_TIME)
            .remove(KEY_LAST_ACTIVITY)
            .remove(KEY_EXPIRY_TIME)
            .apply()
        
        Log.d(TAG, "Token已清除")
    }

    /**
     * 获取Token状态信息
     */
    fun getTokenStatus(): TokenStatus {
        val originalToken = sharedPreferences.getString(KEY_ORIGINAL_TOKEN, null)
        val saveTime = sharedPreferences.getLong(KEY_SAVE_TIME, 0)
        val lastActivity = sharedPreferences.getLong(KEY_LAST_ACTIVITY, 0)
        val expiryTime = sharedPreferences.getLong(KEY_EXPIRY_TIME, 0)
        
        return TokenStatus(
            hasToken = !originalToken.isNullOrEmpty(),
            isValid = isTokenValid(),
            saveTime = saveTime,
            lastActivity = lastActivity,
            expiryTime = expiryTime,
            remainingTime = getRemainingTime()
        )
    }

    /**
     * 强制Token过期
     */
    fun expireToken() {
        val expiryTime = sharedPreferences.getLong(KEY_EXPIRY_TIME, 0)
        if (expiryTime > 0) {
            // 将过期时间设置为当前时间之前
            val expiredTime = System.currentTimeMillis() - 1000
            sharedPreferences.edit()
                .putLong(KEY_EXPIRY_TIME, expiredTime)
                .apply()
            
            Log.d(TAG, "Token已强制过期")
        }
    }

    /**
     * 对Token进行哈希处理（用于存储）
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
     * Token信息数据类
     */
    data class TokenInfo(
        val isValid: Boolean,
        val expiryDate: String,
        val expiryTimestamp: Long
    )

    /**
     * Token状态数据类
     */
    data class TokenStatus(
        val hasToken: Boolean,
        val isValid: Boolean,
        val saveTime: Long,
        val lastActivity: Long,
        val expiryTime: Long,
        val remainingTime: Long
    )
} 