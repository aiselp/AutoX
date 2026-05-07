package com.aiselp.autox.api

import android.accessibilityservice.GestureDescription
import android.os.Looper
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.annotations.V8Property
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueObject
import com.stardust.autojs.core.accessibility.SimpleActionAutomator
import com.stardust.autojs.core.accessibility.UiSelector
import com.stardust.autojs.runtime.ScriptRuntimeV2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

class JsAccessibility(val builder: ScriptRuntimeV2.Builder?) : NativeApi {
    override val moduleId: String = "accessibility"
    lateinit var looper: Looper

    @get:V8Property
    val automator by lazy {
        SimpleActionAutomator(builder!!.accessibilityBridge!!)
    }

    private fun createLooper(): Looper {
        return if (::looper.isInitialized) {
            looper
        } else {
            runBlocking {
                val deferred = CompletableDeferred<Looper>()
                Thread {
                    Looper.prepare()
                    deferred.complete(Looper.myLooper()!!)
                    Looper.loop()
                }.start()
                looper = deferred.await()
            }
            looper
        }
    }

    @V8Function
    fun createStrokeDescriptionArray(): MutableList<GestureDescription.StrokeDescription> {
        return ArrayList()
    }

    @V8Function
    fun selector(): UiSelector {
        return UiSelector(builder!!.accessibilityBridge)
    }

    @V8Function
    fun currentPackage(): String? {
        return builder?.accessibilityBridge?.infoProvider?.latestPackage
    }

    @V8Function
    fun currentActivity(): String? {
        return builder?.accessibilityBridge?.infoProvider?.latestActivity
    }

    override fun install(v8Runtime: V8Runtime, global: V8ValueObject): NativeApi.BindingMode {
        return NativeApi.BindingMode.ObjectBind
    }

    override fun recycle(v8Runtime: V8Runtime, global: V8ValueObject) {
        if (::looper.isInitialized)
            looper.quit()
    }


}