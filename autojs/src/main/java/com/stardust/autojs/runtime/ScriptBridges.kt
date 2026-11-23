package com.stardust.autojs.runtime

import android.os.Looper
import com.stardust.autojs.engine.RhinoJavaScriptEngine
import com.stardust.autojs.rhino.SimpleTypeInfo
import com.stardust.automator.UiObjectCollection
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.annotations.JSFunction
import java.lang.reflect.Method

class ScriptBridges {
    var engine: RhinoJavaScriptEngine? = null

    fun setup(engine: RhinoJavaScriptEngine) {
        this.engine = engine
    }

    private fun <T> useJsContext(f: (context: Context) -> T): T {
        val context = Context.getCurrentContext()
        val cx: Context = context ?: Context.enter().also {
            engine?.setupContext(it)
        }
        try {
            return f(cx)
        } finally {
            if (context == null) {
                Context.exit()
            }
        }
    }

    fun callFunction(func: Any?, target: Any?, args: Array<*>): Any? = useJsContext { context ->
        val jsFn = func as BaseFunction
        val scope = jsFn.parentScope
        val arg = args.map { Context.javaToJS(it, scope) }.toTypedArray()
        try {
            val targetObj = Context.javaToJS(target, scope)
            val thisObj = targetObj as? Scriptable ?: Undefined.SCRIPTABLE_UNDEFINED
            jsFn.call(context, scope, thisObj, arg)
        } catch (e: Exception) {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                engine?.runtime?.exit(e) ?: throw e
            } else {
                throw e
            }
        }
    }

    @JSFunction
    fun toArray(c: Iterable<*>): Scriptable = useJsContext { context ->
        val scope = context.initStandardObjects()
        val array = c.map { Context.javaToJS(it, scope) }.toTypedArray()
        context.newArray(scope, array)
    }

    fun toString(obj: Any?): String {
        return Context.toString(obj)
    }

    fun asArray(obj: UiObjectCollection): Scriptable = useJsContext { context ->
        val arr = toArray(obj.mNodes)
        val thzs = NativeJavaObject(arr, obj, SimpleTypeInfo(obj.javaClass))
        obj.javaClass.methods.forEach { method ->
            val name = method.name
            val nativeMethod = NativeJavaMethod(method, name)
            val bound = BoundFunction(context, arr, nativeMethod, thzs, emptyArray())
            arr.put(name, arr, bound)
        }
        arr
    }
}