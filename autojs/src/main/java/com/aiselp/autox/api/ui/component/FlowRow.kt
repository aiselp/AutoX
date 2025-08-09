package com.aiselp.autox.api.ui.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement
import com.aiselp.autox.api.ui.RenderRow

internal object FlowRow : VueNativeComponent {
    override val tag: String = "FlowRow"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val maxItemsInEachRow: Int? = element.getProp("maxItemsInEachRow")
        FlowRow(
            modifier = modifier,
            verticalArrangement = parseVerticalArrangement(element.getProp("verticalArrangement")),
            horizontalArrangement = parseHorizontalArrangement(element.getProp("horizontalArrangement")),
            maxItemsInEachRow = maxItemsInEachRow ?: Int.MAX_VALUE
        ) {
            element.children.forEach {
                RenderRow(it)
            }
        }
    }
}