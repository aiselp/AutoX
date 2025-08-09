package com.aiselp.autox.api.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement

internal object Box : VueNativeComponent {
    override val tag: String = "box"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val contentAlignment = parseAlignment(element.getProp("contentAlignment"))
        Box(
            modifier = modifier,
            contentAlignment = contentAlignment?: Alignment.TopStart
        ) {
            content()
        }
    }
}