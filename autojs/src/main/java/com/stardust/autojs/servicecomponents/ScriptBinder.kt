package com.stardust.autojs.servicecomponents

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.stardust.autojs.AutoJs
import com.stardust.autojs.IndependentScriptService
import com.stardust.autojs.core.shizuku.ShizukuClient
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.script.ScriptFile
import com.stardust.autojs.script.ScriptSource
import com.stardust.notification.NotificationListenerService
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference

class ScriptBinder(service: IndependentScriptService, val scope: CoroutineScope) : Binder() {
    val wService = WeakReference(service)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        runBlocking {
            data.enforceInterface(DESCRIPTOR)
            Log.d(TAG, "action id = $code")
            when (code) {
                Action.GET_ALL_TASKS.id -> getAllScriptTasks(data, reply!!)
                Action.RUN_SCRIPT.id -> runScript(data)
                Action.STOP_SCRIPT.id -> stopScript(data)
                Action.STOP_ALL_SCRIPT.id -> stopAllScript()
                Action.REGISTER_GLOBAL_SCRIPT_LISTENER.id -> registerGlobalScriptListener(data)
                Action.REGISTER_GLOBAL_CONSOLE_LISTENER.id -> registerGlobalConsoleListener(data)
                Action.NOTIFICATION_LISTENER_SERVICE_STATUS.id -> notificationListenerServiceStatus(
                    reply!!
                )

                Action.BIND_SHIZUKU_SERVICE.id -> bindShizukuUserService()
                else -> Log.w(TAG, "unknown action id = $code")
            }
            Log.d(TAG, "action id = $code, complete")
            return@runBlocking true
        }

    private fun getAllScriptTasks(data: Parcel, reply: Parcel) {
        val scriptExecutions = AutoJs.instance.scriptEngineService.scriptExecutions
        val bundle = Bundle().apply { putInt("size", scriptExecutions.size) }
        for ((i: Int, scriptExecution) in scriptExecutions.withIndex()) {
            bundle.putBundle(
                i.toString(),
                TaskInfo.ExecutionTaskInfo(scriptExecution).toBundle()
            )
        }
        reply.writeNoException()
        reply.writeBundle(bundle)
    }

    private fun runScript(data: Parcel) {
        val bundle = data.readBundle(ClassLoader.getSystemClassLoader())
        check(bundle != null) { "bundle is null" }
        val taskInfo = bundle.getBundle(TaskInfo.TAG)!!.let {
            TaskInfo.fromBundle(it)
        }
        val listener = bundle.getBinder(BinderScriptListener.TAG)?.let {
            BinderScriptListener.ServerInterface(it)
        }
        val config = bundle.getString(ExecutionConfig.tag)?.let {
            ExecutionConfig.fromJson(it)
        }
        Log.d(TAG, "engineName = ${taskInfo.engineName}")
        val source: ScriptSource = ScriptFile(taskInfo.sourcePath).toSource()
        AutoJs.instance.scriptEngineService.execute(
            source, listener,
            config ?: ExecutionConfig(workingDirectory = taskInfo.workerDirectory)
        )
    }

    private fun stopScript(data: Parcel) {
        val id = data.readInt()
        check(id >= 0) { "invalid id" }
        val scriptExecutions = AutoJs.instance.scriptEngineService.scriptExecutions
        for (scriptExecution in scriptExecutions) {
            if (scriptExecution.id == id) {
                scriptExecution.engine.forceStop()
                break
            }
        }
    }

    private fun stopAllScript() = AutoJs.instance.scriptEngineService.stopAll()

    private fun registerGlobalScriptListener(data: Parcel) {
        val binder = data.readStrongBinder()
        val listener = BinderScriptListener.ServerInterface(binder)
        AutoJs.instance.scriptEngineService.registerGlobalScriptExecutionListener(listener)
    }

    private fun registerGlobalConsoleListener(data: Parcel) {
        val binder = data.readStrongBinder()
        val listener = BinderConsoleListener.ServerInterface(binder)
        val sub = AutoJs.instance.globalConsole.logPublish
            .observeOn(Schedulers.single()).subscribe(listener::onPrintln)
    }

    private fun notificationListenerServiceStatus(reply: Parcel) {
        reply.writeNoException()
        reply.writeInt(if (NotificationListenerService.instance != null) 1 else 0)
    }

    private fun bindShizukuUserService() {
        if (ShizukuClient.instance.shizukuConnection.service == null) {
            ShizukuClient.instance.bindService()
        }
    }

    enum class Action(val id: Int) {
        START(1),
        STOP(2),
        GET_ALL_TASKS(3),
        RUN_SCRIPT(4),
        STOP_SCRIPT(5),
        STOP_ALL_SCRIPT(6),
        REGISTER_GLOBAL_SCRIPT_LISTENER(7),
        REGISTER_GLOBAL_CONSOLE_LISTENER(8),
        NOTIFICATION_LISTENER_SERVICE_STATUS(9),
        BIND_SHIZUKU_SERVICE(10);
    }

    companion object {
        const val TAG = "ScriptBinder"
        const val DESCRIPTOR = "com.stardust.autojs.servicecomponents.ScriptBinder"
        suspend fun <T> connect(binder: IBinder, n: suspend TanBinder.() -> T): T {
            val d = Parcel.obtain().apply { writeInterfaceToken(DESCRIPTOR) }
            val r = Parcel.obtain()
            try {
                return TanBinder(binder, data = d, reply = r).n()
            } finally {
                d.recycle()
                r.recycle()
            }
        }
    }
}