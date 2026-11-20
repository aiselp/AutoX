package com.stardust.autojs.rhino

import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.View
import com.stardust.autojs.core.ui.ViewExtras
import com.stardust.autojs.runtime.ScriptBridges
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.automator.UiObject
import com.stardust.automator.UiObjectCollection
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.UniqueTag
import org.mozilla.javascript.lc.type.TypeInfo
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Stardust on 2017/4/5.
 */
open class AndroidContextFactory(private val cacheDirectory: File) : ContextFactory() {
    companion object {
        const val LOG_TAG = "ContextFactory"
        val bridges = ScriptBridges()

        init {
            /*
              一个神奇的bug，若去掉下面这行，则无法执行脚本，原因在运行脚本时访问Context.emptyArgs将得到null
              只有在较早的类加载阶段访问一次该属性才能正常
             */
            Log.d(LOG_TAG, "init emptyArgs ${Context.emptyArgs}")
        }
    }

    private val mContextCount = AtomicInteger()
    private val wrapFactory = WrapFactory()

    init {
        initApplicationClassLoader(createClassLoader(AndroidContextFactory::class.java.classLoader!!))
    }

    /**
     * Create a ClassLoader which is able to deal with bytecode
     *
     * @param parent the parent of the create classloader
     * @return a new ClassLoader
     */
    final override fun createClassLoader(parent: ClassLoader): AndroidClassLoader {
        return AndroidClassLoader(parent, cacheDirectory)
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        if (Thread.currentThread().isInterrupted && Looper.myLooper() != Looper.getMainLooper()) {
            throw ScriptInterruptedException()
        }
    }

    override fun makeContext(): Context {
        val cx: Context = AutoJsContext(this)
        setupContext(cx)
        return cx
    }

    private fun setupContext(context: Context) {
        context.instructionObserverThreshold = 10000
        context.optimizationLevel = -1
        context.languageVersion = Context.VERSION_ES6
        context.locale = Locale.getDefault()
        context.wrapFactory = wrapFactory
    }

    override fun onContextCreated(cx: Context) {
        super.onContextCreated(cx)
        val i = mContextCount.incrementAndGet()
        Log.d(LOG_TAG, "onContextCreated: count = $i")
    }

    override fun onContextReleased(cx: Context) {
        super.onContextReleased(cx)
        val i = mContextCount.decrementAndGet()
        Log.d(LOG_TAG, "onContextReleased: count = $i")
    }

    override fun hasFeature(cx: Context?, featureIndex: Int): Boolean {
        when (featureIndex) {
            Context.FEATURE_ENABLE_XML_SECURE_PARSING -> return false
        }
        return super.hasFeature(cx, featureIndex)
    }

    open class WrapFactory : org.mozilla.javascript.WrapFactory() {
        init {
            isJavaPrimitiveWrap = false
        }

        /**
         * Rhino 1.9.2 内部统一走 wrap(..., TypeInfo)，旧的 wrap(..., Class) 重载只做
         * 一次 TypeInfo 转换后回调本方法。若仍重写旧签名，UiObject / UiObjectCollection
         * 的特殊包装会完全失效，因此迁移到 TypeInfo 签名。
         */
        override fun wrap(cx: Context?, scope: Scriptable?, obj: Any?, staticType: TypeInfo?): Any? {
            return when (obj) {
                is UiObject -> if (Build.VERSION.SDK_INT >= 30) {
                    super.wrap(cx, scope, obj, staticType)
                } else UiObjectProxy(obj)

                is UiObjectCollection -> bridges.asArray(obj)

                is View -> {
                    // Rhino 1.9.2 移除了 VMBridge，InterfaceAdapter 在非脚本线程（如 UI 线程
                    // 的 OnClickListener）里通过 cf.call() 新建 Context 时，只会拿到 factory 级
                    // WrapFactory，拿不到 engine 级那个（那里才处理 View -> NativeView）。
                    // 这里从 wrap 传入的 topScope 反查当前脚本的 runtime（实测 topScope 中
                    // runtime 与 __engine__ 均存在），在 factory 级完成同样的包装，使新线程的
                    // View 依然具备 attr()/click() 等 UI 属性方法。
                    val runtime = runtimeFromScope(scope)
                    if (scope != null && runtime != null) {
                        val staticClass = viewStaticClass(obj, staticType)
                        ViewExtras.getNativeView(scope, obj, staticClass, runtime)
                    } else {
                        super.wrap(cx, scope, obj, staticType)
                    }
                }

                else -> super.wrap(cx, scope, obj, staticType)
            }
        }

        /**
         * 与 [wrap] 同理：Rhino 1.9.2 里 `new JavaClass(...)` 构造出的对象、以及
         * InterfaceAdapter 传给 JS 的参数，可能直接走 wrapAsJavaObject 而不经过
         * wrap(...)。非脚本线程的 Context 只持有本 factory 级 WrapFactory，因此
         * 在这里也要补上 View -> NativeView，否则 UI 属性方法失效。
         */
        override fun wrapAsJavaObject(
            cx: Context?,
            scope: Scriptable?,
            obj: Any?,
            staticType: TypeInfo?
        ): Scriptable? {
            if (obj is View && scope != null) {
                val runtime = runtimeFromScope(scope)
                if (runtime != null) {
                    val staticClass = viewStaticClass(obj, staticType)
                    return ViewExtras.getNativeView(scope, obj, staticClass, runtime)
                }
            }
            return super.wrapAsJavaObject(cx, scope, obj, staticType)
        }

        companion object {
            /**
             * 取 View 包装用的 staticClass。
             *
             * TypeInfo.NONE.asClass() 返回 Object.class（非 null），直接用
             * `staticType?.asClass() ?: obj.javaClass` 会让 fallback 永远不生效，
             * staticClass 退化成 Object，可能让 NativeView 的 Java bean 属性分派落空。
             * NONE（Object.class）时改用实际类。
             */
            @JvmStatic
            fun viewStaticClass(obj: Any, staticType: TypeInfo?): Class<*> {
                val cls = staticType?.asClass()
                return if (cls == null || cls == Any::class.java) obj.javaClass else cls
            }

            /**
             * 从 wrap 的 scope 中取当前脚本的 ScriptRuntime。
             * scope 通常是脚本的 topScope，里面由 JavaScriptEngine 放入 "runtime"；
             * 新线程通过 InterfaceAdapter 回调时传入的也是同一个 topScope。
             */
            @JvmStatic
            fun runtimeFromScope(scope: Scriptable?): ScriptRuntime? {
                if (scope == null) return null
                return try {
                    val value = ScriptableObject.getProperty(scope, "runtime")
                    if (value === UniqueTag.NOT_FOUND || value == null) {
                        null
                    } else {
                        (value as? ScriptRuntime)
                            ?: (value as? org.mozilla.javascript.NativeJavaObject)?.unwrap() as? ScriptRuntime
                    }
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }
}