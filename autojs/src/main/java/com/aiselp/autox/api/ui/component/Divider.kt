package com.aiselp.autox.api.ui.component

import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiselp.autox.api.ui.ComposeElement

internal object Divider : VueNativeComponent {
    override val tag: String = "Divider"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val thickness = parseFloat(element.getProp("thickness"))
        val color = parseColor(element.getProp("color"))
        val type: String? = element.getProp("type")
        when (type) {
            "horizontal" -> HorizontalDivider(
                modifier = modifier,
                color = color ?: DividerDefaults.color,
                thickness = thickness?.dp ?: DividerDefaults.Thickness
            )

            "vertical" -> VerticalDivider(
                modifier = modifier,
                color = color ?: DividerDefaults.color,
                thickness = thickness?.dp ?: DividerDefaults.Thickness
            )
        }
    }

}