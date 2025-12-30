package org.autojs.autoxjs.mcp.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import androidx.preference.PreferenceManager
import org.autojs.autoxjs.mcp.McpResponse
import org.autojs.autoxjs.mcp.tool.McpTool
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.stardust.autojs.servicecomponents.BinderScriptListener
import com.stardust.autojs.servicecomponents.EngineController
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

private val gson = Gson()

data class RunScriptRequest(
    val script: String,
    val name: String? = null,
    val mode: String? = null,
    val timeoutMillis: Long? = null
)

data class ListAppsRequest(
    val query: String? = null,
    val includeSystem: Boolean? = false,
    val limit: Int? = null
)

data class SaveScriptRequest(
    val script: String,
    val name: String? = null,
    val overwrite: Boolean? = false
)

data class RunScriptFileRequest(
    val path: String,
    val name: String? = null
)

data class ListScriptsRequest(
    val query: String? = null,
    val limit: Int? = null,
    val recursive: Boolean? = false
)

data class ListSamplesRequest(
    val query: String? = null,
    val limit: Int? = null
)

data class ReadSampleRequest(
    val path: String
)

data class ReadScriptRequest(
    val path: String
)

data class UpdateScriptRequest(
    val path: String,
    val script: String
)

data class DeleteScriptRequest(
    val path: String
)

data class RenameScriptRequest(
    val path: String,
    val newName: String
)

data class ListScriptDirsRequest(
    val query: String? = null,
    val limit: Int? = null,
    val recursive: Boolean? = false
)

class DeviceInfoTool : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val locale = Locale.getDefault()
        val data = mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "sdkInt" to Build.VERSION.SDK_INT,
            "device" to Build.DEVICE,
            "locale" to locale.toLanguageTag()
        )
        return McpResponse.ok(data)
    }
}

class RunScriptTool(
    private val appContext: Context,
    private val tracker: JobTracker
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, RunScriptRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        if (request.script.isBlank()) {
            return McpResponse.error("BadRequest", "script is required")
        }
        val job = tracker.newJob(request.name ?: "script")
        val tmp = File(appContext.cacheDir, "mcp-${job.jobId}-${request.name ?: "script"}.js")
        tmp.writeText(request.script)
        val listener = object : BinderScriptListener {
            override fun onStart(taskInfo: com.stardust.autojs.servicecomponents.TaskInfo) {
                tracker.update(job.jobId, JobStatus.Status.RUNNING, "started")
            }

            override fun onSuccess(taskInfo: com.stardust.autojs.servicecomponents.TaskInfo) {
                tracker.update(job.jobId, JobStatus.Status.SUCCESS, "completed")
                tmp.delete()
            }

            override fun onException(taskInfo: com.stardust.autojs.servicecomponents.TaskInfo, e: Throwable) {
                tracker.update(job.jobId, JobStatus.Status.FAILED, e.message)
                tmp.delete()
            }
        }
        EngineController.runScript(tmp, listener, null)
        return McpResponse.ok(mapOf("jobId" to job.jobId))
    }
}

class JobStatusTool(
    private val tracker: JobTracker
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val jobId = params?.get("jobId")?.asInt
            ?: return McpResponse.error("BadRequest", "jobId is required")
        val status = tracker.get(jobId) ?: return McpResponse.error("NotFound", "job not found")
        return McpResponse.ok(status)
    }
}

class CancelJobTool(
    private val tracker: JobTracker
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val jobId = params?.get("jobId")?.asInt
            ?: return McpResponse.error("BadRequest", "jobId is required")
        tracker.update(jobId, JobStatus.Status.CANCELED, "requested cancel")
        // Fallback: stop all scripts if needed (coarse-grained).
        EngineController.stopAllScript()
        return McpResponse.ok(mapOf("jobId" to jobId, "status" to "CANCELED"))
    }
}

class LogsTool(
    private val tracker: JobTracker
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val recent = params?.get("recent")?.asInt ?: 20
        return McpResponse.ok(tracker.recent(recent))
    }
}

class ListAppsTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            if (params == null) ListAppsRequest() else gson.fromJson(params, ListAppsRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val query = request.query?.trim()?.lowercase(Locale.getDefault())
        val includeSystem = request.includeSystem == true
        val limit = request.limit?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val pm = appContext.packageManager
        val apps = pm.getInstalledApplications(0)
        val results = ArrayList<Map<String, Any>>(apps.size.coerceAtMost(limit))
        for (app in apps) {
            if (!includeSystem && (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue
            }
            val label = app.loadLabel(pm)?.toString() ?: app.packageName
            if (!query.isNullOrEmpty()) {
                val match = label.lowercase(Locale.getDefault()).contains(query) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(query)
                if (!match) {
                    continue
                }
            }
            results.add(
                mapOf(
                    "packageName" to app.packageName,
                    "label" to label,
                    "enabled" to app.enabled,
                    "system" to ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                )
            )
            if (results.size >= limit) {
                break
            }
        }
        return McpResponse.ok(mapOf("count" to results.size, "apps" to results))
    }
}

class SaveScriptTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, SaveScriptRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        if (request.script.isBlank()) {
            return McpResponse.error("BadRequest", "script is required")
        }
        val scriptDir = File(getScriptDirPath(appContext))
        if (!scriptDir.exists() && !scriptDir.mkdirs()) {
            return McpResponse.error("Failed", "failed to create script dir")
        }
        var name = request.name?.trim().orEmpty()
        if (name.isBlank()) {
            name = "mcp-script-${System.currentTimeMillis()}.js"
        }
        name = name.replace(Regex("[\\\\/]+"), "_")
        if (!name.endsWith(".js")) {
            name += ".js"
        }
        val target = File(scriptDir, name)
        if (target.exists() && request.overwrite != true) {
            return McpResponse.error("AlreadyExists", "script already exists")
        }
        target.writeText(request.script)
        return McpResponse.ok(
            mapOf(
                "path" to target.absolutePath,
                "name" to target.name,
                "size" to target.length()
            )
        )
    }
}

class RunScriptFileTool(
    private val appContext: Context,
    private val tracker: JobTracker
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, RunScriptFileRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        if (request.path.isBlank()) {
            return McpResponse.error("BadRequest", "path is required")
        }
        val file = resolveScriptFile(appContext, request.path)
        if (!file.exists()) {
            return McpResponse.error("NotFound", "script file not found")
        }
        val job = tracker.newJob(request.name ?: file.nameWithoutExtension)
        val listener = object : BinderScriptListener {
            override fun onStart(taskInfo: com.stardust.autojs.servicecomponents.TaskInfo) {
                tracker.update(job.jobId, JobStatus.Status.RUNNING, "started")
            }

            override fun onSuccess(taskInfo: com.stardust.autojs.servicecomponents.TaskInfo) {
                tracker.update(job.jobId, JobStatus.Status.SUCCESS, "completed")
            }

            override fun onException(taskInfo: com.stardust.autojs.servicecomponents.TaskInfo, e: Throwable) {
                tracker.update(job.jobId, JobStatus.Status.FAILED, e.message)
            }
        }
        EngineController.runScript(file, listener, null)
        return McpResponse.ok(mapOf("jobId" to job.jobId, "path" to file.absolutePath))
    }
}

class ListScriptsTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            if (params == null) ListScriptsRequest() else gson.fromJson(params, ListScriptsRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val root = File(getScriptDirPath(appContext))
        if (!root.exists()) {
            return McpResponse.ok(mapOf("count" to 0, "scripts" to emptyList<Map<String, Any>>()))
        }
        val query = request.query?.trim()?.lowercase(Locale.getDefault())
        val limit = request.limit?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val results = ArrayList<Map<String, Any>>(minOf(64, limit))

        val matcher: (String) -> Boolean = { value ->
            query.isNullOrEmpty() || value.lowercase(Locale.getDefault()).contains(query)
        }

        fun addFile(file: File) {
            val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            if (!matcher(relative) && !matcher(file.name)) {
                return
            }
            results.add(
                mapOf(
                    "path" to relative,
                    "name" to file.name,
                    "size" to file.length(),
                    "modified" to file.lastModified()
                )
            )
        }

        if (request.recursive == true) {
            val stack = ArrayDeque<File>()
            stack.add(root)
            while (stack.isNotEmpty() && results.size < limit) {
                val current = stack.removeFirst()
                val children = current.listFiles().orEmpty()
                for (child in children) {
                    if (child.isDirectory) {
                        stack.add(child)
                    } else if (isScriptFile(child)) {
                        addFile(child)
                        if (results.size >= limit) {
                            break
                        }
                    }
                }
            }
        } else {
            val children = root.listFiles().orEmpty()
            for (child in children) {
                if (child.isFile && isScriptFile(child)) {
                    addFile(child)
                    if (results.size >= limit) {
                        break
                    }
                }
            }
        }

        results.sortBy { it["path"] as String }
        return McpResponse.ok(mapOf("count" to results.size, "scripts" to results))
    }
}

class ListSamplesTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            if (params == null) ListSamplesRequest() else gson.fromJson(params, ListSamplesRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val assets = appContext.assets
        val root = "sample"
        val query = request.query?.trim()?.lowercase(Locale.getDefault())
        val limit = request.limit?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val results = ArrayList<Map<String, Any?>>(minOf(64, limit))

        val matcher: (String) -> Boolean = { value ->
            query.isNullOrEmpty() || value.lowercase(Locale.getDefault()).contains(query)
        }

        val stack = ArrayDeque<String>()
        stack.add(root)
        while (stack.isNotEmpty() && results.size < limit) {
            val current = stack.removeFirst()
            val children = assets.list(current).orEmpty()
            for (child in children) {
                val childPath = if (current.isBlank()) child else "$current/$child"
                val nested = assets.list(childPath).orEmpty()
                if (nested.isNotEmpty()) {
                    stack.add(childPath)
                    continue
                }
                if (!isSampleTextFile(childPath)) {
                    continue
                }
                val relative = childPath.removePrefix("$root/").replace('\\', '/')
                if (!matcher(relative) && !matcher(child)) {
                    continue
                }
                results.add(
                    mapOf(
                        "path" to relative,
                        "name" to child,
                        "size" to getAssetSize(assets, childPath)
                    )
                )
                if (results.size >= limit) {
                    break
                }
            }
        }
        results.sortBy { it["path"] as String }
        return McpResponse.ok(mapOf("count" to results.size, "samples" to results))
    }
}

class ReadSampleTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, ReadSampleRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val assetPath = resolveSamplePath(request.path)
            ?: return McpResponse.error("BadRequest", "path is invalid")
        if (!isSampleTextFile(assetPath)) {
            return McpResponse.error("BadRequest", "not a text sample")
        }
        val assets = appContext.assets
        val content = try {
            assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return McpResponse.error("NotFound", "sample not found")
        }
        val name = assetPath.substringAfterLast('/')
        return McpResponse.ok(
            mapOf(
                "path" to assetPath.removePrefix("sample/"),
                "name" to name,
                "size" to getAssetSize(assets, assetPath),
                "content" to content
            )
        )
    }
}

class ReadScriptTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, ReadScriptRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val file = resolveScriptFileInDir(appContext, request.path)
            ?: return McpResponse.error("BadRequest", "path is invalid")
        if (!file.exists() || !file.isFile) {
            return McpResponse.error("NotFound", "script file not found")
        }
        if (!isScriptFile(file)) {
            return McpResponse.error("BadRequest", "not a script file")
        }
        val content = try {
            file.readText()
        } catch (e: Exception) {
            return McpResponse.error("Failed", "read failed: ${e.message}")
        }
        return McpResponse.ok(
            mapOf(
                "path" to file.absolutePath,
                "name" to file.name,
                "size" to file.length(),
                "script" to content
            )
        )
    }
}

class UpdateScriptTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, UpdateScriptRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        if (request.script.isBlank()) {
            return McpResponse.error("BadRequest", "script is required")
        }
        val file = resolveScriptFileInDir(appContext, request.path)
            ?: return McpResponse.error("BadRequest", "path is invalid")
        if (!file.exists() || !file.isFile) {
            return McpResponse.error("NotFound", "script file not found")
        }
        if (!isScriptFile(file)) {
            return McpResponse.error("BadRequest", "not a script file")
        }
        try {
            file.writeText(request.script)
        } catch (e: Exception) {
            return McpResponse.error("Failed", "write failed: ${e.message}")
        }
        return McpResponse.ok(
            mapOf(
                "path" to file.absolutePath,
                "name" to file.name,
                "size" to file.length(),
                "modified" to file.lastModified()
            )
        )
    }
}

class DeleteScriptTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, DeleteScriptRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val file = resolveScriptFileInDir(appContext, request.path)
            ?: return McpResponse.error("BadRequest", "path is invalid")
        if (!file.exists() || !file.isFile) {
            return McpResponse.error("NotFound", "script file not found")
        }
        if (!isScriptFile(file)) {
            return McpResponse.error("BadRequest", "not a script file")
        }
        if (!file.delete()) {
            return McpResponse.error("Failed", "delete failed")
        }
        return McpResponse.ok(mapOf("path" to file.absolutePath, "deleted" to true))
    }
}

class RenameScriptTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            gson.fromJson(params, RenameScriptRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val file = resolveScriptFileInDir(appContext, request.path)
            ?: return McpResponse.error("BadRequest", "path is invalid")
        if (!file.exists() || !file.isFile) {
            return McpResponse.error("NotFound", "script file not found")
        }
        if (!isScriptFile(file)) {
            return McpResponse.error("BadRequest", "not a script file")
        }
        val newName = normalizeScriptName(file, request.newName)
            ?: return McpResponse.error("BadRequest", "newName is invalid")
        val target = File(file.parentFile, newName)
        if (target.exists()) {
            return McpResponse.error("AlreadyExists", "target already exists")
        }
        if (!file.renameTo(target)) {
            return McpResponse.error("Failed", "rename failed")
        }
        return McpResponse.ok(
            mapOf(
                "path" to target.absolutePath,
                "name" to target.name
            )
        )
    }
}

class ListScriptDirsTool(
    private val appContext: Context
) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val request = try {
            if (params == null) ListScriptDirsRequest() else gson.fromJson(params, ListScriptDirsRequest::class.java)
        } catch (e: Exception) {
            return McpResponse.error("BadRequest", "Invalid request: ${e.message}")
        }
        val root = File(getScriptDirPath(appContext))
        if (!root.exists()) {
            return McpResponse.ok(mapOf("root" to root.absolutePath, "count" to 0, "dirs" to emptyList<Map<String, Any>>()))
        }
        val query = request.query?.trim()?.lowercase(Locale.getDefault())
        val limit = request.limit?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val results = ArrayList<Map<String, Any>>(minOf(64, limit))

        val matcher: (String) -> Boolean = { value ->
            query.isNullOrEmpty() || value.lowercase(Locale.getDefault()).contains(query)
        }

        fun addDir(dir: File) {
            val relative = root.toPath().relativize(dir.toPath()).toString().replace('\\', '/')
            if (relative.isEmpty()) {
                return
            }
            if (!matcher(relative) && !matcher(dir.name)) {
                return
            }
            results.add(
                mapOf(
                    "path" to relative,
                    "name" to dir.name,
                    "modified" to dir.lastModified()
                )
            )
        }

        if (request.recursive == true) {
            val stack = ArrayDeque<File>()
            stack.add(root)
            while (stack.isNotEmpty() && results.size < limit) {
                val current = stack.removeFirst()
                val children = current.listFiles().orEmpty()
                for (child in children) {
                    if (child.isDirectory) {
                        if (child != root) {
                            addDir(child)
                            if (results.size >= limit) {
                                break
                            }
                        }
                        stack.add(child)
                    }
                }
            }
        } else {
            val children = root.listFiles().orEmpty()
            for (child in children) {
                if (child.isDirectory) {
                    addDir(child)
                    if (results.size >= limit) {
                        break
                    }
                }
            }
        }

        results.sortBy { it["path"] as String }
        return McpResponse.ok(mapOf("root" to root.absolutePath, "count" to results.size, "dirs" to results))
    }
}

class NotImplementedTool(private val name: String) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        return McpResponse.error("NotImplemented", "$name not implemented yet")
    }
}

private fun getScriptDirPath(context: Context): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val configured = prefs.getString("key_script_dir_path", null)
    val fallback = getStringResource(context, "default_value_script_dir_path") ?: "/scripts/"
    val dir = configured?.takeIf { it.isNotBlank() } ?: fallback
    return File(Environment.getExternalStorageDirectory(), dir).path
}

private fun getStringResource(context: Context, name: String): String? {
    val resId = context.resources.getIdentifier(name, "string", context.packageName)
    return if (resId != 0) context.getString(resId) else null
}

private fun resolveSamplePath(path: String): String? {
    val normalized = path.trim().replace('\\', '/')
    if (normalized.isBlank() || normalized.startsWith("/")) {
        return null
    }
    if (normalized.split('/').any { it == ".." }) {
        return null
    }
    return if (normalized.startsWith("sample/")) normalized else "sample/$normalized"
}

private fun resolveScriptFile(context: Context, path: String): File {
    val input = path.trim()
    val file = File(input)
    return if (file.isAbsolute) {
        file
    } else {
        File(getScriptDirPath(context), input)
    }
}

private fun resolveScriptFileInDir(context: Context, path: String): File? {
    val input = path.trim()
    if (input.isBlank()) {
        return null
    }
    val root = try {
        File(getScriptDirPath(context)).canonicalFile
    } catch (_: Exception) {
        return null
    }
    val candidate = File(input)
    val resolved = if (candidate.isAbsolute) candidate else File(root, input)
    val canonical = try {
        resolved.canonicalFile
    } catch (_: Exception) {
        return null
    }
    val rootPath = root.path.trimEnd(File.separatorChar) + File.separatorChar
    return if (canonical.path.startsWith(rootPath)) canonical else null
}

private fun normalizeScriptName(original: File, newName: String): String? {
    val trimmed = newName.trim()
    if (trimmed.isBlank() || trimmed.contains('/') || trimmed.contains('\\')) {
        return null
    }
    var name = trimmed
    if (!isScriptName(name)) {
        val ext = original.extension
        name = if (ext.isNotBlank()) "$name.$ext" else "$name.js"
    }
    return if (isScriptName(name)) name else null
}

private fun isScriptName(name: String): Boolean {
    val lower = name.lowercase(Locale.getDefault())
    return lower.endsWith(".js") || lower.endsWith(".auto") || lower.endsWith(".mjs") ||
        lower.endsWith(".cjs") || lower.endsWith("node.js")
}

private fun isScriptFile(file: File): Boolean {
    return isScriptName(file.name)
}

private fun isSampleTextFile(path: String): Boolean {
    val lower = path.lowercase(Locale.getDefault())
    return lower.endsWith(".js") || lower.endsWith(".auto") || lower.endsWith(".mjs") ||
        lower.endsWith(".cjs") || lower.endsWith(".json") || lower.endsWith(".md") ||
        lower.endsWith(".txt") || lower.endsWith(".xml") || lower.endsWith(".yml") ||
        lower.endsWith(".yaml") || lower.endsWith(".py")
}

private fun getAssetSize(assets: android.content.res.AssetManager, path: String): Long? {
    return try {
        assets.openFd(path).use { it.length }
    } catch (_: Exception) {
        try {
            assets.open(path).use { it.available().toLong() }
        } catch (_: Exception) {
            null
        }
    }
}
