package com.stardust.autojs.core.console

import android.util.Log
import com.stardust.util.UiHandler
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by Stardust on 2017/10/22.
 */
open class GlobalConsole(uiHandler: UiHandler) : ConsoleImpl(uiHandler) {

    /** 日志同时写入磁盘，这样应用或脚本进程被杀死后仍能找回日志。 */
    private val logStore: ConsoleLogStore by lazy { ConsoleLogStore.forContext(uiHandler.context) }

    override fun println(level: Int, charSequence: CharSequence?): String? {
        val log = "${DATE_FORMAT.format(Date())}/${getLevelChar(level)}: $charSequence"
        Log.d(TAG, log)
        super.println(level, log)
        logStore.append(level, log)
        return log
    }

    override fun clear() {
        super.clear()
        logStore.clear()
    }

    /**
     * 把磁盘上保存的上一次运行的日志恢复到控制台。
     */
    fun restorePersistedHistory() {
        restoreHistory(logStore)
    }

    fun createObservable(): Observable<String> {
        return logPublish.observeOn(Schedulers.single()).map { it.content.toString() }
    }

    private fun getLevelChar(level: Int): String {
        return when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> ""
        }
    }

    companion object {
        private const val TAG = "GlobalConsole"
        private val DATE_FORMAT
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

}
