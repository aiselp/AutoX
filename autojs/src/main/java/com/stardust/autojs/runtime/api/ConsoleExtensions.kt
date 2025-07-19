package com.stardust.autojs.runtime.api

import android.util.Log
import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.core.console.ConsoleImpl
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.Closeable

class ConsoleExtension(
    val console: ConsoleImpl
) : Console by console, Closeable {
    private val scheduler: Scheduler = Schedulers.newThread()
    private val disposables = mutableSetOf<Disposable>()

    @ScriptInterface
    fun registerConsoleListener(listener: ConsoleListener): Disposable {
        val disposable = console.logPublish.observeOn(scheduler).subscribe {
            listener.onLog(it.content.toString(), it.level, parseLevel(it.level))
        }
        disposables.add(disposable)
        return disposable
    }

    @ScriptInterface
    fun registerGlobalConsoleListener(listener: ConsoleListener): Disposable {
        val globalConsole = console.globalConsole as ConsoleImpl
        val disposable = globalConsole.logPublish.observeOn(scheduler).subscribe {
            println("currentThread: ${Thread.currentThread()}" )
            listener.onLog(it.content.toString(), it.level, parseLevel(it.level))
        }
        disposables.add(disposable)
        return disposable
    }

    override fun close() {
        disposables.forEach { it.dispose() }
        disposables.clear()
        scheduler.shutdown()
    }

    fun interface ConsoleListener {
        fun onLog(log: String, level: Int, levelString: String)
    }

    companion object {
        fun parseLevel(level: Int): String {
            return when (level) {
                Log.VERBOSE -> "VERBOSE"
                Log.DEBUG -> "DEBUG"
                Log.INFO -> "INFO"
                Log.WARN -> "WARN"
                Log.ERROR -> "ERROR"
                Log.ASSERT -> "ASSERT"
                else -> "Unknown"
            }
        }
    }
}