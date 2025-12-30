package org.autojs.autoxjs.mcp

import com.stardust.autojs.AutoJs
import com.stardust.autojs.runtime.ScriptRuntimeV2

class McpRuntimeProvider {
    private val lock = Any()
    private var runtime: ScriptRuntimeV2? = null

    fun getRuntime(): ScriptRuntimeV2 {
        synchronized(lock) {
            val current = runtime
            if (current != null) {
                return current
            }
            val autoJs = try {
                AutoJs.instance
            } catch (e: UninitializedPropertyAccessException) {
                throw IllegalStateException("AutoJs is not initialized", e)
            }
            val created = autoJs.createRuntimeBuilder().build().apply { init() }
            runtime = created
            return created
        }
    }

    fun close() {
        synchronized(lock) {
            runtime?.onExit()
            runtime = null
        }
    }
}
