package com.aiselp.autox.api.ui.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement
import com.aiselp.autox.api.ui.RenderColumn

internal object FlowColumn : VueNativeComponent {
    override val tag: String = "FlowColumn"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val maxItemsInEachColumn: Int? = element.getProp("maxItemsInEachColumn")
        FlowColumn(
            modifier = modifier,
            verticalArrangement = parseVerticalArrangement(element.getProp("verticalArrangement")),
            horizontalArrangement = parseHorizontalArrangement(element.getProp("horizontalArrangement")),
            maxItemsInEachColumn = maxItemsInEachColumn ?: Int.MAX_VALUE
        ) {
            element.children.forEach {
                RenderColumn(it)
            }
        }
    }

}