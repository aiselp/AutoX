package com.aiselp.autox.ui.material3.components

import android.content.Context
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.aiselp.autox.ui.material3.theme.AppTheme


class ComposeDialog(
    private val context: Context,
    content: (@Composable ComposeDialog.() -> Unit)? = null
) : ComponentDialog(context) {
    init {
        window?.decorView?.setBackgroundColor(0x00000000) // Transparent background
        if (content != null) {
            setContent { content() }
        }
    }

    fun setContent(
        content: @Composable () -> Unit
    ) {
        setContentView(ComposeView(context).apply {
            setContent {
                AppTheme {
                    content()
                }
            }
        })
    }

}

@Composable
fun ComposeDialog.AlertDialog(
    title: String,
    positiveText: String? = null,
    onPositiveClick: (() -> Unit)? = null,
    negativeText: String? = null,
    onNegativeClick: (() -> Unit)? = null,
    neutralText: String? = null,
    onNeutralClick: (() -> Unit)? = null,
    content: String,
) {
    BaseDialog(
        title = { DialogTitle(title) },
        positiveText = positiveText,
        onPositiveClick = onPositiveClick,
        negativeText = negativeText,
        onNegativeClick = onNegativeClick,
        neutralText = neutralText,
        onNeutralClick = onNeutralClick
    ) {
        DialogText(text = content)
    }
}