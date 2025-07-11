package com.example.clickhelper.ui.script

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.clickhelper.R
import com.example.clickhelper.model.Script

class ScriptAdapter(
    private val scripts: List<Script>,
    private val onItemClick: (Script) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.ScriptViewHolder>() {

    class ScriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.tv_script_name)
        val eventCountTextView: TextView = itemView.findViewById(R.id.tv_event_count)
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
        
        holder.itemView.setOnClickListener {
            onItemClick(script)
        }
    }

    override fun getItemCount(): Int = scripts.size
} 