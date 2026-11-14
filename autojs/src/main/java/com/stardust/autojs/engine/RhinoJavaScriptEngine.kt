package com.stardust.autojs.engine

import android.util.Log
import android.view.View
import com.stardust.autojs.core.ui.ViewExtras
import com.stardust.autojs.engine.module.AssetAndUrlModuleSourceProvider
import com.stardust.autojs.engine.module.ScopeRequire
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.project.ScriptConfig
import com.stardust.autojs.rhino.AndroidContextFactory
import com.stardust.autojs.rhino.AutoJsContext
import com.stardust.autojs.rhino.RhinoAndroidHelper
import com.stardust.autojs.rhino.TopLevelScope
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.script.JavaScriptFileSource
import com.stardust.autojs.script.JavaScriptSource
import com.stardust.autojs.util.ObjectWatcher
import com.stardust.pio.UncheckedIOException
import org.mozilla.javascript.Context
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider
import org.mozilla.javascript.lc.type.TypeInfo
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI

open class RhinoJavaScriptEngine(private val mAndroidContext: android.content.Context) :
    JavaScriptEngine() {

    private val wrapFactory = WrapFactory()
    val context: Context = enterContext()
    private val mScriptable: TopLevelScope = createScope(this.context)
    var thread: Thread? = null
        private set

    private val initScript: Script by lazy<Script> {
        try {
            val reader = InputStreamReader(mAndroidContext.assets.open("init.js"))
            val script = context.compileReader(reader, SOURCE_NAME_INIT, 1, null)
            script!!
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }
    lateinit var require: ScopeRequire

    val scriptable: Scriptable
        get() = mScriptable

    override fun put(name: String, value: Any?) {
        ScriptableObject.putProperty(mScriptable, name, Context.javaToJS(value, mScriptable))
    }

    override fun setRuntime(runtime: ScriptRuntime) {
        super.setRuntime(runtime)
        runtime.bridges.setup(this)
        runtime.topLevelScope = mScriptable
    }

    public override fun doExecution(source: JavaScriptSource): Any? {
        if (source is JavaScriptFileSource) {
            require.mainModuleId = source.file.path
        } else {
            if (cwd() != null) {
                require.mainModuleId = cwd() + "/" + source.name
            }
        }
        val reader = source.nonNullScriptReader
        try {
            val script = context.compileReader(reader, source.toString(), 1, null)
            return if (hasFeature(ScriptConfig.FEATURE_CONTINUATION)) {
                context.executeScriptWithContinuations(script, mScriptable)
            } else {
                script.exec(context, mScriptable)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun hasFeature(feature: String): Boolean {
        val config = getTag(ExecutionConfig.tag) as ExecutionConfig?
        return config != null && config.scriptConfig.hasFeature(feature)
    }

    override fun forceStop() {
        Log.d(LOG_TAG, "forceStop: interrupt Thread: $thread")
        thread?.interrupt()
    }

    @Synchronized
    override fun destroy() {
        super.destroy()
        if (::require.isInitialized) {
            require.clearCache()
            ObjectWatcher.watch(require, "$this::${require}")
        }
        try {
            Context.exit()
        } catch (e: IllegalStateException) {
            Log.w(LOG_TAG, "Context.exit() on wrong thread", e)
        }
        ObjectWatcher.watch(this, toString())
    }

    override fun init() {
        thread = Thread.currentThread()
        ScriptableObject.putProperty(mScriptable, "__engine__", this)
        initRequireBuilder(context, mScriptable)
        try {
            context.executeScriptWithContinuations(initScript, mScriptable)
        } catch (e: IllegalArgumentException) {
            if ("Script argument was not a script or was not created by interpreted mode " == e.message) {
                initScript.exec(context, mScriptable)
            } else {
                throw e
            }
        }
    }

    private fun initRequireBuilder(context: Context, scope: Scriptable) {
        val provider = AssetAndUrlModuleSourceProvider(
            mAndroidContext,
            listOf(
                AssetAndUrlModuleSourceProvider.NPM_MODULE_DIR,
                AssetAndUrlModuleSourceProvider.V6MODULE_DIR
            )
        )
        require = ScopeRequire(
            context, scope, SoftCachingModuleScriptProvider(provider),
            null, null, false
        )
        require.install(scope)
    }

    private fun createScope(context: Context): TopLevelScope {
        val topLevelScope = TopLevelScope()
        topLevelScope.initStandardObjects(context, false)
        return topLevelScope
    }

    fun enterContext(): Context {
        val context = RhinoAndroidHelper(mAndroidContext).enterContext()
        setupContext(context)
        return context
    }

    fun setupContext(context: Context) {
        context.wrapFactory = wrapFactory
        (context as? AutoJsContext)?.let {
            it.rhinoJavaScriptEngine = this
        }
    }

    private inner class WrapFactory : AndroidContextFactory.WrapFactory() {
        /**
         * Rhino 1.9.2 的 wrap(..., TypeInfo) 最终会调用本方法，因此只在这里把 View
         * 交给 NativeView（NativeJavaObjectWithPrototype 的 prototype 链）即可：
         * UI 属性方法由 Rhino 自己按 Java 成员 + prototype 分派，无需手写反射。
         * wrap(..., TypeInfo) 保持在父类中重写，避免绕过 UiObject 的特殊包装。
         */
        override fun wrapAsJavaObject(
            cx: Context?,
            scope: Scriptable?,
            obj: Any?,
            staticType: TypeInfo?
        ): Scriptable? {
            if (obj is View && scope != null) {
                val staticClass = staticType?.asClass() ?: obj.javaClass
                return ViewExtras.getNativeView(scope, obj, staticClass, runtime)
            }
            return super.wrapAsJavaObject(cx, scope, obj, staticType)
        }
    }

    companion object {
        const val SOURCE_NAME_INIT = "<init>"
        private const val LOG_TAG = "RhinoJavaScriptEngine"
    }
}