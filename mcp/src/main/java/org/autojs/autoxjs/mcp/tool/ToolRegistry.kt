package org.autojs.autoxjs.mcp.tool

import com.google.gson.JsonObject
import org.autojs.autoxjs.mcp.McpResponse
import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {
    private data class Entry(
        val definition: ToolDefinition?,
        val tool: McpTool
    )

    private val tools = ConcurrentHashMap<String, Entry>()

    fun register(name: String, tool: McpTool) {
        tools[name] = Entry(null, tool)
    }

    fun register(definition: ToolDefinition, tool: McpTool) {
        tools[definition.name] = Entry(definition, tool)
    }

    fun names(): Set<String> = tools.keys

    fun definitions(): List<ToolDefinition> {
        return tools.entries.map { (name, entry) ->
            entry.definition ?: ToolDefinition(
                name = name,
                title = null,
                description = "Tool $name",
                inputSchema = ToolSchemas.emptyObject()
            )
        }.sortedBy { it.name }
    }

    suspend fun invoke(name: String, params: JsonObject?): McpResponse {
        val entry = tools[name] ?: return McpResponse.error("NotFound", "Tool `$name` not registered")
        return try {
            entry.tool.handle(params)
        } catch (e: Exception) {
            McpResponse.error("Internal", e.message ?: "Internal error")
        }
    }
}
