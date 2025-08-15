package org.autojs.autojs.ui.common

import android.app.Dialog
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.ComposeDialog
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.stardust.app.DialogUtils
import com.stardust.app.GlobalAppContext.toast
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.servicecomponents.EngineController
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autoxjs.R

/**
 * Created by Stardust on 2017/7/8.
 */
class ScriptLoopDialog(context: Context, private val mScriptFile: ScriptFile) {
    private val dialog: Dialog

    private val mLoopTimes = mutableStateOf("1")
    private val mLoopInterval = mutableStateOf("1.0")
    private val mLoopDelay = mutableStateOf("0.0")


    init {
        dialog = ComposeDialog(context) {
            BaseDialog(
                title = { DialogTitle(stringResource(R.string.text_run_repeatedly)) },
                positiveText = stringResource(R.string.ok),
                onPositiveClick = { dismiss();startScriptRunningLoop() }
            ) {
                Column {
                    TextField(
                        value = mLoopTimes.value,
                        label = {
                            Text(
                                stringResource(R.string.text_loop_times) + "-(${
                                    stringResource(R.string.hint_loop_times)
                                })"
                            )
                        },
                        onValueChange = { mLoopTimes.value = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = mLoopInterval.value,
                        label = { Text(stringResource(R.string.text_loop_interval)) },
                        onValueChange = { mLoopInterval.value = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = mLoopDelay.value,
                        label = {
                            Text(
                                stringResource(R.string.text_loop_delay) + "-(${
                                    stringResource(R.string.hint_loop_delay)
                                })"
                            )
                        },
                        onValueChange = { mLoopDelay.value = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }
    }

    private fun startScriptRunningLoop() {
        try {
            val loopTimes = mLoopTimes.value.toInt()
            val loopInterval = mLoopInterval.value.toFloat()
            val loopDelay = mLoopDelay.value.toFloat()
            EngineController.runScript(
                mScriptFile, null, ExecutionConfig(
                    workingDirectory = mScriptFile.parent ?: "/",
                    delay = (1000L * loopDelay).toLong(),
                    loopTimes = loopTimes,
                    interval = (loopInterval * 1000L).toLong()
                )
            )
        } catch (_: NumberFormatException) {
            toast(R.string.text_number_format_error)
        }
    }

    fun show() {
        DialogUtils.showDialog(dialog)
    }
}
