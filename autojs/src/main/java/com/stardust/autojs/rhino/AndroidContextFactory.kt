package com.stardust.autojs.rhino

import android.os.Build
import android.os.Looper
import android.util.Log
import com.stardust.autojs.runtime.ScriptBridges
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.automator.UiObject
import com.stardust.automator.UiObjectCollection
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
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
                else -> super.wrap(cx, scope, obj, staticType)
            }
        }
    }
}