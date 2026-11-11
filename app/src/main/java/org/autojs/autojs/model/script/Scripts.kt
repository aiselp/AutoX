package org.autojs.autojs.model.script

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.execution.ScriptExecution
import com.stardust.autojs.execution.ScriptExecutionListener
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.autojs.script.ScriptSource
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.toast
import com.stardust.util.IntentUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.autojs.autojs.Pref
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autoxjs.R
import org.mozilla.javascript.RhinoException
import java.io.File
import java.io.FileFilter
import com.aiselp.autojs.codeeditor.EditActivity as EditActivity2
import org.autojs.autojs.ui.edit.EditActivity as EditActivity1

/**
 * Created by Stardust on 2017/5/3.
 */

object Scripts {

    const val ACTION_ON_EXECUTION_FINISHED = "ACTION_ON_EXECUTION_FINISHED"
    const val EXTRA_EXCEPTION_MESSAGE = "message"
    const val EXTRA_EXCEPTION_LINE_NUMBER = "lineNumber"
    const val EXTRA_EXCEPTION_COLUMN_NUMBER = "columnNumber"
    private const val TAG = "Scripts"

    val FILE_FILTER = FileFilter { file ->
        file.isDirectory || file.name.endsWith(".js")
                || file.name.endsWith(".auto")
    }

    private val BROADCAST_SENDER_SCRIPT_EXECUTION_LISTENER =
        object : ScriptExecutionListener {
            override fun onStart(execution: ScriptExecution?) {}

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                GlobalAppContext.get().sendBroadcast(Intent(ACTION_ON_EXECUTION_FINISHED))
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                val rhinoException = getRhinoException(e)
                var line = -1
                var col = 0
                if (rhinoException != null) {
                    line = rhinoException.lineNumber()
                    col = rhinoException.columnNumber()
                }
                if (ScriptInterruptedException.causedByInterrupted(e)) {
                    GlobalAppContext.get().sendBroadcast(
                        Intent(ACTION_ON_EXECUTION_FINISHED)
                            .putExtra(EXTRA_EXCEPTION_LINE_NUMBER, line)
                            .putExtra(EXTRA_EXCEPTION_COLUMN_NUMBER, col)
                    )
                } else {
                    GlobalAppContext.get().sendBroadcast(
                        Intent(ACTION_ON_EXECUTION_FINISHED)
                            .putExtra(EXTRA_EXCEPTION_MESSAGE, e.message)
                            .putExtra(EXTRA_EXCEPTION_LINE_NUMBER, line)
                            .putExtra(EXTRA_EXCEPTION_COLUMN_NUMBER, col)
                    )
                }
            }

        }


    fun openByOtherApps(uri: Uri) {
        IntentUtil.viewFile(GlobalAppContext.get(), uri, "text/plain", AppFileProvider.AUTHORITY)
    }

    fun openByOtherApps(file: File) {
        openByOtherApps(Uri.fromFile(file))
    }


    fun edit(context: Context, file: ScriptFile) {
        if (Pref.getEditor()) {
            EditActivity2.editFile(context, file)
        } else {
            EditActivity1.editFile(context, file.simplifiedName, file.path, false)
        }
    }

    fun edit(context: Context, path: String) {
        edit(context, ScriptFile(path))
    }

    @JvmOverloads
    fun run(
        file: File,
        config: ExecutionConfig = ExecutionConfig(workingDirectory = file.parent)
    ): ScriptExecution? {
        return try {
            AutoJs.getInstance().scriptEngineService.execute(
                ScriptFile(file).toSource(), config
            )
        } catch (e: Exception) {
            Log.e(TAG, e.stackTraceToString())
            EngineController.scope.launch(Dispatchers.Main) {
                toast(GlobalAppContext.get(), e.message)
            }
            null
        }

    }


    fun run(source: ScriptSource): ScriptExecution? {
        return try {
            AutoJs.getInstance().scriptEngineService.execute(
                source,
                ExecutionConfig(workingDirectory = Pref.getScriptDirPath())
            )
        } catch (e: Exception) {
            Log.e(TAG, e.stackTraceToString())
            EngineController.scope.launch(Dispatchers.Main) {
                toast(GlobalAppContext.get(), e.message)
            }
            null
        }

    }

    fun runWithBroadcastSender(file: File): ScriptExecution {
        return AutoJs.getInstance().scriptEngineService.execute(
            ScriptFile(file).toSource(), BROADCAST_SENDER_SCRIPT_EXECUTION_LISTENER,
            ExecutionConfig(workingDirectory = file.parent)
        )
    }


    fun runRepeatedly(
        scriptFile: ScriptFile,
        loopTimes: Int,
        delay: Long,
        interval: Long
    ): ScriptExecution {
        val source = scriptFile.toSource()
        val directoryPath = scriptFile.parent
        return AutoJs.getInstance().scriptEngineService.execute(
            source, ExecutionConfig(
                workingDirectory = directoryPath,
                delay = delay, loopTimes = loopTimes, interval = interval
            )
        )
    }

    fun getRhinoException(throwable: Throwable?): RhinoException? {
        var e = throwable
        while (e != null) {
            if (e is RhinoException) {
                return e
            }
            e = e.cause
        }
        return null
    }

    fun send(file: ScriptFile) {
        val context = GlobalAppContext.get()
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(
                        Intent.EXTRA_STREAM,
                        IntentUtil.getUriOfFile(context, file.path, AppFileProvider.AUTHORITY)
                    ),
                GlobalAppContext.getString(R.string.text_send)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

    }
}
