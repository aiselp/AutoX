package com.stardust.autojs.core.console

import android.content.Intent
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.toColorInt
import com.stardust.autojs.R
import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.runtime.ScriptRuntimeV2.Companion.getStackTrace
import com.stardust.autojs.runtime.api.AbstractConsole
import com.stardust.autojs.runtime.api.Console
import com.stardust.autojs.runtime.exception.ScriptException
import com.stardust.autojs.util.FloatingPermission
import com.stardust.autojs.util.isUiThread
import com.stardust.enhancedfloaty.FloatyService
import com.stardust.util.UiHandler
import com.stardust.util.ViewUtil
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Stardust on 2017/5/2.
 */
open class ConsoleImpl @JvmOverloads constructor(
    private val mUiHandler: UiHandler,
    val globalConsole: Console? = null
) : AbstractConsole() {
    private var maxLines = 5 * 1000

    interface LogListener {
        fun onNewLog(logEntry: LogEntry)

        fun onLogClear()
    }

    val logPublish: PublishSubject<LogEntry> = PublishSubject.create()
    val allLogs: ArrayList<LogEntry> = ArrayList()
    private val mIdCounter = AtomicInteger(0)
    private val mConsoleFloaty = ConsoleFloaty(this)

    @get:ScriptInterface
    val floatyWindow = ConsoleFloatyWindow(mConsoleFloaty)

    private var mLogListener: WeakReference<LogListener?>? = null
    private var mConsoleView: WeakReference<ConsoleView?>? = null
    private var consoleInput = CompletableDeferred<CharSequence>()


    fun setConsoleView(consoleView: ConsoleView?) {
        mConsoleView = WeakReference(consoleView)
        setLogListener(consoleView)
    }


    fun setLogListener(logListener: LogListener?) {
        mLogListener = WeakReference(logListener)
    }

    fun printAllStackTrace(t: Throwable?) {
        println(Log.ERROR, getStackTrace(t!!, true))
    }

    fun getStackTrace(t: Throwable?): String {
        return getStackTrace(t!!, false)
    }

    override fun println(level: Int, charSequence: CharSequence?): String? {
        val logEntry = LogEntry(mIdCounter.getAndIncrement(), level, charSequence.toString(), true)
        synchronized(allLogs) {
            allLogs.add(logEntry)
        }
        globalConsole?.println(level, charSequence)
        mLogListener?.get()?.onNewLog(logEntry)

        if (maxLines > 0 && allLogs.size > maxLines) {
            clear()
        }
        logPublish.onNext(logEntry)
        return null
    }

    override fun setTitle(title: CharSequence, color: String, size: Int) {
        var color: String? = color
        if (TextUtils.isEmpty(color)) {
            color = "#fe14efb1"
        }
        mConsoleFloaty.setTitle(title, color!!.toColorInt(), size)
    }

    fun setTitle(title: CharSequence?) {
        mConsoleFloaty.setTitle(title, -0x1eb104f, -1)
    }

    fun setTitle(title: CharSequence?, color: String?) {
        var color = color
        if (TextUtils.isEmpty(color)) {
            color = "#fe14efb1"
        }
        mConsoleFloaty.setTitle(title, color!!.toColorInt(), -1)
    }

    override fun setBackground(color: String?) {
        val view = mConsoleView?.get()
        check(view != null) { ScriptException("consoleView is null") }
        view.setBackgroundColor(color!!.toColorInt())
    }

    override fun setLogSize(size: Int) {
        mConsoleView?.get().let {
            check(it != null) { ScriptException("consoleView is null") }
            it.setLogSize(size)
        }
    }

    override fun setCanInput(can: Boolean) {
        val view = mConsoleView?.get()
        check(view != null) { ScriptException("consoleView is null") }
        if (can) {
            view.showEditText()
        } else {
            view.hideEditText()
        }
    }

    public override fun write(level: Int, charSequence: CharSequence) {
        println(level, charSequence)
    }


    override fun clear() {
        synchronized(allLogs) {
            allLogs.clear()
        }
        if (mLogListener != null && mLogListener!!.get() != null) {
            mLogListener!!.get()!!.onLogClear()
        }
    }

    override fun show(isAutoHide: Boolean) {
        setAutoHide(isAutoHide)
        if (floatyWindow.isShown) {
            return
        }
        if (!FloatingPermission.canDrawOverlays(mUiHandler.context)) {
            FloatingPermission.manageDrawOverlays(mUiHandler.context)
            mUiHandler.toast(R.string.text_no_floating_window_permission)
            return
        }
        startFloatyService()
        floatyWindow.show()
    }

    override fun show() {
        show(false)
    }

    private fun startFloatyService() {
        val context = mUiHandler.context
        context.startService(Intent(context, FloatyService::class.java))
    }

    override fun hide() {
        floatyWindow.hide()
    }

    override fun setMaxLines(maxLines: Int) {
        this.maxLines = maxLines
    }


    fun setSize(w: Int, h: Int) {
        mUiHandler.post {
            if (floatyWindow.isShown) {
                ViewUtil.setViewMeasure(mConsoleFloaty.expandedView, w, h)
            }
        }
    }

    fun setPosition(x: Int, y: Int) {
        mUiHandler.post {
            if (floatyWindow.isShown) floatyWindow.windowBridge.updatePosition(x, y)
        }
    }

    @ScriptInterface
    fun rawInput(): String {
        if (isUiThread()) {
            throw RuntimeException("无法在ui线程执行阻塞操作: console.rawInput()")
        }
        return runBlocking {
            consoleInput = CompletableDeferred()
            if (!floatyWindow.isShown) {
                show()
            }
            withTimeout(2000) {
                do {
                    val consoleView = mConsoleView?.get()
                    if (consoleView != null) {
                        consoleView.showEditText()
                        break
                    }
                    delay(1)
                } while (true)
            }
            consoleInput.await().toString()
        }
    }

    @ScriptInterface
    fun rawInput(data: Any?, vararg param: Any?): String {
        log(data, *param)
        return rawInput()
    }

    fun submitInput(input: CharSequence): Boolean {
        return consoleInput.complete(input)
    }


    override fun error(data: Any?, vararg options: Any?) {
        var data = data
        if (data is Throwable) {
            data = getStackTrace(data as Throwable?)
        }
        if (options.isNotEmpty()) {
            val sb = StringBuilder(data?.toString() ?: "")
            val newOptions = ArrayList<Any?>()
            for (option in options) {
                if (option is Throwable) {
                    sb.append(getStackTrace(option)).append(" ")
                } else {
                    newOptions.add(option)
                }
            }
            data = sb.toString()
            if (newOptions.isEmpty()) {
                super.error(data, *newOptions.toTypedArray())
            } else {
                super.error(data)
            }
        } else {
            super.error(data, *options)
        }
    }
}
