package org.autojs.autoxjs.mcp

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.autojs.autoxjs.mcp.tool.ToolRegistry
import java.net.NetworkInterface

/**
 * Lightweight MCP server wrapper based on Ktor.
 */
class McpServer(
    private val appContext: Context,
    private val registry: ToolRegistry
) {
    private val gson = Gson()
    private var engine: ApplicationEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonRpcHandler = McpJsonRpcHandler(
        registry,
        serverInfoProvider = { buildServerInfo() }
    )

    val isRunning: Boolean
        get() = engine?.application?.environment?.monitor != null

    fun start(config: McpConfig) {
        stop()
        if (!config.enabled) return
        engine = embeddedServer(Netty, port = config.port, host = config.host) {
            install(WebSockets)
            routing {
                post("/mcp") {
                    if (!isOriginAllowed(config, call.request.headers["Origin"])) {
                        call.respond(
                            HttpStatusCode.Forbidden
                        )
                        return@post
                    }

                    if (!authorize(config, call.request.headers["X-Token"])) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }

                    val body = call.receiveText()
                    val response = jsonRpcHandler.handleText(body)
                    if (response.body == null) {
                        call.respond(response.status)
                    } else {
                        call.respondText(
                            gson.toJson(response.body),
                            ContentType.Application.Json,
                            response.status
                        )
                    }
                }

                get("/mcp") {
                    call.respond(HttpStatusCode.MethodNotAllowed)
                }

            }
        }.also { engine -> engine.start(wait = false) }
        Log.i(TAG, "MCP server started on ${config.host}:${config.port}")
    }

    fun stop() {
        try {
            engine?.stop(1000, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        } finally {
            engine = null
        }
    }

    private fun authorize(config: McpConfig, tokenHeader: String?): Boolean {
        val expected = config.token
        return expected.isNullOrBlank() || expected == tokenHeader
    }

    private fun isOriginAllowed(config: McpConfig, originHeader: String?): Boolean {
        if (originHeader.isNullOrBlank()) {
            return true
        }
        val host = try {
            Uri.parse(originHeader).host
        } catch (_: Exception) {
            null
        } ?: return false

        val allowedHosts = if (config.allowNetwork) {
            localHosts()
        } else {
            setOf("localhost", "127.0.0.1", "::1")
        }
        return allowedHosts.contains(host)
    }

    private fun localHosts(): Set<String> {
        val hosts = mutableSetOf("localhost", "127.0.0.1", "::1")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val host = address.hostAddress?.substringBefore('%')
                    if (!host.isNullOrBlank()) {
                        hosts.add(host)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve local addresses", e)
        }
        return hosts
    }

    private fun buildServerInfo(): McpServerInfo {
        val name = "AutoX MCP"
        val versionName = try {
            val info: PackageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            info.versionName
        } catch (_: Exception) {
            null
        }
        return McpServerInfo(
            name = name,
            title = name,
            version = versionName,
            description = "AutoX embedded MCP tools server"
        )
    }

    companion object {
        private const val TAG = "McpServer"
    }
}
