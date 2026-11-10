package com.aiselp.autox.ui.log

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.stardust.autojs.R

/**
 * 日志页面顶栏上的操作：「复制全部」按钮和一个包含多选、保存为文件、分享的更多菜单。
 *
 * @param showCopyAllButton 为 false 时顶栏上不放「复制全部」按钮，改为放进更多菜单（顶栏空间紧张时使用）
 */
@Composable
fun ConsoleLogActions(controller: ConsoleLogController, showCopyAllButton: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    if (showCopyAllButton) {
        IconButton(onClick = controller::copyAll) {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = stringResource(R.string.text_log_copy_all)
            )
        }
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.text_log_more_actions)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!showCopyAllButton) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.text_log_copy_all)) },
                    onClick = {
                        expanded = false
                        controller.copyAll()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.text_log_multi_select)) },
                onClick = {
                    expanded = false
                    controller.enterSelectionMode()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.text_log_save_to_file)) },
                onClick = {
                    expanded = false
                    controller.saveToFile()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.text_log_share)) },
                onClick = {
                    expanded = false
                    controller.share()
                }
            )
        }
    }
}

/**
 * 多选状态下替代普通顶栏的操作栏：显示已选行数，提供退出、全选和复制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleSelectionTopBar(controller: ConsoleLogController) {
    TopAppBar(
        title = { Text(stringResource(R.string.text_log_selected_count, controller.selectedCount)) },
        navigationIcon = {
            IconButton(onClick = controller::exitSelectionMode) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.text_log_exit_selection)
                )
            }
        },
        actions = {
            TextButton(onClick = controller::selectAll) {
                Text(stringResource(R.string.text_log_select_all))
            }
            TextButton(
                onClick = controller::copySelected,
                enabled = controller.selectedCount > 0
            ) {
                Text(stringResource(R.string.text_log_copy_selected))
            }
        }
    )
}
