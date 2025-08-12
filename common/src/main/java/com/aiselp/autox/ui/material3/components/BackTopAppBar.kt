package com.aiselp.autox.ui.material3.components

import android.app.Activity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    val context = LocalContext.current
    TopAppBar(
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge )},
        navigationIcon = {
            IconButton(onClick = onBack ?: {
                (context as? Activity)?.finish();Unit
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
        }, actions = actions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuTopAppBar(
    title: String,
    showMenuButton: Boolean = true,
    openMenuRequest: (() -> Unit) = {},
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        title = { Text(text = title) },
        actions = actions,
        navigationIcon = {
            if (showMenuButton) {
                IconButton(onClick = openMenuRequest) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = null
                    )
                }
            }
        }
    )
}