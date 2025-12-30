package org.autojs.autoxjs.mcp.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject

object ToolSchemas {
    fun emptyObject(): JsonObject {
        return JsonObject().apply {
            addProperty("type", "object")
            addProperty("additionalProperties", false)
        }
    }

    fun objectSchema(
        properties: Map<String, JsonObject>,
        required: List<String> = emptyList(),
        additionalProperties: Boolean = false
    ): JsonObject {
        val schema = JsonObject()
        schema.addProperty("type", "object")
        val props = JsonObject()
        for ((key, value) in properties) {
            props.add(key, value)
        }
        schema.add("properties", props)
        if (required.isNotEmpty()) {
            val req = JsonArray()
            required.forEach { req.add(it) }
            schema.add("required", req)
        }
        schema.addProperty("additionalProperties", additionalProperties)
        return schema
    }

    fun stringSchema(description: String? = null): JsonObject {
        return JsonObject().apply {
            addProperty("type", "string")
            if (!description.isNullOrBlank()) {
                addProperty("description", description)
            }
        }
    }

    fun intSchema(description: String? = null): JsonObject {
        return JsonObject().apply {
            addProperty("type", "integer")
            if (!description.isNullOrBlank()) {
                addProperty("description", description)
            }
        }
    }

    fun booleanSchema(description: String? = null): JsonObject {
        return JsonObject().apply {
            addProperty("type", "boolean")
            if (!description.isNullOrBlank()) {
                addProperty("description", description)
            }
        }
    }
}
