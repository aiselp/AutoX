package org.autojs.autojs.ui.common

import android.content.Context
import android.view.View
import android.widget.EditText
import com.afollestad.materialdialogs.MaterialDialog
import com.stardust.app.DialogUtils
import com.stardust.app.GlobalAppContext.toast
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.servicecomponents.EngineController
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autoxjs.R
import org.autojs.autoxjs.databinding.DialogScriptLoopBinding

/**
 * Created by Stardust on 2017/7/8.
 */
class ScriptLoopDialog(context: Context?, private val mScriptFile: ScriptFile) {
    private val mDialog: MaterialDialog

    private val bind = kotlin.run {
        val view = View.inflate(context!!, R.layout.dialog_script_loop, null)
        DialogScriptLoopBinding.bind(view)
    }

    private val mLoopTimes: EditText = bind.loopTimes
    private val mLoopInterval: EditText = bind.loopInterval
    private val mLoopDelay: EditText = bind.loopDelay


    init {
        mDialog = MaterialDialog.Builder(context!!)
            .title(R.string.text_run_repeatedly)
            .customView(bind.root, true)
            .positiveText(R.string.ok)
            .onPositive { _, _ -> startScriptRunningLoop() }
            .build()
    }

    private fun startScriptRunningLoop() {
        try {
            val loopTimes = mLoopTimes.text.toString().toInt()
            val loopInterval = mLoopInterval.text.toString().toFloat()
            val loopDelay = mLoopDelay.text.toString().toFloat()
            EngineController.runScript(
                mScriptFile, null, ExecutionConfig(
                    workingDirectory = mScriptFile.parent ?: "/",
                    delay = (1000L * loopDelay).toLong(),
                    loopTimes = loopTimes,
                    interval = (loopInterval * 1000L).toLong()
                )
            )
        } catch (e: NumberFormatException) {
            toast(R.string.text_number_format_error)
        }
    }

    fun windowType(windowType: Int): ScriptLoopDialog {
        mDialog.window?.setType(windowType)
        return this
    }

    fun show() {
        DialogUtils.showDialog(mDialog)
    }
}
