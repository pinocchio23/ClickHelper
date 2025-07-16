package com.example.clickhelper.ui.script

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.clickhelper.R
import com.example.clickhelper.model.Script

class ScriptAdapter(
    private val scripts: List<Script>,
    private val onItemClick: (Script) -> Unit,
    private val onDeleteClick: (Script) -> Unit,
    private val onSelectionChange: (Script) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.ScriptViewHolder>() {

    private var selectedScriptId: String? = null

    class ScriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.card_script)
        val nameTextView: TextView = itemView.findViewById(R.id.tv_script_name)
        val eventCountTextView: TextView = itemView.findViewById(R.id.tv_event_count)
        val selectedIndicator: ImageView = itemView.findViewById(R.id.iv_selected_indicator)
        val deleteButton: ImageView = itemView.findViewById(R.id.iv_delete_script)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_script, parent, false)
        return ScriptViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScriptViewHolder, position: Int) {
        val script = scripts[position]
        holder.nameTextView.text = script.name
        holder.eventCountTextView.text = "${script.events.size} 个事件"
        
        // 设置选择状态指示器
        val isSelected = script.id == selectedScriptId
        holder.selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
        
        // 设置选中状态的视觉效果
        val context = holder.itemView.context
        if (isSelected) {
            // 选中状态：设置背景和增加阴影效果
            holder.cardView.background = ContextCompat.getDrawable(context, R.drawable.selected_script_background)
            holder.cardView.cardElevation = 8f // 增加阴影效果
        } else {
            // 未选中状态：恢复默认样式
            // 明确重置背景，确保没有残留的选中状态
            holder.cardView.background = null
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            holder.cardView.cardElevation = 4f // 默认阴影
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            // 切换选择状态
            if (selectedScriptId != script.id) {
                val previousSelectedId = selectedScriptId
                selectedScriptId = script.id
                onSelectionChange(script)
                
                // 更精确地更新视图：只更新需要改变的项目
                if (previousSelectedId != null) {
                    // 找到之前选中的项目并更新
                    val previousIndex = scripts.indexOfFirst { it.id == previousSelectedId }
                    if (previousIndex != -1) {
                        notifyItemChanged(previousIndex)
                    }
                }
                // 更新当前选中的项目
                notifyItemChanged(position)
            } else {
                // 如果已经选择了，则点击进入编辑
                onItemClick(script)
            }
        }
        
        // 设置删除按钮点击事件
        holder.deleteButton.setOnClickListener {
            onDeleteClick(script)
        }
    }

    override fun getItemCount(): Int = scripts.size
    
    /**
     * 设置当前选择的脚本ID
     */
    fun setSelectedScriptId(scriptId: String?) {
        val previousSelectedId = selectedScriptId
        selectedScriptId = scriptId
        
        // 更精确地更新视图：只更新需要改变的项目
        if (previousSelectedId != null && previousSelectedId != scriptId) {
            // 清除之前选中的项目
            val previousIndex = scripts.indexOfFirst { it.id == previousSelectedId }
            if (previousIndex != -1) {
                notifyItemChanged(previousIndex)
            }
        }
        
        if (scriptId != null && scriptId != previousSelectedId) {
            // 更新当前选中的项目
            val currentIndex = scripts.indexOfFirst { it.id == scriptId }
            if (currentIndex != -1) {
                notifyItemChanged(currentIndex)
            }
        }
        
        // 如果需要全量更新（比如清除所有选择），则使用notifyDataSetChanged
        if (previousSelectedId != null && scriptId == null) {
            notifyDataSetChanged()
        }
    }
    
    /**
     * 获取当前选择的脚本ID
     */
    fun getSelectedScriptId(): String? = selectedScriptId
} 