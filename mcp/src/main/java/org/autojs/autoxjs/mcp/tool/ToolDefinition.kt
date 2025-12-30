package org.autojs.autoxjs.mcp.tool

import com.google.gson.JsonObject

data class ToolDefinition(
    val name: String,
    val title: String?,
    val description: String,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject? = null
)
