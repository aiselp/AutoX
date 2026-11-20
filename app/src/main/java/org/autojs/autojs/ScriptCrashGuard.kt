package org.autojs.autojs

import android.os.Looper
import android.util.Log
import com.stardust.autojs.ScriptEngineService
import com.stardust.autojs.engine.JavaScriptEngine
import com.stardust.autojs.runtime.ScriptRuntime

/**
 * `:script` 进程主线程上的脚本异常兜底。
 *
 * Rhino 1.9.x 移除 VMBridge 后，用户通过 `JavaAdapter` / `new View.OnClickListener(...)`
 * 等原生 Java 接口代理抛出的异常不再经过 Auto.js 的 ScriptBridges.callFunction 兜底，
 * 会一路冒到 UI 线程 Looper 之外，触发默认 UncaughtExceptionHandler 而杀掉进程。
 *
 * 这里在 `ErrorReportActivity.install()` 之后再包一层：如果异常发生在 :script 进程的主线程，
 * 并且能找到正在运行的 ScriptRuntime，就按脚本异常结束脚本，而不是让整个进程闪退。
 * 其它情况（非主线程、找不到 runtime、兜底本身失败）一律交还原 handler，保持原有错误报告行为。
 *
 * 注意这是止血措施，不是 VMBridge 的等价替代：boolean 接口方法在 JS 函数未返回值时，
 * 旧版会把 undefined 转成 false，1.9.2 走原生 jsToJava 转换，语义仍有差异；
 * 此兜底只保证异常不会演变成进程崩溃。
 */
object ScriptCrashGuard {
    private const val TAG = "ScriptCrashGuard"

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val runtime = currentRuntimeOrNull()
            if (thread === Looper.getMainLooper().thread && runtime != null) {
                var handled = false
                try {
                    Log.e(TAG, "拦截 :script 进程主线程脚本异常，改为结束脚本", throwable)
                    runtime.exit(throwable)
                    handled = true
                } catch (e: Throwable) {
                    Log.e(TAG, "脚本异常兜底失败，交还原异常处理器", e)
                }
                if (handled) {
                    // 异常已经冒出 Looper.loop()，主线程的消息循环已经终止。
                    // :script 进程没有 Activity，但后续的 ui.run / Handler 回调仍然依赖
                    // 主线程 Looper，因此必须重新进入循环，否则进程虽未闪退却会失去响应。
                    Looper.loop()
                    return@setDefaultUncaughtExceptionHandler
                }
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Log.e(TAG, "线程异常且没有原异常处理器", throwable)
            }
        }
    }

    private fun currentRuntimeOrNull(): ScriptRuntime? = try {
        ScriptEngineService.instance?.engines
            ?.filterIsInstance<JavaScriptEngine>()
            ?.mapNotNull { it.runtime }
            ?.firstOrNull()
    } catch (e: Throwable) {
        Log.e(TAG, "查找 ScriptRuntime 失败", e)
        null
    }
}
