package org.autojs.autoxjs.mcp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.http.HttpStatusCode
import org.autojs.autoxjs.mcp.tool.ToolDefinition
import org.autojs.autoxjs.mcp.tool.ToolRegistry

data class McpServerInfo(
    val name: String,
    val title: String? = null,
    val version: String? = null,
    val description: String? = null
)

data class RpcHttpResponse(
    val status: HttpStatusCode,
    val body: JsonObject? = null
)

class McpJsonRpcHandler(
    private val registry: ToolRegistry,
    private val serverInfoProvider: () -> McpServerInfo,
    private val protocolVersion: String = PROTOCOL_VERSION
) {
    private val gson = Gson()

    suspend fun handleText(body: String): RpcHttpResponse {
        val json = try {
            JsonParser.parseString(body)
        } catch (e: Exception) {
            return RpcHttpResponse(
                HttpStatusCode.BadRequest,
                errorResponse(null, -32700, "Parse error")
            )
        }
        if (!json.isJsonObject) {
            return RpcHttpResponse(
                HttpStatusCode.BadRequest,
                errorResponse(null, -32600, "Invalid Request")
            )
        }
        return handleJson(json.asJsonObject)
    }

    suspend fun handleJson(json: JsonObject): RpcHttpResponse {
        val jsonrpc = json.get("jsonrpc")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        if (jsonrpc != "2.0") {
            return RpcHttpResponse(HttpStatusCode.BadRequest, errorResponse(null, -32600, "Invalid Request"))
        }

        val methodElement = json.get("method")
        val idElement = json.get("id")

        if (methodElement == null) {
            val hasResultOrError = json.has("result") || json.has("error")
            return if (hasResultOrError) {
                RpcHttpResponse(HttpStatusCode.Accepted)
            } else {
                RpcHttpResponse(HttpStatusCode.BadRequest, errorResponse(null, -32600, "Invalid Request"))
            }
        }

        if (!methodElement.isJsonPrimitive || !methodElement.asJsonPrimitive.isString) {
            return RpcHttpResponse(HttpStatusCode.BadRequest, errorResponse(null, -32600, "Invalid Request"))
        }

        val method = methodElement.asString
        val params = json.get("params")

        if (idElement == null || idElement.isJsonNull) {
            handleNotification(method, params)
            return RpcHttpResponse(HttpStatusCode.Accepted)
        }

        val response = handleRequest(method, idElement, params)
        return RpcHttpResponse(HttpStatusCode.OK, response)
    }

    private fun handleNotification(method: String, params: JsonElement?) {
        when (method) {
            "notifications/initialized" -> Unit
            else -> Unit
        }
    }

    private suspend fun handleRequest(method: String, id: JsonElement, params: JsonElement?): JsonObject {
        return when (method) {
            "initialize" -> handleInitialize(id, params)
            "ping" -> successResponse(id, JsonObject())
            "tools/list" -> handleToolsList(id, params)
            "tools/call" -> handleToolsCall(id, params)
            else -> errorResponse(id, -32601, "Method not found")
        }
    }

    private fun handleInitialize(id: JsonElement, params: JsonElement?): JsonObject {
        if (params == null || !params.isJsonObject) {
            return errorResponse(id, -32602, "Invalid params")
        }
        val clientVersion = params.asJsonObject.get("protocolVersion")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        val info = serverInfoProvider()
        val result = JsonObject().apply {
            addProperty("protocolVersion", clientVersion ?: protocolVersion)
            add("capabilities", JsonObject().apply {
                add("tools", JsonObject().apply {
                    addProperty("listChanged", false)
                })
            })
            add("serverInfo", JsonObject().apply {
                addProperty("name", info.name)
                info.title?.let { addProperty("title", it) }
                info.version?.let { addProperty("version", it) }
                info.description?.let { addProperty("description", it) }
            })
        }
        return successResponse(id, result)
    }

    private fun handleToolsList(id: JsonElement, params: JsonElement?): JsonObject {
        if (params != null && !params.isJsonObject) {
            return errorResponse(id, -32602, "Invalid params")
        }
        val toolsJson = JsonArray()
        registry.definitions().forEach { toolsJson.add(toolToJson(it)) }
        val result = JsonObject().apply {
            add("tools", toolsJson)
        }
        return successResponse(id, result)
    }

    private suspend fun handleToolsCall(id: JsonElement, params: JsonElement?): JsonObject {
        if (params == null || !params.isJsonObject) {
            return errorResponse(id, -32602, "Invalid params")
        }
        val paramsObj = params.asJsonObject
        val name = paramsObj.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: return errorResponse(id, -32602, "Invalid params")
        val args = when {
            paramsObj.get("arguments") == null -> JsonObject()
            paramsObj.get("arguments").isJsonObject -> paramsObj.get("arguments").asJsonObject
            else -> return errorResponse(id, -32602, "Invalid params")
        }

        Log.i(TAG, "tools/call id=$id name=$name argsKeys=${args.keySet().joinToString(",")}")
        val response = registry.invoke(name, args)
        Log.i(TAG, "tools/call id=$id name=$name ok=${response.ok} error=${response.errorCode}")
        val content = JsonArray()
        val text = if (response.ok) {
            response.data?.let { data ->
                if (data is String) data else gson.toJson(data)
            } ?: "ok"
        } else {
            response.errorMessage ?: response.errorCode ?: "error"
        }
        content.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", text)
        })

        val result = JsonObject().apply {
            add("content", content)
            addProperty("isError", !response.ok)
        }
        return successResponse(id, result)
    }

    private fun toolToJson(definition: ToolDefinition): JsonObject {
        return JsonObject().apply {
            addProperty("name", definition.name)
            definition.title?.let { addProperty("title", it) }
            addProperty("description", definition.description)
            add("inputSchema", definition.inputSchema)
            definition.outputSchema?.let { add("outputSchema", it) }
        }
    }

    private fun successResponse(id: JsonElement, result: JsonObject): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
            add("result", result)
        }
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
        }
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-11-25"
        private const val TAG = "McpToolCall"
    }
}
