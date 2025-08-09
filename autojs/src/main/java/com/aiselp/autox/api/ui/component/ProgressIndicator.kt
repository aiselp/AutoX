package com.aiselp.autox.api.ui.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiselp.autox.api.ui.ComposeElement

internal object ProgressIndicator : VueNativeComponent {
    override val tag: String = "ProgressIndicator"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val type: String? = element.getProp("type")
        val color = parseColor(element.getProp("color"))
        val strokeWidth = parseFloat(element.getProp("strokeWidth"))?.dp
        val trackColor = parseColor(element.getProp("trackColor"))
        val progress = parseFloat(element.getProp("progress"))
        val indeterminate: Boolean? = element.getProp("indeterminate")
        if (type == "circular") {
            if (indeterminate == true) {
                CircularProgressIndicator(
                    modifier = modifier,
                    color ?: ProgressIndicatorDefaults.circularColor,
                    trackColor = trackColor ?: ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                    strokeWidth = strokeWidth ?: ProgressIndicatorDefaults.CircularStrokeWidth,
                )
            } else {
                CircularProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = modifier,
                    color ?: ProgressIndicatorDefaults.circularColor,
                    trackColor = trackColor ?: ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                    strokeWidth = strokeWidth ?: ProgressIndicatorDefaults.CircularStrokeWidth,
                )
            }
        }
        if (type == "linear") {
            if (indeterminate == true) {
                LinearProgressIndicator(
                    modifier = modifier,
                    color = color ?: ProgressIndicatorDefaults.linearColor,
                    trackColor = trackColor ?: ProgressIndicatorDefaults.linearTrackColor,
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = modifier,
                    color = color ?: ProgressIndicatorDefaults.linearColor,
                    trackColor = trackColor ?: ProgressIndicatorDefaults.linearTrackColor,
                )
            }
        }
    }
}