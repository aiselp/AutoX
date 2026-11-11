package com.aiselp.autojs.codeeditor.plugins

import android.app.Activity
import com.aiselp.autojs.codeeditor.EditActivity
import com.aiselp.autojs.codeeditor.web.PluginManager
import com.aiselp.autojs.codeeditor.web.annotation.WebFunction
import com.stardust.autojs.servicecomponents.BinderScriptListener
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.autojs.servicecomponents.TaskInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class AppController(
    val activity: Activity,
    val editorModel: EditActivity.EditorModel,
    val coroutineScope: CoroutineScope
) {
    companion object {
        const val TAG = "AppController"
    }


    @WebFunction
    fun exit(call: PluginManager.WebCall) {
        call.onSuccess(null)
        activity.finish()
    }

    @WebFunction
    fun back(call: PluginManager.WebCall) {
        call.onSuccess(null)
        coroutineScope.launch(Dispatchers.Main) {
            activity.moveTaskToBack(false)
        }
    }

    @WebFunction
    fun runScript(call: PluginManager.WebCall) {
        val path = FileSystem.parsePath(call.data!!)
        coroutineScope.launch(Dispatchers.Main) {
            editorModel.showLog = true
            editorModel.running = true
            editorModel.lastScriptFile = path
        }
        EngineController.runScript(path, listener = object : BinderScriptListener {
            override fun onStart(taskInfo: TaskInfo) {
                coroutineScope.launch(Dispatchers.Main) { editorModel.running = true }
            }

            override fun onSuccess(taskInfo: TaskInfo) {
                coroutineScope.launch(Dispatchers.Main) { editorModel.running = false }
                call.onSuccess(null)
            }

            override fun onException(taskInfo: TaskInfo, e: Throwable) {
                coroutineScope.launch(Dispatchers.Main) { editorModel.running = false }
                call.onError(Exception(e))
            }

        })
    }
}