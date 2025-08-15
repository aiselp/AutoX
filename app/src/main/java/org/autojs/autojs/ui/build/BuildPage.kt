package org.autojs.autojs.ui.build

import android.content.Context
import android.os.Environment
import com.stardust.pio.PFile
import org.autojs.autojs.Pref
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autoxjs.R
import java.io.File


fun selectSourceFilePath(context: Context, scriptPath: String, onResult: (File) -> Unit) {
    val initialDir = File(scriptPath).parent
    FileChooserDialogBuilder(context, title = context.getString(R.string.text_source_file_path))
        .dir(
            Environment.getExternalStorageDirectory().path,
            initialDir ?: Pref.getScriptDirPath()
        )
        .singleChoice { file: PFile -> onResult(file) }
        .show()
}

fun selectOutputDirPath(context: Context, outputPath: String, onResult: (File) -> Unit) {
    val initialDir =
        if (File(outputPath).exists()) outputPath else Pref.getScriptDirPath()
    FileChooserDialogBuilder(context, context.getString(R.string.text_output_apk_path))
        .dir(initialDir)
        .chooseDir()
        .singleChoice { dir: PFile -> onResult(dir) }
        .show()
}

