package com.stardust.autojs

import com.stardust.autojs.engine.RhinoJavaScriptEngine
import com.stardust.autojs.engine.ScriptEngine
import org.mozilla.javascript.ScriptableObject


fun ScriptEngine<*>.getGlobalProperty(key: String): Any? {
    if (this is RhinoJavaScriptEngine) {
        return ScriptableObject.getProperty(this.scriptable, key)
    }

    throw NotImplementedError()
}

fun Any?.toDouble(): Double? {
    when (this) {
        is Double -> return this
        is Number -> return this.toDouble()
        else -> return null
    }
}