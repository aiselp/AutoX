package com.aiselp.autox.api.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.aiselp.autox.api.ui.ComposeElement
import com.aiselp.autox.api.ui.Render
import com.aiselp.autox.api.ui.component.TextField.parseKeyboardOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

internal object OutlinedTextField : VueNativeComponent {
    override val tag: String = "OutlinedTextField"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val value = remember { mutableStateOf("") }
        LaunchedEffect(element.getProp("value")) {
            delay(100)
            yield()
            value.value = element.getProp("value") ?: ""
        }
        val onValueChange = element.getEvent("onValueChange")
        val enabled: Boolean? = element.getProp("enabled")
        val readOnly: Boolean? = element.getProp("readOnly")
        val label = element.findTemplate("label") ?: run {
            element.getProp<Any>("label")?.let {
                ComposeElement("text").apply {
                    setProp("text", it)
                }
            }
        }
        val keyboardOptions = parseKeyboardOptions(element)
        val isError: Boolean? = element.getProp("isError")
        val singleLine: Boolean? = element.getProp("singleLine")
        val placeholder = element.findTemplate("placeholder") ?: run {
            element.getProp<Any>("placeholder")?.let {
                ComposeElement("text").apply {
                    setProp("text", it)
                }
            }
        }
        val supportingText = element.findTemplate("supportingText") ?: run {
            element.getProp<Any>("supportingText")?.let {
                ComposeElement("text").apply {
                    setProp("text", it)
                }
            }
        }
        val leadingIcon = element.findTemplate("leadingIcon") ?: run {
            element.getProp<Any>("leadingIcon")?.let {
                ComposeElement("Icon").apply {
                    setProp("src", it)
                    setProp("tint", MaterialTheme.colorScheme.primary)
                }
            }
        }
        KeyboardOptions()
        val trailingIcon = element.findTemplate("trailingIcon") ?: run {
            element.getProp<Any>("trailingIcon")?.let {
                ComposeElement("Icon").apply {
                    setProp("src", it)
                    setProp("tint", MaterialTheme.colorScheme.primary)
                }
            }
        }
        val prefix = element.findTemplate("prefix") ?: run {
            element.getProp<Any>("prefix")?.let {
                ComposeElement("text").apply {
                    setProp("text", it)
                }
            }
        }
        val suffix = element.findTemplate("suffix") ?: run {
            element.getProp<Any>("suffix")?.let {
                ComposeElement("text").apply {
                    setProp("text", it)
                }
            }
        }
        val visualTransformation = if (element.getProp<Any>("keyboardType") == "password") {
            PasswordVisualTransformation()
        } else VisualTransformation.None
        val maxLines: Int? = element.getProp("maxLines")
        val minLines: Int? = element.getProp("minLines")
        OutlinedTextField(
            value = value.value,
            onValueChange = { value.value = it;onValueChange?.invoke(it) },
            modifier = modifier,
            enabled = enabled ?: true,
            readOnly = readOnly ?: false,
            leadingIcon = leadingIcon?.let { { it.Render() } },
            trailingIcon = trailingIcon?.let { { it.Render() } },
            label = label?.let { { it.Render() } },
            prefix = prefix?.let { { it.Render() } },
            suffix = suffix?.let { { it.Render() } },
            visualTransformation = visualTransformation,
            placeholder = placeholder?.let { { it.Render() } },
            supportingText = supportingText?.let { { it.Render() } },
            isError = isError ?: false,
            singleLine = singleLine ?: false,
            keyboardOptions = keyboardOptions,
            maxLines = maxLines ?: if (singleLine == true) 1 else Int.MAX_VALUE,
            minLines = minLines ?: 1,
        )
    }

}