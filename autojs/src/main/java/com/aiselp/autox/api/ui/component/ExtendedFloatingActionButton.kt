package com.aiselp.autox.api.ui.component

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement
import com.aiselp.autox.api.ui.ComposeTextNode
import com.aiselp.autox.api.ui.Render

internal object ExtendedFloatingActionButton : VueNativeComponent {
    override val tag: String = "ExtendedFloatingActionButton"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val text = element.findTemplate("text") ?: (ComposeTextNode(
            element.getProp("text") ?: ""
        ))
        val icon = element.findTemplate("icon") ?: (ComposeElement(Icon.tag).apply {
            setProp("src", element.getProp("icon"))
        })
        val expanded: Boolean? = element.getProp("expanded")
        ExtendedFloatingActionButton(
            expanded = expanded ?: true,
            text = { text.Render() },
            icon = { icon.Render() },
            onClick = { element.getEvent("onClick")?.invoke() },
            modifier = modifier,
        )
    }

}