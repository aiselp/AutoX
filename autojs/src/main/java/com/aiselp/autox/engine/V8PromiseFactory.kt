package com.aiselp.autox.engine

import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueFunction
import com.caoccao.javet.values.reference.V8ValueObject
import com.caoccao.javet.values.reference.V8ValuePromise

class V8PromiseFactory(val runtime: V8Runtime, val eventLoopQueue: EventLoopQueue) {
    private val executor = runtime.getExecutor(
        """
        (()=>{
            const adapter = {};
         
            adapter.promise = new Promise(function(resolve, reject) {
                adapter.resolve = resolve          
                adapter.reject = reject
            })
            return adapter
        })()
    """.trimIndent()
    )

    fun newPromiseAdapter(): PromiseAdapter {
        return PromiseAdapter(executor.execute(), eventLoopQueue)
    }

    fun newKeepRunningPromiseAdapter(): KeepRunningPromiseAdapter {
        return KeepRunningPromiseAdapter(executor.execute(), eventLoopQueue)
    }

    class KeepRunningPromiseAdapter(
        adapter: V8ValueObject,
        val eventLoopQueue: EventLoopQueue
    ) : PromiseAdapter(adapter, eventLoopQueue) {
        val task = Any()

        init {
            eventLoopQueue.createPersistentTask(task)
        }

        override fun resolve(arg: Any?) {
            super.resolve(arg)
            eventLoopQueue.cancelPersistentTask(task)
        }
        override fun reject(arg: Any?) {
            super.reject(arg)
            eventLoopQueue.cancelPersistentTask(task)
        }
    }

    open class PromiseAdapter(
        private val adapter: V8ValueObject,
        private val eventLoopQueue: EventLoopQueue
    ) : AutoCloseable {
        val promise: V8ValuePromise
            get() = adapter.get("promise")

        @Volatile
        private var promiseStatus = PENDING

        open fun resolve(arg: Any?) {
            if (promiseStatus != PENDING) {
                return
            }
            eventLoopQueue.addTask {
                val resolve = adapter.get<V8ValueFunction>("resolve")
                resolve.use { it.callVoid(null, arg) }
                close()
            }
            promiseStatus = FULFILLED
        }

        open fun reject(arg: Any?) {
            if (promiseStatus != PENDING) {
                return
            }
            eventLoopQueue.addTask {
                val reject = adapter.get<V8ValueFunction>("reject")
                reject.use { it.callVoid(null, arg) }
                close()
            }
            promiseStatus = REJECTED
        }

        companion object {
            const val PENDING = 0
            const val FULFILLED = 1
            const val REJECTED = 2
        }

        override fun close() {
            promise.close()
        }
    }
}