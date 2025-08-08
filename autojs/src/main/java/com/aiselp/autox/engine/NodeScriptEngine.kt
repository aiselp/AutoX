package com.aiselp.autox.engine

import android.content.Context
import android.util.Log
import com.aiselp.autox.api.JavaInteractor
import com.aiselp.autox.api.JsAccessibility
import com.aiselp.autox.api.JsApp
import com.aiselp.autox.api.JsClipManager
import com.aiselp.autox.api.JsDialogs
import com.aiselp.autox.api.JsEngines
import com.aiselp.autox.api.JsMedia
import com.aiselp.autox.api.JsToast
import com.aiselp.autox.api.JsUi
import com.aiselp.autox.api.NodeConsole
import com.aiselp.autox.module.NodeModuleResolver
import com.caoccao.javet.entities.JavetEntityError
import com.caoccao.javet.enums.V8AwaitMode
import com.caoccao.javet.exceptions.BaseJavetScriptingException
import com.caoccao.javet.exceptions.JavetScriptingError
import com.caoccao.javet.interop.NodeRuntime
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.converters.JavetProxyConverter
import com.caoccao.javet.node.modules.NodeModuleModule
import com.caoccao.javet.node.modules.NodeModuleProcess
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueError
import com.caoccao.javet.values.reference.V8ValuePromise
import com.stardust.autojs.AutoJs
import com.stardust.autojs.BuildConfig
import com.stardust.autojs.engine.ScriptEngine
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.runtime.ScriptRuntimeV2
import com.stardust.autojs.runtime.exception.ScriptException
import com.stardust.autojs.script.ScriptSource
import com.stardust.pio.PFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.locks.ReentrantLock

class NodeScriptEngine(val context: Context) :
    ScriptEngine.AbstractScriptEngine<ScriptSource>(), ScriptEngine.EngineEvent {
    val runtime: NodeRuntime = V8Host.getNodeInstance().createV8Runtime()

    private val tags = mutableMapOf<String, Any?>()
    private val v8Locker = run {
        runtime.v8Locker
        ReentrantLock()
    }
    private val config: ExecutionConfig by lazy {
        tags[ExecutionConfig.tag] as ExecutionConfig
    }
    private val moduleDirectory = getModuleDirectory(context)
    private val resultListener = PromiseListener()
    private val console = NodeConsole(context)
    private val nativeApiManager = NativeApiManager(this)
    val converter = JavetProxyConverter()
    val scope = CoroutineScope(Dispatchers.Default)
    val eventLoopQueue = EventLoopQueue(runtime)
    val promiseFactory = V8PromiseFactory(runtime, eventLoopQueue)
    val builder: ScriptRuntimeV2.Builder? = try {
        AutoJs.instance.createRuntimeBuilder()
    } catch (e: Throwable) {
        null
    }

    init {
        Log.i(TAG, "node version: ${runtime.version}")
    }

    override fun put(name: String, value: Any?) {
        tags[name] = value
    }

    override fun forceStop() {
        forceStop(InterruptedException("force stop"))
    }

    fun forceStop(message: String, stack: String) {
        console.error(stack)
        forceStop(ScriptException(message))
    }

    fun forceStop(e: Throwable) {
        Log.i(TAG, "force stop")
        resultListener.cancel()
        if (runtime.isInUse) {
            runtime.terminateExecution()
        }
        if (scope.isActive) scope.cancel("force stop", e)
        console.error(e.stackTraceToString())
    }

    override fun init() {
        runtime.converter = converter
        runtime.allowEval(true)
        runtime.isStopping = true
        runtime.getExecutor(
            """
            (()=>{
                let c = 0
                process.on('uncaughtException', (err) => {
                    c++
                    if (c > 5) process.abort()
                    Autox.exit(err)
                })
                process.exit = function(code) {
                    if (code > 0){
                        throw new Error('exit with code: ' + code)
                    }else Autox.safeExit()
                }
            })()
        """.trimIndent()
        ).executeVoid()
        runtime.logger
        initializeApi()
    }

    private fun initializeApi() = runtime.globalObject.use { global ->
        nativeApiManager.register(console)
        nativeApiManager.register(JsUi(this))
        nativeApiManager.register(JsClipManager(context, eventLoopQueue))
        nativeApiManager.register(JavaInteractor(scope, converter, promiseFactory))
        nativeApiManager.register(JsToast(context, scope))
        nativeApiManager.register(JsMedia(context))
        nativeApiManager.register(JsDialogs(eventLoopQueue, context, scope))
        nativeApiManager.register(JsEngines(this))
        nativeApiManager.register(JsApp(context))
        nativeApiManager.register(JsAccessibility(builder))
        nativeApiManager.initialize(runtime, global)
    }

    override fun execute(scriptSource: ScriptSource): Any? = runBlocking {
        check(scriptSource is NodeScriptSource) { "scriptSource must be NodeScriptSource" }
        Log.i(TAG, "execute: ${scriptSource.file.path}")
        val scriptFile = scriptSource.file
        try {
            initializeModule(scriptFile).use {
                if (it is V8ValuePromise)
                    it.register(resultListener)
                else resultListener.onFulfilled(it)
            }
            while (scope.isActive) {
//                    Log.d(TAG,"loop ing...")
                if (runtime.await(V8AwaitMode.RunNoWait) or
                    eventLoopQueue.executeQueue()
                ) {
                    Thread.sleep(1)
                    continue
                } else break
            }
            if (resultListener.isFulfilledCalled) {
                return@runBlocking resultListener.await()
            } else if (resultListener.isRejectedCalled) {
                exceptionHandling(resultListener.await())
            } else {
                return@runBlocking null
            }
        } catch (e: Throwable) {
            exceptionHandling(e)
        }
    }

    override fun emit(name: String, vararg args: Any?) {
        val jsEngines = nativeApiManager.getNativeApi(JsEngines.ID) as? JsEngines
        jsEngines?.let { eventLoopQueue.addTask { it.emitEngineEvent(name, args) } }
    }

    private fun exceptionHandling(e: Any?) {
        when (e) {
            is V8ValueError -> run {
                console.error(e.stack)
                throw ScriptException(e.message)
            }

            is JavetEntityError -> run {
                console.error(e.stack)
                throw ScriptException(e.message)
            }

            is JavetScriptingError -> run {
                console.error(e.stack)
                throw ScriptException(e.message)
            }

            is BaseJavetScriptingException -> run {
                if (e.cause != null) {
                    exceptionHandling(e.cause)
                }
                e.scriptingError.let {
                    console.error("${it.stack}\n  at ${it.resourceName}:${it.lineNumber}:${it.startColumn}")
                }

                throw e
            }

            is ScriptException -> throw e

            is Throwable -> run {
                console.error(e.stackTraceToString())
                throw e
            }

            else -> throw ScriptException(e.toString())
        }
    }

    private fun initializeModule(file: File): V8Value {
        val parentFile = file.parentFile ?: File("/")
        runtime.getNodeModule(NodeModuleProcess::class.java).workingDirectory = parentFile
        val nodeModuleResolver = NodeModuleResolver(runtime, file, moduleDirectory)
        runtime.v8ModuleResolver = nodeModuleResolver
        runtime.globalObject.delete(NodeModuleModule.PROPERTY_REQUIRE)
        return if (NodeModuleResolver.isEsModule(file)) {
            //es module
            NodeModuleResolver.compileV8Module(runtime, file.readText(), file.path).run {
                nodeModuleResolver.addCacheModule(this)
                execute()
            }
        } else {
            //commonjs
            nodeModuleResolver.require.call(
                null, runtime.createV8ValueString(file.path)
            )
        }
    }

    override fun destroy() {
        if (scope.isActive) scope.cancel()
        val code = if (scope.isActive) 0 else 1
        try {
            runtime.getExecutor("process.emit('exit',$code);").executeVoid()
        } catch (e: Throwable) {
            Log.w(TAG, e.stackTraceToString())
        }
        nativeApiManager.recycle(runtime, runtime.globalObject)
        eventLoopQueue.recycle()
        if (!runtime.isClosed) {
            runtime.lowMemoryNotification()
            runtime.close()
        }
        super.destroy()
    }

    companion object {
        const val ID = "com.aiselp.autox.engine.NodeScriptEngine"
        private const val TAG = "NodeScriptEngine"
        fun getModuleDirectory(context: Context): File {
            return File(context.filesDir, "v7_modules")
        }

        fun initModuleResource(context: Context, appVersionChange: Boolean) {
            val moduleDirectory = getModuleDirectory(context)
            if (appVersionChange || BuildConfig.DEBUG || !moduleDirectory.isDirectory) {
                PFiles.removeDir(moduleDirectory.path)
                moduleDirectory.mkdirs()
                PFiles.copyAssetDir(context.assets, "modules/npm", moduleDirectory)
                PFiles.copyAssetDir(context.assets, "v7modules", moduleDirectory)
                initPackageFile(moduleDirectory)
            }
        }

        private fun initPackageFile(dir: File) {
            dir.listFiles()?.forEach {
                if (it.isDirectory) {
                    val packageJsonFile = File(it, "package.json")
                    if (!packageJsonFile.isFile()) {
                        packageJsonFile.writeText(
                            """
                            {
                                "name": "${it.name}",
                                "version": "0.0.0",
                                "main": "index.js"
                            }
                        """.trimIndent()
                        )
                    }
                }
            }
        }
    }
}