package com.stardust.autojs.servicecomponents

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.IndependentScriptService
import com.stardust.autojs.core.console.ConsoleImpl
import com.stardust.autojs.core.console.LogEntry
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.util.UiHandler
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ScriptServiceConnection : ServiceConnection {
    val binderConsoleListener = BinderConsoleListener.ClientInterface()
    var binding: CompletableJob? = null
    var service: IBinder? = null
    var application: Context = GlobalAppContext.get()
    private val connected = Job()
    val consoleImpl: ConsoleImpl =
        object : ConsoleImpl(UiHandler(application)), BinderConsoleListener {
            override fun onPrintln(log: LogEntry) {
                println(log.level, log.content)
            }
        }.apply {
            binderConsoleListener.logPublish
                .observeOn(AndroidSchedulers.mainThread()).subscribe(::onPrintln)
        }


    @Volatile
    var isConnected = false
        private set

    @OptIn(DelicateCoroutinesApi::class)
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(TAG, "Script service connected")
        this.service = service
        isConnected = true
        binding?.complete()
        connected.complete()
        binderConsoleListener.logPublish.onNext(
            LogEntry(
                level = Log.INFO,
                content = "Script service connected"
            )
        )
        GlobalScope.launch {
            registerGlobalConsoleListener(binderConsoleListener)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        isConnected = false
        binding = null
        binderConsoleListener.logPublish.onNext(
            LogEntry(
                level = Log.ERROR,
                content = "Script service disconnected"
            )
        )
    }

    private suspend fun <T> sendBinder(n: suspend TanBinder.() -> T): T {
        Log.d("ScriptServiceConnection", "sendBinder: before awaitConnected")
        try {
            awaitConnected()
            Log.d("ScriptServiceConnection", "sendBinder: after awaitConnected, service=$service")
        } catch (e: Exception) {
            Log.e("ScriptServiceConnection", "sendBinder: awaitConnected failed", e)
            throw e
        }

        return ScriptBinder.connect(service!!, n).also {
            Log.d("ScriptServiceConnection", "sendBinder: connect completed")
        }
    }

    suspend fun getAllScriptTasks(): MutableList<TaskInfo> = sendBinder {
        action = ScriptBinder.Action.GET_ALL_TASKS.id
        send()
        reply!!.readException()
        val bundle = reply.readBundle(ClassLoader.getSystemClassLoader())
        check(bundle != null) { "bundle is null" }
        val size = bundle.getInt("size")
        val tasks = mutableListOf<TaskInfo>()
        for (i in 1..size) {
            tasks.add(TaskInfo.fromBundle(bundle.getBundle((i - 1).toString())!!))
        }
        return@sendBinder tasks
    }

    suspend fun runScript(
        taskInfo: TaskInfo,
        listener: BinderScriptListener? = null,
        config: ExecutionConfig? = null
    ) = sendBinder {
        action = ScriptBinder.Action.RUN_SCRIPT.id
        data.writeBundle(Bundle().apply {
            putBundle(TaskInfo.TAG, taskInfo.toBundle())
            if (config != null) {
                putString(ExecutionConfig.tag, ExecutionConfig.toJson(config))
            }
            if (listener != null) {
                putBinder(BinderScriptListener.TAG, listener.toBinder())
            }
        })
        send()
    }

    suspend fun getMemoryInfo(): Debug.MemoryInfo = sendBinder {
        action = ScriptBinder.Action.GET_MEMORY_INFO.id
        send()
        reply!!.readException()
        val memoryInfo = Debug.MemoryInfo.CREATOR.createFromParcel(reply)
        return@sendBinder memoryInfo
    }

    suspend fun stopAllScript() = sendBinder {
        action = ScriptBinder.Action.STOP_ALL_SCRIPT.id
        send()
    }

    suspend fun stopScript(id: Int) = sendBinder {
        action = ScriptBinder.Action.STOP_SCRIPT.id
        data.writeInt(id)
        send()
    }

    suspend fun appExit() = sendBinder {
        action = ScriptBinder.Action.APP_EXIT.id
        send()
    }

    suspend fun registerGlobalScriptListener(listener: BinderScriptListener) = sendBinder {
        action = ScriptBinder.Action.REGISTER_GLOBAL_SCRIPT_LISTENER.id
        data.writeStrongBinder(listener.toBinder())
        send()
    }

    suspend fun registerGlobalConsoleListener(listener: Binder) = sendBinder {
        action = ScriptBinder.Action.REGISTER_GLOBAL_CONSOLE_LISTENER.id
        data.writeStrongBinder(listener)
        send()
    }

    suspend fun notificationListenerServiceStatus(): Boolean = sendBinder {
        action = ScriptBinder.Action.NOTIFICATION_LISTENER_SERVICE_STATUS.id
        send()
        reply!!.readException()
        reply.readInt() == 1
    }

    suspend fun bindShizukuUserService() = sendBinder {
        action = ScriptBinder.Action.BIND_SHIZUKU_SERVICE.id
        send()
    }

    private fun isForegroundServiceEnabled(): Boolean {
        return try {
            // 方式1：通过反射调用 Pref
            val prefClass = Class.forName("org.autojs.autojs.Pref")
            val method = prefClass.getMethod("isForegroundServiceEnabled")
            method.invoke(null) as Boolean
        } catch (e: Exception) {
            // 方式2：直接读取 SharedPreferences
            val prefs = GlobalAppContext.get().getSharedPreferences("org.autojs.autojs_preferences", Context.MODE_PRIVATE)
            prefs.getBoolean("foreground_service_enabled", false)
        }
    }

    private fun ensureForegroundService(context: Context) {
        try {
            if (IndependentScriptService.isForegroundRunning) {
                Log.d(TAG, "Foreground service already running")
                return
            }

            val isEnabled = isForegroundServiceEnabled()
            Log.d(TAG, "Starting foreground service from awaitConnected")
            IndependentScriptService.startForeground(context)
            if (!isEnabled) {
                Log.d(TAG, "Stop foreground service from awaitConnected after 5 seconds")

                Handler(context.mainLooper).postDelayed({
                    try {
                        val stopIntent = Intent(context, IndependentScriptService::class.java).apply {
                            action = IndependentScriptService.ACTION_STOP_FOREGROUND
                        }
                        context.startService(stopIntent)
                    } catch (e: Exception) {
                        Log.e("FlashService", "Stop failed", e)
                    }
                }, 5000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    suspend fun awaitConnected() = withTimeout(6000) {
        Log.d(TAG, "awaitConnected: start, isConnected=$isConnected, binding=$binding")

        if (isConnected) {
            Log.d(TAG, "awaitConnected: already connected")
            return@withTimeout
        }
        if (binding == null) {
            Log.d(TAG, "awaitConnected: binding is null, calling bind")
            bind(application)
            ensureForegroundService(application)
        }

        Log.d(TAG, "awaitConnected: waiting for binding to complete, binding: $binding")
        binding!!.join()
        Log.d(TAG, "awaitConnected: binding completed")
    }

    fun bind(context: Context) {
        if (isConnected) return
        application = context.applicationContext
        context.applicationContext.bindService(
            Intent(context, IndependentScriptService::class.java),
            this,
            Context.BIND_AUTO_CREATE
        )
        binding = Job()

    }

    companion object {
        private const val TAG = "ScriptServiceConnection"
        val GlobalConnection by lazy { ScriptServiceConnection() }
    }
}