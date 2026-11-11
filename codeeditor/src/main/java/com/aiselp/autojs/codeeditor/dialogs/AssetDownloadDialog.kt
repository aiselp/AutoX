package com.aiselp.autojs.codeeditor.dialogs

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.aiselp.autojs.codeeditor.R
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.DialogController
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.stardust.util.IntentUtil
import kotlinx.coroutines.launch

open class AssetDownloadDialog() : DialogController() {
    override val properties = DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false
    )

    @Composable
    fun Dialog() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        BaseDialog(
            onDismissRequest = { scope.launch { dismiss() } },
            title = { DialogTitle(title = stringResource(R.string.asset_download)) },
            positiveText = stringResource(R.string.go_download),
            onPositiveClick = { IntentUtil.browse(context, assetDownloadUri) },
            negativeText = stringResource(R.string.exit),
            onNegativeClick = { (context as Activity).finish() },
            neutralText = stringResource(R.string.import_asset),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.asset_download_desc))
            }
        }
    }

    companion object {
        private const val assetDownloadUri = "https://github.com/aiselp/vscode-mobile/releases"
    }
}