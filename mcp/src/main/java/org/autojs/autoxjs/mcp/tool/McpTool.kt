package org.autojs.autoxjs.mcp.tool

import com.google.gson.JsonObject
import org.autojs.autoxjs.mcp.McpResponse

/**
 * A single MCP tool handler.
 */
fun interface McpTool {
    suspend fun handle(params: JsonObject?): McpResponse
}
