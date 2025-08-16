package com.aiselp.autox.ui.material3.components

import android.content.Context
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiselp.autox.DataStoreKey
import com.aiselp.autox.utils.dataStore
import com.stardust.toast
import com.stardust.util.ClipboardUtil
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.autojs.autojs.ui.main.drawer.DrawerViewModel
import org.autojs.autoxjs.R
import org.joda.time.DateTimeZone
import org.joda.time.Instant


@Composable
fun DialogController.UpdateDialog(
    model: DrawerViewModel = viewModel(),
    autoUpdate: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BaseDialog(
        onDismissRequest = { dismiss() },
        title = {
            DialogTitle(
                title = stringResource(
                    R.string.text_new_version2,
                    model.githubReleaseInfo!!.name
                )
            )
        },
        positiveText = stringResource(id = R.string.text_download),
        onPositiveClick = {
            dismiss();model.downloadApk()
        },
        negativeText = stringResource(id = R.string.cancel),
        onNegativeClick = { dismiss() },
        neutralText = stringResource(R.string.text_copy_link),
        onNeutralClick = {
            ClipboardUtil.setClip(context, model.getApkNameAndDownloadLink().second)
            dismiss()
            toast(context, R.string.text_copy_successfully)
        }
    ) {
        val date = rememberSaveable {
            Instant.parse(model.githubReleaseInfo!!.createdAt)
                .toDateTime(DateTimeZone.getDefault())
                .toString("yyyy-MM-dd HH:mm:ss")
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(id = R.string.text_release_date, date)
            )
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        val content =
                            model.githubReleaseInfo!!.body.trim().replace("\r\n", "\n")
                                .replace("\n", "  \n")
                        val markdwon = Markwon.builder(context).build()
                        markdwon.setMarkdown(this, content)
                    }
                }
            )
            if (autoUpdate) {
                Row {
                    var checked by remember { mutableStateOf(false) }
                    CheckboxOption(checked = checked, onCheckedChange = {
                        checked = it
                        val name = if (it) {
                            model.githubReleaseInfo!!.name
                        } else {
                            ""
                        }
                        scope.launch {
                            context.dataStore.edit {
                                it[DataStoreKey.ignore_update] = name
                            }
                        }
                    }, name = stringResource(R.string.ui_ignore_update))
                }
            }
        }
    }
}

suspend fun isIgnoreUpdate(context: Context, versionName: String): Boolean {
    return context.dataStore.data.first()[DataStoreKey.ignore_update] == versionName
}

