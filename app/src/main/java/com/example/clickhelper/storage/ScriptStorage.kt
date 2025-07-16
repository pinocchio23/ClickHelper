package com.example.clickhelper.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.clickhelper.model.Script
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ScriptStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "ScriptStorage"
        private const val SCRIPTS_DIR = "ClickHelper"
        private const val SCRIPTS_FILE = "scripts.json"
        private const val SELECTED_SCRIPT_KEY = "selected_script_id"
    }
    
    private val scriptsDir: File by lazy {
        File(context.getExternalFilesDir(null), SCRIPTS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    private val scriptsFile: File by lazy {
        File(scriptsDir, SCRIPTS_FILE)
    }
    
    suspend fun saveScripts(scripts: List<Script>): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            scripts.forEach { script ->
                val scriptJson = JSONObject().apply {
                    put("id", script.id)
                    put("name", script.name)
                    put("executionMode", script.executionMode.name)
                    
                    val eventsArray = JSONArray()
                    script.events.forEach { event ->
                        val eventJson = JSONObject().apply {
                            put("type", event.type.name)
                            put("params", JSONObject(event.params))
                        }
                        eventsArray.put(eventJson)
                    }
                    put("events", eventsArray)
                }
                jsonArray.put(scriptJson)
            }
            
            FileOutputStream(scriptsFile).use { fos ->
                fos.write(jsonArray.toString().toByteArray())
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存脚本失败", e)
            false
        }
    }
    
    suspend fun loadScripts(): List<Script> = withContext(Dispatchers.IO) {
        try {
            if (!scriptsFile.exists()) {
                return@withContext emptyList<Script>()
            }
            
            val jsonString = FileInputStream(scriptsFile).use { fis ->
                fis.readBytes().toString(Charsets.UTF_8)
            }
            
            val jsonArray = JSONArray(jsonString)
            val scripts = mutableListOf<Script>()
            
            for (i in 0 until jsonArray.length()) {
                val scriptJson = jsonArray.getJSONObject(i)
                val executionMode = try {
                    com.example.clickhelper.model.ExecutionMode.valueOf(
                        scriptJson.optString("executionMode", "ONCE")
                    )
                } catch (e: Exception) {
                    com.example.clickhelper.model.ExecutionMode.ONCE
                }
                val script = Script(
                    id = scriptJson.getString("id"),
                    name = scriptJson.getString("name"),
                    events = mutableListOf(),
                    executionMode = executionMode
                )
                
                val eventsArray = scriptJson.getJSONArray("events")
                for (j in 0 until eventsArray.length()) {
                    val eventJson = eventsArray.getJSONObject(j)
                    val eventType = com.example.clickhelper.model.EventType.valueOf(eventJson.getString("type"))
                    val paramsJson = eventJson.getJSONObject("params")
                    
                    val params = mutableMapOf<String, Any>()
                    paramsJson.keys().forEach { key ->
                        val value = paramsJson.get(key)
                        // 修复数据类型转换问题：将JSON中的Double转换为适当的类型
                        params[key] = when (value) {
                            is Double -> {
                                // 对于坐标相关的参数，转换为Float
                                if (key in listOf("x", "y", "startX", "startY", "endX", "endY", "left", "top", "right", "bottom")) {
                                    value.toFloat()
                                } else {
                                    // 对于其他数值参数（如duration），保持为Int
                                    value.toInt()
                                }
                            }
                            else -> value
                        }
                    }
                    script.events.add(
                        com.example.clickhelper.model.ScriptEvent(eventType, params)
                    )
                }
                
                scripts.add(script)
            }
            
            scripts
        } catch (e: Exception) {
            Log.e(TAG, "加载脚本失败", e)
            emptyList()
        }
    }
    
    suspend fun saveScript(script: Script): Boolean = withContext(Dispatchers.IO) {
        try {
            val scripts = loadScripts().toMutableList()
            val existingIndex = scripts.indexOfFirst { it.id == script.id }
            
            if (existingIndex >= 0) {
                scripts[existingIndex] = script
            } else {
                scripts.add(script)
            }
            
            saveScripts(scripts)
        } catch (e: Exception) {
            Log.e(TAG, "保存单个脚本失败", e)
            false
        }
    }
    
    suspend fun deleteScript(scriptId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val scripts = loadScripts().toMutableList()
            scripts.removeAll { it.id == scriptId }
            saveScripts(scripts)
        } catch (e: Exception) {
            Log.e(TAG, "删除脚本失败", e)
            false
        }
    }
    
    /**
     * 保存当前选择的脚本ID
     */
    suspend fun saveSelectedScriptId(scriptId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("script_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (scriptId != null) {
                    putString(SELECTED_SCRIPT_KEY, scriptId)
                } else {
                    remove(SELECTED_SCRIPT_KEY)
                }
                apply()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存选择的脚本ID失败", e)
            false
        }
    }
    
    /**
     * 加载当前选择的脚本ID
     */
    suspend fun loadSelectedScriptId(): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("script_settings", Context.MODE_PRIVATE)
            prefs.getString(SELECTED_SCRIPT_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "加载选择的脚本ID失败", e)
            null
        }
    }
}