package org.autojs.autojs.external.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stardust.app.GlobalAppContext.toast
import com.stardust.autojs.execution.ExecutionConfig
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTaskManager.getIntentTaskOfAction

open class BaseBroadcastReceiver : BroadcastReceiver() {
    @SuppressLint("CheckResult")
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOG_TAG, "onReceive: intent = $intent, this = $this")
        try {
            getIntentTaskOfAction(intent.action)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ intentTask: IntentTask? ->
                    runTask(
                        context, intent, intentTask!!
                    )
                }, { obj: Throwable -> obj.printStackTrace() })
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    companion object {
        private const val LOG_TAG = "BaseBroadcastReceiver"

        fun runTask(context: Context, intent: Intent, task: IntentTask) {
            Log.d(
                LOG_TAG,
                "runTask: action = " + intent.action + ", script = " + task.scriptPath
            )
            val file = ScriptFile(task.scriptPath!!)
            val config = ExecutionConfig()
            config.setArgument("intent", intent.clone())
            config.workingDirectory = file.getParent() ?: "/"
            try {
                AutoJs.getInstance().scriptEngineService.execute(file.toSource(), config)
            } catch (e: Exception) {
                e.printStackTrace()
                toast(e.message)
            }
        }
    }
}
