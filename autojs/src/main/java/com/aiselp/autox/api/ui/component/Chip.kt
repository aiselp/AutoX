package com.aiselp.autox.api.ui.component

import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiselp.autox.api.ui.ComposeElement
import com.aiselp.autox.api.ui.ComposeTextNode
import com.aiselp.autox.api.ui.Render

internal object Chip : VueNativeComponent {
    override val tag: String = "Chip"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val type: String? = element.getProp("type")
        val style: String? = element.getProp("style")
        val enabled: Boolean = element.getProp("enabled") ?: true
        val selected: Boolean = element.getProp("selected") ?: false
        val onClick = element.getEvent("onClick")
        val label = element.findTemplate("label") ?: run {
            ComposeTextNode(element.getProp("label") ?: "")
        }
        val leadingIcon = element.findTemplate("leadingIcon") ?: run {
            if (element.getProp<Any>("leadingIcon") != null) {
                ComposeElement(Icon.tag).apply {
                    setProp("src", element.getProp("leadingIcon"))
                }
            } else null
        }
        val trailingIcon = element.findTemplate("trailingIcon") ?: run {
            if (element.getProp<Any>("trailingIcon") != null) {
                ComposeElement(Icon.tag).apply {
                    setProp("src", element.getProp("trailingIcon"))
                }
            } else null
        }
        val icon = element.findTemplate("'icon'") ?: run {
            if (element.getProp<Any>("icon") != null) {
                ComposeElement(Icon.tag).apply {
                    setProp("src", element.getProp("icon"))
                }
            } else null
        }
        if (style == "elevated") {
            when (type) {
                "assist" -> ElevatedAssistChip(
                    onClick = { onClick?.invoke() },
                    label = { label.Render() },
                    modifier = modifier,
                    leadingIcon = leadingIcon?.let { { it.Render() } },
                    trailingIcon = trailingIcon?.let { { it.Render() } },
                    enabled = enabled,
                )

                "filter" -> ElevatedFilterChip(
                    selected = selected,
                    onClick = { onClick?.invoke() },
                    label = { label.Render() },
                    modifier = modifier,
                    leadingIcon = leadingIcon?.let { { it.Render() } },
                    trailingIcon = trailingIcon?.let { { it.Render() } },
                    enabled = enabled,
                )

                "suggestion" -> ElevatedSuggestionChip(
                    onClick = { onClick?.invoke() },
                    label = { label.Render() },
                    modifier = modifier,
                    icon = icon?.let { { it.Render() } },
                    enabled = enabled,
                )
            }
            return
        }
        when (type) {
            "assist" -> AssistChip(
                onClick = { onClick?.invoke() },
                label = { label.Render() },
                modifier = modifier,
                leadingIcon = leadingIcon?.let { { it.Render() } },
                trailingIcon = trailingIcon?.let { { it.Render() } },
                enabled = enabled,
            )

            "filter" -> FilterChip(
                selected = selected,
                onClick = { onClick?.invoke() },
                label = { label.Render() },
                modifier = modifier,
                leadingIcon = leadingIcon?.let { { it.Render() } },
                trailingIcon = trailingIcon?.let { { it.Render() } },
                enabled = enabled,
            )

            "input" -> kotlin.run {
                val avatar = element.findTemplate("avatar") ?: run {
                    if (element.getProp<Any>("avatar") != null) {
                        ComposeElement(Icon.tag).apply {
                            setProp("src", element.getProp("avatar"))
                        }
                    } else null
                }
                InputChip(
                    selected = selected,
                    onClick = { onClick?.invoke() },
                    label = { label.Render() },
                    modifier = modifier,
                    leadingIcon = leadingIcon?.let { { it.Render() } },
                    avatar = avatar?.let { { it.Render() } },
                    trailingIcon = trailingIcon?.let { { it.Render() } },
                    enabled = enabled,
                )
            }

            "suggestion" -> SuggestionChip(
                onClick = { onClick?.invoke() },
                label = { label.Render() },
                modifier = modifier,
                enabled = enabled,
                icon = icon?.let { { it.Render() } }
            )
        }
    }

}