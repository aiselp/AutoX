package org.autojs.autoxjs.mcp

/**
 * Configuration for the MCP in-app server.
 */
data class McpConfig(
    val enabled: Boolean = false,
    val host: String = "0.0.0.0",
    val port: Int = 27190,
    val token: String? = null,
    val allowBase64: Boolean = false,
    val allowNetwork: Boolean = true
)
