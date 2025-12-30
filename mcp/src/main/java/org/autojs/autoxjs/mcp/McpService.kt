package org.autojs.autoxjs.mcp

import android.content.Context
import android.util.Log
import org.autojs.autoxjs.mcp.tool.ToolDefinition
import org.autojs.autoxjs.mcp.tool.ToolRegistry
import org.autojs.autoxjs.mcp.tool.ToolSchemas
import org.autojs.autoxjs.mcp.tools.AppControlTool
import org.autojs.autoxjs.mcp.tools.CancelJobTool
import org.autojs.autoxjs.mcp.tools.DeleteScriptTool
import org.autojs.autoxjs.mcp.tools.DeviceInfoTool
import org.autojs.autoxjs.mcp.tools.FindElementTool
import org.autojs.autoxjs.mcp.tools.FindElementsTool
import org.autojs.autoxjs.mcp.tools.GetCurrentActivityTool
import org.autojs.autoxjs.mcp.tools.GetForegroundAppTool
import org.autojs.autoxjs.mcp.tools.GetRecentScreenshotTool
import org.autojs.autoxjs.mcp.tools.GetUiTreeTool
import org.autojs.autoxjs.mcp.tools.JobStatusTool
import org.autojs.autoxjs.mcp.tools.JobTracker
import org.autojs.autoxjs.mcp.tools.ListAppsTool
import org.autojs.autoxjs.mcp.tools.ListSamplesTool
import org.autojs.autoxjs.mcp.tools.ListScriptDirsTool
import org.autojs.autoxjs.mcp.tools.ListScriptsTool
import org.autojs.autoxjs.mcp.tools.LogsTool
import org.autojs.autoxjs.mcp.tools.McpToolContext
import org.autojs.autoxjs.mcp.tools.OcrTool
import org.autojs.autoxjs.mcp.tools.ReadSampleTool
import org.autojs.autoxjs.mcp.tools.ReadScriptTool
import org.autojs.autoxjs.mcp.tools.RenameScriptTool
import org.autojs.autoxjs.mcp.tools.RunScriptTool
import org.autojs.autoxjs.mcp.tools.RunScriptFileTool
import org.autojs.autoxjs.mcp.tools.SaveScriptTool
import org.autojs.autoxjs.mcp.tools.ScreenshotTool
import org.autojs.autoxjs.mcp.tools.SwipeTool
import org.autojs.autoxjs.mcp.tools.TapTool
import org.autojs.autoxjs.mcp.tools.UpdateScriptTool

/**
 * Facade to start/stop MCP server with default tools registered.
 */
class McpService(private val context: Context) {
    private val tracker = JobTracker()
    private val registry = ToolRegistry()
    private val runtimeProvider = McpRuntimeProvider()
    private val screenshotStore = ScreenshotStore()
    @Volatile
    private var currentConfig: McpConfig = McpConfig()
    private val toolContext = McpToolContext(
        appContext = context.applicationContext,
        runtimeProvider = runtimeProvider,
        screenshotStore = screenshotStore
    ) { currentConfig }
    private var server: McpServer? = null

    init {
        registerDefaultTools()
    }

    fun start(config: McpConfig) {
        currentConfig = config
        if (!config.enabled) {
            stop()
            return
        }
        if (server == null) {
            server = McpServer(context.applicationContext, registry)
        }
        server?.start(config)
        Log.i(TAG, "MCP service started")
    }

    fun stop() {
        server?.stop()
        runtimeProvider.close()
        Log.i(TAG, "MCP service stopped")
    }

    private fun registerDefaultTools() {
        registry.apply {
            register(
                ToolDefinition(
                    name = "device_info",
                    title = "Device Info",
                    description = "Return device information and locale.",
                    inputSchema = ToolSchemas.emptyObject()
                ),
                DeviceInfoTool()
            )
            register(
                ToolDefinition(
                    name = "run_script",
                    title = "Run Script",
                    description = "Run an AutoJs script and return a job id.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "script" to ToolSchemas.stringSchema("JavaScript source to execute."),
                            "name" to ToolSchemas.stringSchema("Optional job name."),
                            "mode" to ToolSchemas.stringSchema("Optional runtime mode."),
                            "timeoutMillis" to ToolSchemas.intSchema("Optional timeout in milliseconds.")
                        ),
                        required = listOf("script")
                    )
                ),
                RunScriptTool(context, tracker)
            )
            register(
                ToolDefinition(
                    name = "job_status",
                    title = "Job Status",
                    description = "Get status for a script job.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf("jobId" to ToolSchemas.intSchema("Job id.")),
                        required = listOf("jobId")
                    )
                ),
                JobStatusTool(tracker)
            )
            register(
                ToolDefinition(
                    name = "cancel_job",
                    title = "Cancel Job",
                    description = "Cancel a running job.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf("jobId" to ToolSchemas.intSchema("Job id.")),
                        required = listOf("jobId")
                    )
                ),
                CancelJobTool(tracker)
            )
            register(
                ToolDefinition(
                    name = "logs",
                    title = "Logs",
                    description = "Get recent job logs.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf("recent" to ToolSchemas.intSchema("Max number of log entries.")),
                        required = emptyList()
                    )
                ),
                LogsTool(tracker)
            )
            register(
                ToolDefinition(
                    name = "list_apps",
                    title = "List Apps",
                    description = "List installed apps with optional filtering.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "query" to ToolSchemas.stringSchema("Filter by package name or label."),
                            "includeSystem" to ToolSchemas.booleanSchema("Include system apps."),
                            "limit" to ToolSchemas.intSchema("Max number of results.")
                        )
                    )
                ),
                ListAppsTool(context)
            )
            register(
                ToolDefinition(
                    name = "save_script",
                    title = "Save Script",
                    description = "Save a script file into AutoJs default scripts directory.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "script" to ToolSchemas.stringSchema("JavaScript source to save."),
                            "name" to ToolSchemas.stringSchema("Optional script file name."),
                            "overwrite" to ToolSchemas.booleanSchema("Overwrite if file exists.")
                        ),
                        required = listOf("script")
                    )
                ),
                SaveScriptTool(context)
            )
            register(
                ToolDefinition(
                    name = "list_samples",
                    title = "List Samples",
                    description = "List built-in sample scripts shipped with AutoJs.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "query" to ToolSchemas.stringSchema("Filter by sample path or name."),
                            "limit" to ToolSchemas.intSchema("Max number of results.")
                        )
                    )
                ),
                ListSamplesTool(context)
            )
            register(
                ToolDefinition(
                    name = "read_sample",
                    title = "Read Sample",
                    description = "Read a built-in sample script by path.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "path" to ToolSchemas.stringSchema("Sample path under assets/sample.")
                        ),
                        required = listOf("path")
                    )
                ),
                ReadSampleTool(context)
            )
            register(
                ToolDefinition(
                    name = "read_script",
                    title = "Read Script",
                    description = "Read a script file from AutoJs scripts directory.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "path" to ToolSchemas.stringSchema("Script file path relative to scripts dir.")
                        ),
                        required = listOf("path")
                    )
                ),
                ReadScriptTool(context)
            )
            register(
                ToolDefinition(
                    name = "update_script",
                    title = "Update Script",
                    description = "Overwrite an existing script file in AutoJs scripts directory.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "path" to ToolSchemas.stringSchema("Script file path relative to scripts dir."),
                            "script" to ToolSchemas.stringSchema("JavaScript source to save.")
                        ),
                        required = listOf("path", "script")
                    )
                ),
                UpdateScriptTool(context)
            )
            register(
                ToolDefinition(
                    name = "delete_script",
                    title = "Delete Script",
                    description = "Delete a script file from AutoJs scripts directory.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "path" to ToolSchemas.stringSchema("Script file path relative to scripts dir.")
                        ),
                        required = listOf("path")
                    )
                ),
                DeleteScriptTool(context)
            )
            register(
                ToolDefinition(
                    name = "rename_script",
                    title = "Rename Script",
                    description = "Rename a script file in AutoJs scripts directory.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "path" to ToolSchemas.stringSchema("Script file path relative to scripts dir."),
                            "newName" to ToolSchemas.stringSchema("New script file name.")
                        ),
                        required = listOf("path", "newName")
                    )
                ),
                RenameScriptTool(context)
            )
            register(
                ToolDefinition(
                    name = "run_script_file",
                    title = "Run Script File",
                    description = "Run a script file from AutoJs scripts directory or absolute path.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "path" to ToolSchemas.stringSchema("Script file path or name under scripts dir."),
                            "name" to ToolSchemas.stringSchema("Optional job name.")
                        ),
                        required = listOf("path")
                    )
                ),
                RunScriptFileTool(context, tracker)
            )
            register(
                ToolDefinition(
                    name = "list_scripts",
                    title = "List Scripts",
                    description = "List scripts from AutoJs scripts directory.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "query" to ToolSchemas.stringSchema("Filter by file name or path."),
                            "limit" to ToolSchemas.intSchema("Max number of results."),
                            "recursive" to ToolSchemas.booleanSchema("List subdirectories.")
                        )
                    )
                ),
                ListScriptsTool(context)
            )
            register(
                ToolDefinition(
                    name = "list_script_dirs",
                    title = "List Script Dirs",
                    description = "List directories under AutoJs scripts directory.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "query" to ToolSchemas.stringSchema("Filter by directory name or path."),
                            "limit" to ToolSchemas.intSchema("Max number of results."),
                            "recursive" to ToolSchemas.booleanSchema("List subdirectories.")
                        )
                    )
                ),
                ListScriptDirsTool(context)
            )
            register(
                ToolDefinition(
                    name = "tap",
                    title = "Tap",
                    description = "Tap on screen at the given coordinates.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "x" to ToolSchemas.intSchema("X coordinate."),
                            "y" to ToolSchemas.intSchema("Y coordinate.")
                        ),
                        required = listOf("x", "y")
                    )
                ),
                TapTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "swipe",
                    title = "Swipe",
                    description = "Swipe from one coordinate to another.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "x1" to ToolSchemas.intSchema("Start X coordinate."),
                            "y1" to ToolSchemas.intSchema("Start Y coordinate."),
                            "x2" to ToolSchemas.intSchema("End X coordinate."),
                            "y2" to ToolSchemas.intSchema("End Y coordinate."),
                            "duration" to ToolSchemas.intSchema("Duration in milliseconds.")
                        ),
                        required = listOf("x1", "y1", "x2", "y2")
                    )
                ),
                SwipeTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "find_element",
                    title = "Find Element",
                    description = "Find a UI element by text, id, desc, or className.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "text" to ToolSchemas.stringSchema("Match element text."),
                            "id" to ToolSchemas.stringSchema("Match resource id."),
                            "desc" to ToolSchemas.stringSchema("Match content description."),
                            "className" to ToolSchemas.stringSchema("Match class name."),
                            "timeoutMillis" to ToolSchemas.intSchema("Search timeout in milliseconds.")
                        )
                    )
                ),
                FindElementTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "find_elements",
                    title = "Find Elements",
                    description = "Find multiple UI elements by text, id, desc, or className.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "text" to ToolSchemas.stringSchema("Match element text."),
                            "id" to ToolSchemas.stringSchema("Match resource id."),
                            "desc" to ToolSchemas.stringSchema("Match content description."),
                            "className" to ToolSchemas.stringSchema("Match class name."),
                            "timeoutMillis" to ToolSchemas.intSchema("Search timeout in milliseconds."),
                            "limit" to ToolSchemas.intSchema("Max number of results.")
                        )
                    )
                ),
                FindElementsTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "screenshot",
                    title = "Screenshot",
                    description = "Capture a screenshot and store it on device.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf("asBase64" to ToolSchemas.booleanSchema("Include base64 in response."))
                    )
                ),
                ScreenshotTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "ocr",
                    title = "OCR",
                    description = "Run OCR on a screenshot or image file.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "source" to ToolSchemas.stringSchema("Use 'screenshot' for last screenshot."),
                            "path" to ToolSchemas.stringSchema("Absolute file path to image."),
                            "language" to ToolSchemas.stringSchema("OCR language code.")
                        )
                    )
                ),
                OcrTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "app_control",
                    title = "App Control",
                    description = "Launch or force-stop an app.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf(
                            "action" to ToolSchemas.stringSchema("launch | bring_to_front | force_stop"),
                            "packageName" to ToolSchemas.stringSchema("Target package name.")
                        ),
                        required = listOf("action", "packageName")
                    )
                ),
                AppControlTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "get_foreground_app",
                    title = "Get Foreground App",
                    description = "Get the foreground package name.",
                    inputSchema = ToolSchemas.emptyObject()
                ),
                GetForegroundAppTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "get_current_activity",
                    title = "Get Current Activity",
                    description = "Get the current activity class name.",
                    inputSchema = ToolSchemas.emptyObject()
                ),
                GetCurrentActivityTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "get_ui_tree",
                    title = "Get UI Tree",
                    description = "Capture the current UI tree.",
                    inputSchema = ToolSchemas.emptyObject()
                ),
                GetUiTreeTool(toolContext)
            )
            register(
                ToolDefinition(
                    name = "get_recent_screenshot",
                    title = "Get Recent Screenshot",
                    description = "Return metadata (and optional base64) of the most recent screenshot.",
                    inputSchema = ToolSchemas.objectSchema(
                        mapOf("asBase64" to ToolSchemas.booleanSchema("Include base64 in response."))
                    )
                ),
                GetRecentScreenshotTool(toolContext)
            )
        }
    }

    companion object {
        private const val TAG = "McpService"
    }
}
