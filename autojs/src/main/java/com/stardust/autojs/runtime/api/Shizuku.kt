package com.stardust.autojs.runtime.api

import android.content.Context
import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.core.shizuku.ShizukuClient
import com.stardust.autojs.core.util.Shell2
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

class Shizuku(context: Context) {
    private var shizukuShellCreate = false
    private val id = fId.getAndIncrement()
    private val packageName = context.packageName

    @ScriptInterface
    fun runRhinoScriptFile(path: String) = runBlocking {
        val shizukuService = ShizukuClient.instance.ensureShizukuService()
        shizukuService.runRhinoScriptFile(path)
    }

    @ScriptInterface
    fun runRhinoScript(script: String) = runBlocking {
        val shizukuService = ShizukuClient.instance.ensureShizukuService()
        shizukuService.runRhinoScript(script)
    }

    @ScriptInterface
    fun isShizukuAlive(): Boolean = ShizukuClient.instance.shizukuConnection.service != null

    @ScriptInterface
    fun runShizukuShellCommand(cmd: String): AbstractShell.Result = runBlocking {
        val shizukuService = ShizukuClient.instance.ensureShizukuService()
        val r = shizukuService.runShellCommand(id, cmd)
        shizukuShellCreate = true
        return@runBlocking Shell2.fromResultJson(r)
    }

    @ScriptInterface
    fun openAccessibility() {
        runShizukuShellCommand(
            "settings put secure enabled_accessibility_services ${packageName}/${accessibilityServiceName}"
        )
    }

    fun recycle() {
        if (shizukuShellCreate) {
            ShizukuClient.instance.shizukuConnection.service?.recycleShell(id)
        }
    }

    companion object {
        private val accessibilityServiceName =
            com.stardust.autojs.core.accessibility.AccessibilityService::class.java.name
        private val fId = AtomicInteger(1)
    }
}