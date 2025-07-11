package com.example.clickhelper.model

import java.io.Serializable

enum class EventType { CLICK, SWIPE, WAIT, OCR }

enum class ExecutionMode { ONCE, REPEAT }

data class ScriptEvent(
    val type: EventType,
    val params: Map<String, Any>
) : Serializable

data class Script(
    val id: String,
    var name: String,
    val events: MutableList<ScriptEvent>,
    var executionMode: ExecutionMode = ExecutionMode.ONCE
) : Serializable 