package com.aiselp.autox.api.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement

internal object Slider : VueNativeComponent {
    override val tag: String = "Slider"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val enabled: Boolean? = element.getProp("enabled")
        val value = parseFloat(element.getProp("value"))
        val steps = parseFloat(element.getProp("steps"))?.toInt()
        val onValueChangeFinished = element.getEvent("onValueChangeFinished")
        val onValueChange = element.getEvent("onValueChange")
        val min = parseFloat(element.getProp("min"))
        val max = parseFloat(element.getProp("max"))
        val valueRange = if (max != null && min != null) min..max else 0f..1f

        Slider(
            modifier = modifier,
            value = value ?: 0f,
            onValueChange = { onValueChange?.invoke(it) },
            enabled = enabled ?: true,
            onValueChangeFinished = { onValueChangeFinished?.invoke() },
            steps = steps ?: 0,
            valueRange = valueRange,
        )
    }

}