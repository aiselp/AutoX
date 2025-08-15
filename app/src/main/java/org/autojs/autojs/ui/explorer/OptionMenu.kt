package org.autojs.autojs.ui.explorer

import android.util.Log
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerSampleItem
import org.autojs.autoxjs.R


@Composable
fun ExplorerViewKt.OptionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    explorerItem: ExplorerItem,
    onMenuSelect: (OptionMenu) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val menus = OptionMenu.entries.toMutableSet()
        explorerItem.let {
            Log.i("ExplorerViewKt", "OptionMenu: ${it.isEditable}")
            if (!it.isExecutable) {
                menus.remove(OptionMenu.RUN_REPEATEDLY)
                menus.remove(OptionMenu.TIMED_TASK)
                menus.remove(OptionMenu.CREATE_SHORTCUT)
                menus.remove(OptionMenu.OPEN_BY_OTHER_APPS)
                menus.remove(OptionMenu.BUILD_APK)
            }
            if (!it.canDelete()) {
                menus.remove(OptionMenu.DELETE)
            }
            if (!it.canRename()) {
                menus.remove(OptionMenu.RENAME)
            }
            if (it !is ExplorerSampleItem) {
                menus.remove(OptionMenu.RESET_TO_INITIAL_CONTENT)
            }
        }
        menus.forEach {
            DropdownMenuItem(
                text = { Text(text = stringResource(it.resId)) },
                onClick = { onMenuSelect(it) }
            )
        }
    }
}

@Composable
fun ExplorerViewKt.OptionMenu2(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onMenuSelect: (OptionMenu) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val menus = setOf(
            OptionMenu.RENAME,
            OptionMenu.DELETE,
        )
        menus.forEach {
            DropdownMenuItem(
                text = { Text(text = stringResource(it.resId)) },
                onClick = { onMenuSelect(it) }
            )
        }
    }
}

enum class OptionMenu(val resId: Int) {
    RUN_REPEATEDLY(R.string.text_run_repeatedly),
    RENAME(R.string.text_rename),
    DELETE(R.string.text_delete),
    SEND(R.string.text_send),
    RESET_TO_INITIAL_CONTENT(R.string.text_reset_to_initial_content),

    TIMED_TASK(R.string.text_timed_task),
    CREATE_SHORTCUT(R.string.text_send_shortcut),
    OPEN_BY_OTHER_APPS(R.string.text_open_by_other_apps),
    BUILD_APK(R.string.text_build_apk),
}