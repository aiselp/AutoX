package com.aiselp.autox.api.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiselp.autox.api.ui.ComposeElement

internal object Dialog : VueNativeComponent {

    override val tag: String = "Dialog"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val properties = DialogProperties(
            dismissOnBackPress = element.getProp("dismissOnBackPress") ?: true,
            dismissOnClickOutside = element.getProp("dismissOnClickOutside") ?: true,
            usePlatformDefaultWidth = element.getProp("usePlatformDefaultWidth") ?: true,
            decorFitsSystemWindows = element.getProp("decorFitsSystemWindows") ?: true
        )
        Dialog(
            onDismissRequest = { element.getEvent("onDismissRequest")?.invoke() },
            properties = properties
        ) {
            content()
        }
    }

}