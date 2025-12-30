package org.autojs.autoxjs.mcp.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.stardust.autojs.core.accessibility.UiSelector
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.autojs.runtime.ScriptRuntimeV2
import com.stardust.autojs.runtime.api.Images
import com.stardust.autojs.AutoJs
import com.stardust.view.accessibility.LayoutInspector
import com.stardust.view.accessibility.NodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.autoxjs.mcp.McpConfig
import org.autojs.autoxjs.mcp.McpResponse
import org.autojs.autoxjs.mcp.McpRuntimeProvider
import org.autojs.autoxjs.mcp.ScreenshotInfo
import org.autojs.autoxjs.mcp.ScreenshotStore
import org.autojs.autoxjs.mcp.tool.McpTool
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

private val gson = Gson()

data class McpToolContext(
    val appContext: Context,
    val runtimeProvider: McpRuntimeProvider,
    val screenshotStore: ScreenshotStore,
    val configProvider: () -> McpConfig
)

data class TapRequest(val x: Int, val y: Int)
data class SwipeRequest(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val duration: Int? = 300)
data class FindElementRequest(
    val text: String? = null,
    val id: String? = null,
    val desc: String? = null,
    val className: String? = null,
    val timeoutMillis: Long? = 2000
)
data class FindElementsRequest(
    val text: String? = null,
    val id: String? = null,
    val desc: String? = null,
    val className: String? = null,
    val timeoutMillis: Long? = 2000,
    val limit: Int? = 20
)
data class ScreenshotRequest(val asBase64: Boolean? = false)
data class GetRecentScreenshotRequest(val asBase64: Boolean? = false)
data class OcrRequest(val source: String? = null, val path: String? = null, val language: String? = "zh")
data class AppControlRequest(val action: String, val packageName: String)

class TapTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, TapRequest::class.java)
            ?: return McpResponse.error("BadRequest", "x/y required")
        val runtime = ctx.runtimeProvider.getRuntime()
        return try {
            val ok = runtime.automator.click(req.x, req.y)
            if (ok) McpResponse.ok(mapOf("ok" to true)) else McpResponse.error("Failed", "tap failed")
        } catch (e: Exception) {
            McpResponse.error("Failed", e.message ?: "tap failed")
        }
    }
}

class SwipeTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, SwipeRequest::class.java)
            ?: return McpResponse.error("BadRequest", "x1/y1/x2/y2 required")
        val runtime = ctx.runtimeProvider.getRuntime()
        val duration = req.duration ?: 300
        return try {
            val ok = runtime.automator.swipe(req.x1, req.y1, req.x2, req.y2, duration)
            if (ok) McpResponse.ok(mapOf("ok" to true)) else McpResponse.error("Failed", "swipe failed")
        } catch (e: Exception) {
            McpResponse.error("Failed", e.message ?: "swipe failed")
        }
    }
}

class FindElementTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, FindElementRequest::class.java) ?: FindElementRequest()
        if (req.text.isNullOrBlank() && req.id.isNullOrBlank() && req.desc.isNullOrBlank() && req.className.isNullOrBlank()) {
            return McpResponse.error("BadRequest", "criteria required")
        }
        val runtime = ctx.runtimeProvider.getRuntime()
        val selector = runtime.selector()
        applySelector(selector, req.text, req.id, req.desc, req.className)
        return try {
            val timeout = req.timeoutMillis ?: 2000
            val obj = selector.findOne(timeout)
            if (obj == null) {
                McpResponse.error("NotFound", "element not found")
            } else {
                val rect = obj.bounds()
                val data = mapOf(
                    "bounds" to mapOf("left" to rect.left, "top" to rect.top, "right" to rect.right, "bottom" to rect.bottom),
                    "center" to mapOf("x" to rect.centerX(), "y" to rect.centerY()),
                    "text" to obj.text(),
                    "id" to obj.id(),
                    "desc" to obj.desc(),
                    "className" to obj.className(),
                    "packageName" to obj.packageName()
                )
                McpResponse.ok(data)
            }
        } catch (e: Exception) {
            McpResponse.error("Failed", e.message ?: "find_element failed")
        }
    }
}

class FindElementsTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, FindElementsRequest::class.java) ?: FindElementsRequest()
        if (req.text.isNullOrBlank() && req.id.isNullOrBlank() && req.desc.isNullOrBlank() && req.className.isNullOrBlank()) {
            return McpResponse.error("BadRequest", "criteria required")
        }
        val runtime = ctx.runtimeProvider.getRuntime()
        val selector = runtime.selector()
        applySelector(selector, req.text, req.id, req.desc, req.className)
        return try {
            val timeout = req.timeoutMillis ?: 2000
            val limit = req.limit?.takeIf { it > 0 } ?: 20
            val collection = findCollectionWithTimeout(selector, timeout)
            if (collection.isEmpty) {
                return McpResponse.ok(mapOf("count" to 0, "elements" to emptyList<Map<String, Any>>()))
            }
            val results = ArrayList<Map<String, Any?>>(minOf(limit, collection.size()))
            for (obj in collection) {
                if (obj == null) {
                    continue
                }
                val rect = obj.bounds()
                results.add(
                    mapOf(
                        "bounds" to mapOf(
                            "left" to rect.left,
                            "top" to rect.top,
                            "right" to rect.right,
                            "bottom" to rect.bottom
                        ),
                        "center" to mapOf("x" to rect.centerX(), "y" to rect.centerY()),
                        "text" to obj.text(),
                        "id" to obj.id(),
                        "desc" to obj.desc(),
                        "className" to obj.className(),
                        "packageName" to obj.packageName()
                    )
                )
                if (results.size >= limit) {
                    break
                }
            }
            McpResponse.ok(mapOf("count" to results.size, "elements" to results))
        } catch (e: Exception) {
            McpResponse.error("Failed", e.message ?: "find_elements failed")
        }
    }
}

class GetForegroundAppTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val runtime = ctx.runtimeProvider.getRuntime()
        val info = runtime.info
        val packageName = info.getLatestPackageByUsageStatsIfGranted()
            .takeIf { it.isNotBlank() }
            ?: info.latestPackage
        if (packageName.isBlank()) {
            return McpResponse.error("NotFound", "foreground app not found")
        }
        val pm = ctx.appContext.packageManager
        val label = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo)?.toString() ?: packageName
        } catch (_: Exception) {
            packageName
        }
        val activity = info.latestActivity.takeIf { it.isNotBlank() }
        return McpResponse.ok(
            mapOf(
                "packageName" to packageName,
                "label" to label,
                "activity" to activity
            )
        )
    }
}

class GetCurrentActivityTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val runtime = ctx.runtimeProvider.getRuntime()
        val info = runtime.info
        val activity = info.latestActivity.takeIf { it.isNotBlank() }
            ?: return McpResponse.error("NotFound", "activity not found")
        val packageName = info.getLatestPackageByUsageStatsIfGranted()
            .takeIf { it.isNotBlank() }
            ?: info.latestPackage
        return McpResponse.ok(
            mapOf(
                "activity" to activity,
                "packageName" to packageName
            )
        )
    }
}

class ScreenshotTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, ScreenshotRequest::class.java) ?: ScreenshotRequest()
        val runtime = ctx.runtimeProvider.getRuntime()
        return try {
            val image = captureImage(runtime)
            val file = saveImage(ctx.appContext, image)
            val info = ScreenshotInfo(
                path = file.absolutePath,
                width = image.width,
                height = image.height,
                timestamp = System.currentTimeMillis()
            )
            ctx.screenshotStore.update(info)
            val data = if (req.asBase64 == true) {
                if (!ctx.configProvider().allowBase64) {
                    return McpResponse.error("Forbidden", "base64 disabled by config")
                }
                mapOf(
                    "path" to info.path,
                    "width" to info.width,
                    "height" to info.height,
                    "timestamp" to info.timestamp,
                    "base64" to imageToBase64(image)
                )
            } else {
                mapOf(
                    "path" to info.path,
                    "width" to info.width,
                    "height" to info.height,
                    "timestamp" to info.timestamp
                )
            }
            image.recycle()
            McpResponse.ok(data)
        } catch (e: Exception) {
            McpResponse.error("Failed", e.message ?: "screenshot failed")
        }
    }
}

class GetRecentScreenshotTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, GetRecentScreenshotRequest::class.java) ?: GetRecentScreenshotRequest()
        var info = ctx.screenshotStore.get()
        var captured: ImageWrapper? = null
        if (info == null) {
            val runtime = ctx.runtimeProvider.getRuntime()
            try {
                val image = captureImage(runtime)
                val file = saveImage(ctx.appContext, image)
                info = ScreenshotInfo(
                    path = file.absolutePath,
                    width = image.width,
                    height = image.height,
                    timestamp = System.currentTimeMillis()
                )
                ctx.screenshotStore.update(info)
                captured = image
            } catch (e: Exception) {
                captured?.recycle()
                return McpResponse.error("Failed", e.message ?: "screenshot failed")
            }
        }
        val data = mutableMapOf<String, Any>(
            "path" to info.path,
            "width" to info.width,
            "height" to info.height,
            "timestamp" to info.timestamp
        )
        if (req.asBase64 == true) {
            if (!ctx.configProvider().allowBase64) {
                captured?.recycle()
                return McpResponse.error("Forbidden", "base64 disabled by config")
            }
            val base64 = if (captured != null) {
                imageToBase64(captured)
            } else {
                val bitmap = BitmapFactory.decodeFile(info.path)
                    ?: return McpResponse.error("Failed", "image decode failed")
                val image = ImageWrapper.ofBitmap(bitmap)
                val encoded = imageToBase64(image)
                image.recycle()
                encoded
            }
            data["base64"] = base64
        }
        captured?.recycle()
        return McpResponse.ok(data)
    }
}

class OcrTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, OcrRequest::class.java) ?: OcrRequest()
        val runtime = ctx.runtimeProvider.getRuntime()
        var image: ImageWrapper? = null
        return try {
            image = when {
                req.path?.isNotBlank() == true -> {
                    val bitmap = BitmapFactory.decodeFile(req.path)
                        ?: return McpResponse.error("Failed", "image decode failed")
                    ImageWrapper.ofBitmap(bitmap)
                }
                req.source?.lowercase() == "screenshot" -> {
                    val captured = captureImage(runtime)
                    val file = saveImage(ctx.appContext, captured)
                    val info = ScreenshotInfo(
                        path = file.absolutePath,
                        width = captured.width,
                        height = captured.height,
                        timestamp = System.currentTimeMillis()
                    )
                    ctx.screenshotStore.update(info)
                    captured
                }
                else -> null
            } ?: return McpResponse.error("BadRequest", "path or source required")

            val language = req.language ?: "zh"
            val result = runtime.gmlkit.ocr(image, language)
            if (result != null) McpResponse.ok(result) else McpResponse.error("Failed", "ocr failed")
        } catch (e: Exception) {
            McpResponse.error("Failed", e.message ?: "ocr failed")
        } finally {
            image?.recycle()
        }
    }
}

class AppControlTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        val req = parse(params, AppControlRequest::class.java)
            ?: return McpResponse.error("BadRequest", "action/packageName required")
        val runtime = ctx.runtimeProvider.getRuntime()
        return when (req.action.lowercase()) {
            "launch", "bring_to_front" -> {
                val ok = runtime.app.launchPackage(req.packageName)
                if (ok) McpResponse.ok(mapOf("ok" to true)) else McpResponse.error("Failed", "launch failed")
            }
            "force_stop" -> {
                val result = runtime.shell.exec("am force-stop ${req.packageName}", false)
                if (result.code == 0) McpResponse.ok(mapOf("ok" to true)) else McpResponse.error(
                    "Failed",
                    "force_stop failed: ${result.error}"
                )
            }
            else -> McpResponse.error("BadRequest", "unsupported action")
        }
    }
}

class GetUiTreeTool(private val ctx: McpToolContext) : McpTool {
    override suspend fun handle(params: JsonObject?): McpResponse {
        return try {
            val inspector = AutoJs.instance.layoutInspector
            val capture = captureLayout(inspector)
            val data = capture?.let { root ->
                val nodes = buildCompactNodes(root, null, true)
                nodes.firstOrNull()
            } ?: return McpResponse.error("Failed", "capture failed")
            McpResponse.ok(data)
        } catch (e: Exception) {
            McpResponse.error("Failed", e.message ?: "capture failed")
        }
    }
}

private suspend fun captureLayout(inspector: LayoutInspector): NodeInfo? {
    val deferred = CompletableDeferred<NodeInfo?>()
    val listener = object : LayoutInspector.CaptureAvailableListener {
        override fun onCaptureAvailable(capture: NodeInfo?) {
            inspector.removeCaptureAvailableListener(this)
            deferred.complete(capture)
        }
    }
    inspector.addCaptureAvailableListener(listener)
    if (!inspector.captureCurrentWindow()) {
        inspector.removeCaptureAvailableListener(listener)
        return null
    }
    return withTimeoutOrNull(TimeUnit.SECONDS.toMillis(3)) { deferred.await() }
}

private fun buildCompactNodes(node: NodeInfo, parentPackage: String?, isRoot: Boolean): List<Map<String, Any?>> {
    val bounds = node.boundsInScreen
    val data = LinkedHashMap<String, Any?>()

    val hasSignal = hasNodeSignal(node)

    val className = node.className?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
        ?: node.className?.toString()
    if (!className.isNullOrBlank()) {
        data["c"] = className
    }

    if (hasSignal || isRoot) {
        node.id?.takeIf { it.isNotBlank() }?.let { data["id"] = it }
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { data["t"] = it }
        node.desc?.toString()?.takeIf { it.isNotBlank() }?.let { data["d"] = it }
    }

    val pkg = node.packageName?.toString()?.takeIf { it.isNotBlank() }
    if (pkg != null && pkg != parentPackage) {
        data["p"] = pkg
    }

    data["b"] = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)

    val flags = StringBuilder()
    if (node.clickable) flags.append('c')
    if (node.focusable) flags.append('f')
    if (node.scrollable) flags.append('s')
    if (node.longClickable) flags.append('l')
    if (!node.enabled) flags.append('d')
    if (flags.isNotEmpty() && (hasSignal || isRoot)) {
        data["a"] = flags.toString()
    }

    val nextParent = pkg ?: parentPackage
    val children = node.getChildren()
    val hasDirectSignalChild = children.any { hasNodeSignal(it) }
    val keptChildren = children.flatMap { buildCompactNodes(it, nextParent, false) }

    val keepNode = isRoot || hasSignal || hasDirectSignalChild
    if (keepNode) {
        if (keptChildren.isNotEmpty()) {
            data["children"] = keptChildren
        }
        return listOf(data)
    }
    return keptChildren
}

private fun hasNodeSignal(node: NodeInfo): Boolean {
    if (node.clickable || node.focusable || node.scrollable || node.longClickable) {
        return true
    }
    if (!node.text?.toString().isNullOrBlank()) {
        return true
    }
    if (!node.desc?.toString().isNullOrBlank()) {
        return true
    }
    if (!node.id.isNullOrBlank()) {
        return true
    }
    return false
}

private fun applySelector(
    selector: UiSelector,
    text: String?,
    id: String?,
    desc: String?,
    className: String?
) {
    text?.takeIf { it.isNotBlank() }?.let { selector.text(it) }
    id?.takeIf { it.isNotBlank() }?.let { selector.id(it) }
    desc?.takeIf { it.isNotBlank() }?.let { selector.desc(it) }
    className?.takeIf { it.isNotBlank() }?.let { selector.className(it) }
}

private fun findCollectionWithTimeout(selector: UiSelector, timeoutMillis: Long): com.stardust.automator.UiObjectCollection {
    if (timeoutMillis <= 0) {
        return selector.find()
    }
    val start = SystemClock.uptimeMillis()
    var collection = selector.find()
    while (collection.isEmpty && SystemClock.uptimeMillis() - start < timeoutMillis) {
        Thread.sleep(50)
        collection = selector.find()
    }
    return collection
}

private fun <T> parse(params: JsonObject?, clazz: Class<T>): T? {
    return try {
        if (params == null) null else gson.fromJson(params, clazz)
    } catch (_: Exception) {
        null
    }
}

private fun captureImage(runtime: ScriptRuntimeV2): ImageWrapper {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return runtime.automator.takeScreenshot2Sync()
    }
    @Suppress("UNCHECKED_CAST")
    val images = runtime.images as Images
    return images.captureScreen()
}

private suspend fun saveImage(context: Context, image: ImageWrapper): File = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "mcp-screenshot-${System.currentTimeMillis()}.png")
    image.saveTo(file.absolutePath)
    file
}

private fun imageToBase64(image: ImageWrapper): String {
    val output = ByteArrayOutputStream()
    val source = image.bitmap
    val scaled = scaleBitmapIfNeeded(source, BASE64_MAX_DIMENSION)
    val target = scaled ?: source
    target.compress(Bitmap.CompressFormat.JPEG, BASE64_JPEG_QUALITY, output)
    scaled?.recycle()
    return android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
}

private fun scaleBitmapIfNeeded(source: Bitmap, maxDimension: Int): Bitmap? {
    val width = source.width
    val height = source.height
    val maxSide = maxOf(width, height)
    if (maxSide <= maxDimension) {
        return null
    }
    val scale = maxDimension.toFloat() / maxSide.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
}

private const val BASE64_MAX_DIMENSION = 720
private const val BASE64_JPEG_QUALITY = 70
