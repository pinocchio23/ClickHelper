package com.example.clickhelper.ui.script

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.clickhelper.R
import com.example.clickhelper.model.EventType
import com.example.clickhelper.model.ScriptEvent
import java.util.Collections

class EventAdapter(
    private val events: MutableList<ScriptEvent>,
    private val onItemClick: (ScriptEvent, Int) -> Unit = { _, _ -> },
    private val onItemMoved: () -> Unit = {}
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    private var itemTouchHelper: ItemTouchHelper? = null

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.iv_event_icon)
        val titleTextView: TextView = itemView.findViewById(R.id.tv_event_title)
        val descriptionTextView: TextView = itemView.findViewById(R.id.tv_event_description)
        val dragHandle: ImageView = itemView.findViewById(R.id.iv_drag_handle)
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                
                // 不允许移动OCR节点，也不允许其他节点移动到OCR节点位置之后
                val fromEvent = events[fromPosition]
                val toEvent = events[toPosition]
                
                if (fromEvent.type == EventType.OCR) {
                    return false // OCR节点不能移动
                }
                
                if (toEvent.type == EventType.OCR) {
                    return false // 不能移动到OCR节点位置
                }
                
                // 如果列表中有OCR节点，不能移动到最后一个位置（OCR节点前面）
                val hasOcrNode = events.any { it.type == EventType.OCR }
                if (hasOcrNode && toPosition == events.size - 1) {
                    return false
                }
                
                // 执行移动
                Collections.swap(events, fromPosition, toPosition)
                notifyItemMoved(fromPosition, toPosition)
                onItemMoved() // 通知保存
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false // 禁用长按拖拽，使用拖拽手柄
            }
        }
        
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        
        when (event.type) {
            EventType.CLICK -> {
                holder.iconImageView.setImageResource(R.drawable.ic_click)
                holder.titleTextView.text = "点击"
                val x = (event.params["x"] as? Number)?.toFloat() ?: 0f
                val y = (event.params["y"] as? Number)?.toFloat() ?: 0f
                holder.descriptionTextView.text = "点击位置: (${x.toInt()}, ${y.toInt()})"
            }
            EventType.SWIPE -> {
                holder.iconImageView.setImageResource(R.drawable.ic_swipe)
                holder.titleTextView.text = "滑动"
                val startX = (event.params["startX"] as? Number)?.toFloat() ?: 0f
                val startY = (event.params["startY"] as? Number)?.toFloat() ?: 0f
                val endX = (event.params["endX"] as? Number)?.toFloat() ?: 0f
                val endY = (event.params["endY"] as? Number)?.toFloat() ?: 0f
                holder.descriptionTextView.text = "从 (${startX.toInt()}, ${startY.toInt()}) 到 (${endX.toInt()}, ${endY.toInt()})"
            }
            EventType.WAIT -> {
                holder.iconImageView.setImageResource(R.drawable.ic_wait)
                holder.titleTextView.text = "等待"
                val duration = (event.params["duration"] as? Number)?.toInt() ?: 1000
                holder.descriptionTextView.text = "等待 ${duration}ms"
            }
            EventType.OCR -> {
                holder.iconImageView.setImageResource(R.drawable.ic_ocr)
                holder.titleTextView.text = "识别文本"
                val left = event.params["left"] as? Number
                val top = event.params["top"] as? Number
                val right = event.params["right"] as? Number
                val bottom = event.params["bottom"] as? Number
                val targetNumber = event.params["targetNumber"] as? Number
                val targetText = event.params["targetText"] as? String
                val comparisonType = event.params["comparisonType"] as? String ?: "小于"
                
                if (left != null && top != null && right != null && bottom != null) {
                    val targetInfo = if (targetNumber != null) {
                        // 数字识别
                        "$comparisonType $targetNumber"
                    } else if (targetText != null) {
                        // 文字识别
                        "$comparisonType $targetText"
                    } else {
                        // 兼容旧数据
                        "识别文本"
                    }
                    holder.descriptionTextView.text = "区域: (${left.toInt()}, ${top.toInt()}) - (${right.toInt()}, ${bottom.toInt()})\n目标: $targetInfo"
                } else {
                    holder.descriptionTextView.text = "识别屏幕上的文本"
                }
            }
        }
        
        // 设置拖拽手柄的可见性和功能
        if (event.type == EventType.OCR) {
            // OCR节点不显示拖拽手柄
            holder.dragHandle.visibility = View.GONE
        } else {
            holder.dragHandle.visibility = View.VISIBLE
            holder.dragHandle.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(holder)
                }
                false
            }
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(event, position)
        }
    }

    override fun getItemCount(): Int = events.size
} 