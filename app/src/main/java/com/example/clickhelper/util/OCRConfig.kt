package com.example.clickhelper.util

/**
 * OCR优化配置类
 * 包含各种优化参数和策略配置
 */
object OCRConfig {
    
    // 图像质量参数
    const val MIN_CHAR_SIZE = 16           // ML Kit推荐的最小字符大小
    const val OPTIMAL_CHAR_SIZE = 24       // ML Kit推荐的最佳字符大小
    const val MAX_CHAR_SIZE = 48           // 最大字符大小
    const val QUALITY_THRESHOLD = 100.0    // 图像质量阈值
    
    // 置信度参数
    const val MIN_CONFIDENCE = 0.5f        // 最小置信度阈值
    const val HIGH_CONFIDENCE = 0.8f       // 高置信度阈值
    
    // 图像处理参数
    const val GAUSSIAN_KERNEL_SIZE = 3     // 高斯滤波核大小
    const val ADAPTIVE_THRESHOLD_WINDOW = 15 // 自适应阈值窗口大小
    const val ADAPTIVE_THRESHOLD_C = 10    // 自适应阈值常数
    
    // 增强参数
    const val CONTRAST_FACTOR = 1.5f       // 对比度增强因子
    const val GAMMA_CORRECTION = 1.2f      // 伽马校正值
    const val USM_AMOUNT = 1.5f           // USM锐化强度
    
    // 形态学参数
    const val MORPHOLOGY_KERNEL_SIZE = 3   // 形态学核大小
    
    // 边缘检测参数
    const val EDGE_THRESHOLD = 128         // 边缘检测阈值
    const val TEXT_REGION_MARGIN = 10      // 文本区域边距
    
    // 投票参数
    const val MIN_VOTE_COUNT = 2          // 最小投票数
    const val CONFIDENCE_WEIGHT = 0.7f    // 置信度权重
    const val FREQUENCY_WEIGHT = 0.3f     // 频率权重
    
    // 数字验证参数
    const val MIN_VALID_NUMBER = -999999999.0  // 最小有效数字
    const val MAX_VALID_NUMBER = 999999999.0   // 最大有效数字
    const val MAX_DECIMAL_PLACES = 6           // 最大小数位数
    
    // 识别策略配置
    data class StrategyConfig(
        val name: String,
        val enabled: Boolean = true,
        val weight: Float = 1.0f,
        val description: String
    )
    
    val RECOGNITION_STRATEGIES = listOf(
        StrategyConfig("原图", true, 1.0f, "使用原始图像进行识别"),
        StrategyConfig("CLAHE增强", true, 1.2f, "自适应直方图均衡化增强"),
        StrategyConfig("USM锐化", true, 1.1f, "USM锐化增强边缘"),
        StrategyConfig("伽马校正", true, 1.0f, "伽马校正调整亮度"),
        StrategyConfig("自适应阈值", true, 1.3f, "自适应阈值二值化"),
        StrategyConfig("高对比度", true, 1.1f, "对比度增强"),
        StrategyConfig("降噪处理", true, 0.9f, "中值滤波降噪"),
        StrategyConfig("形态学处理", true, 1.0f, "形态学开运算")
    )
    
    // 数字匹配模式
    val NUMBER_PATTERNS = listOf(
        "[+-]?\\d{1,10}\\.\\d{1,6}".toRegex(),  // 带符号小数
        "\\d{1,10}\\.\\d{1,6}".toRegex(),      // 小数
        "\\d{1,10},\\d{1,6}".toRegex(),       // 逗号小数
        "[+-]?\\d{1,10}".toRegex(),           // 带符号整数
        "\\d{1,10}".toRegex()                 // 整数
    )
    
    // 文本清理规则
    val TEXT_CLEANUP_RULES = listOf(
        "[^0-9.,\\-+\\s]".toRegex() to "",    // 移除非数字字符
        "\\s+".toRegex() to " ",              // 合并多个空格
        "^\\s+|\\s+$".toRegex() to ""         // 移除首尾空格
    )
    
    // 获取启用的策略
    fun getEnabledStrategies(): List<StrategyConfig> {
        return RECOGNITION_STRATEGIES.filter { it.enabled }
    }
    
    // 计算策略权重
    fun calculateStrategyWeight(strategyName: String): Float {
        return RECOGNITION_STRATEGIES.find { it.name == strategyName }?.weight ?: 1.0f
    }
    
    // 验证数字是否有效
    fun isValidNumber(number: Double): Boolean {
        return number.isFinite() && 
               number >= MIN_VALID_NUMBER && 
               number <= MAX_VALID_NUMBER
    }
    
    // 计算最佳缩放因子
    fun calculateOptimalScaleFactor(estimatedCharSize: Int): Float {
        return when {
            estimatedCharSize < MIN_CHAR_SIZE -> {
                OPTIMAL_CHAR_SIZE.toFloat() / estimatedCharSize
            }
            estimatedCharSize > MAX_CHAR_SIZE -> {
                OPTIMAL_CHAR_SIZE.toFloat() / estimatedCharSize
            }
            else -> 1.0f
        }
    }
} 