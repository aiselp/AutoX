package com.stardust.autojs

import com.stardust.autojs.execution.ScriptExecution
import com.stardust.autojs.execution.ScriptExecutionListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class ScriptResultViewer : ScriptExecutionListener {
    var result = CompletableDeferred<Any?>()
    val finish = Job()

    override fun onStart(execution: ScriptExecution?) {
    }

    override fun onSuccess(execution: ScriptExecution?, result: Any?) {
        this.result.complete(result)
        finish.complete()
    }

    override fun onException(execution: ScriptExecution?, e: Throwable?) {
        this.result.completeExceptionally(e ?: Error("Unknown error"))
        finish.complete()
    }

    suspend fun waitForFinish() {
        finish.join()
    }

    suspend fun waitForFinish(timeout: Long, outAction: () -> Unit) {
        try {
            withTimeout(timeout) { finish.join() }
        } catch (e: TimeoutCancellationException) {
            outAction()
            throw e
        }
    }

    suspend fun waitForSuccess(): Any? {
        return result.await()
    }

    suspend fun waitForSuccess(timeout: Long, outAction: () -> Unit): Any? {
        try {
            return withTimeout(timeout) {
                result.await()
            }
        } catch (e: TimeoutCancellationException) {
            outAction()
            throw e
        }
    }

}