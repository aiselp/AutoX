package com.aiselp.autox.api

import android.util.Log
import com.aiselp.autox.engine.V8PromiseFactory
import com.aiselp.autox.utils.Members
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.interop.converters.JavetObjectConverter
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueFunction
import com.caoccao.javet.values.reference.V8ValueObject
import com.caoccao.javet.values.reference.V8ValuePromise
import com.stardust.autojs.runtime.exception.ScriptException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class JavaInteractor(
    val scope: CoroutineScope, private val converter: JavetObjectConverter,
    private val promiseFactory: V8PromiseFactory
) : NativeApi {
    override val moduleId: String = "java"
    private lateinit var v8Runtime: V8Runtime
    override fun install(v8Runtime: V8Runtime, global: V8ValueObject): NativeApi.BindingMode {
        this.v8Runtime = v8Runtime
        return NativeApi.BindingMode.ObjectBind
    }

    override fun recycle(v8Runtime: V8Runtime, global: V8ValueObject) {

    }

    private fun asyncInvoke(
        con: CoroutineDispatcher,
        func: () -> Any?,
    ): V8ValuePromise {
        val promiseAdapter = promiseFactory.newKeepRunningPromiseAdapter()
        val promise = promiseAdapter.promise
        scope.launch(con) {
            try {
                val r = func()
                promiseAdapter.resolve(r)
            } catch (e: Throwable) {
                promiseAdapter.reject(e)
            }
        }
        return promise
    }

    @V8Function
    fun callback(fn: V8ValueFunction) {
        val v8Callback = promiseFactory.eventLoopQueue.createV8Callback(fn)
        scope.launch {
            delay(2000)
            v8Callback.invoke()
        }
    }

    @V8Function
    fun loadClass(className: String): Class<*> {
        return Class.forName(className)
    }

    @V8Function
    fun invoke(vararg args: V8Value?): Any? {
        val exec = findMethod(args.toList())
        return exec()
    }

    @V8Function
    fun invokeIo(vararg args: V8Value?): V8ValuePromise {
        return asyncInvoke(Dispatchers.IO, findMethod(args.toList()))
    }


    @V8Function
    fun invokeUi(vararg args: V8Value?): V8ValuePromise {
        return asyncInvoke(Dispatchers.Main, findMethod(args.toList()))
    }


    @V8Function
    fun invokeDefault(vararg args: V8Value?): V8ValuePromise {
        return asyncInvoke(Dispatchers.Default, findMethod(args.toList()))
    }


    @V8Function
    fun printCurrentThread() {
        Log.d(TAG, "Current Thread: ${Thread.currentThread()}")
    }

    private fun findMethod(args: List<V8Value?>): () -> Any? {
        val argList = args.map { converter.toObject<Any?>(it) }
        Log.i(TAG, "invoke  $args")
        val javaObj = argList.getOrNull(0)
        check(javaObj != null) { ScriptException("javaObj is null") }
        check(javaObj !is V8Value) { ScriptException("javaObj must not be a V8Value, but was ${javaObj.javaClass.name}") }
        val methodName = argList.getOrNull(1)
        check(methodName is String) { ScriptException("methodName must be a String, but was ${methodName?.javaClass?.name ?: "null"}") }
        val javaArgs = argList.getOrNull(2)
        check(javaArgs is List<*>) { ScriptException("javaArgs must be a List, but was ${javaArgs?.javaClass?.name ?: "null"}") }
        val argTpyes = javaArgs.map { it?.let { it::class } }
        val method = Members.findMethod(javaObj::class, methodName, argTpyes)
        checkNotNull(method) { ScriptException("method not found ${javaObj::class.qualifiedName}$${methodName} (${argTpyes.joinToString()})") }
        return { method.call(javaObj, *(javaArgs.toTypedArray())) }
    }

    companion object {
        private const val TAG = "JavaInteractor"
    }
}