package org.autojs.autojs.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.aiselp.autox.ui.material3.components.AlertDialog
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.ComposeDialog
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.google.android.material.snackbar.Snackbar
import com.stardust.app.DialogUtils
import com.stardust.app.GlobalAppContext.post
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.pio.PFile
import com.stardust.pio.PFiles.copy
import com.stardust.pio.PFiles.createIfNotExists
import com.stardust.pio.PFiles.deleteRecursively
import com.stardust.pio.PFiles.getExtension
import com.stardust.pio.PFiles.getNameWithoutExtension
import com.stardust.pio.PFiles.write
import com.stardust.pio.UncheckedIOException
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.internal.functions.ObjectHelper
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.Pref
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts.edit
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.shortcut.ShortcutCreateActivity
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity
import org.autojs.autoxjs.R
import java.io.File
import java.io.IOException

/**
 * Created by Stardust on 2017/7/31.
 */
@SuppressLint("CheckResult")
class ScriptOperations {
    private val mExplorerPage: ExplorerPage
    private val mContext: Context
    private val mView: View?
    private val currentDirectory: ScriptFile?
    private val mExplorer: Explorer

    @JvmOverloads
    constructor(
        context: Context,
        view: View?,
        currentDirectory: ScriptFile? = ScriptFile(Pref.getScriptDirPath())
    ) {
        mContext = context
        mView = view
        this.currentDirectory = currentDirectory
        mExplorer = Explorers.workspace()
        mExplorerPage = ExplorerDirPage(currentDirectory, null)
    }

    constructor(context: Context, view: View?, page: ExplorerPage?) {
        mContext = context
        mView = view
        check(page != null)
        this.currentDirectory = page.toScriptFile()
        mExplorer = Explorers.workspace()
        mExplorerPage = page
    }

    fun newScriptFileForScript(script: String?) {
        showFileNameInputDialog("", "js")
            .subscribe(Consumer { input: String? ->
                createScriptFile(
                    this.currentDirectoryPath + input + ".js", script, false
                )
            })
    }

    private val currentDirectoryPath: String
        get() = this.currentDirectory!!.path + "/"

    fun createScriptFile(path: String, script: String?, edit: Boolean) {
        if (createIfNotExists(path)) {
            if (script != null) {
                try {
                    write(path, script)
                } catch (e: UncheckedIOException) {
                    showMessage(R.string.text_file_write_fail)
                    return
                }
            }
            notifyFileCreated(this.currentDirectory, ScriptFile(path))
            if (edit) edit(mContext, path)
        } else {
            showMessage(R.string.text_create_fail)
        }
    }

    private fun notifyFileCreated(directory: ScriptFile?, scriptFile: ScriptFile) {
        if (scriptFile.isDirectory()) {
            mExplorer.notifyItemCreated(ExplorerDirPage(scriptFile, mExplorerPage))
        } else {
            mExplorer.notifyItemCreated(ExplorerFileItem(scriptFile, mExplorerPage))
        }
    }

    fun importFile(pathFrom: String): Observable<String?>? {
        return showFileNameInputDialog(getNameWithoutExtension(pathFrom), getExtension(pathFrom))
            .observeOn(Schedulers.io())
            .map({ input: String? ->
                val pathTo = this.currentDirectoryPath + input + "." + getExtension(pathFrom)
                if (copy(pathFrom, pathTo)) {
                    showMessage(R.string.text_import_succeed)
                } else {
                    showMessage(R.string.text_import_fail)
                }
                pathTo
            })
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(Consumer { path: String? ->
                notifyFileCreated(
                    this.currentDirectory, ScriptFile(
                        path!!
                    )
                )
            })
    }


    private fun showMessage(resId: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showMessageWithoutThreadSwitch(resId)
        }
        //switch to ui thread to show message
        post { showMessageWithoutThreadSwitch(resId) }
    }

    private fun showMessageWithoutThreadSwitch(resId: Int) {
        if (mView != null) {
            Snackbar.make(mView, resId, Snackbar.LENGTH_SHORT).show()
        } else {
            Toast.makeText(mContext, resId, Toast.LENGTH_SHORT).show()
        }
    }


    private fun showFileNameInputDialog(prefix: String?, ext: String?): Observable<String> {
        return showNameInputDialog(prefix)
    }

    private fun showNameInputDialog(
        prefix: String?,
    ): Observable<String> {
        val input = PublishSubject.create<String>()
        val dialog = ComposeDialog(mContext) {
            val state = remember { mutableStateOf(prefix ?: "") }
            BaseDialog(
                title = { DialogTitle(stringResource(R.string.text_name)) },
                positiveText = stringResource(R.string.ok),
                onPositiveClick = {
                    dismiss()
                    input.onNext(state.value)
                    input.onComplete()
                },
                negativeText = stringResource(R.string.cancel),
                onNegativeClick = { dismiss() }
            ) {
                TextField(
                    value = state.value,
                    onValueChange = { state.value = it },
                    label = { Text(stringResource(R.string.text_please_input_name)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        }
        DialogUtils.showDialog(
            dialog
        )
        return input
    }


    fun rename(item: ExplorerFileItem) {
        val originalName = item.name
        showNameInputDialog(originalName)
            .subscribe({ newName: String? ->
                val newItem = item.rename(newName)
                if (ObjectHelper.equals(newItem.toScriptFile(), item.toScriptFile())) {
                    showMessage(R.string.error_cannot_rename)
                    throw IOException()
                }
                notifyFileChanged(this.currentDirectory, item, newItem)
                newItem
            })
    }

    private fun notifyFileChanged(
        directory: ScriptFile?,
        oldItem: ExplorerFileItem?,
        newItem: ExplorerFileItem?
    ) {
        mExplorer.notifyItemChanged(oldItem, newItem)
    }

    fun createShortcut(file: ScriptFile?) {
        mContext.startActivity(
            Intent(mContext, ShortcutCreateActivity::class.java)
                .putExtra(ShortcutCreateActivity.EXTRA_FILE, file)
        )
    }

    fun delete(scriptFile: ScriptFile, onDelete: () -> Unit = {}) {
        val dialog = ComposeDialog(mContext) {
            AlertDialog(
                title = stringResource(R.string.text_hint),
                content = stringResource(
                    R.string.text_are_you_sure_to_delete, scriptFile.getName()
                ),
                positiveText = stringResource(R.string.ok),
                onPositiveClick = { dismiss();deleteWithoutConfirm(scriptFile);onDelete() },
                negativeText = stringResource(R.string.cancel),
                onNegativeClick = { dismiss() }
            )
        }
        DialogUtils.showDialog(dialog)
    }

    fun deleteWithoutConfirm(scriptFile: ScriptFile) {
        val isDir = scriptFile.isDirectory()
        val f = File(scriptFile.absolutePath)
        EngineController.scope.launch(Dispatchers.IO) {
            val deleted = deleteRecursively(f)
            withContext(Dispatchers.Main) {
                showMessage(if (deleted) R.string.text_already_delete else R.string.text_delete_failed)
                if (deleted) notifyFileRemoved(isDir, scriptFile)
            }
        }
    }

    private fun notifyFileRemoved(isDir: Boolean, scriptFile: ScriptFile?) {
        if (isDir) {
            mExplorer.notifyItemRemoved(ExplorerDirPage(scriptFile, mExplorerPage))
        } else {
            mExplorer.notifyItemRemoved(ExplorerFileItem(scriptFile, mExplorerPage))
        }
    }


    fun importFile() {
        FileChooserDialogBuilder(mContext, mContext.getString(R.string.text_select_file_to_import))
            .dir(Environment.getExternalStorageDirectory().path)
            .justScriptFile()
            .singleChoice { file: PFile? -> importFile(file!!.getPath())!!.subscribe() }
            .show()
    }

    fun timedTask(scriptFile: ScriptFile) {
        val intent = Intent(mContext, TimedTaskSettingActivity::class.java)
        intent.putExtra(ScriptIntents.EXTRA_KEY_PATH, scriptFile.getPath())
        mContext.startActivity(intent)
    }


    companion object {
        private const val LOG_TAG = "ScriptOperations"
    }
}
