package org.autojs.autojs.external.open

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.DialogController
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.toast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.Pref
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.log.LogActivityKt
import org.autojs.autoxjs.R
import java.io.File

/**
 * Created by Stardust on 2017/2/2.
 */
class OpenIntentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val menuDialog = DialogController(initShow = true)
        val importFileDialog = DialogController()
        val menus = mapOf(
            getString(R.string.text_edit_script) to ::editFile,
            getString(R.string.text_edit_script) + "(新编辑器)" to ::editFile2,
            getString(R.string.text_import_script) to { importFileDialog.show() },
            getString(R.string.text_run_script) to ::runFile,
        )
        val uri = intent.data ?: return finish()
        val fileName = File(uri.path!!).name
        setContent {
            val scope = rememberCoroutineScope()
            AppTheme {
                importFileDialog.ImportFileDialog(uri = uri)
                menuDialog.BaseDialog(onDismissRequest = {
                    menuDialog.dismiss();finish()
                }, title = { DialogTitle(title = fileName) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                        for ((text, action) in menus) {
                            Row(modifier.clickable {
                                scope.launch { menuDialog.dismiss() }
                                action(uri)
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = text)
                            }
                        }
                    }
                }
            }
        }

    }

    private fun editFile(file: Uri) {
        val path = file.path!!
        if (file.scheme == "file" && File(path).isFile()) {
            EditActivity.editFile(this, path, false)
        } else {
            EditActivity.editFile(this, file, false)
        }
        finish()
    }

    private fun editFile2(file: Uri) {
        val path = file.path!!
        if (file.scheme == "file" && File(path).isFile()) {
            com.aiselp.autojs.codeeditor.EditActivity.editFile(this, File(path))
        } else
            toast(context = this, R.string.edit_and_run_handle_intent_error)
        finish()
    }

    @Composable
    private fun DialogController.ImportFileDialog(uri: Uri) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val scriptDirPath = Pref.getScriptDirPath()
        var fileName by remember { mutableStateOf(File(uri.path!!).name) }
        fun onPositiveClick() = scope.launch(Dispatchers.IO) {
            runCatching {
                val newFile = File(scriptDirPath, fileName)
                check(!newFile.exists()) { getString(R.string.text_file_exists) }
                when (uri.scheme) {
                    "file" -> {
                        File(uri.path!!).copyTo(newFile, overwrite = false)
                    }

                    "content" -> {
                        context.contentResolver.openInputStream(uri)
                            .use {
                                check(it != null) { "importFile failed" }
                                newFile.outputStream().use { out ->
                                    it.copyTo(out)
                                }
                            }
                    }

                    else -> {
                        cancel(getString(R.string.edit_and_run_handle_intent_error))
                    }
                }
            }.onSuccess {
                scope.launch { toast(context, R.string.text_import_succeed) }
            }.onFailure {
                scope.launch { toast(context = context, it.message ?: "unknown error") }
            }
            finish()
        }

        BaseDialog(onDismissRequest = { scope.launch { dismiss();finish() } }, title = {
            DialogTitle(title = stringResource(R.string.text_name))
        }, positiveText = stringResource(R.string.ok), onPositiveClick = {
            onPositiveClick()
        }) {
            TextField(
                value = fileName, onValueChange = { fileName = it },
                singleLine = true
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runFile(file: Uri) {
        GlobalScope.launch(Dispatchers.Main) {
            Log.d("OpenIntentActivity", "runFile: $file")
            when (file.scheme) {
                "file" -> {
                    EngineController.runScript(File(file.path!!))
                }

                "content" -> withContext(Dispatchers.IO) {
                    val name = File(file.path!!).name
                    val script = File(cacheDir, "script-cache/$name")
                    script.parentFile?.mkdirs()
                    this@OpenIntentActivity.contentResolver.openInputStream(file)
                        .use {
                            check(it != null) { "runFile failed" }
                            script.outputStream().use { out ->
                                it.copyTo(out)
                            }
                        }
                    EngineController.runScript(script)
                }

                else -> throw IllegalArgumentException("unknown scheme: ${file.scheme}")
            }
            LogActivityKt.start(this@OpenIntentActivity)
            finish()
        }
    }

}