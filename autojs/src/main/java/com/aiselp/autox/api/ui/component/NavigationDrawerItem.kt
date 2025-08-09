package com.aiselp.autox.api.ui.component

import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement
import com.aiselp.autox.api.ui.ComposeTextNode
import com.aiselp.autox.api.ui.Render

internal object NavigationDrawerItem : VueNativeComponent {
    override val tag: String = "NavigationDrawerItem"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val selected: Boolean? = element.getProp("selected")
        NavigationDrawerItem(
            modifier = modifier,
            selected = selected ?: false,
            onClick = { element.getEvent("onClick")?.invoke() },
            icon = {
                element.findTemplate("icon")?.Render() ?: run {
                    element.getProp<Any>("icon")?.let {
                        ComposeElement("Icon").apply {
                            setProp("src", it)
                        }.Render()
                    }
                }
            },
            label = {
                element.findTemplate("label")?.Render() ?: run {
                    element.getProp<String>("label")?.let {
                        ComposeTextNode(it).Render()
                    }
                }
            },
        )
    }

}