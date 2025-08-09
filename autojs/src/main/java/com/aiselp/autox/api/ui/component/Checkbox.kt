package com.aiselp.autox.api.ui.component

import androidx.compose.material3.Checkbox
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import com.aiselp.autox.api.ui.ComposeElement

internal object Checkbox : VueNativeComponent {
    override val tag: String = "Checkbox"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val status = when (element.getProp<String>("status")) {
            null -> null
            "on" -> ToggleableState.On
            "off" -> ToggleableState.Off
            "indeterminate" -> ToggleableState.Indeterminate
            else -> null
        }
        val checked: Boolean? = element.getProp("checked")
        val onCheckedChange = element.getEvent("onCheckedChange")
        val onClick = element.getEvent("onClick")
        val enabled: Boolean? = element.getProp("enabled")
        if (status != null) {
            TriStateCheckbox(
                state = status,
                onClick = onClick?.let { { onClick.invoke() } },
                modifier = modifier,
                enabled = enabled ?: true
            )
            return
        }
        Checkbox(
            checked = checked ?: false,
            onCheckedChange = onCheckedChange?.let { { onCheckedChange.invoke(it) } },
            modifier = modifier,
            enabled = enabled ?: true
        )
    }
}