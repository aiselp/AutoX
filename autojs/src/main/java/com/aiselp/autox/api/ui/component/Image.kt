package com.aiselp.autox.api.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.aiselp.autox.api.ui.ComposeElement

internal object Image : VueNativeComponent {
    override val tag: String = "Image"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val src = element.getProp<Any>("src")
        val alpha = parseFloat(element.getProp("alpha")) ?: DefaultAlpha
        val alignment = parseAlignment(element.getProp("alignment")) ?: Alignment.Center
        val contentDescription: String? = element.getProp("contentDescription")
        val model = if (src is String) {
            parseDrawable(src)?.let {
                painterResource(it)
            } ?: src
        } else src
        when (model) {
            is ImageVector -> Image(
                modifier = modifier,
                imageVector = model,
                alpha = alpha,
                alignment = alignment,
                contentDescription = contentDescription,
            )

            is Painter -> Image(
                modifier = modifier,
                painter = model,
                alpha = alpha,
                alignment = alignment,
                contentDescription = contentDescription,
            )

            else -> AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = parseContentScale(element.getProp("contentScale")),
                alpha = alpha,
                alignment = alignment,
            )
        }
    }

}