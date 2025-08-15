package org.autojs.autojs.ui.filechooser

import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.ComposeDialog
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.stardust.pio.PFile
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileProvider
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.Scripts.FILE_FILTER
import org.autojs.autoxjs.R
import java.io.File
import java.io.FileFilter

/**
 * Created by Stardust on 2017/10/19.
 */
class FileChooserDialogBuilder(context: Context, title: String) {
    fun interface SingleChoiceCallback {
        fun onSelected(file: PFile)
    }

    fun interface MultiChoiceCallback {
        fun onSelected(files: MutableList<PFile>)
    }

    private val mFileChooseListView = FileChooseListView(context)
    private var mCallback: MultiChoiceCallback? = null
    private var mFileFilter: FileFilter? = null
    private var mRootDir: String? = null
    private var mInitialDir: String? = null

    val dialog = ComposeDialog(context) {
        BaseDialog(
            title = { DialogTitle(title = title) },
            positiveText = stringResource(R.string.ok),
            onPositiveClick = { dismiss();notifySelected() },
            negativeText = stringResource(R.string.cancel),
            onNegativeClick = { dismiss() }
        ) {
            AndroidView(
                factory = { mFileChooseListView }
            )
        }
    }


    private fun notifySelected() {
        if (mCallback == null) return
        val selectedFiles = mFileChooseListView.selectedFiles
        if (selectedFiles.isEmpty()) {
            mCallback!!.onSelected(mutableListOf(mFileChooseListView.currentDirectory!!))
        } else {
            mCallback!!.onSelected(selectedFiles as MutableList<PFile>)
        }
    }

    fun dir(rootDir: String?, initialDir: String?): FileChooserDialogBuilder {
        mRootDir = rootDir
        mInitialDir = initialDir
        return this
    }

    fun dir(dir: String?): FileChooserDialogBuilder {
        mRootDir = dir
        return this
    }

    fun justScriptFile(): FileChooserDialogBuilder {
        mFileFilter = FILE_FILTER
        return this
    }


    fun chooseDir(): FileChooserDialogBuilder {
        mFileFilter = FileFilter { obj: File? -> obj!!.isDirectory() }
        mFileChooseListView.setCanChooseDir(true)
        return this
    }

    fun singleChoice(callback: SingleChoiceCallback): FileChooserDialogBuilder {
        mFileChooseListView.setMaxChoice(1)
        mCallback =
            MultiChoiceCallback { files -> callback.onSelected(files[0]) }
        return this
    }


    fun singleChoice(): Observable<PFile?> {
        val result = PublishSubject.create<PFile?>()
        singleChoice { file ->
            result.onNext(file)
            result.onComplete()
        }
        dialog.show()
        return result
    }

    fun show() {
        build().show()
    }

    fun build(): ComposeDialog {
        val root = ExplorerDirPage.createRoot(mRootDir)
        val explorer = if (mFileFilter == null) Explorers.external() else Explorer(
            ExplorerFileProvider(mFileFilter), 0
        )
        if (mInitialDir == null) {
            mFileChooseListView.setExplorer(explorer, root)
        } else {
            mFileChooseListView.setExplorer(
                explorer, root,
                ExplorerDirPage(mInitialDir, root)
            )
        }
        return dialog
    }
}
