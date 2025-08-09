package com.aiselp.autox.api.ui.component

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement

internal object RadioButton : VueNativeComponent {
    override val tag: String = "RadioButton"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val selected: Boolean? = element.getProp("selected")
        val enabled: Boolean? = element.getProp("enabled")
        RadioButton(
            selected = selected ?: false,
            onClick = { element.getEvent("onClick")?.invoke() },
            modifier = modifier,
            enabled = enabled ?: true
        )
    }

}