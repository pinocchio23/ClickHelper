package com.example.clickhelper.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.clickhelper.R

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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
    private var hasClick = false
    private var hasSwipe = false
    private var hasOcrRect = false

    private val clickPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val clickRingPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val swipePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val arrowPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ocrRectPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val ocrFillPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        alpha = 50
        isAntiAlias = true
    }

    fun setClickPoint(x: Float, y: Float) {
        clickX = x
        clickY = y
        hasClick = true
        hasSwipe = false
        hasOcrRect = false
    }

    fun setSwipePath(startX: Float, startY: Float, endX: Float, endY: Float) {
        swipeStartX = startX
        swipeStartY = startY
        swipeEndX = endX
        swipeEndY = endY
        hasSwipe = true
        hasClick = false
        hasOcrRect = false
    }

    fun setOcrRect(startX: Float, startY: Float, endX: Float, endY: Float) {
        ocrLeft = kotlin.math.min(startX, endX)
        ocrTop = kotlin.math.min(startY, endY)
        ocrRight = kotlin.math.max(startX, endX)
        ocrBottom = kotlin.math.max(startY, endY)
        hasOcrRect = true
        hasClick = false
        hasSwipe = false
    }

    fun clear() {
        hasClick = false
        hasSwipe = false
        hasOcrRect = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (hasClick) {
            drawClickPoint(canvas)
        }

        if (hasSwipe) {
            drawSwipePath(canvas)
        }

        if (hasOcrRect) {
            drawOcrRect(canvas)
        }
    }

    private fun drawClickPoint(canvas: Canvas) {
        // 绘制点击点的圆圈
        canvas.drawCircle(clickX, clickY, 30f, clickPaint)
        canvas.drawCircle(clickX, clickY, 50f, clickRingPaint)
        canvas.drawCircle(clickX, clickY, 70f, clickRingPaint.apply { alpha = 128 })
    }

    private fun drawSwipePath(canvas: Canvas) {
        // 绘制滑动路径
        canvas.drawLine(swipeStartX, swipeStartY, swipeEndX, swipeEndY, swipePaint)
        
        // 绘制起点
        canvas.drawCircle(swipeStartX, swipeStartY, 20f, Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
            isAntiAlias = true
        })
        
        // 绘制终点箭头
        drawArrow(canvas, swipeStartX, swipeStartY, swipeEndX, swipeEndY)
    }

    private fun drawArrow(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
        val arrowLength = 40f
        val arrowAngle = Math.PI / 6 // 30度
        val arrowOffset = 25f // 箭头相对于终点的偏移距离，避免与终点圆圈重叠
        
        // 计算箭头方向
        val dx = endX - startX
        val dy = endY - startY
        val length = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        
        if (length > 0) {
            val unitX = dx / length
            val unitY = dy / length
            
            // 计算箭头尖端位置，向起点方向偏移一定距离
            val arrowTipX = endX - arrowOffset * unitX
            val arrowTipY = endY - arrowOffset * unitY
            
            // 箭头的两个分支
            val arrowX1 = arrowTipX - arrowLength * (unitX * Math.cos(arrowAngle) - unitY * Math.sin(arrowAngle)).toFloat()
            val arrowY1 = arrowTipY - arrowLength * (unitX * Math.sin(arrowAngle) + unitY * Math.cos(arrowAngle)).toFloat()
            
            val arrowX2 = arrowTipX - arrowLength * (unitX * Math.cos(-arrowAngle) - unitY * Math.sin(-arrowAngle)).toFloat()
            val arrowY2 = arrowTipY - arrowLength * (unitX * Math.sin(-arrowAngle) + unitY * Math.cos(-arrowAngle)).toFloat()
            
            // 绘制箭头
            val arrowPath = Path().apply {
                moveTo(arrowTipX, arrowTipY)
                lineTo(arrowX1, arrowY1)
                lineTo(arrowX2, arrowY2)
                close()
            }
            
            canvas.drawPath(arrowPath, arrowPaint)
        }
        
        // 绘制终点圆圈
        canvas.drawCircle(endX, endY, 15f, Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        })
    }

    private fun drawOcrRect(canvas: Canvas) {
        // 绘制半透明填充
        canvas.drawRect(ocrLeft, ocrTop, ocrRight, ocrBottom, ocrFillPaint)
        
        // 绘制边框
        canvas.drawRect(ocrLeft, ocrTop, ocrRight, ocrBottom, ocrRectPaint)
        
        // 绘制四个角的标记
        val cornerSize = 20f
        val cornerPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        
        // 左上角
        canvas.drawLine(ocrLeft, ocrTop, ocrLeft + cornerSize, ocrTop, cornerPaint)
        canvas.drawLine(ocrLeft, ocrTop, ocrLeft, ocrTop + cornerSize, cornerPaint)
        
        // 右上角
        canvas.drawLine(ocrRight - cornerSize, ocrTop, ocrRight, ocrTop, cornerPaint)
        canvas.drawLine(ocrRight, ocrTop, ocrRight, ocrTop + cornerSize, cornerPaint)
        
        // 左下角
        canvas.drawLine(ocrLeft, ocrBottom - cornerSize, ocrLeft, ocrBottom, cornerPaint)
        canvas.drawLine(ocrLeft, ocrBottom, ocrLeft + cornerSize, ocrBottom, cornerPaint)
        
        // 右下角
        canvas.drawLine(ocrRight - cornerSize, ocrBottom, ocrRight, ocrBottom, cornerPaint)
        canvas.drawLine(ocrRight, ocrBottom - cornerSize, ocrRight, ocrBottom, cornerPaint)
    }
} 