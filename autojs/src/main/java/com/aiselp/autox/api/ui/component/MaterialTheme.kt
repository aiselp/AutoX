package com.aiselp.autox.api.ui.component

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aiselp.autox.api.ui.ComposeElement

internal object MaterialTheme : VueNativeComponent {
    override val tag: String = "MaterialTheme"

    @Composable
    override fun Render(
        modifier: Modifier,
        element: ComposeElement,
        content: @Composable () -> Unit
    ) {
        val lightColorScheme: ColorScheme? = element.getProp("lightColorScheme")
        val darkColorScheme: ColorScheme? = element.getProp("darkColorScheme")
        val dynamicColor = element.getProp("dynamicColor") ?: false
        val dark = when (element.getProp<Any>("dark")) {
            true -> true
            false -> false
            else -> isSystemInDarkTheme()
        }
        val context = LocalContext.current
        val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) darkColorScheme else lightColorScheme
        } ?: MaterialTheme.colorScheme

        val statusBarColor = parseColor(element.getProp("statusBarColor"))
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                window.statusBarColor = statusBarColor?.toArgb() ?: colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            }
        }

        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}