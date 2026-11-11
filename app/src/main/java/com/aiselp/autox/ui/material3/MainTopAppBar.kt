package com.aiselp.autox.ui.material3

import android.content.Intent
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.aiselp.autojs.codeeditor.EditActivity
import com.aiselp.autox.ui.material3.components.MenuTopAppBar
import org.autojs.autojs.ui.log.LogActivityKt
import org.autojs.autojs.ui.main.BottomNavigationItem
import org.autojs.autoxjs.R


@Composable
fun MainTopAppBar(
    openMenuRequest: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    MenuTopAppBar(
        title = stringResource(id = R.string.app_name),
        openMenuRequest = openMenuRequest,
        actions = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                EditorButton()
            }
            LogButton()
            actions()
        }
    )
}

@Composable
private fun EditorButton() {
    val context = LocalContext.current
    IconButton(onClick = {
        context.startActivity(Intent(context, EditActivity::class.java))
    }) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "editor",
            tint = Color(0xFF996231)
        )
    }
}

//主界面日志按钮
@Composable
private fun LogButton() {
    val context = LocalContext.current
    IconButton(onClick = { LogActivityKt.start(context) }) {
        Icon(
            painter = painterResource(id = R.drawable.ic_logcat),
            contentDescription = stringResource(id = R.string.text_logcat),
            tint = Color(0xFF005BC9)
        )
    }
}

@Composable
fun BottomBar(
    items: List<BottomNavigationItem>,
    currentSelected: Int,
    onSelectedChange: (Int) -> Unit
) {
    NavigationBar {
        items.forEachIndexed { i, item ->
            NavigationBarItem(
                selected = i == currentSelected,
                onClick = { onSelectedChange(i) },
                label = { Text(text = item.label) },
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = item.label
                    )
                })
        }
    }
}